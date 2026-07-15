package com.aiagents.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scheduled_tasks",
    indices = [
        Index(value = ["nextRunAt"]),
        Index(value = ["enabled"]),
        Index(value = ["conversationId"])
    ]
)
data class ScheduledTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workspaceId: Long,
    val conversationId: Long? = null,     // chat where every execution is persisted
    val agentName: String? = null,       // null = Cortex
    val prompt: String,                   // what to execute
    val scheduleType: String,             // once, daily, weekly, interval
    val scheduleValue: String,            // "09:00", "MON,WED,FRI 09:00", "30m", ISO datetime
    val label: String = "",               // human-readable label
    val enabled: Boolean = true,
    val lastRunAt: Long? = null,
    val lastResult: String? = null,       // last execution result summary (truncated)
    val nextRunAt: Long,
    val runCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
