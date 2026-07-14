package com.aiagents.app.data.orchestration

/** Pure formatting shared by both modern tool-based and legacy delegation flows. */
object SubagentResultFormatter {
    fun buildSynthesisPrompt(
        originalRequest: String,
        results: List<IsolatedExecutionResult>
    ): String {
        val outputs = results.joinToString("\n\n---\n\n") { result ->
            buildString {
                append("## Agent: ${result.agentName} (sub-conversation #${result.subConversationId})\n")
                append("Status: ${statusLabel(result)}\n\n")
                append(result.summary)
                if (result.filesModified.isNotEmpty()) {
                    append("\nFiles: ${result.filesModified.joinToString()}")
                }
                if (result.testsRun.isNotEmpty()) {
                    append("\nChecks: ${result.testsRun.joinToString()}")
                }
            }
        }

        return """You are Cortex. Synthesize the output of ${results.size} subagents into the final answer for the user.

## Original user request
$originalRequest

## Subagent outputs
$outputs

## Required response
1. Give one coherent, self-contained final answer.
2. Preserve useful findings from partial results; clearly distinguish them from confirmed complete results.
3. Resolve duplication and conflicts, and mention only blockers that still matter.
4. If code was changed, summarize the integration and checks without repeating entire files.
5. Do not call tools, delegate again, or discuss this synthesis prompt."""
    }

    fun buildFallback(results: List<IsolatedExecutionResult>): String {
        if (results.isEmpty()) {
            return "No se pudo ejecutar ningún subagente ni generar la respuesta final."
        }

        return buildString {
            appendLine("No pude completar la síntesis automática, pero recuperé los resultados disponibles:")
            results.forEachIndexed { index, result ->
                appendLine()
                appendLine("### ${result.agentName} — ${statusLabel(result)}")
                append(result.summary.ifBlank { "Sin contenido recuperable." })
                if (result.filesModified.isNotEmpty()) {
                    appendLine()
                    append("Archivos: ${result.filesModified.joinToString()}")
                }
                if (result.testsRun.isNotEmpty()) {
                    appendLine()
                    append("Comprobaciones: ${result.testsRun.joinToString()}")
                }
                if (index != results.lastIndex) appendLine()
            }
        }.trim()
    }

    private fun statusLabel(result: IsolatedExecutionResult): String = when {
        result.success -> "COMPLETADO"
        result.exitReason == "partial_transport_failure" -> "PARCIAL RECUPERADO"
        result.exitReason == "cancelled" -> "CANCELADO"
        result.exitReason == "dependency_failed" -> "OMITIDO"
        else -> "FALLÓ"
    }
}
