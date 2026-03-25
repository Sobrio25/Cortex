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

class GrokClient(
    private val okHttpClient: OkHttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://api.x.ai"
) : AIClient {

    companion object {
        private const val TAG = "GrokClient"
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

                val chatResponse = gson.fromJson(responseBody, GrokChatResponse::class.java)
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

                val chatResponse = gson.fromJson(responseBody, GrokChatResponse::class.java)
                val choice = chatResponse.choices?.firstOrNull()
                
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
                    reasoning = null
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
        )
    }

    override suspend fun getAvailableModels(): Result<List<String>> = Result.success(
        listOf(
            "grok-4",
            "grok-4.1",
            "grok-4.1-fast",
            "grok-3",
            "grok-3-mini",
            "grok-2-1212",
            "grok-2-vision-1212"
        )
    )
    // Modelos Grok/xAI disponibles a febrero 2026:
    // - grok-4: Flagship model, reasoning, 256K contexto
    // - grok-4.1: Advanced reasoning, 65% less hallucinations, 256K contexto
    // - grok-4.1-fast: Fast version of 4.1, 256K contexto
    // - grok-3: Real-time research, transparent reasoning, 131K contexto
    // - grok-3-mini: Lightweight, cost-efficient, 32K contexto
    // - grok-2-1212: Balanced reasoning, 131K contexto
    // - grok-2-vision-1212: Vision capabilities, 131K contexto

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

// Data classes for xAI/Grok API response
data class GrokChatResponse(
    val id: String?,
    val choices: List<GrokChoice>?,
    val created: Long?,
    val model: String?,
    val usage: GrokUsage?
)

data class GrokChoice(
    val index: Int?,
    val message: GrokMessage?,
    val finish_reason: String?
)

data class GrokMessage(
    val role: String?,
    val content: String?,
    val tool_calls: List<GrokToolCall>?
)

data class GrokToolCall(
    val id: String?,
    val type: String?,
    val function: GrokFunction?
)

data class GrokFunction(
    val name: String?,
    val arguments: String?
)

data class GrokUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int?,
    @SerializedName("completion_tokens") val completionTokens: Int?,
    @SerializedName("total_tokens") val totalTokens: Int?
)
