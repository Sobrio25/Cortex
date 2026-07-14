package com.aiagents.app.data.orchestration

import com.aiagents.app.data.local.SubagentExecutionDao
import com.aiagents.app.data.model.SubagentExecutionEntity
import com.aiagents.app.data.model.boundedSummary
import com.aiagents.app.domain.model.SubagentResult
import com.aiagents.app.domain.model.SubagentStatus
import com.aiagents.app.domain.model.SubagentTaskEnvelope
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Singleton
class SubagentRuntime @Inject constructor(
    private val dao: SubagentExecutionDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    private val recovery = scope.async {
        dao.markInterruptedExecutionsUnknown(System.currentTimeMillis())
    }

    suspend fun enqueue(task: SubagentTaskEnvelope) {
        recovery.await()
        dao.upsert(SubagentExecutionEntity.queued(task))
    }

    fun register(taskId: String, job: Job) {
        jobs[taskId] = job
    }

    suspend fun markRunning(taskId: String, subConversationId: Long) {
        dao.markRunning(taskId, subConversationId, System.currentTimeMillis())
    }

    suspend fun complete(task: SubagentTaskEnvelope, result: SubagentResult) {
        jobs.remove(task.taskId)
        dao.finish(
            taskId = task.taskId,
            status = if (result.success) SubagentStatus.COMPLETED.name else SubagentStatus.FAILED.name,
            summary = result.boundedSummary(task.budget.maxResultChars),
            filesModified = result.filesModified.joinToString("\n"),
            testsRun = result.testsRun.joinToString("\n"),
            exitReason = result.exitReason,
            errorCode = result.errorCode,
            errorMessage = result.errorMessage,
            completedAt = result.completedAt
        )
    }

    suspend fun cancelled(task: SubagentTaskEnvelope, reason: String) {
        jobs.remove(task.taskId)
        val now = System.currentTimeMillis()
        dao.finish(
            taskId = task.taskId,
            status = SubagentStatus.CANCELLED.name,
            summary = "",
            filesModified = "",
            testsRun = "",
            exitReason = "cancelled",
            errorCode = "CANCELLED",
            errorMessage = reason,
            completedAt = now
        )
    }

    fun cancel(taskId: String, reason: String = "Cancelled by user"): Boolean {
        val job = jobs[taskId] ?: return false
        job.cancel(CancellationException(reason))
        return true
    }

    fun cancelForConversation(conversationId: Long, reason: String = "Parent execution cancelled") {
        scope.launch {
            dao.getActiveForConversation(conversationId).forEach { execution ->
                cancel(execution.taskId, reason)
            }
        }
    }

    fun observeForConversation(conversationId: Long): Flow<List<SubagentExecutionEntity>> =
        dao.observeForConversation(conversationId)

    fun observeChildren(parentTaskId: String): Flow<List<SubagentExecutionEntity>> =
        dao.observeChildren(parentTaskId)
}
