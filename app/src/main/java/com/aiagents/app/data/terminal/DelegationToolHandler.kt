package com.aiagents.app.data.terminal

/**
 * Defines the `delegate_to_agent` tool for structured agent delegation.
 *
 * Instead of relying on fragile text-pattern matching (DELEGATE: [AgentName]),
 * Cortex uses this tool call to delegate tasks. LLMs produce reliable structured
 * tool calls, making delegation robust across all providers.
 *
 * Parallel execution: when Cortex makes multiple `delegate_to_agent` calls in a
 * single response, all delegations execute simultaneously.
 */
class DelegationToolHandler {
    companion object {
        const val TOOL_NAME = "delegate_to_agent"
        val ALL_TOOL_NAMES = setOf(TOOL_NAME)

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_NAME,
                    "description" to buildString {
                        append("Delegate a task to a specialized agent who will execute it with full tool access (terminal, files, search, etc.). ")
                        append("The agent works in an isolated context and returns the result. ")
                        append("PARALLEL EXECUTION: call this tool MULTIPLE TIMES in a single response to run agents simultaneously. ")
                        append("Each call is independent — include ALL needed context in the task description.")
                    },
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "agent_name" to mapOf(
                                "type" to "string",
                                "description" to "Exact name of the agent from the Available Agents list"
                            ),
                            "task" to mapOf(
                                "type" to "string",
                                "description" to "Complete, self-contained task description. Include ALL context: requirements, file paths, tech stack, interfaces with other components, constraints. The agent sees ONLY this."
                            )
                        ),
                        "required" to listOf("agent_name", "task")
                    )
                )
            )
        )
    }
}
