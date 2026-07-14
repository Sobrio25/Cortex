package com.aiagents.app.data.orchestration

import com.aiagents.app.domain.model.ProviderType
import com.aiagents.app.domain.model.SubagentTaskEnvelope
import com.aiagents.app.domain.model.SubagentRole
import com.aiagents.app.domain.model.SubagentWorkspacePolicy
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

@Singleton
class SubagentScheduler @Inject constructor() {
    private val cloudPermits = Semaphore(CLOUD_CONCURRENCY)
    private val kiloPermits = Semaphore(KILO_CONCURRENCY)
    private val localPermits = Semaphore(LOCAL_CONCURRENCY)
    private val orchestratorPermitsByDepth = ConcurrentHashMap<Int, Semaphore>()
    private val workspaceWriteLocks = ConcurrentHashMap<Long, Mutex>()

    suspend fun <T> run(task: SubagentTaskEnvelope, block: suspend () -> T): T {
        val provider = task.modelKey.substringBefore('|')
        // Orchestrators get a separate permit because they suspend while their children work.
        // Sharing worker permits would deadlock when every parent is waiting for a queued child.
        val semaphore = when {
            task.role == SubagentRole.ORCHESTRATOR ->
                orchestratorPermitsByDepth.getOrPut(task.depth) { Semaphore(ORCHESTRATORS_PER_DEPTH) }
            provider == ProviderType.LOCAL.name -> localPermits
            provider == ProviderType.KILO.name -> kiloPermits
            else -> cloudPermits
        }
        return semaphore.withPermit {
            if (task.workspacePolicy == SubagentWorkspacePolicy.WRITE_EXCLUSIVE) {
                workspaceWriteLocks.getOrPut(task.workspaceId) { Mutex() }.withLock { block() }
            } else {
                block()
            }
        }
    }

    companion object {
        const val CLOUD_CONCURRENCY = 3
        const val KILO_CONCURRENCY = 2
        const val LOCAL_CONCURRENCY = 1
        const val ORCHESTRATORS_PER_DEPTH = 1
    }
}
