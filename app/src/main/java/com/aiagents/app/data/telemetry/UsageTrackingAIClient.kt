package com.aiagents.app.data.telemetry

import com.aiagents.app.data.remote.AIClient
import com.aiagents.app.data.remote.ChatMessage
import com.aiagents.app.data.remote.ChatResponseWithTools
import com.aiagents.app.data.remote.RemoteModelInfo
import com.aiagents.app.data.remote.StreamingChunk
import com.aiagents.app.domain.model.ProviderType
import com.aiagents.app.domain.model.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlin.math.ceil

class UsageTrackingAIClient(
    private val delegate: AIClient,
    private val sink: AppUsageSink,
    private val providerType: ProviderType
) : AIClient {
    private val source = if (providerType == ProviderType.LOCAL) AppUsageSource.LOCAL else AppUsageSource.BYOK
    private val provider = providerType.telemetryName()

    override suspend fun chat(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int
    ): Result<String> {
        val promptTokens = AppUsageTokenEstimator.prompt(systemPrompt, messages, emptyList())
        val startedAt = System.nanoTime()
        return try {
            delegate.chat(model, messages, systemPrompt, temperature, maxTokens).also { result ->
                report(
                    model = model,
                    promptTokens = promptTokens,
                    completionTokens = result.getOrNull()?.let(AppUsageTokenEstimator::text) ?: 0,
                    startedAt = startedAt,
                    success = result.isSuccess,
                    operation = AppUsageOperation.CHAT,
                    usageEstimated = true
                )
            }
        } catch (error: Throwable) {
            report(model, promptTokens, 0, startedAt, false, AppUsageOperation.CHAT, true)
            throw error
        }
    }

    override suspend fun chatWithTools(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): Result<ChatResponseWithTools> {
        val promptTokens = AppUsageTokenEstimator.prompt(systemPrompt, messages, tools)
        val startedAt = System.nanoTime()
        return try {
            delegate.chatWithTools(model, messages, systemPrompt, temperature, maxTokens, tools).also { result ->
                val usage = result.getOrNull()?.usage
                report(
                    model = model,
                    promptTokens = usage?.inputTokens ?: promptTokens,
                    completionTokens = usage?.outputTokens
                        ?: result.getOrNull()?.let(AppUsageTokenEstimator::response)
                        ?: 0,
                    startedAt = startedAt,
                    success = result.isSuccess,
                    operation = AppUsageOperation.CHAT_WITH_TOOLS,
                    usageEstimated = usage == null
                )
            }
        } catch (error: Throwable) {
            report(model, promptTokens, 0, startedAt, false, AppUsageOperation.CHAT_WITH_TOOLS, true)
            throw error
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
        val promptTokens = AppUsageTokenEstimator.prompt(systemPrompt, messages, tools)
        val startedAt = System.nanoTime()
        var completionTokens = 0L
        var completed = false
        var failed = false
        var providerUsage: com.aiagents.app.data.remote.TokenUsage? = null
        try {
            delegate.chatWithToolsStreaming(model, messages, systemPrompt, temperature, maxTokens, tools)
                .collect { chunk ->
                    completionTokens += AppUsageTokenEstimator.chunk(chunk)
                    if (!chunk.error.isNullOrBlank()) failed = true
                    if (chunk.done) completed = true
                    if (chunk.usage != null) providerUsage = chunk.usage
                    emit(chunk)
                }
        } catch (error: Throwable) {
            failed = true
            throw error
        } finally {
            report(
                model = model,
                promptTokens = providerUsage?.inputTokens ?: promptTokens,
                completionTokens = providerUsage?.outputTokens ?: completionTokens,
                startedAt = startedAt,
                success = completed && !failed,
                operation = AppUsageOperation.STREAM,
                usageEstimated = providerUsage == null
            )
        }
    }

    override suspend fun getAvailableModels(): Result<List<String>> = delegate.getAvailableModels()

    override suspend fun getAvailableModelInfos(): Result<List<RemoteModelInfo>> = delegate.getAvailableModelInfos()

    private fun report(
        model: String,
        promptTokens: Long,
        completionTokens: Long,
        startedAt: Long,
        success: Boolean,
        operation: AppUsageOperation,
        usageEstimated: Boolean
    ) {
        sink.record(
            AppUsageEvent(
                source = source.wireValue,
                provider = provider,
                model = model.take(200),
                promptTokens = promptTokens.coerceAtLeast(0),
                completionTokens = completionTokens.coerceAtLeast(0),
                usageEstimated = usageEstimated,
                durationMs = ((System.nanoTime() - startedAt) / 1_000_000).coerceAtLeast(0),
                status = if (success) "success" else "error",
                operation = operation.wireValue
            )
        )
    }
}

internal object AppUsageTokenEstimator {
    private const val MESSAGE_OVERHEAD = 4L
    private const val IMAGE_ESTIMATE = 1_000L

    fun text(value: String): Long {
        if (value.isEmpty()) return 0
        val codePoints = value.codePointCount(0, value.length)
        return ceil(codePoints / 4.0).toLong().coerceAtLeast(1)
    }

    fun prompt(systemPrompt: String, messages: List<ChatMessage>, tools: List<Map<String, Any>>): Long {
        var total = text(systemPrompt) + 2
        messages.forEach { message ->
            total += MESSAGE_OVERHEAD + text(message.role) + text(message.content)
            total += message.toolCalls.orEmpty().sumOf(::toolCall)
            if (!message.imageDataUri.isNullOrBlank()) total += IMAGE_ESTIMATE
            if (!message.toolResultImageUri.isNullOrBlank()) total += IMAGE_ESTIMATE
        }
        total += structured(tools)
        return total.coerceAtLeast(0)
    }

    fun response(response: ChatResponseWithTools): Long =
        text(response.content.orEmpty()) +
            text(response.reasoning.orEmpty()) +
            response.toolCalls.orEmpty().sumOf(::toolCall)

    fun chunk(chunk: StreamingChunk): Long =
        text(chunk.content.orEmpty()) +
            text(chunk.reasoning.orEmpty()) +
            chunk.toolCalls.orEmpty().sumOf(::toolCall)

    private fun toolCall(call: ToolCall): Long =
        text(call.function.name) + text(call.function.arguments)

    private fun structured(value: Any?): Long = when (value) {
        null -> 0
        is String -> text(value)
        is Number, is Boolean -> text(value.toString())
        is Map<*, *> -> value.entries.sumOf { (key, child) -> structured(key) + structured(child) }
        is Iterable<*> -> value.sumOf(::structured)
        is Array<*> -> value.sumOf(::structured)
        else -> 0
    }
}
