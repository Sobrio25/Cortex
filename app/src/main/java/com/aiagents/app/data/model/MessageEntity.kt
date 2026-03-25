package com.aiagents.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AgentEntity::class,
            parentColumns = ["id"],
            childColumns = ["agentId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workspaceId"), Index("agentId"), Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workspaceId: Long,
    val agentId: Long? = null,
    val conversationId: Long? = null,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachedFiles: String = "",
    val toolCallsJson: String = "",
    val toolResultsJson: String = "",
    val reasoning: String? = null,
    val subConversationId: Long? = null
) {
    fun toDomain(): Message = Message(
        id = id,
        role = MessageRole.valueOf(role),
        content = content,
        timestamp = timestamp,
        attachedFiles = attachedFiles.split(",").filter { it.isNotBlank() },
        toolCalls = parseToolCalls(toolCallsJson),
        toolResults = parseToolResults(toolResultsJson),
        reasoning = reasoning,
        imageDataUris = emptyList(), // No se guardan en BD, se usan solo en memoria para enviar al modelo
        subConversationId = subConversationId
    )

    private fun parseToolCalls(json: String): List<ToolCall> {
        if (json.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<ToolCall>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseToolResults(json: String): List<ToolResult> {
        if (json.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<ToolResult>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private val gson = Gson()

        fun fromDomain(message: Message, workspaceId: Long, agentId: Long? = null, conversationId: Long? = null): MessageEntity = MessageEntity(
            id = message.id,
            workspaceId = workspaceId,
            agentId = agentId,
            conversationId = conversationId,
            role = message.role.name,
            content = message.content,
            timestamp = message.timestamp,
            attachedFiles = message.attachedFiles.joinToString(","),
            toolCallsJson = if (message.toolCalls.isEmpty()) "" else gson.toJson(message.toolCalls),
            toolResultsJson = if (message.toolResults.isEmpty()) "" else gson.toJson(message.toolResults),
            reasoning = message.reasoning,
            subConversationId = message.subConversationId
        )
    }
}
