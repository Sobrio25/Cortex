package com.aiagents.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "skills",
    indices = [
        Index(value = ["slug"], unique = true),
        Index(value = ["status"]),
        Index(value = ["origin"])
    ]
)
data class SkillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val slug: String,
    val name: String,
    val description: String,
    val whenToUse: String,
    val instructions: String,
    val category: String = "CUSTOM",
    val requiredTools: String = "",
    val status: String,
    val origin: String,
    val isImmutable: Boolean = false,
    val version: Int = 1,
    val createdAt: Long,
    val updatedAt: Long,
    val activatedAt: Long? = null,
    val archivedAt: Long? = null
)

@Entity(
    tableName = "skill_reviews",
    foreignKeys = [
        ForeignKey(
            entity = SkillEntity::class,
            parentColumns = ["id"],
            childColumns = ["candidateSkillId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["status"]),
        Index(value = ["candidateSkillId"]),
        Index(value = ["createdAt"]),
        Index(value = ["transcriptFingerprint"], unique = true)
    ]
)
data class SkillReviewEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scopeHash: String,
    val messageCount: Int,
    val transcriptFingerprint: String,
    val redactedTranscript: String,
    val status: String,
    val summary: String = "",
    val candidateSkillId: Long? = null,
    val createdAt: Long,
    val completedAt: Long? = null
)
