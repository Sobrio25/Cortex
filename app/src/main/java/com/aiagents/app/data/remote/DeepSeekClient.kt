package com.aiagents.app.data.remote

import android.util.Log
import com.aiagents.app.domain.model.ToolCall
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class DeepSeekClient(
    private val okHttpClient: OkHttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://api.deepseek.com"
) : AIClient {

    companion object {
        private const val TAG = "DeepSeekClient"
    }

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    override suspend fun chat(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildJsonRequest(model, messages, systemPrompt, temperature, maxTokens, null)
            
            val request = Request.Builder()
                .url("$baseUrl/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody(jsonMediaType))
                .build()

            val client = okHttpClient.newBuilder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "API error: ${response.code} - $errorBody")
                    return@withContext Result.failure(IOException("API error: ${response.code}"))
                }

                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response"))

                val chatResponse = gson.fromJson(responseBody, DeepSeekChatResponse::class.java)
                val content = chatResponse.choices?.firstOrNull()?.message?.content
                    ?: return@withContext Result.failure(IOException("No content in response"))

                Result.success(content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Chat error", e)
            Result.failure(e)
        }
    }

    override suspend fun chatWithTools(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): Result<ChatResponseWithTools> = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildJsonRequest(model, messages, systemPrompt, temperature, maxTokens, tools)
            
            val request = Request.Builder()
                .url("$baseUrl/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody(jsonMediaType))
                .build()

            val client = okHttpClient.newBuilder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "API error: ${response.code} - $errorBody")
                    return@withContext Result.failure(IOException("API error: ${response.code}"))
                }

                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response"))

                val chatResponse = gson.fromJson(responseBody, DeepSeekChatResponse::class.java)
                val choice = chatResponse.choices?.firstOrNull()
                
                // DeepSeek R1 has reasoning_content for chain-of-thought
                val reasoning = choice?.message?.reasoning_content

                // Check for tool calls
                val toolCalls = choice?.message?.tool_calls?.map { toolCall ->
                    ToolCall(
                        id = toolCall.id ?: "",
                        type = toolCall.type ?: "function",
                        function = com.aiagents.app.domain.model.ToolFunction(
                            name = toolCall.function?.name ?: "",
                            arguments = toolCall.function?.arguments ?: "{}"
                        )
                    )
                }

                val content = choice?.message?.content

                Result.success(ChatResponseWithTools(
                    content = content,
                    toolCalls = toolCalls,
                    finishReason = choice?.finish_reason,
                    reasoning = reasoning
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Chat with tools error", e)
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
        val requestJson = buildJsonRequest(model, messages, systemPrompt, temperature, maxTokens, tools)
        val jsonObj = com.google.gson.JsonParser.parseString(requestJson).asJsonObject
        jsonObj.addProperty("stream", true)

        @Suppress("UNCHECKED_CAST")
        val requestBody = Gson().fromJson(jsonObj, Map::class.java) as Map<String, Any?>

        return streamOpenAICompatible(
            okHttpClient = okHttpClient,
            url = "$baseUrl/v1/chat/completions",
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            ),
            requestBody = requestBody
        ).withRealtimeThinkTagParsing()
    }

    override suspend fun getAvailableModels(): Result<List<String>> = Result.success(
        listOf(
            "deepseek-chat",
            "deepseek-reasoner"
        )
    )
    // Modelos DeepSeek disponibles a febrero 2026:
    // - deepseek-chat: DeepSeek-V3.2 (Non-thinking Mode), 128K contexto
    // - deepseek-reasoner: DeepSeek-V3.2 (Thinking Mode), 128K contexto, soporta reasoning

    private fun buildJsonRequest(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>?
    ): String {
        val json = JsonObject()
        json.addProperty("model", model)
        
        val messagesArray = mutableListOf<Map<String, Any?>>()
        
        // Add system message
        if (systemPrompt.isNotBlank()) {
            messagesArray.add(mapOf("role" to "system", "content" to systemPrompt))
        }
        
        // Add conversation messages
        messages.forEach { msg ->
            val msgMap = mutableMapOf<String, Any?>("role" to msg.role, "content" to msg.content)
            
            // Handle tool calls
            if (!msg.toolCalls.isNullOrEmpty()) {
                val toolCallsList = msg.toolCalls.map { tc ->
                    mapOf(
                        "id" to tc.id,
                        "type" to tc.type,
                        "function" to mapOf(
                            "name" to tc.function.name,
                            "arguments" to tc.function.arguments
                        )
                    )
                }
                msgMap["tool_calls"] = toolCallsList
            }
            
            // Handle tool responses
            if (msg.toolCallId != null) {
                msgMap["tool_call_id"] = msg.toolCallId
                msgMap["name"] = msg.name
            }
            
            messagesArray.add(msgMap)
        }
        
        json.add("messages", gson.toJsonTree(messagesArray))
        json.addProperty("temperature", temperature.coerceIn(0f, 2f))
        if (maxTokens > 0) {
            json.addProperty("max_tokens", maxTokens)
        }
        
        // Add tools if provided
        if (!tools.isNullOrEmpty()) {
            json.add("tools", gson.toJsonTree(tools))
        }
        
        return gson.toJson(json)
    }
}

// Data classes for DeepSeek API response
data class DeepSeekChatResponse(
    val id: String?,
    val choices: List<DeepSeekChoice>?,
    val created: Long?,
    val model: String?,
    val usage: DeepSeekUsage?
)

data class DeepSeekChoice(
    val index: Int?,
    val message: DeepSeekMessage?,
    val finish_reason: String?
)

data class DeepSeekMessage(
    val role: String?,
    val content: String?,
    val reasoning_content: String?, // For DeepSeek R1 reasoning
    val tool_calls: List<DeepSeekToolCall>?
)

data class DeepSeekToolCall(
    val id: String?,
    val type: String?,
    val function: DeepSeekFunction?
)

data class DeepSeekFunction(
    val name: String?,
    val arguments: String?
)

data class DeepSeekUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int?,
    @SerializedName("completion_tokens") val completionTokens: Int?,
    @SerializedName("total_tokens") val totalTokens: Int?
)
