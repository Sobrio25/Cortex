package com.aiagents.app.data.model

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "cortex_memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val category: String,
    val subcategory: String = "",
    val importance: Int = 5,
    val confidence: Float = 1.0f,
    val source: String = "",
    val accessCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val lastAccessedAt: Long,
    val expiresAt: Long? = null
)

@Entity(tableName = "cortex_memory_fts")
@Fts4(contentEntity = MemoryEntity::class)
data class MemoryFtsEntity(
    val content: String
)

@Entity(
    tableName = "cortex_memory_links",
    foreignKeys = [
        ForeignKey(
            entity = MemoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        androidx.room.Index("sourceId"),
        androidx.room.Index("targetId")
    ]
)
data class MemoryLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceId: Long,
    val targetId: Long,
    val linkType: String,  // "related", "contradicts", "supersedes", "refines"
    val strength: Float = 1.0f,
    val createdAt: Long
)
