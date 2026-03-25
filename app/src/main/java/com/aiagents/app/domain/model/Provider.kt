package com.aiagents.app.domain.model

enum class ProviderType {
    OPENROUTER,
    GOOGLE_AI,
    OPENAI,
    OLLAMA,
    MINIMAX,
    MOONSHOT,
    ANTHROPIC,
    DEEPSEEK,
    GROK,
    KILO,
    ALIBABA,
    OPENCODE,
    ZAI,
    LOCAL
}

data class ProviderConfig(
    val type: ProviderType,
    val apiKey: String = "",
    val baseUrl: String = "",
    val models: List<String> = emptyList()
)

data class ModelInfo(
    val id: String,
    val name: String,
    val provider: ProviderType,
    val contextLength: Int = 4096
)
