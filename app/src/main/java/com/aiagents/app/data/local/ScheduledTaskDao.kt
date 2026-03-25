package com.aiagents.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aiagents.app.data.model.ScheduledTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledTaskDao {

    @Insert
    suspend fun insert(task: ScheduledTaskEntity): Long

    @Query("SELECT * FROM scheduled_tasks ORDER BY nextRunAt ASC")
    suspend fun getAll(): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_tasks ORDER BY nextRunAt ASC")
    fun observeAll(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id")
    suspend fun getById(id: Long): ScheduledTaskEntity?

    @Query("SELECT * FROM scheduled_tasks WHERE enabled = 1 ORDER BY nextRunAt ASC")
    suspend fun getEnabled(): List<ScheduledTaskEntity>

    @Query("UPDATE scheduled_tasks SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE scheduled_tasks SET lastRunAt = :time, lastResult = :result, runCount = runCount + 1, nextRunAt = :nextRun WHERE id = :id")
    suspend fun markExecuted(id: Long, time: Long, result: String?, nextRun: Long)

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun delete(id: Long)
}
