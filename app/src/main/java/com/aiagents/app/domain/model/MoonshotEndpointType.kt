package com.aiagents.app.domain.model

enum class MoonshotEndpointType {
    GLOBAL,
    CHINA,
    CODING;

    val displayName: String
        get() = when (this) {
            GLOBAL -> "Moonshot Internacional"
            CHINA -> "Moonshot China"
            CODING -> "Kimi Coding"
        }

    val baseUrl: String
        get() = when (this) {
            GLOBAL -> "https://api.moonshot.ai/v1/"
            CHINA -> "https://api.moonshot.cn/v1/"
            CODING -> "https://api.kimi.com/coding/v1/"
        }

    val usesAnthropicFormat: Boolean
        get() = this == CODING

    val preferenceKey: String
        get() = when (this) {
            GLOBAL -> "GLOBAL"
            CHINA -> "CHINA"
            CODING -> "CODING"
        }

    val defaultModels: List<String>
        get() = when (this) {
            GLOBAL -> listOf("kimi-k2.5", "kimi-k2-thinking", "kimi-k2-turbo-preview")
            CHINA -> listOf("kimi-k2.5", "kimi-k2-thinking", "kimi-k2-turbo-preview")
            CODING -> listOf("k2p5", "kimi-k2-thinking")
        }
}
