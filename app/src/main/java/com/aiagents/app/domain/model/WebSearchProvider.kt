package com.aiagents.app.domain.model

/** Provider used by the unified web_search tool. Native is deliberately the safe default. */
enum class WebSearchProvider {
    NATIVE,
    BRAVE,
    SERPAPI;

    companion object {
        fun fromStoredValue(value: String?): WebSearchProvider =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NATIVE
    }
}
