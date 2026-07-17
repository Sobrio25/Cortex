package com.aiagents.app.data.diagnostics

import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import com.aiagents.app.domain.model.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticTraceStoreTest {
    @Test
    fun `trace follows provider tool and final response lifecycle`() {
        val store = DiagnosticTraceStore.forTesting(capacity = 4)
        val traceId = "turn-test"
        store.beginProviderRequest(
            traceId = traceId,
            provider = "MANAGED",
            model = "free-model",
            agent = "Cortex",
            messageCount = 1,
            exposedToolCount = 12,
            nowEpochMs = 100
        )
        store.completeProviderRequest(
            traceId = traceId,
            durationMs = 45,
            toolCalls = listOf(toolCall("call-1", "weather_forecast")),
            nowEpochMs = 145
        )

        assertEquals(DiagnosticTraceStatus.AWAITING_TOOLS, store.traces.value.single().status)

        val toolResult = ToolResult("call-1", "weather_forecast", "private raw weather payload")
        store.recordToolResults(listOf(toolResult), nowEpochMs = 155)
        assertEquals(DiagnosticTraceStatus.COMPLETED, store.traces.value.single().status)

        store.beginProviderRequest(
            traceId = traceId,
            provider = "MANAGED",
            model = "free-model",
            agent = "Cortex",
            messageCount = 3,
            exposedToolCount = 12,
            existingToolResults = listOf(toolResult),
            nowEpochMs = 160
        )
        store.completeProviderRequest(
            traceId = traceId,
            durationMs = 30,
            toolCalls = emptyList(),
            nowEpochMs = 190
        )

        val completed = store.traces.value.single()
        assertEquals(DiagnosticTraceStatus.COMPLETED, completed.status)
        assertEquals(2, completed.requestCount)
        assertEquals(75, completed.providerDurationMs)
        assertEquals(2, completed.toolEvents.size)
        assertTrue(completed.toolEvents.any { it.phase == DiagnosticToolPhase.CALL })
        assertTrue(completed.toolEvents.any { it.phase == DiagnosticToolPhase.RESULT })
        assertFalseTraceContains(completed, "private raw weather payload")
    }

    @Test
    fun `ring buffer keeps only newest traces and can be cleared`() {
        val store = DiagnosticTraceStore.forTesting(capacity = 2)
        repeat(3) { index ->
            store.beginProviderRequest(
                traceId = "turn-$index",
                provider = "LOCAL",
                model = "model-$index",
                agent = "Agent",
                messageCount = 1,
                exposedToolCount = 0,
                nowEpochMs = index.toLong()
            )
        }

        assertEquals(listOf("turn-2", "turn-1"), store.traces.value.map { it.id })
        store.clear()
        assertTrue(store.traces.value.isEmpty())
    }

    @Test
    fun `managed HTTP 429 is classified as a rate limit`() {
        val store = DiagnosticTraceStore.forTesting(capacity = 2)
        store.beginProviderRequest(
            traceId = "turn-quota",
            provider = "MANAGED",
            model = "auto",
            agent = "Cortex",
            messageCount = 1,
            exposedToolCount = 8,
            nowEpochMs = 100
        )

        store.failProviderRequest(
            traceId = "turn-quota",
            durationMs = 20,
            throwable = IllegalStateException("HTTP 429: cuota semanal agotada"),
            nowEpochMs = 120
        )

        assertEquals("RATE_LIMIT", store.traces.value.single().errorCategory)
    }

    @Test
    fun `correlation id is stable for rounds in one user turn`() {
        val user = Message(id = 42, role = MessageRole.USER, content = "secret", timestamp = 1234)
        val sameTurn = listOf(
            user,
            Message(role = MessageRole.TOOL, content = "result", timestamp = 1235)
        )

        assertEquals(TurnCorrelationId.from(1, listOf(user)), TurnCorrelationId.from(1, sameTurn))
        assertNotEquals(TurnCorrelationId.from(1, sameTurn), TurnCorrelationId.from(2, sameTurn))
    }

    private fun toolCall(id: String, name: String) = ToolCall(
        id = id,
        function = ToolFunction(name = name, arguments = "{\"private\":true}")
    )

    private fun assertFalseTraceContains(trace: DiagnosticTurnTrace, value: String) {
        assertTrue(trace.toString().contains(value).not())
    }
}
