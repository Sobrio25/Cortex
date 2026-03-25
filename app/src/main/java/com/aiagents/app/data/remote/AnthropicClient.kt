package com.aiagents.app.data.remote

import android.util.Log
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

class AnthropicClient(
    private val okHttpClient: OkHttpClient,
    private val apiKey: String
) : AIClient {

    private val api: AnthropicApi = Retrofit.Builder()
        .baseUrl("https://api.anthropic.com/v1/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AnthropicApi::class.java)

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Any>>() {}.type

    /**
     * Converts tools from OpenAI format ({type:"function", function:{name, description, parameters}})
     * to Anthropic format ({name, description, input_schema}).
     * All tool handlers define tools in OpenAI format, so this conversion is required.
     */
    private fun convertToolsToAnthropicFormat(tools: List<Map<String, Any>>): List<Map<String, Any>> {
        return tools.map { tool ->
            @Suppress("UNCHECKED_CAST")
            val function = tool["function"] as? Map<String, Any>
            if (function != null) {
                // OpenAI format → Anthropic format
                mapOf(
                    "name" to (function["name"] ?: ""),
                    "description" to (function["description"] ?: ""),
                    "input_schema" to (function["parameters"] ?: mapOf("type" to "object"))
                )
            } else {
                // Already in Anthropic format (or unknown), pass through
                tool
            }
        }
    }

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
            Log.d("AnthropicClient", "Starting chat with model: $model, messages: ${messages.size}")

            val anthropicMessages = buildAnthropicMessages(messages)

            val anthropicTools = if (tools.isEmpty()) null else convertToolsToAnthropicFormat(tools)

            val request = AnthropicRequest(
                model = model,
                messages = anthropicMessages,
                maxTokens = maxTokens,
                system = systemPrompt.ifBlank { null },
                temperature = temperature,
                tools = anthropicTools
            )

            Log.d("AnthropicClient", "Sending request to API...")
            val response = api.messages(
                apiKey = apiKey,
                anthropicVersion = "2023-06-01",
                request = request
            )

            if (response.error != null) {
                Log.e("AnthropicClient", "API Error: ${response.error.message}")
                return Result.failure(Exception(response.error.message))
            }

            val content = response.content?.firstOrNull { it.type == "text" }?.text
            val toolCalls = response.content?.filter { it.type == "tool_use" }?.map { tc ->
                ToolCall(
                    id = tc.id ?: "",
                    type = "function",
                    function = ToolFunction(
                        name = tc.name ?: "",
                        arguments = if (tc.input != null) gson.toJson(tc.input) else "{}"
                    )
                )
            }
            val finishReason = response.stopReason
            val reasoning = response.content?.firstOrNull { it.type == "thinking" }?.thinking

            Log.d("AnthropicClient", "Response: content=${content?.take(100)}, tools=${toolCalls?.size ?: 0}")

            Result.success(ChatResponseWithTools(content, toolCalls, finishReason, reasoning))
        } catch (e: Exception) {
            Log.e("AnthropicClient", "Error in chat", e)
            Result.failure(e)
        }
    }

    /**
     * Convierte la lista de ChatMessage al formato correcto de Anthropic:
     *  - Mensajes ASSISTANT con tool_calls → incluye bloques tool_use
     *  - Mensajes TOOL → role "user" con bloque tool_result (incluye imagen si la hay)
     *  - Múltiples TOOL consecutivos → un solo mensaje "user" con varios tool_result
     */
    private fun buildAnthropicMessages(messages: List<ChatMessage>): List<AnthropicMessage> {
        val result = mutableListOf<AnthropicMessage>()
        var i = 0
        while (i < messages.size) {
            val msg = messages[i]

            if (msg.role == "tool") {
                // Agrupar todos los mensajes TOOL consecutivos en un solo user message
                val toolResultBlocks = mutableListOf<AnthropicContent>()
                while (i < messages.size && messages[i].role == "tool") {
                    val toolMsg = messages[i]
                    toolResultBlocks.add(buildToolResultBlock(toolMsg))
                    i++
                }
                result.add(AnthropicMessage(role = "user", content = toolResultBlocks))
            } else {
                result.add(buildRegularMessage(msg))
                i++
            }
        }
        return result
    }

    /** Construye el bloque tool_result para un mensaje TOOL. */
    private fun buildToolResultBlock(msg: ChatMessage): AnthropicContent {
        val innerBlocks = mutableListOf<AnthropicContent>()

        // Imagen del resultado (ej. read_image_file)
        val imgUri = msg.toolResultImageUri
        if (imgUri != null) {
            val mimeType = imgUri.substringAfter("data:").substringBefore(";base64,")
            val b64 = imgUri.substringAfter(";base64,")
            innerBlocks.add(
                AnthropicContent(
                    type = "image",
                    source = AnthropicImageSource(mediaType = mimeType, data = b64)
                )
            )
        }

        if (msg.content.isNotBlank()) {
            innerBlocks.add(AnthropicContent(type = "text", text = msg.content))
        }

        return AnthropicContent(
            type = "tool_result",
            toolUseId = msg.toolCallId,
            content = if (innerBlocks.isNotEmpty()) innerBlocks else null,
            text = if (innerBlocks.isEmpty() && msg.content.isNotBlank()) msg.content else null
        )
    }

    /** Construye un AnthropicMessage para mensajes user/assistant. */
    private fun buildRegularMessage(msg: ChatMessage): AnthropicMessage {
        val blocks = mutableListOf<AnthropicContent>()

        if (msg.content.isNotBlank()) {
            blocks.add(AnthropicContent(type = "text", text = msg.content))
        }

        // tool_use blocks para mensajes assistant con llamadas a herramientas
        msg.toolCalls?.forEach { toolCall ->
            val inputMap: Map<String, Any>? = try {
                gson.fromJson(toolCall.function.arguments, mapType)
            } catch (e: Exception) {
                null
            }
            blocks.add(
                AnthropicContent(
                    type = "tool_use",
                    id = toolCall.id,
                    name = toolCall.function.name,
                    input = inputMap
                )
            )
        }

        // Imagen vision (usuario adjunta imagen)
        val imgUri = msg.imageDataUri
        if (imgUri != null) {
            val mimeType = imgUri.substringAfter("data:").substringBefore(";base64,")
            val b64 = imgUri.substringAfter(";base64,")
            blocks.add(
                AnthropicContent(
                    type = "image",
                    source = AnthropicImageSource(mediaType = mimeType, data = b64)
                )
            )
        }

        if (blocks.isEmpty()) blocks.add(AnthropicContent(type = "text", text = ""))
        return AnthropicMessage(role = msg.role, content = blocks)
    }

    override fun chatWithToolsStreaming(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): Flow<StreamingChunk> {
        val anthropicMessages = buildAnthropicMessages(messages)

        val requestBody = mutableMapOf<String, Any?>(
            "model" to model,
            "messages" to anthropicMessages.map { msg ->
                mapOf(
                    "role" to msg.role,
                    "content" to msg.content.map { block ->
                        val map = mutableMapOf<String, Any?>("type" to block.type)
                        block.text?.let { map["text"] = it }
                        block.id?.let { map["id"] = it }
                        block.name?.let { map["name"] = it }
                        block.input?.let { map["input"] = it }
                        block.toolUseId?.let { map["tool_use_id"] = it }
                        block.content?.let { nested ->
                            map["content"] = nested.map { inner ->
                                val innerMap = mutableMapOf<String, Any?>("type" to inner.type)
                                inner.text?.let { innerMap["text"] = it }
                                inner.source?.let { src ->
                                    innerMap["source"] = mapOf(
                                        "type" to src.type,
                                        "media_type" to src.mediaType,
                                        "data" to src.data
                                    )
                                }
                                innerMap
                            }
                        }
                        block.source?.let { src ->
                            map["source"] = mapOf(
                                "type" to src.type,
                                "media_type" to src.mediaType,
                                "data" to src.data
                            )
                        }
                        map
                    }
                )
            },
            "max_tokens" to maxTokens,
            "temperature" to temperature,
            "stream" to true
        )
        if (systemPrompt.isNotBlank()) requestBody["system"] = systemPrompt
        if (tools.isNotEmpty()) requestBody["tools"] = convertToolsToAnthropicFormat(tools)

        return streamAnthropic(
            okHttpClient = okHttpClient,
            apiKey = apiKey,
            requestBody = requestBody
        )
    }

    override suspend fun getAvailableModels(): Result<List<String>> {
        return try {
            val response = api.getModels(
                apiKey = apiKey,
                anthropicVersion = "2023-06-01"
            )
            Result.success(response.data.map { it.id })
        } catch (e: Exception) {
            Log.e("AnthropicClient", "Error getting models, using defaults", e)
            Result.success(listOf(
                "claude-opus-4-6",
                "claude-sonnet-4-6",
                "claude-haiku-4-5",
                "claude-opus-4-5",
                "claude-sonnet-4-5"
            ))
        }
    }

    interface AnthropicApi {
        @POST("messages")
        suspend fun messages(
            @Header("x-api-key") apiKey: String,
            @Header("anthropic-version") anthropicVersion: String,
            @Body request: AnthropicRequest
        ): AnthropicResponse

        @GET("models")
        suspend fun getModels(
            @Header("x-api-key") apiKey: String,
            @Header("anthropic-version") anthropicVersion: String
        ): AnthropicModelsResponse
    }
}

data class AnthropicRequest(
    val model: String,
    val messages: List<AnthropicMessage>,
    @SerializedName("max_tokens")
    val maxTokens: Int,
    val system: String? = null,
    val temperature: Float? = null,
    val tools: List<Map<String, Any>>? = null
)

data class AnthropicMessage(
    val role: String,
    val content: List<AnthropicContent>
)

data class AnthropicImageSource(
    val type: String = "base64",
    @SerializedName("media_type") val mediaType: String,
    val data: String
)

data class AnthropicContent(
    val type: String = "text",
    val text: String? = null,
    val source: AnthropicImageSource? = null,
    val id: String? = null,
    val name: String? = null,
    val input: Map<String, Any>? = null,
    // Para bloques tool_result
    @SerializedName("tool_use_id") val toolUseId: String? = null,
    val content: List<AnthropicContent>? = null
)

data class AnthropicResponse(
    val id: String? = null,
    val content: List<AnthropicContentResponse>? = null,
    val error: AnthropicError? = null,
    @SerializedName("stop_reason")
    val stopReason: String? = null
)

data class AnthropicContentResponse(
    val type: String,
    val text: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: Map<String, Any>? = null,
    val thinking: String? = null
)

data class AnthropicError(
    val type: String? = null,
    val message: String
)

data class AnthropicModelsResponse(
    val data: List<AnthropicModelInfo>
)

data class AnthropicModelInfo(
    val id: String,
    val display_name: String? = null
)
