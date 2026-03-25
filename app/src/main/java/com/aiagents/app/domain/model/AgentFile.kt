package com.aiagents.app.domain.model

data class AgentFile(
    val id: Long = 0,
    val workspaceId: Long,
    val name: String,
    val path: String,
    val mimeType: String,
    val size: Long,
    val generatedByAI: Boolean = false,
    val uploadedAt: Long = System.currentTimeMillis()
)
