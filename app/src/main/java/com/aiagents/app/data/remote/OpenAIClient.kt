package com.aiagents.app.data.remote

import android.util.Log
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

class OpenAIClient(
    private val okHttpClient: OkHttpClient,
    private val apiKey: String,
    private val baseUrl: String? = null
) : AIClient {

    private val authorizationHeader: String?
        get() = apiKey.trim().takeIf { it.isNotEmpty() }?.let { "Bearer $it" }

    private val effectiveBaseUrl = (baseUrl ?: "https://api.openai.com/v1/").let {
        if (it.endsWith('/')) it else "$it/"
    }

    private val api: OpenAiApi = Retrofit.Builder()
        .baseUrl(effectiveBaseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenAiApi::class.java)

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
            Log.d("OpenAIClient", "Starting chat with model: $model")
            
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
            
            Log.d("OpenAIClient", "Sending request to API...")
            val response = api.chat(authorizationHeader, request)
            
            if (response.error != null) {
                Log.e("OpenAIClient", "API Error: ${response.error.message}")
                return Result.failure(Exception(response.error.message))
            }
            
            val choice = response.choices?.firstOrNull()
            val content = choice?.message?.content
            var toolCalls = choice?.message?.toolCalls?.map { tc ->
                ToolCall(
                    id = tc.id,
                    type = "function",
                    function = ToolFunction(
                        name = tc.function.name,
                        arguments = tc.function.arguments
                    )
                )
            }

            // Fallback: parse XML-style tool calls from content (MiniMax via Kilo, etc.)
            var cleanContent = content
            if (toolCalls.isNullOrEmpty() && content != null && content.contains("<invoke name=")) {
                val parsed = parseXmlToolCalls(content)
                if (parsed.isNotEmpty()) {
                    toolCalls = parsed
                    cleanContent = content
                        .replace(Regex("<(?:minimax:)?tool_call>.*?</(?:minimax:)?tool_call>", RegexOption.DOT_MATCHES_ALL), "")
                        .replace(Regex("<invoke name=.*?</invoke>", RegexOption.DOT_MATCHES_ALL), "")
                        .trim()
                        .ifEmpty { null }
                }
            }

            val finishReason = choice?.finishReason

            // Extraer reasoning de múltiples fuentes posibles
            val reasoningFromField = choice?.message?.reasoning
                ?: choice?.message?.thinking

            // Extraer reasoning de tags <think> si existe (DeepSeek, etc.)
            val reasoningFromTags = extractThinkingFromContent(cleanContent)

            // Limpiar el contenido si tiene tags <think>
            cleanContent = removeThinkingTags(cleanContent)

            // Usar el reasoning que no sea null/vacío
            val finalReasoning = reasoningFromField?.ifBlank { null }
                ?: reasoningFromTags?.ifBlank { null }

            Log.d(
                "OpenAIClient",
                "Response received: hasContent=${!cleanContent.isNullOrBlank()}, " +
                    "tools=${toolCalls?.size ?: 0}, hasReasoning=${!finalReasoning.isNullOrBlank()}"
            )

            Result.success(ChatResponseWithTools(cleanContent, toolCalls, finishReason, finalReasoning))
        } catch (e: Exception) {
            Log.e("OpenAIClient", "Error in chat", e)
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
            url = "${effectiveBaseUrl}chat/completions",
            headers = buildMap {
                put("Content-Type", "application/json")
                authorizationHeader?.let { put("Authorization", it) }
            },
            requestBody = requestBody
        ).withRealtimeThinkTagParsing()
    }

    override suspend fun getAvailableModels(): Result<List<String>> {
        return getAvailableModelInfos().map { models -> models.map { it.id } }
    }

    override suspend fun getAvailableModelInfos(): Result<List<RemoteModelInfo>> {
        return try {
            val response = api.getModels(authorizationHeader)
            Result.success(
                response.data.map { model ->
                    RemoteModelInfo(
                        id = model.id,
                        contextWindow = model.contextLength?.takeIf { it > 0 }
                    )
                }
            )
        } catch (e: Exception) {
            Log.e("OpenAIClient", "Error getting models", e)
            Result.failure(e)
        }
    }

    interface OpenAiApi {
        @POST("chat/completions")
        suspend fun chat(
            @Header("Authorization") authorization: String?,
            @Body request: ChatRequest
        ): ChatResponse

        @GET("models")
        suspend fun getModels(
            @Header("Authorization") authorization: String?
        ): ModelsResponse
    }
}
