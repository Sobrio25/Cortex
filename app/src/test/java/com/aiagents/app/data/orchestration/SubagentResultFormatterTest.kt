package com.aiagents.app.data.orchestration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentResultFormatterTest {
    @Test
    fun fallbackPreservesCompletedAndPartialResults() {
        val fallback = SubagentResultFormatter.buildFallback(
            listOf(
                result("Researcher", "Hallazgo confirmado", success = true),
                result(
                    "Reviewer",
                    "Hallazgo visible antes del corte",
                    success = false,
                    exitReason = "partial_transport_failure"
                )
            )
        )

        assertTrue(fallback.contains("Researcher — COMPLETADO"))
        assertTrue(fallback.contains("Hallazgo confirmado"))
        assertTrue(fallback.contains("Reviewer — PARCIAL RECUPERADO"))
        assertTrue(fallback.contains("Hallazgo visible antes del corte"))
    }

    @Test
    fun synthesisPromptExplicitlyDisablesToolsAndIncludesPartialOutput() {
        val prompt = SubagentResultFormatter.buildSynthesisPrompt(
            originalRequest = "Investiga el tema",
            results = listOf(
                result(
                    "Researcher",
                    "Resultado recuperado",
                    success = false,
                    exitReason = "partial_transport_failure"
                )
            )
        )

        assertTrue(prompt.contains("Investiga el tema"))
        assertTrue(prompt.contains("Status: PARCIAL RECUPERADO"))
        assertTrue(prompt.contains("Resultado recuperado"))
        assertTrue(prompt.contains("Do not call tools"))
    }

    @Test
    fun emptyFallbackIsStillAUserVisibleFinalAnswer() {
        val fallback = SubagentResultFormatter.buildFallback(emptyList())

        assertFalse(fallback.isBlank())
        assertTrue(fallback.contains("No se pudo ejecutar"))
    }

    @Test
    fun synthesisAndFallbackUseOnlyBoundedChildSummary() {
        val result = IsolatedExecutionResult(
            finalContent = "RAW_API_PAYLOAD_SHOULD_STAY_IN_CHILD",
            summary = "Documento creado: https://docs.google.com/example",
            subConversationId = 8L,
            success = true,
            agentName = "Writer"
        )

        val prompt = SubagentResultFormatter.buildSynthesisPrompt("Crea el documento", listOf(result))
        val fallback = SubagentResultFormatter.buildFallback(listOf(result))

        assertTrue(prompt.contains(result.summary))
        assertTrue(fallback.contains(result.summary))
        assertFalse(prompt.contains(result.finalContent))
        assertFalse(fallback.contains(result.finalContent))
    }

    private fun result(
        agentName: String,
        content: String,
        success: Boolean,
        exitReason: String = if (success) "completed" else "exception"
    ) = IsolatedExecutionResult(
        finalContent = content,
        subConversationId = 1L,
        success = success,
        agentName = agentName,
        exitReason = exitReason
    )
}
