package com.aiagents.app.data.events

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentChangeNotifierTest {
    @Test
    fun `memory event identifies secondary persistence without exposing content`() = runBlocking {
        val notifier = AgentChangeNotifier()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { notifier.events.first() }

        notifier.memorySaved(AgentChangeNotifier.TARGET_ARCHIVE, itemCount = 3)

        val event = pending.await()
        assertEquals(AgentChangeKind.MEMORY_SAVED, event.kind)
        assertEquals("Memoria actualizada", event.title)
        assertEquals("3 datos en memoria secundaria", event.detail)
    }

    @Test
    fun `skill events distinguish creation from update`() = runBlocking {
        val notifier = AgentChangeNotifier()
        val createdPending = async(start = CoroutineStart.UNDISPATCHED) { notifier.events.first() }
        notifier.skillCreated("Clima local")
        val created = createdPending.await()

        val updatedPending = async(start = CoroutineStart.UNDISPATCHED) { notifier.events.first() }
        notifier.skillUpdated("Clima local")
        val updated = updatedPending.await()

        assertEquals(AgentChangeKind.SKILL_CREATED, created.kind)
        assertEquals("Skill creada", created.title)
        assertEquals(AgentChangeKind.SKILL_UPDATED, updated.kind)
        assertEquals("Skill actualizada", updated.title)
        assertEquals("Clima local", updated.detail)
    }
}
