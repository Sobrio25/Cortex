package com.aiagents.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aiagents.app.domain.model.Conversation
import com.aiagents.app.domain.model.ConversationContextKind

@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workspaceId"), Index("lastMemoryExtraction")]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workspaceId: Long,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val parentConversationId: Long? = null,
    val delegationAgentName: String? = null,
    val delegationTask: String? = null,
    val status: String = "active",
    val lastMemoryExtraction: Long? = null,  // Timestamp of last successful memory extraction
    val contextKind: String = ConversationContextKind.CHAT.name
) {
    fun toDomain(): Conversation = Conversation(
        id = id,
        workspaceId = workspaceId,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        parentConversationId = parentConversationId,
        delegationAgentName = delegationAgentName,
        delegationTask = delegationTask,
        status = status,
        lastMemoryExtraction = lastMemoryExtraction,
        contextKind = runCatching { ConversationContextKind.valueOf(contextKind) }
            .getOrDefault(ConversationContextKind.CHAT)
    )

    /**
     * Returns true if there might be new content to extract since last extraction.
     */
    fun hasNewContent(): Boolean {
        return lastMemoryExtraction == null || updatedAt > lastMemoryExtraction
    }

    companion object {
        fun fromDomain(conversation: Conversation): ConversationEntity = ConversationEntity(
            id = conversation.id,
            workspaceId = conversation.workspaceId,
            title = conversation.title,
            createdAt = conversation.createdAt,
            updatedAt = conversation.updatedAt,
            parentConversationId = conversation.parentConversationId,
            delegationAgentName = conversation.delegationAgentName,
            delegationTask = conversation.delegationTask,
            status = conversation.status,
            lastMemoryExtraction = conversation.lastMemoryExtraction,
            contextKind = conversation.contextKind.name
        )
    }
}
