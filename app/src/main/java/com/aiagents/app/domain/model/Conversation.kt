package com.aiagents.app.domain.model

data class Conversation(
    val id: Long = 0,
    val workspaceId: Long,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val parentConversationId: Long? = null,
    val delegationAgentName: String? = null,
    val delegationTask: String? = null,
    val status: String = "active",
    val lastMemoryExtraction: Long? = null,
    val contextKind: ConversationContextKind = ConversationContextKind.CHAT
)

enum class ConversationContextKind {
    CHAT,
    VOICE_ASSISTANT
}
