package com.aiagents.app.domain.model

enum class ZAIPlanType {
    STANDARD,
    GLM_CODING;

    val displayName: String
        get() = when (this) {
            STANDARD -> "Z.AI Estándar"
            GLM_CODING -> "GLM Coding Plan"
        }

    val baseUrl: String
        get() = when (this) {
            STANDARD -> "https://api.z.ai/api/paas/v4/"
            GLM_CODING -> "https://api.z.ai/api/coding/paas/v4/"
        }

    val preferenceKey: String
        get() = when (this) {
            STANDARD -> "STANDARD"
            GLM_CODING -> "GLM_CODING"
        }

}
