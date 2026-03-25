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

class MiniMaxClient(
    private val okHttpClient: OkHttpClient,
    private val apiKey: String
) : AIClient {

    private val api: MiniMaxApi = Retrofit.Builder()
        .baseUrl("https://api.minimax.io/v1/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(MiniMaxApi::class.java)

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
            Log.d("MiniMaxClient", "Starting chat with model: $model")
            
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
            
            Log.d("MiniMaxClient", "Sending request to API...")
            val response = api.chat("Bearer $apiKey", request)
            
            if (response.error != null) {
                Log.e("MiniMaxClient", "API Error: ${response.error.message}")
                return Result.failure(Exception(response.error.message))
            }
            
            val choice = response.choices?.firstOrNull()
            val content = choice?.message?.content
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
            
            // Extract reasoning from multiple possible sources
            val reasoningFromField = choice?.message?.reasoning
                ?: choice?.message?.thinking
            
            // Extract reasoning from <think> tags if exists
            val reasoningFromTags = extractThinkingFromContent(content)
            
            // Clean content if it has <think> tags
            val cleanContent = removeThinkingTags(content)
            
            // Use the reasoning that's not null/empty
            val finalReasoning = reasoningFromField?.ifBlank { null } 
                ?: reasoningFromTags?.ifBlank { null }
            
            Log.d("MiniMaxClient", "Response received: content=${cleanContent?.take(100)}, tools=${toolCalls?.size ?: 0}, reasoning=${finalReasoning?.take(100) ?: "null"}")
            
            Result.success(ChatResponseWithTools(cleanContent, toolCalls, finishReason, finalReasoning))
        } catch (e: Exception) {
            Log.e("MiniMaxClient", "Error in chat", e)
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
            url = "https://api.minimax.io/v1/chat/completions",
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            ),
            requestBody = requestBody
        )
    }

    override suspend fun getAvailableModels(): Result<List<String>> {
        return try {
            val response = api.getModels("Bearer $apiKey")
            Result.success(response.data.map { it.id })
        } catch (e: Exception) {
            Log.e("MiniMaxClient", "Error getting models, using defaults", e)
            Result.success(listOf(
                "MiniMax-M2.5",
                "MiniMax-M2.5-highspeed",
                "MiniMax-M2.1",
                "MiniMax-M2.1-highspeed",
                "MiniMax-M2"
            ))
        }
    }

    interface MiniMaxApi {
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