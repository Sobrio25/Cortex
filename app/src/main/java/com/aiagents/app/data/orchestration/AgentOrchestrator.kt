package com.aiagents.app.data.orchestration

import android.util.Log
import com.aiagents.app.data.repository.AgentRepository
import com.aiagents.app.domain.model.Agent
import com.aiagents.app.domain.model.isOrchestrator
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Result of parsing a local model's delegation response. */
sealed class DelegationResult {
    data class DelegateToAgent(val agentName: String) : DelegationResult()
    data class NoAgent(val reason: String) : DelegationResult()
    data object ParseError : DelegationResult()
}

/**
 * Represents a parallel delegation entry, which may include a subtask description.
 * Format: [AgentName: subtask description] or just [AgentName]
 */
data class ParallelDelegationEntry(
    val agentName: String,
    val subtask: String? = null
)

@Singleton
class AgentOrchestrator @Inject constructor(
    private val repository: AgentRepository
) {
    companion object {
        private const val TAG = "AgentOrchestrator"
        internal const val DEFAULT_CORTEX_PROMPT =
            "Complete the user's request directly and use available tools when they improve accuracy."
    }
    suspend fun buildPrompt(cortex: Agent): String = buildString {
        val storedPrompt = cortex.systemPrompt.trim()
        val isGeneratedLegacyPrompt = storedPrompt.contains("{agents_list}") ||
            storedPrompt.contains("## DELEGATION PROTOCOL") ||
            storedPrompt.contains("central AI agent orchestration system", ignoreCase = true)
        (if (isGeneratedLegacyPrompt) DEFAULT_CORTEX_PROMPT else storedPrompt)
            .trim()
            .takeIf(String::isNotBlank)
            ?.let { appendLine(it).appendLine() }

        appendLine(
            """
## OPERATING MODEL
- Complete the user's request directly with the available tools. Programming, research, writing, and file work do not require a persistent specialist agent.
- Use `spawn_subagents` only when parallel independent work, a fresh isolated review, noisy intermediate context, or a scoped external integration materially helps. Tasks are temporary workers unless the user explicitly names a custom agent.
- Use parallel mode only for independent tasks; serialize dependent steps and overlapping workspace writes. Give every worker a self-contained goal and acceptance criteria, then synthesize the results.
- Google Docs, Drive, Sheets, Gmail, Calendar, and Slides must run in an isolated worker with the narrowest matching `google_*` capability. Never expose raw integration payloads.
- Work autonomously when the request is clear. Ask only when a missing decision blocks the requested outcome. If choices are required, use `<ask_options titulo="Pregunta">` with 2–10 `- Opción` lines.

## RESPONSE
- Tool calls and delegation are internal. Do not narrate them or expose tool transcripts.
- Return one consolidated user-facing response after all work finishes.
- Verify writes and external actions before claiming success. For current facts, search first and retry another source when fetching fails.
            """.trimIndent()
        )
        appendLine()
        append(buildPersonalitySection(cortex))
    }

    private fun buildPersonalitySection(cortex: Agent): String {
        fun desc(level: Int, low: String, midLow: String, mid: String, midHigh: String, high: String) = when (level) {
            in 0..20 -> low; in 21..40 -> midLow; in 41..60 -> mid; in 61..80 -> midHigh; else -> high
        }
        val sarcasm = desc(cortex.sarcasmLevel, "serious/direct", "slightly ironic", "moderate sarcasm", "sharp humor", "very sarcastic")
        val creativity = desc(cortex.creativityLevel, "conventional", "practical", "balanced", "creative", "highly innovative")
        val formality = desc(cortex.formalityLevel, "very casual", "relaxed", "balanced", "formal", "very formal")
        val empathy = desc(cortex.empathyLevel, "fact-focused", "practical", "moderate empathy", "empathetic", "deeply empathetic")
        val precision = desc(cortex.technicalPrecision, "conceptual", "accessible", "balanced", "technical", "highly technical")

        return "## Personality\nSarcasm:${cortex.sarcasmLevel}($sarcasm) Creative:${cortex.creativityLevel}($creativity) Formal:${cortex.formalityLevel}($formality) Empathy:${cortex.empathyLevel}($empathy) Precision:${cortex.technicalPrecision}($precision)"
    }

    /**
     * Builds a compact prompt optimized for small local models (e.g. Gemma 3 1B).
     * Keeps total prompt under ~500 tokens for fast on-device routing.
     */
    suspend fun buildCompactDelegationPrompt(userQuery: String): String {
        val agents = repository.getAllAgentsOnce()
            .filter { !it.isOrchestrator && it.whenToUse.isNotEmpty() }

        val agentList = if (agents.isEmpty()) {
            "No agents available."
        } else {
            agents.mapIndexed { i, agent ->
                "${i + 1}. ${agent.name} — ${agent.whenToUse}"
            }.joinToString("\n")
        }

        return buildString {
            appendLine("You are a router. Your ONLY task is to pick which agent should respond.")
            appendLine()
            appendLine("Agents:")
            appendLine(agentList)
            appendLine()
            appendLine("Reply ONLY with:")
            appendLine("TOOL_CALL: {\"name\": \"delegate\", \"arguments\": {\"agent\": \"ExactName\"}}")
            appendLine()
            appendLine("If no agent fits:")
            appendLine("TOOL_CALL: {\"name\": \"no_agent\", \"arguments\": {\"reason\": \"brief reason\"}}")
            appendLine()
            appendLine("Examples:")
            for (agent in agents) {
                val firstKeyword = agent.whenToUse.split(",").firstOrNull()?.trim() ?: continue
                appendLine("User: Help me with $firstKeyword")
                appendLine("TOOL_CALL: {\"name\": \"delegate\", \"arguments\": {\"agent\": \"${agent.name}\"}}")
                appendLine()
            }
            appendLine("User: Hello, how are you?")
            appendLine("TOOL_CALL: {\"name\": \"no_agent\", \"arguments\": {\"reason\": \"general greeting\"}}")
            appendLine()
            appendLine("User: $userQuery")
        }
    }

    /**
     * Parses a TOOL_CALL response from the local routing model.
     * Expected formats:
     * - TOOL_CALL: {"name": "delegate", "arguments": {"agent": "AgentName"}}
     * - TOOL_CALL: {"name": "no_agent", "arguments": {"reason": "..."}}
     */
    fun parseDelegationFromToolCall(response: String): DelegationResult {
        val toolCallRegex = """TOOL_CALL:\s*(\{.*\})""".toRegex()
        val match = toolCallRegex.find(response)
            ?: return DelegationResult.ParseError

        return try {
            val json = JSONObject(match.groupValues[1])
            val name = json.optString("name", "")
            val args = json.optJSONObject("arguments")

            when (name) {
                "delegate" -> {
                    val agentName = args?.optString("agent", "")
                    if (!agentName.isNullOrBlank()) {
                        DelegationResult.DelegateToAgent(agentName)
                    } else {
                        DelegationResult.ParseError
                    }
                }
                "no_agent" -> {
                    val reason = args?.optString("reason", "no reason") ?: "no reason"
                    DelegationResult.NoAgent(reason)
                }
                else -> DelegationResult.ParseError
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse local routing response: ${e.message}")
            DelegationResult.ParseError
        }
    }

    /**
     * Parses a single delegation: DELEGATE: [agent name] | "agent name" | bare name
     * Also supports legacy DELEGAR: format for backwards compatibility.
     * Returns null if not found or if it's a multi-agent delegation.
     */
    fun parseDelegation(response: String): String? {
        val patterns = listOf(
            // Standard: DELEGATE: [AgentName] — with optional markdown bold/backticks
            Regex("\\*{0,2}DELEGAT(?:E|AR)(?!_SE(?:Q|CUENCIAL)|_PAR(?:ALELO)?)\\*{0,2}\\s*:\\s*\\[([^\\]]+)\\]", RegexOption.IGNORE_CASE),
            // With quotes: DELEGATE: "AgentName"
            Regex("\\*{0,2}DELEGAT(?:E|AR)(?!_SE(?:Q|CUENCIAL)|_PAR(?:ALELO)?)\\*{0,2}\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE),
            // Bare name: DELEGATE: AgentName
            Regex("\\*{0,2}DELEGAT(?:E|AR)(?!_SE(?:Q|CUENCIAL)|_PAR(?:ALELO)?)\\*{0,2}\\s*:\\s*([^\\n\\[\"\\-,]+)", RegexOption.IGNORE_CASE),
            // Backtick wrapped: `DELEGATE: [AgentName]`
            Regex("`DELEGAT(?:E|AR)(?!_SE(?:Q|CUENCIAL)|_PAR(?:ALELO)?):\\s*\\[([^\\]]+)\\]`", RegexOption.IGNORE_CASE),
            Regex("`DELEGAT(?:E|AR)(?!_SE(?:Q|CUENCIAL)|_PAR(?:ALELO)?):\\s*([^`]+)`", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val result = pattern.find(response)?.groupValues?.get(1)?.trim()
            if (!result.isNullOrEmpty()) return result
        }
        return null
    }

    /**
     * Parses a sequential pipeline delegation:
     * DELEGATE_SEQ: [agent1] -> [agent2] -> [agent3]
     * Also supports legacy DELEGAR_SECUENCIAL: format.
     * Returns ordered list of agent names, or null if not found.
     */
    fun parseDelegationSequence(response: String): List<String>? {
        val withBrackets = Regex(
            "\\*{0,2}`?DELEGAT(?:E_SEQ|AR_SECUENCIAL)`?\\*{0,2}\\s*:\\s*((?:\\[[^\\]]+\\]\\s*->\\s*)*\\[[^\\]]+\\])",
            RegexOption.IGNORE_CASE
        )
        val withoutBrackets = Regex(
            "\\*{0,2}`?DELEGAT(?:E_SEQ|AR_SECUENCIAL)`?\\*{0,2}\\s*:\\s*([^\\n]+)",
            RegexOption.IGNORE_CASE
        )

        withBrackets.find(response)?.let { match ->
            val agents = match.groupValues[1]
                .split("->")
                .map { it.trim().removePrefix("[").removeSuffix("]").trim() }
                .filter { it.isNotEmpty() }
            if (agents.size >= 2) return agents
        }

        withoutBrackets.find(response)?.let { match ->
            val agents = match.groupValues[1]
                .split("->")
                .map { it.trim().removePrefix("[").removeSuffix("]").removePrefix("\"").removeSuffix("\"").trim() }
                .filter { it.isNotEmpty() }
            if (agents.size >= 2) return agents
        }

        return null
    }

    /**
     * Parses a parallel delegation:
     * DELEGATE_PAR: [agent1], [agent2], [agent3]
     * With subtasks: DELEGATE_PAR: [agent1: subtask1], [agent2: subtask2]
     * Same agent multiple times: DELEGATE_PAR: [Programmer: frontend], [Programmer: backend]
     * Also supports legacy DELEGAR_PARALELO: format.
     * Returns list of ParallelDelegationEntry, or null if not found.
     */
    fun parseDelegationParallel(response: String): List<ParallelDelegationEntry>? {
        val withBrackets = Regex(
            "\\*{0,2}`?DELEGAT(?:E_PAR|AR_PARALELO)`?\\*{0,2}\\s*:\\s*((?:\\[[^\\]]+\\]\\s*,?\\s*)+)",
            RegexOption.IGNORE_CASE
        )
        val withoutBrackets = Regex(
            "\\*{0,2}`?DELEGAT(?:E_PAR|AR_PARALELO)`?\\*{0,2}\\s*:\\s*([^\\n]+)",
            RegexOption.IGNORE_CASE
        )

        withBrackets.find(response)?.let { match ->
            val entries = match.groupValues[1]
                .split(",")
                .map { it.trim().removePrefix("[").removeSuffix("]").trim() }
                .filter { it.isNotEmpty() }
                .map { parseParallelEntry(it) }
            if (entries.size >= 2) return entries
        }

        withoutBrackets.find(response)?.let { match ->
            val entries = match.groupValues[1]
                .split(",")
                .map { it.trim().removePrefix("[").removeSuffix("]").removePrefix("\"").removeSuffix("\"").trim() }
                .filter { it.isNotEmpty() }
                .map { parseParallelEntry(it) }
            if (entries.size >= 2) return entries
        }

        return null
    }

    /**
     * Parses "AgentName: subtask" or just "AgentName" into a ParallelDelegationEntry.
     */
    private fun parseParallelEntry(raw: String): ParallelDelegationEntry {
        val colonIndex = raw.indexOf(':')
        return if (colonIndex > 0) {
            val name = raw.substring(0, colonIndex).trim()
            val subtask = raw.substring(colonIndex + 1).trim()
            ParallelDelegationEntry(name, subtask.ifEmpty { null })
        } else {
            ParallelDelegationEntry(raw.trim())
        }
    }

    /**
     * Extracts any text written by Cortex before the DELEGATE/DELEGAR line,
     * to be shown as a visible message in the chat.
     */
    fun extractPreDelegationMessage(content: String): String? {
        val delegatePattern = Regex(
            "\\*{0,2}`?DELEGAT(?:E(?:_SEQ|_PAR)?|AR(?:_SECUENCIAL|_PARALELO)?)`?\\*{0,2}\\s*:\\s*.*",
            RegexOption.IGNORE_CASE
        )
        val match = delegatePattern.find(content) ?: return null
        val before = content.substring(0, match.range.first).trim()
        return if (before.isNotEmpty()) before else null
    }

    /**
     * Removes all DELEGATE/DELEGAR lines from a response,
     * returning only the human-readable content.
     */
    fun stripDelegationLines(content: String): String {
        return content.lines()
            .filter { line ->
                val trimmed = line.trim()
                    .removePrefix("**").removePrefix("*").removePrefix("`")
                    .trimStart()
                !trimmed.startsWith("DELEGATE", ignoreCase = true) &&
                !trimmed.startsWith("DELEGAR", ignoreCase = true)
            }
            .joinToString("\n")
            .trim()
    }
}
