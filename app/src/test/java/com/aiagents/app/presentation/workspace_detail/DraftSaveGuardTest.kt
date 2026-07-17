package com.aiagents.app.presentation.workspace_detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftSaveGuardTest {
    private val workspace = 8L

    @Test
    fun `rapid A to B to A switch rejects both obsolete debounce writes`() {
        val guard = DraftSaveGuard()
        val a = DraftScope(workspace, 10L)
        val b = DraftScope(workspace, 20L)
        val oldA = guard.capture(a, "borrador A")

        guard.invalidate()
        val oldB = guard.capture(b, "borrador B")
        guard.invalidate()
        val currentA = guard.capture(a, "nuevo A")

        assertFalse(guard.canPersist(oldA, a, "nuevo A"))
        assertFalse(guard.canPersist(oldB, a, "nuevo A"))
        assertTrue(guard.canPersist(currentA, a, "nuevo A"))
    }

    @Test
    fun `send invalidation rejects save that was waiting for debounce`() {
        val guard = DraftSaveGuard()
        val scope = DraftScope(workspace, 10L)
        val pending = guard.capture(scope, "mensaje enviado")

        guard.invalidate()

        assertFalse(guard.canPersist(pending, scope, "mensaje enviado"))
    }
}
