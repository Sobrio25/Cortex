package com.aiagents.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_local_models")
data class CustomLocalModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val huggingFaceRepoId: String,
    val huggingFaceUrl: String,
    val fileName: String,
    val sizeBytes: Long,
    val description: String,
    val contextLength: Int = 4096,
    val requiresLicense: Boolean = false,
    val requiresHFToken: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)
