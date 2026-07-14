package com.aiagents.app.data.remote

import com.aiagents.app.domain.model.ZAIPlanType
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient

class ZAIClient(
    private val okHttpClient: OkHttpClient,
    private val apiKey: String,
    baseUrl: String? = null
) : AIClient {

    private val effectiveBaseUrl = (baseUrl ?: ZAIPlanType.STANDARD.baseUrl).let {
        if (it.endsWith("/")) it else "$it/"
    }

    // Usa OpenAIClient internamente para las llamadas a la API
    private val openAIClient = OpenAIClient(okHttpClient, apiKey, effectiveBaseUrl)

    override suspend fun chat(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int
    ): Result<String> {
        return openAIClient.chat(model, messages, systemPrompt, temperature, maxTokens)
    }

    override suspend fun chatWithTools(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): Result<ChatResponseWithTools> {
        return openAIClient.chatWithTools(model, messages, systemPrompt, temperature, maxTokens, tools)
    }

    override suspend fun getAvailableModels(): Result<List<String>> {
        return getAvailableModelInfos().map { models -> models.map { it.id } }
    }

    override suspend fun getAvailableModelInfos(): Result<List<RemoteModelInfo>> {
        return openAIClient.getAvailableModelInfos()
    }

    override fun chatWithToolsStreaming(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): Flow<StreamingChunk> {
        return openAIClient.chatWithToolsStreaming(model, messages, systemPrompt, temperature, maxTokens, tools)
    }
}
