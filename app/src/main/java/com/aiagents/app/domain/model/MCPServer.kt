package com.aiagents.app.domain.model

data class MCPServer(
    val id: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean,
    val iconRes: Int? = null,
    val config: MCPConfig = MCPConfig.Empty,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

sealed interface MCPConfig {
    object Empty : MCPConfig
    data class BraveSearch(
        val apiKey: String = "",
        val isConfigured: Boolean = false
    ) : MCPConfig
    data class GoogleMaps(
        val apiKey: String = "",
        val isConfigured: Boolean = false
    ) : MCPConfig
    data class SerpApi(
        val apiKey: String = "",
        val isConfigured: Boolean = false
    ) : MCPConfig
}

enum class MCPServerType {
    BRAVE_SEARCH,
    GOOGLE_MAPS,
    SERPAPI,
    TAVILY,
    CUSTOM
}
