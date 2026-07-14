package com.aiagents.app.data.remote

import android.util.Log
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

class OpenRouterClient(
    private val okHttpClient: OkHttpClient,
    private val apiKey: String
) : AIClient {

    private val api: OpenRouterApi = Retrofit.Builder()
        .baseUrl("https://openrouter.ai/api/v1/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenRouterApi::class.java)

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
            Log.d("OpenRouterClient", "Starting chat with model: $model")
            
            val allMessages = mutableListOf(
                ChatMessage("system", systemPrompt)
            )
            allMessages.addAll(messages)
            
            val request = ChatRequest(
                model = model,
                messages = allMessages.map { it.toRequestFormat() },
                temperature = temperature,
                maxTokens = maxTokens,
                tools = if (tools.isEmpty()) null else tools
            )
            
            Log.d("OpenRouterClient", "Sending request to API...")
            val response = api.chat("Bearer $apiKey", request)
            
            if (response.error != null) {
                Log.e("OpenRouterClient", "API Error: ${response.error.message}")
                return Result.failure(Exception(response.error.message))
            }
            
            val choice = response.choices?.firstOrNull()
            val content = choice?.message?.content ?: ""
            val toolCalls = choice?.message?.toolCalls?.map { tc ->
                ToolCall(
                    id = tc.id,
                    type = tc.type ?: "function",
                    function = ToolFunction(
                        name = tc.function.name,
                        arguments = tc.function.arguments
                    )
                )
            }
            val finishReason = choice?.finishReason
            
            // Extraer reasoning de múltiples fuentes posibles
            // 1. Campo reasoning (OpenAI o1, etc.)
            // 2. Campo thinking (Anthropic, etc.)
            // 3. Tags <think> en el contenido (DeepSeek, etc.)
            val reasoningFromField = choice?.message?.reasoning
                ?: choice?.message?.thinking
            
            // Extraer reasoning de tags <think> si existe
            val reasoningFromTags = extractThinkingFromContent(content)
            
            // Limpiar el contenido si tiene tags <think>
            val cleanContent = removeThinkingTags(content)
            
            // Usar el reasoning que no sea null/vacío
            val finalReasoning = reasoningFromField?.ifBlank { null } 
                ?: reasoningFromTags?.ifBlank { null }
            
            Log.d("OpenRouterClient", "Response received: hasContent=${!cleanContent.isNullOrBlank()}, tools=${toolCalls?.size ?: 0}, hasReasoning=${!finalReasoning.isNullOrBlank()}")
            
            Result.success(ChatResponseWithTools(cleanContent, toolCalls, finishReason, finalReasoning))
        } catch (e: Exception) {
            Log.e("OpenRouterClient", "Error in chat", e)
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
    ): Flow<StreamingChunk> {
        val allMessages = mutableListOf(ChatMessage("system", systemPrompt))
        allMessages.addAll(messages)

        val requestBody = mutableMapOf<String, Any?>(
            "model" to model,
            "messages" to allMessages.map { it.toStreamingMap() },
            "temperature" to temperature,
            "max_tokens" to maxTokens,
            "stream" to true
        )
        if (tools.isNotEmpty()) requestBody["tools"] = tools

        return streamOpenAICompatible(
            okHttpClient = okHttpClient,
            url = "https://openrouter.ai/api/v1/chat/completions",
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            ),
            requestBody = requestBody
        ).withRealtimeThinkTagParsing()
    }

    override suspend fun getAvailableModels(): Result<List<String>> {
        return getAvailableModelInfos().map { models -> models.map { it.id } }
    }

    override suspend fun getAvailableModelInfos(): Result<List<RemoteModelInfo>> {
        return try {
            val response = api.getModels("Bearer $apiKey")
            Result.success(
                response.data.map { model ->
                    RemoteModelInfo(
                        id = model.id,
                        contextWindow = model.contextLength?.takeIf { it > 0 }
                    )
                }
            )
        } catch (e: Exception) {
            Log.e("OpenRouterClient", "Error getting models", e)
            Result.failure(e)
        }
    }

    interface OpenRouterApi {
        @POST("chat/completions")
        suspend fun chat(
            @Header("Authorization") authorization: String,
            @Body request: ChatRequest
        ): ChatResponse

        @GET("models")
        suspend fun getModels(
            @Header("Authorization") authorization: String
        ): ModelsResponse
    }
}

internal fun ChatMessage.toRequestFormat(): MessageRequestFormat {
    // Si hay imagen, serializar content como lista de content blocks (OpenAI vision)
    val contentValue: Any? = if (imageDataUri != null) {
        listOf(
            mapOf("type" to "text", "text" to content.ifBlank { "Imagen:" }),
            mapOf("type" to "image_url", "image_url" to mapOf("url" to imageDataUri))
        )
    } else {
        content
    }
    // Normalize tool call type to "function" for OpenAI-compatible APIs (may be "tool_use" from Anthropic providers)
    val normalizedToolCalls = toolCalls?.map { tc ->
        if (tc.type != "function") tc.copy(type = "function") else tc
    }
    return MessageRequestFormat(
        role = role,
        content = contentValue,
        toolCalls = normalizedToolCalls,
        toolCallId = toolCallId,
        name = name
    )
}

data class MessageRequestFormat(
    val role: String,
    val content: Any?,   // String para texto, List para vision content blocks
    @SerializedName("tool_calls")
    val toolCalls: List<ToolCall>? = null,
    @SerializedName("tool_call_id")
    val toolCallId: String? = null,
    val name: String? = null
)

data class ChatRequest(
    val model: String,
    val messages: List<MessageRequestFormat>,
    val temperature: Float = 0.7f,
    @SerializedName("max_tokens")
    val maxTokens: Int = 4096,
    val tools: List<Map<String, Any>>? = null
)

data class ChatResponse(
    val choices: List<Choice>?,
    val error: ApiError? = null
)

data class Choice(
    val message: ChatMessageResponse,
    val index: Int,
    @SerializedName("finish_reason")
    val finishReason: String? = null
)

data class ChatMessageResponse(
    val role: String,
    val content: String?,
    @SerializedName("tool_calls")
    val toolCalls: List<ToolCallResponse>? = null,
    val reasoning: String? = null,
    val thinking: String? = null
)

data class ToolCallResponse(
    val id: String,
    val type: String? = "function",
    val function: ToolFunctionResponse
)

data class ToolFunctionResponse(
    val name: String,
    val arguments: String
)

data class ApiError(
    val message: String,
    val type: String,
    val code: String? = null
)

data class ModelsResponse(
    val data: List<ModelData>
)

data class ModelData(
    val id: String,
    val name: String? = null,
    @SerializedName(value = "context_length", alternate = ["max_model_len"])
    val contextLength: Int? = null
)
