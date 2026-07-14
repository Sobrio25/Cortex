package com.aiagents.app.data.remote

import android.util.Log
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

class OllamaClient(
    private val okHttpClient: OkHttpClient,
    baseUrl: String
) : AIClient {

    private val effectiveBaseUrl = if (baseUrl.isBlank()) {
        "http://localhost:11434/"
    } else {
        if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    }

    init {
        Log.d("OllamaClient", "Using base URL: $effectiveBaseUrl")
    }

    private val api: OllamaApi = Retrofit.Builder()
        .baseUrl(effectiveBaseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OllamaApi::class.java)

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
            Log.d("OllamaClient", "Starting chat with model: $model, tools=${tools.size}")
            
            val allMessages = mutableListOf(
                ChatMessage("system", systemPrompt)
            )
            allMessages.addAll(messages)
            
            // Ollama requiere que las tools estén en formato específico
            val formattedTools = if (tools.isEmpty()) null else formatToolsForOllama(tools)
            
            if (formattedTools != null) {
                Log.d("OllamaClient", "Sending ${formattedTools.size} tools to Ollama")
                formattedTools.forEach { tool ->
                    val func = tool["function"] as? Map<*, *>
                    Log.d("OllamaClient", "Tool: ${func?.get("name")}")
                }
            }
            
            val request = OllamaRequest(
                model = model,
                messages = allMessages,
                stream = false,
                options = OllamaOptions(
                    temperature = temperature,
                    numPredict = maxTokens
                ),
                tools = formattedTools
            )
            
            Log.d("OllamaClient", "Sending request to Ollama API...")
            val response = api.chat(request)
            
            val content = response.message?.content
            val toolCalls = response.message?.toolCalls?.map { tc ->
                // Convertir el Map de argumentos a JSON string
                val argumentsJson = tc.function?.arguments?.let { Gson().toJson(it) } ?: "{}"
                ToolCall(
                    id = tc.id ?: java.util.UUID.randomUUID().toString(),
                    type = "function",
                    function = ToolFunction(
                        name = tc.function?.name ?: "",
                        arguments = argumentsJson
                    )
                )
            }
            
            // Extract reasoning from multiple possible sources
            val reasoningFromField = response.message?.reasoning
                ?: response.message?.thinking
            
            // Extract reasoning from <think> tags if exists
            val reasoningFromTags = extractThinkingFromContent(content)
            
            // Clean content if it has <think> tags
            val cleanContent = removeThinkingTags(content)
            
            // Use the reasoning that's not null/empty
            val finalReasoning = reasoningFromField?.ifBlank { null } 
                ?: reasoningFromTags?.ifBlank { null }
            
            Log.d("OllamaClient", "Response received: hasContent=${!cleanContent.isNullOrBlank()}, tools=${toolCalls?.size ?: 0}")
            if (toolCalls?.isNotEmpty() == true) {
                Log.d("OllamaClient", "Tool calls detected: ${toolCalls.map { it.function.name }}")
            }
            
            Result.success(ChatResponseWithTools(cleanContent, toolCalls, null, finalReasoning))
        } catch (e: Exception) {
            Log.e("OllamaClient", "Error in chat", e)
            Result.failure(e)
        }
    }

    /**
     * Ollama usa un formato similar a OpenAI para tools, pero necesitamos asegurarnos
     * de que el campo 'parameters' esté correctamente formateado.
     */
    private fun formatToolsForOllama(tools: List<Map<String, Any>>): List<Map<String, Any>> {
        return tools.map { tool ->
            val function = tool["function"] as? Map<String, Any> ?: return@map tool
            
            // Asegurarnos de que parameters tenga el formato correcto
            val parameters = function["parameters"] as? Map<String, Any>
            if (parameters != null) {
                // El formato ya es correcto
                tool
            } else {
                Log.w("OllamaClient", "Tool ${function["name"]} has invalid parameters format")
                tool
            }
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
        val allMessages = mutableListOf(ChatMessage("system", systemPrompt))
        allMessages.addAll(messages)

        val formattedTools = if (tools.isEmpty()) null else formatToolsForOllama(tools)

        val requestMap = mutableMapOf<String, Any?>(
            "model" to model,
            "messages" to allMessages.map { mapOf("role" to it.role, "content" to it.content) },
            "stream" to true,
            "options" to mapOf("temperature" to temperature, "num_predict" to maxTokens)
        )
        if (formattedTools != null) requestMap["tools"] = formattedTools

        val body = Gson().toJson(requestMap)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${effectiveBaseUrl}api/chat")
            .post(body)
            .build()

        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            emit(StreamingChunk(error = "Ollama error: ${response.code}"))
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
        var accToolCalls: List<ToolCall>? = null

        try {
            reader.useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    try {
                        val json = JsonParser.parseString(line).asJsonObject
                        val done = json.get("done")?.asBoolean ?: false
                        val msg = json.getAsJsonObject("message")

                        if (msg != null) {
                            val delta = msg.get("content")?.asString
                            if (!delta.isNullOrEmpty()) {
                                contentBuilder.append(delta)
                                emit(StreamingChunk(content = delta))
                            }

                            // Tool calls come in the final message when done=true
                            val tcs = msg.getAsJsonArray("tool_calls")
                            if (tcs != null && tcs.size() > 0) {
                                accToolCalls = tcs.map { tc ->
                                    val fn = tc.asJsonObject.getAsJsonObject("function")
                                    ToolCall(
                                        id = java.util.UUID.randomUUID().toString(),
                                        type = "function",
                                        function = ToolFunction(
                                            name = fn?.get("name")?.asString ?: "",
                                            arguments = fn?.get("arguments")?.let { Gson().toJson(it) } ?: "{}"
                                        )
                                    )
                                }
                            }
                        }

                        if (done) break
                    } catch (e: Exception) {
                        Log.w("OllamaClient", "Error parsing stream chunk: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            emit(StreamingChunk(error = "Stream error: ${e.message}"))
            return@flow
        } finally {
            response.close()
        }

        // Handle <think> tags
        val fullContent = contentBuilder.toString()
        val thinkingFromTags = extractThinkingFromContent(fullContent)
        if (thinkingFromTags != null) {
            emit(StreamingChunk(reasoning = thinkingFromTags))
        }

        emit(StreamingChunk(done = true, toolCalls = accToolCalls))
    }.flowOn(Dispatchers.IO)

    override suspend fun getAvailableModels(): Result<List<String>> {
        return getAvailableModelInfos().map { models -> models.map { it.id } }
    }

    override suspend fun getAvailableModelInfos(): Result<List<RemoteModelInfo>> {
        return try {
            val response = api.getModels()
            val models = response.models.map { model ->
                val contextWindow = runCatching {
                    api.showModel(OllamaShowRequest(model.name)).modelInfo.entries
                        .firstNotNullOfOrNull { (key, value) ->
                            if (key.endsWith(".context_length")) {
                                (value as? Number)?.toInt()?.takeIf { it > 0 }
                            } else {
                                null
                            }
                        }
                }.getOrNull()
                RemoteModelInfo(id = model.name, contextWindow = contextWindow)
            }
            Log.d("OllamaClient", "Available models: ${models.size}")
            Result.success(models)
        } catch (e: Exception) {
            Log.e("OllamaClient", "Error getting models from Ollama", e)
            Result.failure(e)
        }
    }

    interface OllamaApi {
        @POST("api/chat")
        suspend fun chat(
            @Body request: OllamaRequest
        ): OllamaResponse

        @GET("api/tags")
        suspend fun getModels(): OllamaModelsResponse

        @POST("api/show")
        suspend fun showModel(@Body request: OllamaShowRequest): OllamaShowResponse
    }
}

data class OllamaRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val options: OllamaOptions? = null,
    val tools: List<Map<String, Any>>? = null
)

data class OllamaOptions(
    val temperature: Float? = null,
    val numPredict: Int? = null
)

data class OllamaResponse(
    val message: OllamaMessage? = null
)

data class OllamaMessage(
    val role: String,
    val content: String?,
    @SerializedName("tool_calls")
    val toolCalls: List<OllamaToolCall>? = null,
    val reasoning: String? = null,
    val thinking: String? = null
)

data class OllamaToolCall(
    val id: String? = null,
    val function: OllamaToolFunction? = null
)

data class OllamaToolFunction(
    val name: String? = null,
    val arguments: Map<String, Any>? = null
)

data class OllamaModelsResponse(
    val models: List<OllamaModel>
)

data class OllamaModel(
    val name: String
)

data class OllamaShowRequest(
    val model: String
)

data class OllamaShowResponse(
    @SerializedName("model_info")
    val modelInfo: Map<String, Any> = emptyMap()
)
