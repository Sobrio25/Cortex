package com.aiagents.app.domain.model

import java.util.UUID

enum class SubagentStatus {
    QUEUED,
    RUNNING,
    WAITING_PERMISSION,
    COMPLETED,
    FAILED,
    CANCELLED,
    UNKNOWN
}

enum class SubagentRole {
    LEAF,
    ORCHESTRATOR
}

enum class SubagentExecutionMode {
    PARALLEL,
    SEQUENTIAL
}

enum class SubagentFailurePolicy {
    FAIL_FAST,
    CONTINUE
}

enum class SubagentWorkspacePolicy {
    READ_ONLY_SHARED,
    WRITE_EXCLUSIVE
}

enum class SubagentToolPermission {
    ALLOW,
    ASK,
    DENY
}

data class SubagentBudget(
    val maxIterations: Int = 25,
    val timeoutMillis: Long? = null,
    val maxResultChars: Int = 6_000
) {
    init {
        require(maxIterations in 1..100) { "maxIterations must be between 1 and 100" }
        require(timeoutMillis == null || timeoutMillis >= 30_000L) {
            "timeoutMillis must be null or at least 30 seconds"
        }
        require(maxResultChars in 1_000..50_000) {
            "maxResultChars must be between 1,000 and 50,000"
        }
    }
}

data class SubagentTaskEnvelope(
    val taskId: String = UUID.randomUUID().toString(),
    val parentTaskId: String? = null,
    val parentConversationId: Long,
    val workspaceId: Long,
    val agentId: Long,
    val agentName: String,
    val goal: String,
    val context: String = "",
    val acceptanceCriteria: String = "",
    val mode: SubagentExecutionMode = SubagentExecutionMode.PARALLEL,
    val failurePolicy: SubagentFailurePolicy = SubagentFailurePolicy.FAIL_FAST,
    val role: SubagentRole = SubagentRole.LEAF,
    val depth: Int = 1,
    val modelKey: String,
    val allowedTools: Set<String> = emptySet(),
    val toolPermissions: Map<String, SubagentToolPermission> = emptyMap(),
    val workspacePolicy: SubagentWorkspacePolicy = SubagentWorkspacePolicy.READ_ONLY_SHARED,
    val budget: SubagentBudget = SubagentBudget(),
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(taskId.isNotBlank()) { "taskId is required" }
        require(goal.isNotBlank()) { "goal is required" }
        require(depth >= 1) { "depth must be at least 1" }
    }
}

data class SubagentResult(
    val taskId: String,
    val agentName: String,
    val subConversationId: Long?,
    val success: Boolean,
    val finalContent: String,
    val summary: String,
    val filesModified: List<String> = emptyList(),
    val testsRun: List<String> = emptyList(),
    val exitReason: String,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val startedAt: Long,
    val completedAt: Long = System.currentTimeMillis()
)
