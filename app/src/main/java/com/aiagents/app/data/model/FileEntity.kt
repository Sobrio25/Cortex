package com.aiagents.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aiagents.app.domain.model.AgentFile

@Entity(
    tableName = "files",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workspaceId")]
)
data class FileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workspaceId: Long,
    val name: String,
    val path: String,
    val mimeType: String,
    val size: Long,
    val generatedByAI: Boolean = false,
    val uploadedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): AgentFile = AgentFile(
        id = id,
        workspaceId = workspaceId,
        name = name,
        path = path,
        mimeType = mimeType,
        size = size,
        generatedByAI = generatedByAI,
        uploadedAt = uploadedAt
    )

    companion object {
        fun fromDomain(file: AgentFile): FileEntity = FileEntity(
            id = file.id,
            workspaceId = file.workspaceId,
            name = file.name,
            path = file.path,
            mimeType = file.mimeType,
            size = file.size,
            generatedByAI = file.generatedByAI,
            uploadedAt = file.uploadedAt
        )
    }
}
