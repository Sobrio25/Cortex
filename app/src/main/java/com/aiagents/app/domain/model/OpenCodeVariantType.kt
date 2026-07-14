package com.aiagents.app.domain.model

enum class OpenCodeVariantType {
    ZEN,
    GO;

    val displayName: String
        get() = when (this) {
            ZEN -> "OpenCode Zen"
            GO -> "OpenCode Go"
        }

    val baseUrl: String
        get() = when (this) {
            ZEN -> "https://opencode.ai/zen/v1/"
            GO -> "https://opencode.ai/zen/go/v1/"
        }

    val preferenceKey: String
        get() = when (this) {
            ZEN -> "ZEN"
            GO -> "GO"
        }

}
