package com.aiagents.app.domain.model

/**
 * OpenAI's public inference API authenticates with API keys, not a ChatGPT OAuth session.
 * OAUTH_BACKEND lets the Android client call a user-controlled backend which keeps provider
 * credentials server-side and exposes its own user OAuth/session to Cortex.
 */
enum class OpenAIAuthMode {
    API_KEY,
    OAUTH_BACKEND;

    companion object {
        /** Keeps installations made before the OAuth mode was named explicitly working. */
        fun fromStoredValue(value: String?): OpenAIAuthMode = when (value) {
            "OAUTH_BACKEND", "BACKEND_PROXY" -> OAUTH_BACKEND
            else -> API_KEY
        }
    }
}
