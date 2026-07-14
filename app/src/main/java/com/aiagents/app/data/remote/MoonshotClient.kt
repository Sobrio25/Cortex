package com.aiagents.app.data.remote

import android.util.Log
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

class MoonshotClient(
    private val okHttpClient: OkHttpClient,
    private val apiKey: String,
    private val baseUrl: String? = null,
    private val isCodingMode: Boolean = false
) : AIClient {

    private val TAG = if (isCodingMode) "KimiCodingClient" else "MoonshotClient"

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Any>>() {}.type

    // OpenAI-format API (Moonshot Internacional / China)
    private val openAiApi: MoonshotApi? = if (!isCodingMode) {
        Retrofit.Builder()
            .baseUrl(baseUrl?.let { if (it.endsWith("/")) it else "$it/" } ?: "https://api.moonshot.ai/v1/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MoonshotApi::class.java)
    } else null

    // Anthropic-format API (Kimi Coding)
    private val codingApi: KimiCodingApi? = if (isCodingMode) {
        Retrofit.Builder()
            .baseUrl(baseUrl?.let { if (it.endsWith("/")) it else "$it/" } ?: "https://api.kimi.com/coding/v1/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KimiCodingApi::class.java)
    } else null

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
        return if (isCodingMode) {
            chatWithToolsCoding(model, messages, systemPrompt, temperature, maxTokens, tools)
        } else {
            chatWithToolsOpenAI(model, messages, systemPrompt, temperature, maxTokens, tools)
        }
    }

    // ── OpenAI format (Moonshot Internacional / China) ────────────────────────

    private suspend fun chatWithToolsOpenAI(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): Result<ChatResponseWithTools> {
        val api = openAiApi!!
        return try {
            Log.d(TAG, "Starting chat with model: $model")

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

            val response = api.chat("Bearer $apiKey", request)

            if (response.error != null) {
                Log.e(TAG, "API Error: ${response.error.message}")
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

            val reasoningFromField = choice?.message?.reasoning
                ?: choice?.message?.thinking
            val reasoningFromTags = extractThinkingFromContent(content)
            val cleanContent = removeThinkingTags(content)
            val finalReasoning = reasoningFromField?.ifBlank { null }
                ?: reasoningFromTags?.ifBlank { null }

            Log.d(TAG, "Response: hasContent=${!cleanContent.isNullOrBlank()}, tools=${toolCalls?.size ?: 0}")

            Result.success(ChatResponseWithTools(cleanContent, toolCalls, finishReason, finalReasoning))
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e(TAG, "HTTP ${e.code()} error: $errorBody", e)
            val errorMessage = try {
                val errorJson = JsonParser.parseString(errorBody).asJsonObject
                errorJson.getAsJsonObject("error")?.get("message")?.asString ?: e.message()
            } catch (_: Exception) {
                e.message()
            }
            Result.failure(Exception("Error ${e.code()}: $errorMessage"))
        } catch (e: Exception) {
            Log.e(TAG, "Error in chat", e)
            Result.failure(e)
        }
    }

    // ── Anthropic format (Kimi Coding) ───────────────────────────────────────

    private suspend fun chatWithToolsCoding(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): Result<ChatResponseWithTools> {
        val api = codingApi!!
        return try {
            Log.d(TAG, "Starting Kimi Coding chat with model: $model")

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

            val response = api.messages(apiKey = apiKey, request = request)

            if (response.error != null) {
                Log.e(TAG, "API Error: ${response.error.message}")
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

            Log.d(TAG, "Response: hasContent=${!content.isNullOrBlank()}, tools=${toolCalls?.size ?: 0}")

            Result.success(ChatResponseWithTools(content, toolCalls, finishReason, reasoning))
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e(TAG, "HTTP ${e.code()} error: $errorBody", e)
            val errorMessage = try {
                val errorJson = JsonParser.parseString(errorBody).asJsonObject
                errorJson.getAsJsonObject("error")?.get("message")?.asString
                    ?: errorJson.get("message")?.asString
                    ?: e.message()
            } catch (_: Exception) {
                e.message()
            }
            Result.failure(Exception("Error ${e.code()}: $errorMessage"))
        } catch (e: Exception) {
            Log.e(TAG, "Error in chat", e)
            Result.failure(e)
        }
    }

    private fun buildAnthropicMessages(messages: List<ChatMessage>): List<AnthropicMessage> {
        val result = mutableListOf<AnthropicMessage>()
        var i = 0
        while (i < messages.size) {
            val msg = messages[i]

            if (msg.role == "tool") {
                // Group consecutive tool messages into a single user message
                val toolResultBlocks = mutableListOf<AnthropicContent>()
                while (i < messages.size && messages[i].role == "tool") {
                    val toolMsg = messages[i]
                    val innerBlocks = if (toolMsg.content.isNotBlank()) {
                        listOf(AnthropicContent(type = "text", text = toolMsg.content))
                    } else null
                    toolResultBlocks.add(
                        AnthropicContent(
                            type = "tool_result",
                            toolUseId = toolMsg.toolCallId,
                            content = innerBlocks,
                            text = if (innerBlocks == null && toolMsg.content.isNotBlank()) toolMsg.content else null
                        )
                    )
                    i++
                }
                result.add(AnthropicMessage(role = "user", content = toolResultBlocks))
            } else {
                val blocks = mutableListOf<AnthropicContent>()
                if (msg.content.isNotBlank()) {
                    blocks.add(AnthropicContent(type = "text", text = msg.content))
                }
                // tool_use blocks for assistant messages with tool calls
                msg.toolCalls?.forEach { toolCall ->
                    val inputMap: Map<String, Any>? = try {
                        gson.fromJson(toolCall.function.arguments, mapType)
                    } catch (_: Exception) { null }
                    blocks.add(
                        AnthropicContent(
                            type = "tool_use",
                            id = toolCall.id,
                            name = toolCall.function.name,
                            input = inputMap
                        )
                    )
                }
                if (blocks.isEmpty()) blocks.add(AnthropicContent(type = "text", text = ""))
                result.add(AnthropicMessage(role = msg.role, content = blocks))
                i++
            }
        }
        return result
    }

    /**
     * Convert tools from OpenAI format to Anthropic format.
     * OpenAI: {"type": "function", "function": {"name": ..., "description": ..., "parameters": ...}}
     * Anthropic: {"name": ..., "description": ..., "input_schema": ...}
     */
    private fun convertToolsToAnthropicFormat(tools: List<Map<String, Any>>): List<Map<String, Any>> {
        return tools.map { tool ->
            @Suppress("UNCHECKED_CAST")
            val function = tool["function"] as? Map<String, Any> ?: return@map tool
            mapOf(
                "name" to (function["name"] ?: ""),
                "description" to (function["description"] ?: ""),
                "input_schema" to (function["parameters"] ?: mapOf("type" to "object"))
            )
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
        if (isCodingMode) {
            // Kimi Coding uses Anthropic format
            val anthropicMessages = buildAnthropicMessages(messages)
            val anthropicTools = if (tools.isEmpty()) null else convertToolsToAnthropicFormat(tools)

            val requestBody = mutableMapOf<String, Any?>(
                "model" to model,
                "messages" to anthropicMessages.map { msg ->
                    mapOf("role" to msg.role, "content" to msg.content.map { block ->
                        val map = mutableMapOf<String, Any?>("type" to block.type)
                        block.text?.let { map["text"] = it }
                        block.id?.let { map["id"] = it }
                        block.name?.let { map["name"] = it }
                        block.input?.let { map["input"] = it }
                        block.toolUseId?.let { map["tool_use_id"] = it }
                        map
                    })
                },
                "max_tokens" to maxTokens,
                "temperature" to temperature,
                "stream" to true
            )
            if (systemPrompt.isNotBlank()) requestBody["system"] = systemPrompt
            if (anthropicTools != null) requestBody["tools"] = anthropicTools

            val codingBaseUrl = baseUrl?.let { if (it.endsWith("/")) it else "$it/" }
                ?: "https://api.kimi.com/coding/v1/"

            return streamAnthropic(
                okHttpClient = okHttpClient,
                apiKey = apiKey,
                requestBody = requestBody,
                url = "${codingBaseUrl}messages",
                extraHeaders = emptyMap()
            ).withRealtimeThinkTagParsing()
        } else {
            // Moonshot OpenAI-compatible
            val allMessages = mutableListOf(ChatMessage("system", systemPrompt))
            allMessages.addAll(messages)

            val effectiveUrl = baseUrl?.let { if (it.endsWith("/")) it else "$it/" }
                ?: "https://api.moonshot.ai/v1/"

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
                url = "${effectiveUrl}chat/completions",
                headers = mapOf(
                    "Authorization" to "Bearer $apiKey",
                    "Content-Type" to "application/json"
                ),
                requestBody = requestBody
            ).withRealtimeThinkTagParsing()
        }
    }

    // ── Model listing ────────────────────────────────────────────────────────

    override suspend fun getAvailableModels(): Result<List<String>> {
        return getAvailableModelInfos().map { models -> models.map { it.id } }
    }

    override suspend fun getAvailableModelInfos(): Result<List<RemoteModelInfo>> {
        return try {
            val response = if (isCodingMode) {
                codingApi!!.getModels(apiKey = apiKey, authorization = "Bearer $apiKey")
            } else {
                openAiApi!!.getModels("Bearer $apiKey")
            }
            Result.success(
                response.data.map { model ->
                    RemoteModelInfo(model.id, model.contextLength?.takeIf { it > 0 })
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting models", e)
            Result.failure(e)
        }
    }

    // ── Retrofit interfaces ──────────────────────────────────────────────────

    interface MoonshotApi {
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

    interface KimiCodingApi {
        @POST("messages")
        suspend fun messages(
            @Header("x-api-key") apiKey: String,
            @Body request: AnthropicRequest
        ): AnthropicResponse

        @GET("models")
        suspend fun getModels(
            @Header("x-api-key") apiKey: String,
            @Header("Authorization") authorization: String
        ): ModelsResponse
    }
}
