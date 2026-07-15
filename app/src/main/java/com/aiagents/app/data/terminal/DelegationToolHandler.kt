package com.aiagents.app.data.terminal

/**
 * Typed subagent spawning contract. `delegate_to_agent` remains accepted by the
 * dispatcher for old conversations, but only `spawn_subagents` is advertised.
 */
class DelegationToolHandler {
    companion object {
        const val TOOL_NAME = "spawn_subagents"
        const val LEGACY_TOOL_NAME = "delegate_to_agent"
        val ALL_TOOL_NAMES = setOf(TOOL_NAME, LEGACY_TOOL_NAME)

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_NAME,
                    "description" to buildString {
                        append("Spawn one or more temporary subagents with isolated conversations and explicit budgets. ")
                        append("Cortex creates a suitable temporary worker automatically; agent_name is only for an existing custom agent explicitly chosen by the user. ")
                        append("Use parallel mode only for independent tasks. Use sequential mode when each task depends on the previous result. ")
                        append("Choose read_only_shared for research/review and write_exclusive for tasks that modify files. ")
                        append("For external integrations, request the narrowest capability (for example google_docs) so the work and tool history stay isolated. ")
                        append("Children are leaf agents by default. Use role=orchestrator only when nested delegation is genuinely needed.")
                    },
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "tasks" to mapOf(
                                "type" to "array",
                                "minItems" to 1,
                                "maxItems" to 12,
                                "items" to mapOf(
                                    "type" to "object",
                                    "properties" to mapOf(
                                        "agent_name" to mapOf(
                                            "type" to "string",
                                            "description" to "Optional existing custom agent name. Omit it to create an ephemeral worker for this task."
                                        ),
                                        "goal" to mapOf(
                                            "type" to "string",
                                            "description" to "A bounded, self-contained objective"
                                        ),
                                        "context" to mapOf(
                                            "type" to "string",
                                            "description" to "Only the relevant constraints, resources, paths, and prior decisions"
                                        ),
                                        "acceptance_criteria" to mapOf(
                                            "type" to "string",
                                            "description" to "Observable conditions that define completion"
                                        ),
                                        "role" to mapOf(
                                            "type" to "string",
                                            "enum" to listOf("leaf", "orchestrator"),
                                            "description" to "leaf by default; orchestrator may spawn bounded children"
                                        ),
                                        "workspace_policy" to mapOf(
                                            "type" to "string",
                                            "enum" to listOf("read_only_shared", "write_exclusive"),
                                            "description" to "read_only_shared permits parallel reads; write_exclusive serializes workspace writes"
                                        ),
                                        "capabilities" to mapOf(
                                            "type" to "array",
                                            "description" to "Optional scoped external capabilities. Use the narrowest capability required; never request unrelated access.",
                                            "uniqueItems" to true,
                                            "items" to mapOf(
                                                "type" to "string",
                                                "enum" to listOf(
                                                    "google_docs",
                                                    "google_drive",
                                                    "google_sheets",
                                                    "google_gmail",
                                                    "google_calendar",
                                                    "google_slides",
                                                    "google_workspace"
                                                )
                                            )
                                        ),
                                        "model" to mapOf(
                                            "type" to "string",
                                            "description" to "Optional PROVIDER|model override"
                                        ),
                                        "max_iterations" to mapOf(
                                            "type" to "integer",
                                            "minimum" to 1,
                                            "maximum" to 100
                                        )
                                    ),
                                    "required" to listOf("goal")
                                )
                            ),
                            "mode" to mapOf(
                                "type" to "string",
                                "enum" to listOf("parallel", "sequential")
                            ),
                            "failure_policy" to mapOf(
                                "type" to "string",
                                "enum" to listOf("fail_fast", "continue"),
                                "description" to "Sequential batches default to fail_fast"
                            )
                        ),
                        "required" to listOf("tasks")
                    )
                )
            )
        )
    }
}
