package com.aiagents.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Restores the persisted download state represented by database schema v39. */
@Entity(tableName = "download_progress")
data class DownloadProgressEntity(
    @PrimaryKey
    val modelId: String,
    val modelName: String,
    val fileName: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: String,
    val errorMessage: String?,
    val workId: String?,
    val updatedAt: Long
)
