package com.aiagents.app.data.orchestration

import android.util.Log
import com.aiagents.app.data.local.MemoryDao
import com.aiagents.app.data.repository.AgentRepository
import com.aiagents.app.domain.model.Agent
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
    private val repository: AgentRepository,
    private val memoryDao: MemoryDao
) {
    companion object {
        private const val TAG = "AgentOrchestrator"
    }
    suspend fun buildPrompt(cortex: Agent): String {
        val basePrompt = cortex.systemPrompt
        val agents = repository.getAllAgentsOnce()
            .filter { it.id != cortex.id && it.whenToUse.isNotEmpty() }

        val personalitySection = buildPersonalitySection(cortex)

        val agentsListSection = if (agents.isEmpty()) {
            "## Agents\nNo specialized agents configured. Answer all requests yourself."
        } else {
            val agentDescriptions = agents.joinToString("\n") { agent ->
                "- ${agent.name} (${agent.role}): ${agent.whenToUse}"
            }
            """
## Available Agents
$agentDescriptions

## PROJECT PLANNING
When the user requests a complex task (building a website, app, system, multi-step project, etc.):
1. Do NOT delegate immediately
2. First, create and present a project plan that includes:
   - **Objective**: What will be built
   - **Components/Features**: List of key parts
   - **Tech Stack**: Technologies to use (if applicable)
   - **Steps**: Numbered action plan
3. Present the plan to the user and ask for confirmation
4. Do NOT mention which agents will handle the task — the user doesn't need to know internal routing details
4. Only AFTER the user approves, delegate with the plan as context

For simple/quick tasks (translate text, answer a question, single file edit, etc.), delegate directly without planning.

## DELEGATION — HOW TO DELEGATE
Use the `delegate_to_agent` tool to delegate tasks. Each agent runs in isolation with full tool access.

**CRITICAL — YOU MUST PARALLELIZE:**
For ANY task with 2+ components, you MUST call `delegate_to_agent` MULTIPLE TIMES in ONE response.
All calls execute SIMULTANEOUSLY. This is 3-5x faster than sending everything to one agent.
You CAN and SHOULD call the SAME agent type multiple times (e.g. Programmer 3 times with 3 different subtasks).

WRONG (slow, single agent does everything):
→ 1x delegate_to_agent(Programmer, "build entire landing page with hero, gallery, and contact form")

CORRECT (fast, 3 agents work in parallel):
→ 1x delegate_to_agent(Programmer, "Build hero section in src/components/Hero.tsx: centered heading 'Welcome', subtitle, CTA button. Use React + Tailwind. Export default component.")
→ 1x delegate_to_agent(Programmer, "Build projects gallery in src/components/Gallery.tsx: grid of cards with image, title, description. 3 columns desktop, 1 mobile. Use React + Tailwind. Export default.")
→ 1x delegate_to_agent(Programmer, "Build contact form in src/components/Contact.tsx: fields name, email, message. Validation required. Submit button. Use React + Tailwind. Export default.")

WRONG (slow, single researcher does everything):
→ 1x delegate_to_agent(Researcher, "Research the latest AI frameworks, compare pricing, and find benchmarks")

CORRECT (fast, 3 researchers work in parallel):
→ 1x delegate_to_agent(Researcher, "Research the latest AI frameworks in 2025: list top 5 with key features, pros/cons")
→ 1x delegate_to_agent(Researcher, "Research and compare pricing of major AI APIs: OpenAI, Anthropic, Google, etc.")
→ 1x delegate_to_agent(Researcher, "Find the latest AI model benchmarks: MMLU, HumanEval, coding, reasoning scores")

**REMEMBER: You MUST make multiple delegate_to_agent calls in ONE response. The system executes them ALL simultaneously. NEVER send just one call when the task can be split.**

**When to parallelize (ALMOST ALWAYS):**
- Building a website/app → one Programmer per page/component/section
- API development → one Programmer per endpoint or layer
- Refactoring → one Programmer per module or file
- Research → one Researcher per topic or source
- Bug fixing multiple files → one Programmer per file
- ANY task with 2+ independent parts

**When single agent (VERY RARE):**
- Truly atomic: "fix typo in line 42 of main.py"
- Single-file changes with no way to split

**Task description rules:**
- SELF-CONTAINED: include ALL context, file paths, tech stack, constraints
- SPECIFIC: full requirements, not vague instructions
- Include interfaces with other components so parts integrate correctly

Rules:
- Use EXACT agent names from Available Agents — never translate or modify
- Greetings/simple questions: respond directly, no delegation

## CRITICAL: YOUR ROLE AS ORCHESTRATOR
You are a COORDINATOR, not a worker. Your job is to PLAN and DELEGATE, not to execute tasks yourself.

**NEVER do these things directly:**
- Do NOT call write_file, execute_command, read_text_file, or any file/terminal tools for complex tasks
- Do NOT write code yourself — that is what specialized agents are for
- Do NOT execute multiple tool calls in sequence to build something — use delegate_to_agent instead

**ALWAYS delegate (via delegate_to_agent) when:**
- The task involves writing code or creating files
- The task requires multiple steps (mkdir, write files, run commands)
- The task involves building, modifying, or debugging a project
- The task involves research requiring multiple searches

**You MAY respond directly (without delegation) ONLY when:**
- Answering a simple question or greeting
- Presenting a project plan for user approval
- Synthesizing results from delegated agents
- Saving a memory (memory_save tool)
- Updating the task plan (todo_write tool)

If you find yourself about to call write_file or execute_command, STOP — you should be using delegate_to_agent instead.
            """.trimIndent()
        }

        val currentDate = LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(java.util.Locale("es", "MX")))

        // Build capabilities summary (dynamic, based on configured services)
        val capabilitiesSection = repository.buildCapabilitiesSummary(cortex.enableTerminal)

        return buildString {
            val promptWithDate = basePrompt.replace("{CURRENT_DATE}", currentDate)
            if (promptWithDate.contains("{agents_list}")) {
                appendLine(promptWithDate.replace("{agents_list}", agentsListSection))
            } else {
                appendLine(promptWithDate)
                appendLine()
                appendLine(agentsListSection)
            }
            appendLine()
            appendLine(capabilitiesSection)
            appendLine()
            appendLine(personalitySection)
            appendLine()
            appendLine("## MEMORY")
            val importantMemories = try {
                memoryDao.getAll(100)
                    .filter { it.importance >= 7 && it.confidence >= 0.5f }
                    .sortedByDescending { it.importance }
            } catch (_: Exception) { emptyList() }
            if (importantMemories.isNotEmpty()) {
                appendLine("Known: ${importantMemories.joinToString(", ") { it.content }}")
            }
            appendLine("Use memory_save with compact 'key: value'. Keys in English, values in user's language.")
            appendLine("Importance: 9-10 identity, 7-8 preferences, 5-6 context, 3-4 casual.")
        }
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
            .filter { it.role != "Agent Orchestrator" && it.whenToUse.isNotEmpty() }

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
