package com.aiagents.app.data.orchestration

import com.aiagents.app.domain.model.SubagentExecutionMode
import com.aiagents.app.domain.model.SubagentFailurePolicy
import com.aiagents.app.domain.model.SubagentRole
import com.aiagents.app.domain.model.SubagentWorkspacePolicy
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class ParsedSubagentTask(
    val agentName: String,
    val goal: String,
    val context: String,
    val acceptanceCriteria: String,
    val role: SubagentRole,
    val workspacePolicy: SubagentWorkspacePolicy,
    val capabilities: Set<String>,
    val modelKey: String?,
    val maxIterations: Int?
)

data class ParsedSubagentBatch(
    val tasks: List<ParsedSubagentTask>,
    val mode: SubagentExecutionMode,
    val failurePolicy: SubagentFailurePolicy
)

object SubagentRequestParser {
    fun parse(arguments: String): Result<ParsedSubagentBatch> = runCatching {
        val root = JsonParser.parseString(arguments).asJsonObject
        val mode = root.string("mode").toExecutionMode()
        val failurePolicy = root.string("failure_policy").toFailurePolicy()
        val taskObjects = when {
            root.has("tasks") && root.get("tasks").isJsonArray ->
                root.getAsJsonArray("tasks").map { it.asJsonObject }
            root.has("goal") || root.has("task") -> listOf(root)
            else -> error("tasks must contain at least one subagent task")
        }
        require(taskObjects.isNotEmpty()) { "tasks must not be empty" }
        require(taskObjects.size <= 12) { "a batch may contain at most 12 tasks" }
        ParsedSubagentBatch(
            tasks = taskObjects.map { task ->
                val agentName = task.string("agent_name")?.trim().orEmpty()
                val goal = (task.string("goal") ?: task.string("task"))?.trim().orEmpty()
                require(goal.isNotBlank()) { "goal is required" }
                ParsedSubagentTask(
                    agentName = agentName,
                    goal = goal,
                    context = task.string("context").orEmpty(),
                    acceptanceCriteria = task.string("acceptance_criteria").orEmpty(),
                    role = task.string("role").toRole(),
                    workspacePolicy = task.string("workspace_policy").toWorkspacePolicy(),
                    capabilities = task.stringSet("capabilities").also { capabilities ->
                        val unknown = capabilities - SubagentCapabilityPolicy.SUPPORTED_CAPABILITIES
                        require(unknown.isEmpty()) {
                            "unsupported capabilities: ${unknown.sorted().joinToString()}"
                        }
                    },
                    modelKey = task.string("model"),
                    maxIterations = task.get("max_iterations")?.takeIf { it.isJsonPrimitive }?.asInt
                        ?.coerceIn(1, 100)
                )
            },
            mode = mode,
            failurePolicy = failurePolicy
        )
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.stringSet(name: String): Set<String> =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { value -> value.takeIf { it.isJsonPrimitive }?.asString }
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()

    private fun String?.toExecutionMode(): SubagentExecutionMode = when (this?.lowercase()) {
        "sequential" -> SubagentExecutionMode.SEQUENTIAL
        "parallel", null -> SubagentExecutionMode.PARALLEL
        else -> error("mode must be parallel or sequential")
    }

    private fun String?.toFailurePolicy(): SubagentFailurePolicy = when (this?.lowercase()) {
        "continue" -> SubagentFailurePolicy.CONTINUE
        "fail_fast", null -> SubagentFailurePolicy.FAIL_FAST
        else -> error("failure_policy must be fail_fast or continue")
    }

    private fun String?.toRole(): SubagentRole = when (this?.lowercase()) {
        "orchestrator" -> SubagentRole.ORCHESTRATOR
        "leaf", null -> SubagentRole.LEAF
        else -> error("role must be leaf or orchestrator")
    }

    private fun String?.toWorkspacePolicy(): SubagentWorkspacePolicy = when (this?.lowercase()) {
        "write_exclusive" -> SubagentWorkspacePolicy.WRITE_EXCLUSIVE
        "read_only_shared", null -> SubagentWorkspacePolicy.READ_ONLY_SHARED
        else -> error("workspace_policy must be read_only_shared or write_exclusive")
    }
}
