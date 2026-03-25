package com.aiagents.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aiagents.app.domain.model.Workspace

@Entity(
    tableName = "workspaces",
    foreignKeys = [
        ForeignKey(
            entity = AgentEntity::class,
            parentColumns = ["id"],
            childColumns = ["activeAgentId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("activeAgentId")]
)
data class WorkspaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val activeAgentId: Long? = null,
    val selectedModel: String = "",
    val systemPrompt: String = "",
    val externalStorageUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Workspace = Workspace(
        id = id,
        name = name,
        description = description,
        activeAgentId = activeAgentId,
        selectedModel = selectedModel,
        systemPrompt = systemPrompt,
        externalStorageUri = externalStorageUri,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(workspace: Workspace): WorkspaceEntity = WorkspaceEntity(
            id = workspace.id,
            name = workspace.name,
            description = workspace.description,
            activeAgentId = workspace.activeAgentId,
            selectedModel = workspace.selectedModel,
            systemPrompt = workspace.systemPrompt,
            externalStorageUri = workspace.externalStorageUri,
            createdAt = workspace.createdAt,
            updatedAt = workspace.updatedAt
        )
    }
}
