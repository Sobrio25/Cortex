package com.aiagents.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aiagents.app.domain.model.SubagentResult
import com.aiagents.app.domain.model.SubagentStatus
import com.aiagents.app.domain.model.SubagentTaskEnvelope

@Entity(
    tableName = "subagent_executions",
    indices = [
        Index("parentConversationId"),
        Index("parentTaskId"),
        Index("workspaceId"),
        Index("status")
    ]
)
data class SubagentExecutionEntity(
    @PrimaryKey val taskId: String,
    val parentTaskId: String?,
    val parentConversationId: Long,
    val subConversationId: Long?,
    val workspaceId: Long,
    val agentId: Long,
    val agentName: String,
    val goal: String,
    val acceptanceCriteria: String,
    val status: String,
    val role: String,
    val mode: String,
    val failurePolicy: String,
    val depth: Int,
    val modelKey: String,
    val allowedTools: String,
    val toolPermissions: String,
    val workspacePolicy: String,
    val maxIterations: Int,
    val timeoutMillis: Long?,
    val maxResultChars: Int,
    val resultSummary: String,
    val filesModified: String,
    val testsRun: String,
    val exitReason: String?,
    val errorCode: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val startedAt: Long?,
    val completedAt: Long?,
    val updatedAt: Long
) {
    companion object {
        fun queued(task: SubagentTaskEnvelope): SubagentExecutionEntity = SubagentExecutionEntity(
            taskId = task.taskId,
            parentTaskId = task.parentTaskId,
            parentConversationId = task.parentConversationId,
            subConversationId = null,
            workspaceId = task.workspaceId,
            agentId = task.agentId,
            agentName = task.agentName,
            goal = task.goal,
            acceptanceCriteria = task.acceptanceCriteria,
            status = SubagentStatus.QUEUED.name,
            role = task.role.name,
            mode = task.mode.name,
            failurePolicy = task.failurePolicy.name,
            depth = task.depth,
            modelKey = task.modelKey,
            allowedTools = task.allowedTools.sorted().joinToString(","),
            toolPermissions = task.toolPermissions.entries
                .sortedBy { it.key }
                .joinToString(",") { "${it.key}=${it.value.name}" },
            workspacePolicy = task.workspacePolicy.name,
            maxIterations = task.budget.maxIterations,
            timeoutMillis = task.budget.timeoutMillis,
            maxResultChars = task.budget.maxResultChars,
            resultSummary = "",
            filesModified = "",
            testsRun = "",
            exitReason = null,
            errorCode = null,
            errorMessage = null,
            createdAt = task.createdAt,
            startedAt = null,
            completedAt = null,
            updatedAt = task.createdAt
        )
    }
}

fun SubagentResult.boundedSummary(maxChars: Int): String = summary.take(maxChars)
