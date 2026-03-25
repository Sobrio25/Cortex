package com.aiagents.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "todos",
    indices = [Index(value = ["conversationId"])]
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val position: Int,
    val content: String,
    val status: String = "pending",    // pending, in_progress, completed, cancelled
    val priority: String = "medium",   // high, medium, low
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
