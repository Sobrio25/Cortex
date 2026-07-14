package com.aiagents.app.data.repository

import com.aiagents.app.domain.model.ProviderType

object TokenCounter {
    private val PROVIDER_CONTEXT_WINDOWS = mapOf(
        "gpt-4o" to 128000,
        "gpt-4o-mini" to 128000,
        "gpt-4-turbo" to 128000,
        "gpt-4" to 8192,
        "gpt-3.5-turbo" to 16385,
        "claude-opus-4-6" to 1000000,
        "claude-sonnet-4-6" to 1000000,
        "claude-haiku-4-5" to 200000,
        "claude-opus-4-5" to 200000,
        "claude-sonnet-4-5" to 200000,
        "claude-3.5-sonnet" to 200000,
        "claude-3-opus" to 200000,
        "claude-3-sonnet" to 200000,
        "claude-3-haiku" to 200000,
        "claude-3.5-haiku" to 200000,
        "gemini-3.1-pro-preview" to 1000000,
        "gemini-3-flash" to 1000000,
        "gemini-2.5-pro" to 1000000,
        "gemini-2.5-flash" to 1000000,
        "gemini-2.5-flash-lite" to 1000000,
        "minimax-m2.5" to 200000,
        "minimax-m2.1" to 200000,
        "minimax-m2" to 200000,
        "kimi-k2.5" to 256000,
        "kimi-k2-thinking" to 256000,
        "kimi-k2-turbo" to 256000,
        "kimi-k2-turbo-preview" to 256000,
        "kimi-for-coding" to 256000,
        "k2p5" to 256000,
        "moonshot-v1-128k" to 128000,
        "moonshot-v1-32k" to 32000,
        "moonshot-v1-8k" to 8000,
        "llama-3.1-405b" to 131072,
        "llama-3.1-70b" to 131072,
        "llama-3.1-8b" to 131072,
        "llama-3.2-90b" to 131072,
        "llama-3.2-11b" to 131072,
        "llama-3.2-3b" to 131072,
        "llama-3.2-1b" to 131072,
        "mistral-large" to 128000,
        "mistral-medium" to 32000,
        "mistral-small" to 32000,
        "codestral" to 32000,
        "deepseek-chat" to 128000,
        "deepseek-coder" to 16384,
        "deepseek-reasoner" to 128000,
        "grok-4" to 256000,
        "grok-4.1" to 256000,
        "grok-4.1-fast" to 256000,
        "grok-3" to 131000,
        "grok-3-mini" to 32000,
        "grok-2-1212" to 131000,
        "grok-2-vision-1212" to 131000,
        "grok-beta" to 128000,
        "qwen-2.5-72b" to 131072,
        "qwen-2.5-32b" to 131072,
        "qwen-2.5-14b" to 131072,
        "qwen-2.5-7b" to 131072,
    )

    private val PROVIDER_DEFAULT_CONTEXT_WINDOWS = mapOf(
        ProviderType.OPENAI to 128_000,
        ProviderType.NVIDIA to 131_072,
        ProviderType.GOOGLE_AI to 1_000_000,
        ProviderType.ANTHROPIC to 200_000,
        ProviderType.MINIMAX to 200_000,
        ProviderType.MOONSHOT to 256_000,
        ProviderType.DEEPSEEK to 128_000,
        ProviderType.GROK to 131_072,
        ProviderType.ZAI to 128_000,
        ProviderType.ALIBABA to 32_768,
        ProviderType.KILO to 32_768,
        ProviderType.OPENCODE to 32_768,
        ProviderType.OPENROUTER to 32_768,
        ProviderType.OLLAMA to 8_192,
        ProviderType.LOCAL to 4_096
    )

    private const val DEFAULT_CONTEXT_WINDOW = 32_768
    private const val MIN_VALID_CONTEXT_WINDOW = 1_024
    private const val MAX_VALID_CONTEXT_WINDOW = 4_000_000
    private val CONTEXT_HINT = Regex("(?:^|[/_:\\-])(\\d{1,4})([km])(?:$|[/_:\\-])", RegexOption.IGNORE_CASE)

    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        
        var tokenCount = 0
        var i = 0
        val len = text.length
        
        while (i < len) {
            val char = text[i]
            
            when {
                char.isWhitespace() -> {
                    tokenCount++
                    i++
                }
                char.isLetterOrDigit() -> {
                    var wordLength = 0
                    while (i < len && text[i].isLetterOrDigit()) {
                        wordLength++
                        i++
                    }
                    tokenCount += maxOf(1, wordLength / 4)
                }
                char in ".,!?;:'\"()-[]{}/@#$%^&*+=<>|\\~`" -> {
                    tokenCount++
                    i++
                }
                char.code > 127 -> {
                    tokenCount += 2
                    i++
                }
                else -> {
                    tokenCount++
                    i++
                }
            }
        }
        
        return maxOf(1, (tokenCount * 1.3).toInt())
    }

    fun estimateMessagesTokens(messages: List<com.aiagents.app.domain.model.Message>): Int {
        var total = 0
        messages.forEach { message ->
            total += 4
            total += estimateTokens(message.content)
            total += estimateTokens(message.attachedFiles.joinToString(" "))
            total += estimateTokens(message.reasoning.orEmpty())
            total += message.toolCalls.sumOf { call ->
                estimateTokens(call.function.name) + estimateTokens(call.function.arguments)
            }
            total += message.toolResults.sumOf { result ->
                estimateTokens(result.name) + estimateTokens(result.content)
            }
        }
        total += 2
        return total
    }

    /**
     * Estimates only fields that are actually serialized into the provider message history.
     * UI-only reasoning is excluded and tool output is counted once through Message.content.
     */
    fun estimateModelInputTokens(messages: List<com.aiagents.app.domain.model.Message>): Int {
        var total = 2
        messages.forEach { message ->
            total += 4
            total += estimateTokens(message.content)
            total += estimateTokens(message.attachedFiles.joinToString(" "))
            total += message.toolCalls.sumOf { call ->
                estimateTokens(call.function.name) + estimateTokens(call.function.arguments)
            }
        }
        return total
    }

    fun getContextWindowForModel(
        modelName: String,
        provider: ProviderType? = null,
        reportedContextWindow: Int? = null
    ): Int {
        reportedContextWindow
            ?.takeIf { it in MIN_VALID_CONTEXT_WINDOW..MAX_VALID_CONTEXT_WINDOW }
            ?.let { return it }

        val normalizedName = normalizeModelName(modelName)

        PROVIDER_CONTEXT_WINDOWS.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { (key, _) -> normalizedName.contains(normalizeModelName(key)) }
            ?.let { return it.value }

        parseContextHint(modelName)?.let { return it }

        return provider?.let(PROVIDER_DEFAULT_CONTEXT_WINDOWS::get) ?: DEFAULT_CONTEXT_WINDOW
    }

    private fun normalizeModelName(modelName: String): String = modelName.lowercase()
            .replace(":", "-")
            .replace("_", "-")
            .replace(".", "-")

    private fun parseContextHint(modelName: String): Int? {
        val match = CONTEXT_HINT.find(modelName.lowercase()) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val multiplier = if (match.groupValues[2].equals("m", ignoreCase = true)) 1_000_000L else 1_000L
        val contextWindow = amount * multiplier
        return contextWindow
            .takeIf { it in MIN_VALID_CONTEXT_WINDOW.toLong()..MAX_VALID_CONTEXT_WINDOW.toLong() }
            ?.toInt()
    }

    fun getContextUsagePercentage(
        currentTokens: Int,
        modelName: String,
        provider: ProviderType? = null,
        reportedContextWindow: Int? = null
    ): Float {
        val contextWindow = getContextWindowForModel(modelName, provider, reportedContextWindow)
        return (currentTokens.toFloat() / contextWindow) * 100
    }

    fun getAvailableTokens(
        currentTokens: Int,
        modelName: String,
        provider: ProviderType? = null,
        reportedContextWindow: Int? = null
    ): Int {
        val contextWindow = getContextWindowForModel(modelName, provider, reportedContextWindow)
        return maxOf(0, contextWindow - currentTokens)
    }

    fun supportsVision(modelName: String): Boolean {
        val normalizedName = modelName.lowercase()
        return normalizedName.contains("vision") ||
                normalizedName.contains("gpt-4o") ||
                normalizedName.contains("gpt-4-turbo") ||
                normalizedName.contains("claude") ||
                normalizedName.contains("gemini") ||
                normalizedName.contains("kimi") ||
                normalizedName.contains("llama-3.2") ||
                normalizedName.contains("qwen-2.5") ||
                normalizedName.contains("pixtral") ||
                normalizedName.contains("grok-4") ||  // Grok 4 y 4.1 soportan imágenes
                normalizedName.contains("grok-3") ||  // Grok 3 soporta imágenes
                normalizedName.contains("grok-2-vision")
    }

    fun supportsDocuments(modelName: String): Boolean {
        val normalizedName = modelName.lowercase()
        return normalizedName.contains("claude") ||
                normalizedName.contains("gemini") ||
                normalizedName.contains("kimi") ||
                normalizedName.contains("gpt-4o") ||
                normalizedName.contains("gpt-4-turbo")
    }

    fun getSupportedFileTypes(modelName: String): List<String> {
        val types = mutableListOf<String>()
        
        if (supportsVision(modelName)) {
            types.addAll(listOf("image/jpeg", "image/png", "image/gif", "image/webp"))
        }
        
        if (supportsDocuments(modelName)) {
            types.addAll(listOf(
                "application/pdf",
                "text/plain",
                "text/markdown",
                "text/csv",
                "application/json",
                "text/html"
            ))
        }
        
        return types
    }
}

object ContextWindowPolicy {
    fun budget(
        contextWindow: Int,
        desiredResponseTokens: Int
    ): ContextBudget {
        val safeWindow = contextWindow.coerceAtLeast(1_024)
        val maximumResponseReserve = (safeWindow / 8).coerceAtLeast(1_024)
        val responseReserve = desiredResponseTokens
            .coerceAtLeast(1_024)
            .coerceAtMost(maximumResponseReserve)
        val safetyReserve = (safeWindow / 50).coerceIn(512, 4_096)
        val promptLimit = (safeWindow - responseReserve - safetyReserve)
            .coerceAtLeast(safeWindow / 2)

        return ContextBudget(
            responseReserve = responseReserve,
            safetyReserve = safetyReserve,
            promptLimit = promptLimit,
            warningTokens = (promptLimit * 0.85f).toInt(),
            criticalTokens = (promptLimit * 0.96f).toInt()
        )
    }
}

object ContextCompactionPlanner {
    fun plan(
        messages: List<com.aiagents.app.domain.model.Message>,
        contextWindow: Int
    ): ContextCompactionPlan {
        if (messages.size < 3) return ContextCompactionPlan(emptyList(), messages)

        val recentTokenBudget = (contextWindow / 10).coerceIn(2_048, 24_000)
        var keptTokens = 0
        var splitIndex = messages.size
        var keptMessages = 0

        while (splitIndex > 0 && (keptMessages < 4 || keptTokens < recentTokenBudget)) {
            splitIndex -= 1
            keptMessages += 1
            keptTokens += TokenCounter.estimateModelInputTokens(listOf(messages[splitIndex]))
        }

        // Always preserve at least the latest user/assistant pair, even when a
        // single recent message consumes the whole target budget.
        splitIndex = splitIndex.coerceAtMost(messages.size - 2)

        // Keep complete turns, as OpenCode does: the verbatim tail must begin at
        // a real user message instead of in the middle of assistant/tool traffic.
        val userBoundary = (splitIndex downTo 1)
            .firstOrNull { messages[it].role == com.aiagents.app.domain.model.MessageRole.USER }
        if (userBoundary != null) splitIndex = userBoundary
        if (splitIndex <= 0) return ContextCompactionPlan(emptyList(), messages)

        return ContextCompactionPlan(
            messagesToSummarize = messages.take(splitIndex),
            messagesToKeep = messages.drop(splitIndex)
        )
    }
}

data class ContextBudget(
    val responseReserve: Int,
    val safetyReserve: Int,
    val promptLimit: Int,
    val warningTokens: Int,
    val criticalTokens: Int
)

data class ContextCompactionPlan(
    val messagesToSummarize: List<com.aiagents.app.domain.model.Message>,
    val messagesToKeep: List<com.aiagents.app.domain.model.Message>
)

data class ContextInfo(
    val currentTokens: Int,
    val maxTokens: Int,
    val usagePercentage: Float,
    val availableTokens: Int,
    val supportsVision: Boolean,
    val supportsDocuments: Boolean,
    val compactionWarningTokens: Int = Int.MAX_VALUE,
    val compactionCriticalTokens: Int = Int.MAX_VALUE
) {
    val shouldPromptCompaction: Boolean
        get() = currentTokens >= compactionWarningTokens

    val isContextCritical: Boolean
        get() = currentTokens >= compactionCriticalTokens
}
