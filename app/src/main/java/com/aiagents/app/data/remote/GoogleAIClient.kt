package com.aiagents.app.data.remote

import android.util.Log
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

class GoogleAIClient(
    private val okHttpClient: OkHttpClient,
    private val apiKey: String,
    private val oauthToken: String? = null
) : AIClient {
    private val useOAuth = !oauthToken.isNullOrBlank()

    private val api: GoogleAiApi = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/v1beta/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GoogleAiApi::class.java)

    override suspend fun chat(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int
    ): Result<String> {
        return chatWithTools(model, messages, systemPrompt, temperature, maxTokens, emptyList()).map { it.content ?: "" }
    }

    override suspend fun chatWithTools(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): Result<ChatResponseWithTools> {
        return try {
            Log.d("GoogleAIClient", "Starting chat with model: $model")
            
            val gson = com.google.gson.Gson()
            val contents = mutableListOf<GoogleContent>()
            // Track tool call IDs converted to text (missing thoughtSignature)
            val textConvertedToolCallIds = mutableSetOf<String>()

            for (msg in messages) {
                when {
                    // Tool result messages → functionResponse (formato Gemini)
                    msg.role == "tool" -> {
                        // If corresponding functionCall was converted to text (no thoughtSignature),
                        // convert this functionResponse to text too
                        if (msg.toolCallId != null && msg.toolCallId in textConvertedToolCallIds) {
                            val toolName = msg.name ?: "tool"
                            val lastContent = contents.lastOrNull()
                            // Merge with previous user content if possible
                            if (lastContent?.role == "user" && lastContent.parts.all { it.text != null }) {
                                contents.removeAt(contents.lastIndex)
                                val mergedParts = lastContent.parts + GooglePart(text = "[$toolName result]: ${msg.content}")
                                contents.add(GoogleContent(role = "user", parts = mergedParts))
                            } else {
                                contents.add(GoogleContent(
                                    role = "user",
                                    parts = listOf(GooglePart(text = "[$toolName result]: ${msg.content}"))
                                ))
                            }
                        } else {
                            val parts = mutableListOf<GooglePart>()
                            parts.add(GooglePart(
                                functionResponse = GoogleFunctionResponse(
                                    name = msg.name ?: "execute_command",
                                    response = mapOf("content" to msg.content)
                                )
                            ))
                            // Si hay imagen adjunta, agregarla como inline_data
                            if (msg.imageDataUri != null) {
                                try {
                                    val mimeType = msg.imageDataUri
                                        .substringAfter("data:").substringBefore(";base64,")
                                    val b64 = msg.imageDataUri.substringAfter(";base64,")
                                    if (mimeType.isNotBlank() && b64.isNotBlank()) {
                                        Log.d("GoogleAIClient", "Adding tool result image: mimeType=$mimeType")
                                        parts.add(GooglePart(inlineData = GoogleInlineData(mimeType, b64)))
                                    }
                                } catch (e: Exception) {
                                    Log.e("GoogleAIClient", "Error parsing image in tool result", e)
                                }
                            }
                            contents.add(GoogleContent(role = "user", parts = parts))
                        }
                    }
                    // Assistant messages with tool calls → functionCall parts
                    msg.role == "assistant" && !msg.toolCalls.isNullOrEmpty() -> {
                        val parts = mutableListOf<GooglePart>()
                        if (msg.content.isNotBlank()) {
                            parts.add(GooglePart(text = msg.content))
                        }

                        // Gemini 3+ requires thoughtSignature on functionCall parts.
                        // Old messages without signatures must be converted to text.
                        val hasSignatures = msg.toolCalls!!.any { it.thoughtSignature != null }

                        if (hasSignatures) {
                            for (tc in msg.toolCalls!!) {
                                val argsMap: Map<String, Any> = try {
                                    @Suppress("UNCHECKED_CAST")
                                    gson.fromJson(tc.function.arguments, Map::class.java) as? Map<String, Any> ?: emptyMap()
                                } catch (e: Exception) {
                                    emptyMap()
                                }
                                parts.add(GooglePart(
                                    functionCall = GoogleFunctionCall(
                                        name = tc.function.name,
                                        args = argsMap
                                    ),
                                    thoughtSignature = tc.thoughtSignature
                                ))
                            }
                        } else {
                            // No signatures - convert to text to avoid Gemini 3 API errors
                            for (tc in msg.toolCalls!!) {
                                parts.add(GooglePart(text = "[Called ${tc.function.name}(${tc.function.arguments})]"))
                                textConvertedToolCallIds.add(tc.id)
                            }
                        }
                        contents.add(GoogleContent(role = "model", parts = parts))
                    }
                    // System messages - skip (handled via systemInstruction)
                    msg.role == "system" -> { /* skip */ }
                    // Regular user/assistant messages
                    else -> {
                        val parts = mutableListOf<GooglePart>()
                        if (msg.content.isNotBlank()) {
                            parts.add(GooglePart(text = msg.content))
                        }
                        // Si hay imagen adjunta, agregarla como inline_data
                        if (msg.imageDataUri != null) {
                            try {
                                val mimeType = msg.imageDataUri
                                    .substringAfter("data:").substringBefore(";base64,")
                                val b64 = msg.imageDataUri.substringAfter(";base64,")
                                // Validar que tenemos datos válidos
                                if (mimeType.isNotBlank() && b64.isNotBlank()) {
                                    Log.d("GoogleAIClient", "Adding image: mimeType=$mimeType, base64 length=${b64.length}")
                                    parts.add(GooglePart(inlineData = GoogleInlineData(mimeType, b64)))
                                } else {
                                    Log.w("GoogleAIClient", "Invalid image data URI format")
                                }
                            } catch (e: Exception) {
                                Log.e("GoogleAIClient", "Error parsing image data URI", e)
                            }
                        }
                        if (parts.isNotEmpty()) {
                            contents.add(GoogleContent(
                                role = if (msg.role == "assistant") "model" else "user",
                                parts = parts
                            ))
                        }
                    }
                }
            }
            
            // Convertir tools de formato OpenAI a formato Gemini
            val geminiTools = if (tools.isEmpty()) {
                null
            } else {
                // Extraer solo functionDeclarations del formato OpenAI
                val functionDecls = tools.mapNotNull { tool ->
                    val function = tool["function"] as? Map<String, Any>
                    function?.let {
                        mapOf(
                            "name" to (it["name"] ?: ""),
                            "description" to (it["description"] ?: ""),
                            "parameters" to (it["parameters"] ?: emptyMap<String, Any>())
                        )
                    }
                }
                if (functionDecls.isEmpty()) null else listOf(mapOf("functionDeclarations" to functionDecls))
            }
            
            // Crear request manualmente como Map para evitar nulls
            val requestBody = mutableMapOf<String, Any>(
                "contents" to contents
            )
            
            // Solo agregar systemInstruction si no está vacío
            if (systemPrompt.isNotBlank()) {
                requestBody["systemInstruction"] = mapOf(
                    "parts" to listOf(mapOf("text" to systemPrompt))
                )
            }
            
            // Solo agregar generationConfig si tiene valores válidos
            val config = mutableMapOf<String, Any>()
            if (temperature > 0) config["temperature"] = temperature
            if (maxTokens > 0) config["maxOutputTokens"] = maxTokens
            if (config.isNotEmpty()) {
                requestBody["generationConfig"] = config
            }
            
            // Solo agregar tools si existen
            if (geminiTools != null) {
                requestBody["tools"] = geminiTools
            }
            
            // Convertir a JSON y luego a RequestBody
            val jsonString = gson.toJson(requestBody)
            Log.d("GoogleAIClient", "Request JSON preview: ${jsonString.take(500)}...")
            
            val requestBodyJson = jsonString.toRequestBody("application/json".toMediaType())
            val response = if (useOAuth) {
                api.generateContentOAuth(model, "Bearer $oauthToken", requestBodyJson)
            } else {
                api.generateContent(model, apiKey, requestBodyJson)
            }
            
            if (response.error != null) {
                Log.e("GoogleAIClient", "API Error ${response.error.code}: ${response.error.message}")
                Log.e("GoogleAIClient", "Full request that caused error: ${jsonString.take(2000)}")
                return Result.failure(Exception("Gemini API Error ${response.error.code}: ${response.error.message}"))
            }
            
            val candidate = response.candidates?.firstOrNull()
            val content = candidate?.content?.parts?.firstOrNull()?.text
            val toolCalls = candidate?.content?.parts?.filter { it.functionCall != null }?.map { part ->
                ToolCall(
                    id = java.util.UUID.randomUUID().toString(),
                    type = "function",
                    function = ToolFunction(
                        name = part.functionCall?.name ?: "",
                        arguments = com.google.gson.Gson().toJson(part.functionCall?.args ?: emptyMap<String, Any>())
                    ),
                    thoughtSignature = part.thoughtSignature
                )
            }
            val finishReason = candidate?.finishReason
            
            // Extract reasoning from <think> tags if present (some Gemini models may use this format)
            val reasoningFromTags = extractThinkingFromContent(content)
            
            // Clean content if it has <think> tags
            val cleanContent = removeThinkingTags(content)
            
            // Use the reasoning that's not null/empty
            val finalReasoning = reasoningFromTags?.ifBlank { null }
            
            Log.d("GoogleAIClient", "Response received: hasContent=${!cleanContent.isNullOrBlank()}, tools=${toolCalls?.size ?: 0}, hasReasoning=${!finalReasoning.isNullOrBlank()}")
            
            Result.success(ChatResponseWithTools(cleanContent, toolCalls, finishReason, finalReasoning))
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e("GoogleAIClient", "HTTP ${e.code()} error for model '$model': $errorBody")
            val errorMessage = try {
                val errorJson = org.json.JSONObject(errorBody ?: "{}")
                val err = errorJson.optJSONObject("error")
                err?.optString("message") ?: errorBody ?: e.message()
            } catch (_: Exception) {
                errorBody ?: e.message()
            }
            Result.failure(Exception("Gemini API Error ${e.code()}: $errorMessage"))
        } catch (e: Exception) {
            Log.e("GoogleAIClient", "Error in chat", e)
            Result.failure(e)
        }
    }

    override fun chatWithToolsStreaming(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): Flow<StreamingChunk> = flow {
        val gson = com.google.gson.Gson()

        // Build contents (same logic as chatWithTools but simplified — no textConvertedToolCallIds tracking)
        val contents = mutableListOf<Map<String, Any>>()
        for (msg in messages) {
            when {
                msg.role == "tool" -> {
                    contents.add(mapOf(
                        "role" to "user",
                        "parts" to listOf(mapOf(
                            "functionResponse" to mapOf(
                                "name" to (msg.name ?: "execute_command"),
                                "response" to mapOf("content" to msg.content)
                            )
                        ))
                    ))
                }
                msg.role == "assistant" && !msg.toolCalls.isNullOrEmpty() -> {
                    val parts = mutableListOf<Map<String, Any?>>()
                    if (msg.content.isNotBlank()) parts.add(mapOf("text" to msg.content))
                    for (tc in msg.toolCalls!!) {
                        val argsMap: Map<String, Any> = try {
                            @Suppress("UNCHECKED_CAST")
                            gson.fromJson(tc.function.arguments, Map::class.java) as? Map<String, Any> ?: emptyMap()
                        } catch (e: Exception) { emptyMap() }
                        val part = mutableMapOf<String, Any?>(
                            "functionCall" to mapOf("name" to tc.function.name, "args" to argsMap)
                        )
                        tc.thoughtSignature?.let { part["thoughtSignature"] = it }
                        parts.add(part)
                    }
                    contents.add(mapOf("role" to "model", "parts" to parts))
                }
                msg.role == "system" -> { /* skip */ }
                else -> {
                    val parts = mutableListOf<Map<String, Any?>>()
                    if (msg.content.isNotBlank()) parts.add(mapOf("text" to msg.content))
                    if (msg.imageDataUri != null) {
                        try {
                            val mimeType = msg.imageDataUri.substringAfter("data:").substringBefore(";base64,")
                            val b64 = msg.imageDataUri.substringAfter(";base64,")
                            if (mimeType.isNotBlank() && b64.isNotBlank()) {
                                parts.add(mapOf("inlineData" to mapOf("mimeType" to mimeType, "data" to b64)))
                            }
                        } catch (_: Exception) {}
                    }
                    if (parts.isNotEmpty()) {
                        contents.add(mapOf(
                            "role" to if (msg.role == "assistant") "model" else "user",
                            "parts" to parts
                        ))
                    }
                }
            }
        }

        val requestBody = mutableMapOf<String, Any>("contents" to contents)
        if (systemPrompt.isNotBlank()) {
            requestBody["systemInstruction"] = mapOf("parts" to listOf(mapOf("text" to systemPrompt)))
        }
        val config = mutableMapOf<String, Any>()
        if (temperature > 0) config["temperature"] = temperature
        if (maxTokens > 0) config["maxOutputTokens"] = maxTokens
        if (config.isNotEmpty()) requestBody["generationConfig"] = config

        if (tools.isNotEmpty()) {
            val functionDecls = tools.mapNotNull { tool ->
                @Suppress("UNCHECKED_CAST")
                val function = tool["function"] as? Map<String, Any>
                function?.let {
                    mapOf("name" to (it["name"] ?: ""), "description" to (it["description"] ?: ""), "parameters" to (it["parameters"] ?: emptyMap<String, Any>()))
                }
            }
            if (functionDecls.isNotEmpty()) requestBody["tools"] = listOf(mapOf("functionDeclarations" to functionDecls))
        }

        val jsonString = gson.toJson(requestBody)
        val body = jsonString.toRequestBody("application/json".toMediaType())

        val url = if (useOAuth) {
            "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent"
        } else {
            "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?key=$apiKey"
        }

        val requestBuilder = Request.Builder().url(url).post(body)
            .addHeader("Content-Type", "application/json")
        if (useOAuth) requestBuilder.addHeader("Authorization", "Bearer $oauthToken")

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            emit(StreamingChunk(error = "Gemini error ${response.code}: ${response.body?.string()}"))
            response.close()
            return@flow
        }

        val reader = response.body?.byteStream()?.bufferedReader()
        if (reader == null) {
            emit(StreamingChunk(error = "Empty response"))
            response.close()
            return@flow
        }

        val contentBuilder = StringBuilder()
        val toolCallsList = mutableListOf<ToolCall>()
        var finishReason: String? = null

        try {
            // Gemini streaming returns a JSON array, each element is a candidate chunk
            val fullText = reader.readText()
            response.close()

            // Parse as JSON array
            val jsonArray = JsonParser.parseString(fullText).asJsonArray
            for (element in jsonArray) {
                val obj = element.asJsonObject
                val candidates = obj.getAsJsonArray("candidates") ?: continue
                if (candidates.size() == 0) continue
                val candidate = candidates[0].asJsonObject
                finishReason = candidate.get("finishReason")?.asString
                val content = candidate.getAsJsonObject("content") ?: continue
                val parts = content.getAsJsonArray("parts") ?: continue

                for (part in parts) {
                    val partObj = part.asJsonObject
                    val text = partObj.get("text")?.asString
                    if (!text.isNullOrEmpty()) {
                        contentBuilder.append(text)
                        emit(StreamingChunk(content = text))
                    }
                    val fc = partObj.getAsJsonObject("functionCall")
                    if (fc != null) {
                        toolCallsList.add(ToolCall(
                            id = java.util.UUID.randomUUID().toString(),
                            type = "function",
                            function = ToolFunction(
                                name = fc.get("name")?.asString ?: "",
                                arguments = gson.toJson(fc.get("args") ?: com.google.gson.JsonObject())
                            ),
                            thoughtSignature = partObj.get("thoughtSignature")?.asString
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleAIClient", "Error reading Gemini stream", e)
            emit(StreamingChunk(error = "Stream error: ${e.message}"))
            return@flow
        }

        emit(StreamingChunk(
            done = true,
            toolCalls = toolCallsList.ifEmpty { null },
            finishReason = finishReason
        ))
    }.flowOn(Dispatchers.IO)

    override suspend fun getAvailableModels(): Result<List<String>> {
        return getAvailableModelInfos().map { models -> models.map { it.id } }
    }

    override suspend fun getAvailableModelInfos(): Result<List<RemoteModelInfo>> {
        return try {
            val response = if (useOAuth) {
                api.getModelsOAuth("Bearer $oauthToken")
            } else {
                api.getModels(apiKey)
            }
            val models = response.models
                .map { model ->
                    RemoteModelInfo(
                        id = model.name.removePrefix("models/"),
                        contextWindow = model.inputTokenLimit?.takeIf { it > 0 }
                    )
                }
                .filter { it.id.startsWith("gemini") }
                .sortedByDescending { it.id }
            if (models.isNotEmpty()) {
                Log.d("GoogleAIClient", "Fetched ${models.size} models from API: ${models.take(5).map { it.id }}")
                Result.success(models)
            } else {
                Log.w("GoogleAIClient", "API returned no Gemini models")
                Result.failure(IllegalStateException("Google AI no devolvió modelos Gemini"))
            }
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e("GoogleAIClient", "HTTP ${e.code()} fetching models: $errorBody")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("GoogleAIClient", "Error getting models", e)
            Result.failure(e)
        }
    }

    interface GoogleAiApi {
        @POST("models/{model}:generateContent")
        suspend fun generateContent(
            @Path("model") model: String,
            @Header("x-goog-api-key") apiKey: String,
            @Body request: RequestBody
        ): GoogleResponse

        @POST("models/{model}:generateContent")
        suspend fun generateContentOAuth(
            @Path("model") model: String,
            @Header("Authorization") authorization: String,
            @Body request: RequestBody
        ): GoogleResponse

        @GET("models")
        suspend fun getModels(
            @Header("x-goog-api-key") apiKey: String
        ): GoogleModelsResponse

        @GET("models")
        suspend fun getModelsOAuth(
            @Header("Authorization") authorization: String
        ): GoogleModelsResponse
    }
}

// Clases de respuesta (se mantienen igual)
data class GoogleContent(
    val role: String,
    val parts: List<GooglePart>
)

/**
 * Datos inline para imágenes en formato Gemini.
 * NOTA: Gemini usa camelCase para los nombres de campos.
 */
data class GoogleInlineData(
    @SerializedName("mimeType") val mimeType: String,
    val data: String  // base64 sin el prefijo data:image/...
)

data class GooglePart(
    val text: String? = null,
    val functionCall: GoogleFunctionCall? = null,
    val functionResponse: GoogleFunctionResponse? = null,
    @SerializedName("inlineData") val inlineData: GoogleInlineData? = null,
    val thoughtSignature: String? = null
)

data class GoogleFunctionResponse(
    val name: String,
    val response: Map<String, Any>
)

data class GoogleFunctionCall(
    val name: String? = null,
    val args: Map<String, Any>? = null
)

data class GoogleResponse(
    val candidates: List<GoogleCandidate>?,
    val error: GoogleError? = null
)

data class GoogleCandidate(
    val content: GoogleContentResponse,
    val finishReason: String? = null
)

data class GoogleContentResponse(
    val parts: List<GooglePart>,
    val role: String
)

data class GoogleError(
    val code: Int,
    val message: String,
    val status: String
)

data class GoogleModelsResponse(
    val models: List<GoogleModel>
)

data class GoogleModel(
    val name: String,
    val inputTokenLimit: Int? = null
)
