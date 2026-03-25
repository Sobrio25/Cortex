package com.aiagents.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mcp_servers")
data class MCPServerEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean = false,
    val configJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class BraveSearchConfig(
    val apiKey: String = "",
    val isConfigured: Boolean = false
)

data class SerpApiConfig(
    val apiKey: String = "",
    val isConfigured: Boolean = false
)
