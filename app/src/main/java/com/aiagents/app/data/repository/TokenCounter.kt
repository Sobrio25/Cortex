package com.aiagents.app.data.repository

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

    private val DEFAULT_CONTEXT_WINDOW = 8192

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
        }
        total += 2
        return total
    }

    fun getContextWindowForModel(modelName: String): Int {
        val normalizedName = modelName.lowercase()
            .replace(":", "-")
            .replace("_", "-")
            .replace(".", "-")
        
        for ((key, value) in PROVIDER_CONTEXT_WINDOWS) {
            if (normalizedName.contains(key.lowercase())) {
                return value
            }
        }
        
        for ((key, value) in PROVIDER_CONTEXT_WINDOWS) {
            if (normalizedName.contains(key.lowercase().split("-").first())) {
                return value
            }
        }
        
        return DEFAULT_CONTEXT_WINDOW
    }

    fun getContextUsagePercentage(currentTokens: Int, modelName: String): Float {
        val contextWindow = getContextWindowForModel(modelName)
        return (currentTokens.toFloat() / contextWindow) * 100
    }

    fun getAvailableTokens(currentTokens: Int, modelName: String): Int {
        val contextWindow = getContextWindowForModel(modelName)
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

data class ContextInfo(
    val currentTokens: Int,
    val maxTokens: Int,
    val usagePercentage: Float,
    val availableTokens: Int,
    val supportsVision: Boolean,
    val supportsDocuments: Boolean
)
