package com.aiagents.app.domain.model

data class Workspace(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val activeAgentId: Long? = null,
    val selectedModel: String = "",
    val systemPrompt: String = "",
    val externalStorageUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
