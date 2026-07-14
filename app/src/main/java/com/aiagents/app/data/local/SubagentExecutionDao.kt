package com.aiagents.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aiagents.app.data.model.SubagentExecutionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubagentExecutionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(execution: SubagentExecutionEntity)

    @Query("SELECT * FROM subagent_executions WHERE taskId = :taskId LIMIT 1")
    suspend fun get(taskId: String): SubagentExecutionEntity?

    @Query(
        "WITH RECURSIVE task_tree(taskId) AS (" +
            "SELECT taskId FROM subagent_executions WHERE parentConversationId = :conversationId " +
            "UNION ALL " +
            "SELECT child.taskId FROM subagent_executions AS child " +
            "JOIN task_tree AS parent ON child.parentTaskId = parent.taskId" +
            ") " +
            "SELECT execution.* FROM subagent_executions AS execution " +
            "JOIN task_tree ON execution.taskId = task_tree.taskId ORDER BY execution.createdAt ASC"
    )
    fun observeForConversation(conversationId: Long): Flow<List<SubagentExecutionEntity>>

    @Query(
        "SELECT * FROM subagent_executions " +
            "WHERE parentTaskId = :parentTaskId ORDER BY createdAt ASC"
    )
    fun observeChildren(parentTaskId: String): Flow<List<SubagentExecutionEntity>>

    @Query(
        "WITH RECURSIVE task_tree(taskId) AS (" +
            "SELECT taskId FROM subagent_executions WHERE parentConversationId = :conversationId " +
            "UNION ALL " +
            "SELECT child.taskId FROM subagent_executions AS child " +
            "JOIN task_tree AS parent ON child.parentTaskId = parent.taskId" +
            ") " +
            "SELECT execution.* FROM subagent_executions AS execution " +
            "JOIN task_tree ON execution.taskId = task_tree.taskId " +
            "WHERE execution.status IN ('QUEUED', 'RUNNING', 'WAITING_PERMISSION')"
    )
    suspend fun getActiveForConversation(conversationId: Long): List<SubagentExecutionEntity>

    @Query(
        "UPDATE subagent_executions SET status = 'RUNNING', subConversationId = :subConversationId, " +
            "startedAt = :startedAt, updatedAt = :startedAt WHERE taskId = :taskId"
    )
    suspend fun markRunning(taskId: String, subConversationId: Long, startedAt: Long)

    @Query(
        "UPDATE subagent_executions SET status = :status, resultSummary = :summary, " +
            "filesModified = :filesModified, testsRun = :testsRun, exitReason = :exitReason, " +
            "errorCode = :errorCode, errorMessage = :errorMessage, completedAt = :completedAt, " +
            "updatedAt = :completedAt WHERE taskId = :taskId"
    )
    suspend fun finish(
        taskId: String,
        status: String,
        summary: String,
        filesModified: String,
        testsRun: String,
        exitReason: String,
        errorCode: String?,
        errorMessage: String?,
        completedAt: Long
    )

    @Query(
        "UPDATE subagent_executions SET status = 'UNKNOWN', exitReason = 'process_restarted', " +
            "errorCode = 'PROCESS_RESTARTED', errorMessage = 'Execution ownership was lost when the app process restarted', " +
            "completedAt = :now, updatedAt = :now " +
            "WHERE status IN ('QUEUED', 'RUNNING', 'WAITING_PERMISSION')"
    )
    suspend fun markInterruptedExecutionsUnknown(now: Long): Int
}
