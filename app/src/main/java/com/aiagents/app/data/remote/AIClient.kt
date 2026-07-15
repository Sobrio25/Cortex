package com.aiagents.app.data.remote

import android.content.Context
import com.aiagents.app.data.local.LocalLLMClient
import com.aiagents.app.data.local.LocalModelRepository
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.domain.model.ProviderType
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.NvidiaProviderConfig
import com.aiagents.app.domain.model.ZAIPlanType
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient

class AIClientFactory(
    private val okHttpClient: OkHttpClient,
    private val localModelRepository: LocalModelRepository? = null,
    private val context: Context? = null,
    private val securePreferences: SecurePreferences? = null,
    private val managedAIClient: ManagedAIClient? = null
) {
    fun createClient(
        providerType: ProviderType,
        apiKey: String,
        baseUrl: String? = null
    ): AIClient {
        val client = when (providerType) {
            ProviderType.MANAGED -> requireNotNull(managedAIClient) {
                "ManagedAIClient no está disponible"
            }
            ProviderType.OPENROUTER -> OpenRouterClient(okHttpClient, apiKey)
            ProviderType.GOOGLE_AI -> GoogleAIClient(okHttpClient, apiKey)
            ProviderType.OPENAI -> OpenAIClient(okHttpClient, apiKey, baseUrl)
            ProviderType.NVIDIA -> OpenAIClient(
                okHttpClient,
                apiKey,
                NvidiaProviderConfig.API_BASE_URL
            )
            ProviderType.OLLAMA -> OllamaClient(okHttpClient, baseUrl?.takeIf { it.isNotBlank() } ?: "http://localhost:11434")
            ProviderType.LM_STUDIO -> OpenAIClient(
                okHttpClient,
                apiKey,
                baseUrl?.takeIf { it.isNotBlank() } ?: "http://10.0.2.2:1234/v1/",
                requireStringContentForToolCalls = true
            )
            ProviderType.MINIMAX -> MiniMaxClient(okHttpClient, apiKey)
            ProviderType.MOONSHOT -> MoonshotClient(
                okHttpClient, apiKey, baseUrl?.takeIf { it.isNotBlank() },
                isCodingMode = baseUrl?.contains("kimi.com/coding") == true
            )
            ProviderType.ANTHROPIC -> {
                val oauthToken = securePreferences?.getAnthropicAccessToken()?.takeIf { it.isNotBlank() }
                AnthropicClient(okHttpClient, apiKey, oauthToken)
            }
            ProviderType.DEEPSEEK -> DeepSeekClient(okHttpClient, apiKey, baseUrl?.takeIf { it.isNotBlank() } ?: "https://api.deepseek.com")
            ProviderType.GROK -> GrokClient(okHttpClient, apiKey, baseUrl?.takeIf { it.isNotBlank() } ?: "https://api.x.ai")
            ProviderType.KILO -> OpenAIClient(okHttpClient, apiKey, "https://api.kilo.ai/api/gateway/")
            ProviderType.ALIBABA -> OpenAIClient(okHttpClient, apiKey, "https://dashscope.aliyuncs.com/compatible-mode/v1/")
            ProviderType.OPENCODE -> OpenCodeClient(
                okHttpClient,
                apiKey,
                baseUrl?.takeIf { it.isNotBlank() } ?: "https://opencode.ai/zen/v1/"
            )
            ProviderType.ZAI -> ZAIClient(okHttpClient, apiKey, baseUrl?.takeIf { it.isNotBlank() } ?: ZAIPlanType.STANDARD.baseUrl)
            ProviderType.LOCAL -> {
                requireNotNull(localModelRepository) { "LocalModelRepository requerido para proveedor LOCAL" }
                requireNotNull(context) { "Context requerido para proveedor LOCAL" }
                LocalLLMClient(context, localModelRepository)
            }
        }
        val providerId = modelsDevProviderId(providerType, baseUrl) ?: return client
        return ModelsDevBackedClient(client, ModelsDevCatalog(okHttpClient), providerId)
    }
}

interface AIClient {
    suspend fun chat(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int
    ): Result<String>
    
    suspend fun chatWithTools(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): Result<ChatResponseWithTools>
    
    suspend fun getAvailableModels(): Result<List<String>>

    /**
     * Returns provider-reported model metadata when the catalog endpoint exposes it.
     * The default keeps existing OpenAI-compatible clients working while allowing
     * richer providers (for example OpenRouter and Google AI) to report the real
     * context window instead of forcing the UI to guess from the model name.
     */
    suspend fun getAvailableModelInfos(): Result<List<RemoteModelInfo>> =
        getAvailableModels().map { models -> models.map(::RemoteModelInfo) }

    /**
     * Streaming version of chatWithTools. Emits StreamingChunk objects as they arrive.
     * Default implementation falls back to non-streaming chatWithTools.
     */
    fun chatWithToolsStreaming(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): Flow<StreamingChunk> {
        // Default: fallback to non-streaming
        return kotlinx.coroutines.flow.flow {
            val result = chatWithTools(model, messages, systemPrompt, temperature, maxTokens, tools)
            result.onSuccess { response ->
                if (!response.reasoning.isNullOrBlank()) {
                    emit(StreamingChunk(reasoning = response.reasoning))
                }
                if (!response.content.isNullOrBlank()) {
                    emit(StreamingChunk(content = response.content))
                }
                emit(StreamingChunk(
                    done = true,
                    toolCalls = response.toolCalls,
                    finishReason = response.finishReason
                ))
            }.onFailure { error ->
                emit(StreamingChunk(error = error.message ?: "Unknown error"))
            }
        }
    }
}

data class RemoteModelInfo(
    val id: String,
    val contextWindow: Int? = null
)

data class StreamingChunk(
    val content: String? = null,
    val reasoning: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val finishReason: String? = null,
    val done: Boolean = false,
    val error: String? = null
)

data class ChatMessage(
    val role: String,
    val content: String,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val name: String? = null,
    val imageDataUri: String? = null,
    /** Imagen resultante de una tool (ej. read_image_file). Solo usada por AnthropicClient. */
    val toolResultImageUri: String? = null,
    /** Stable local ID used only for managed-plan turn accounting; never sent upstream. */
    val clientMessageId: String? = null
)

data class ChatResponseWithTools(
    val content: String?,
    val toolCalls: List<ToolCall>?,
    val finishReason: String?,
    val reasoning: String? = null
)

/**
 * Extrae el contenido de thinking de tags <think>...</think> en el contenido.
 * Usado por modelos como DeepSeek-R1, Kimi K2, etc.
 */
fun extractThinkingFromContent(content: String?): String? {
    if (content.isNullOrBlank()) return null
    
    val thinkRegex = "<think>(.*?)</think>".toRegex(RegexOption.DOT_MATCHES_ALL)
    val match = thinkRegex.find(content)
    
    return match?.groupValues?.get(1)?.trim()
}

/**
 * Remueve los tags <think>...</think> del contenido para mostrar solo la respuesta final.
 */
fun removeThinkingTags(content: String?): String? {
    if (content.isNullOrBlank()) return content
    
    val thinkRegex = "<think>.*?</think>".toRegex(RegexOption.DOT_MATCHES_ALL)
    return thinkRegex.replace(content, "").trim()
}
