package com.aiagents.app.data.background

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import javax.inject.Inject
import javax.inject.Singleton

data class CortexTaskStatus(
    val workspaceId: Long,
    val agentName: String,
    val startedAtMillis: Long
)

/**
 * Process-level registry for user-initiated Cortex work.
 *
 * The execution coroutine is supplied by the owning ViewModel, but its scope is deliberately
 * independent from viewModelScope. This registry keeps cancellation available after the screen
 * is destroyed and owns the foreground-service lifetime for every active workspace task.
 */
@Singleton
class CortexTaskCoordinator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private data class ActiveTask(
        val status: CortexTaskStatus,
        val job: Job
    )

    private val lock = Any()
    private val active = mutableMapOf<Long, ActiveTask>()
    private val _activeTasks = MutableStateFlow<Map<Long, CortexTaskStatus>>(emptyMap())
    val activeTasks: StateFlow<Map<Long, CortexTaskStatus>> = _activeTasks.asStateFlow()

    fun launch(
        workspaceId: Long,
        agentName: String,
        scope: CoroutineScope,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        cancel(workspaceId)

        val status = CortexTaskStatus(
            workspaceId = workspaceId,
            agentName = agentName,
            startedAtMillis = System.currentTimeMillis()
        )

        lateinit var taskJob: Job
        taskJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                // Let launch() return the Job to its owner before user work can complete.
                yield()
                block()
            } finally {
                finish(workspaceId, taskJob)
            }
        }

        val snapshot = synchronized(lock) {
            active[workspaceId] = ActiveTask(status, taskJob)
            publishLocked()
        }
        updateForegroundService(snapshot)
        taskJob.start()
        return taskJob
    }

    fun cancel(workspaceId: Long) {
        val job = synchronized(lock) { active[workspaceId]?.job }
        job?.cancel()
    }

    fun cancelAll() {
        val jobs = synchronized(lock) { active.values.map { it.job } }
        jobs.forEach(Job::cancel)
    }

    private fun finish(workspaceId: Long, completedJob: Job) {
        val snapshot = synchronized(lock) {
            if (active[workspaceId]?.job === completedJob) {
                active.remove(workspaceId)
            }
            publishLocked()
        }
        updateForegroundService(snapshot)
    }

    private fun publishLocked(): Map<Long, CortexTaskStatus> {
        val snapshot = active.mapValues { it.value.status }
        _activeTasks.value = snapshot
        return snapshot
    }

    private fun updateForegroundService(tasks: Map<Long, CortexTaskStatus>) {
        val representative = tasks.values.minByOrNull(CortexTaskStatus::startedAtMillis)
        if (representative == null) {
            CortexForegroundService.stop(context)
        } else {
            CortexForegroundService.start(
                context = context,
                agentName = representative.agentName,
                activeTaskCount = tasks.size,
                startedAtMillis = representative.startedAtMillis
            )
        }
    }
}
