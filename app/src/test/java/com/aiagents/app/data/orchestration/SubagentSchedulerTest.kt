package com.aiagents.app.data.orchestration

import com.aiagents.app.domain.model.SubagentTaskEnvelope
import com.aiagents.app.domain.model.SubagentRole
import com.aiagents.app.domain.model.SubagentWorkspacePolicy
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentSchedulerTest {
    @Test
    fun cloudConcurrencyIsBounded() = runBlocking {
        val scheduler = SubagentScheduler()
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        (1..8).map { index ->
            async {
                scheduler.run(task(index, SubagentWorkspacePolicy.READ_ONLY_SHARED)) {
                    val current = active.incrementAndGet()
                    maximum.updateAndGet { maxOf(it, current) }
                    delay(20)
                    active.decrementAndGet()
                }
            }
        }.awaitAll()

        assertTrue(maximum.get() <= SubagentScheduler.CLOUD_CONCURRENCY)
        assertTrue(maximum.get() > 1)
    }

    @Test
    fun kiloConcurrencyUsesProviderSpecificLimit() = runBlocking {
        val scheduler = SubagentScheduler()
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        (1..6).map { index ->
            async {
                scheduler.run(
                    task(index, SubagentWorkspacePolicy.READ_ONLY_SHARED).copy(
                        modelKey = "KILO|test"
                    )
                ) {
                    val current = active.incrementAndGet()
                    maximum.updateAndGet { maxOf(it, current) }
                    delay(20)
                    active.decrementAndGet()
                }
            }
        }.awaitAll()

        assertEquals(SubagentScheduler.KILO_CONCURRENCY, maximum.get())
    }

    @Test
    fun writeTasksInSameWorkspaceAreExclusive() = runBlocking {
        val scheduler = SubagentScheduler()
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        (1..4).map { index ->
            async {
                scheduler.run(task(index, SubagentWorkspacePolicy.WRITE_EXCLUSIVE)) {
                    val current = active.incrementAndGet()
                    maximum.updateAndGet { maxOf(it, current) }
                    delay(15)
                    active.decrementAndGet()
                }
            }
        }.awaitAll()

        assertEquals(1, maximum.get())
    }

    @Test
    fun orchestratorDoesNotConsumeWorkerPermitWhileChildRuns() = runBlocking {
        val scheduler = SubagentScheduler()
        scheduler.run(task(1, SubagentWorkspacePolicy.READ_ONLY_SHARED).copy(role = SubagentRole.ORCHESTRATOR)) {
            scheduler.run(
                task(9, SubagentWorkspacePolicy.READ_ONLY_SHARED).copy(
                    role = SubagentRole.ORCHESTRATOR,
                    depth = 2
                )
            ) {
                val children = (2..5).map { index ->
                    async {
                        scheduler.run(task(index, SubagentWorkspacePolicy.READ_ONLY_SHARED)) { delay(5) }
                    }
                }
                children.awaitAll()
            }
        }
        Unit
    }

    private fun task(index: Int, policy: SubagentWorkspacePolicy) = SubagentTaskEnvelope(
        taskId = "task-$index",
        parentConversationId = 1,
        workspaceId = 7,
        agentId = index.toLong(),
        agentName = "Agent $index",
        goal = "Task $index",
        modelKey = "OPENAI|test",
        workspacePolicy = policy
    )
}
