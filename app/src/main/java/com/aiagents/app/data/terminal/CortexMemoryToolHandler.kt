package com.aiagents.app.data.terminal

import com.aiagents.app.data.memory.CortexMarkdownMemoryStore
import com.aiagents.app.data.memory.CortexMemoryAction
import com.aiagents.app.data.memory.CortexMemoryMutationResult
import com.aiagents.app.data.memory.CortexMemoryOperation
import com.aiagents.app.data.memory.CortexMemoryPolicy
import com.aiagents.app.data.memory.CortexMemorySnapshot
import com.aiagents.app.data.memory.CortexProfileStore
import com.aiagents.app.domain.model.Agent
import com.aiagents.app.domain.model.isOrchestrator
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Hermes-compatible mutation tool for Cortex's bounded MEMORY.md. */
@Singleton
class CortexMemoryToolHandler @Inject constructor(
    private val store: CortexMarkdownMemoryStore,
    private val profileStore: CortexProfileStore
) {
    private val failureLimiter = CortexMemoryFailureLimiter()

    fun executeTool(
        toolCallId: String,
        arguments: String,
        agent: Agent,
        turnKey: String
    ): CortexMemoryToolResult {
        if (!agent.isOrchestrator) {
            return CortexMemoryToolResult(
                toolCallId,
                success = false,
                content = CortexMemoryToolResponseFormatter.error(
                    "Only the orchestrator can modify MEMORY.md or USER.md.",
                    store.snapshot()
                )
            )
        }
        if (arguments.length > MAX_ARGUMENT_UTF16_UNITS) {
            return CortexMemoryToolResult(
                toolCallId,
                success = false,
                content = CortexMemoryToolResponseFormatter.error(
                    "Memory request is too large.",
                    store.snapshot()
                )
            )
        }

        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            val target = args.stringOrNull("target").orEmpty()
            val targetSnapshot = when (target) {
                TARGET_MEMORY -> store.snapshot()
                TARGET_USER -> profileStore.userSnapshot()
                else -> null
            }
            if (targetSnapshot == null) {
                return CortexMemoryToolResult(
                    toolCallId,
                    success = false,
                    content = CortexMemoryToolResponseFormatter.error(
                        "target must be 'memory' or 'user'.",
                        store.snapshot(),
                        target = TARGET_MEMORY
                    )
                )
            }
            val operations = parseOperations(args)
            val expectedRevision = args.stringOrNull("expected_revision")
            val result = when (target) {
                TARGET_USER -> profileStore.applyUserOperations(operations, expectedRevision)
                else -> store.applyOperations(operations, expectedRevision)
            }
            val terminalFailure = if (result.success) {
                failureLimiter.reset(turnKey)
                false
            } else {
                failureLimiter.isTerminalAfterFailure(turnKey)
            }
            CortexMemoryToolResult(
                toolCallId = toolCallId,
                success = result.success,
                content = if (terminalFailure) {
                    CortexMemoryToolResponseFormatter.terminalFailure(result, target)
                } else {
                    CortexMemoryToolResponseFormatter.result(result, target)
                }
            )
        } catch (error: Exception) {
            CortexMemoryToolResult(
                toolCallId,
                success = false,
                content = CortexMemoryToolResponseFormatter.error(
                    error.message?.take(240) ?: "Invalid memory request.",
                    store.snapshot()
                )
            )
        }
    }

    private fun parseOperations(args: JsonObject): List<CortexMemoryOperation> {
        val batch = args.getAsJsonArray("operations")
        if (batch != null) {
            require(batch.size() in 1..MAX_BATCH_OPERATIONS) {
                "operations must contain between 1 and $MAX_BATCH_OPERATIONS items."
            }
            return batch.mapIndexed { index, element ->
                require(element.isJsonObject) { "Operation ${index + 1} must be an object." }
                parseOperation(element.asJsonObject, index)
            }
        }
        return listOf(parseOperation(args, 0))
    }

    private fun parseOperation(json: JsonObject, index: Int): CortexMemoryOperation {
        val action = CortexMemoryAction.fromWireValue(json.stringOrNull("action"))
            ?: throw IllegalArgumentException(
                "Operation ${index + 1}: action must be add, replace, or remove."
            )
        return CortexMemoryOperation(
            action = action,
            content = json.stringOrNull("content"),
            oldText = json.stringOrNull("old_text")
        )
    }

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = get(name) ?: return null
        if (value.isJsonNull || !value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
        return value.asString
    }

    companion object {
        const val TOOL_NAME = "memory"
        internal const val TARGET_MEMORY = "memory"
        internal const val TARGET_USER = "user"
        private const val MAX_BATCH_OPERATIONS = 32
        private const val MAX_ARGUMENT_UTF16_UNITS = 24_000

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_NAME,
                    "description" to "Manage Cortex's bounded context files, matching Hermes Agent: MEMORY.md (${CortexMemoryPolicy.HERMES_MEMORY_MAX_CHARS} chars) for durable facts and USER.md (${CortexProfileStore.HERMES_USER_MAX_CHARS} chars) for the user profile. Skip temporary progress, obvious facts, raw dumps, secrets, runtime facts, and reusable procedures that belong in skills. Entries are separated by §. Prefer compact wording and consolidate above 80%. There is no read action because frozen snapshots are already in system context. Use operations for an atomic replace/remove/add batch.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "target" to mapOf(
                                "type" to "string",
                                "enum" to listOf(TARGET_MEMORY, TARGET_USER),
                                "description" to "'memory' for MEMORY.md or 'user' for USER.md."
                            ),
                            "action" to mapOf(
                                "type" to "string",
                                "enum" to listOf("add", "replace", "remove"),
                                "description" to "Single-operation form."
                            ),
                            "content" to mapOf(
                                "type" to "string",
                                "description" to "Compact entry for add/replace. Never include credentials or instructions to override the agent."
                            ),
                            "old_text" to mapOf(
                                "type" to "string",
                                "description" to "Short, case-sensitive substring that uniquely identifies one entry for replace/remove."
                            ),
                            "expected_revision" to mapOf(
                                "type" to "string",
                                "description" to "Optional revision returned by the previous memory response."
                            ),
                            "operations" to mapOf(
                                "type" to "array",
                                "maxItems" to MAX_BATCH_OPERATIONS,
                                "description" to "Preferred atomic batch. Final state, including § separators, must fit the limit.",
                                "items" to mapOf(
                                    "type" to "object",
                                    "properties" to mapOf(
                                        "action" to mapOf(
                                            "type" to "string",
                                            "enum" to listOf("add", "replace", "remove")
                                        ),
                                        "content" to mapOf("type" to "string"),
                                        "old_text" to mapOf("type" to "string")
                                    ),
                                    "required" to listOf("action")
                                )
                            )
                        ),
                        "required" to listOf("target")
                    )
                )
            )
        )
    }
}

/** Pure response formatting keeps successful tool history bounded and independently testable. */
internal object CortexMemoryToolResponseFormatter {
    fun result(
        result: CortexMemoryMutationResult,
        target: String = CortexMemoryToolHandler.TARGET_MEMORY
    ): String {
        val json = baseSnapshotJson(
            snapshot = result.snapshot,
            includeEntries = !result.success,
            target = target
        )
        json.addProperty("success", result.success)
        json.addProperty("changed", result.changed)
        if (result.success) {
            json.addProperty("done", true)
            json.addProperty("message", result.message)
            json.addProperty(
                "next_action",
                "Memory operation is complete. Do not call memory again unless another distinct durable fact still needs curation."
            )
        } else {
            json.addProperty("done", false)
            json.addProperty("error", result.message)
        }
        return json.toString()
    }

    fun error(
        message: String,
        snapshot: CortexMemorySnapshot,
        includeEntries: Boolean = false,
        target: String = CortexMemoryToolHandler.TARGET_MEMORY
    ): String = baseSnapshotJson(snapshot, includeEntries, target).apply {
        addProperty("success", false)
        addProperty("changed", false)
        addProperty("done", false)
        addProperty("error", message)
    }.toString()

    fun terminalFailure(
        result: CortexMemoryMutationResult,
        target: String = CortexMemoryToolHandler.TARGET_MEMORY
    ): String =
        baseSnapshotJson(result.snapshot, includeEntries = false, target = target).apply {
            addProperty("success", false)
            addProperty("changed", false)
            addProperty("done", true)
            addProperty(
                "error",
                "Memory was left unchanged after three recoverable failures. Stop retrying this turn and continue the response to the user."
            )
            addProperty(
                "next_action",
                "Do not call memory again in this turn. A later user turn can retry with a shorter or more specific atomic batch."
            )
        }.toString()

    private fun baseSnapshotJson(
        snapshot: CortexMemorySnapshot,
        includeEntries: Boolean,
        target: String
    ): JsonObject = JsonObject().apply {
        addProperty("target", target)
        addProperty("usage", "${snapshot.usedChars}/${snapshot.maxChars}")
        addProperty("usage_percent", snapshot.usagePercent)
        addProperty("remaining_chars", snapshot.remainingChars)
        addProperty("entry_count", snapshot.entries.size)
        addProperty("revision", snapshot.revision)
        addProperty("near_capacity", snapshot.isNearCapacity)
        val entries = JsonArray()
        if (snapshot.storageError == null && includeEntries) {
            snapshot.entries.forEach(entries::add)
        } else if (snapshot.storageError != null) {
            addProperty("context_file_blocked", true)
            addProperty("storage_error", snapshot.storageError)
        }
        if (includeEntries) add("current_entries", entries)
    }
}

/** Bounds recoverable memory retries per user turn, matching Hermes' anti-loop behavior. */
internal class CortexMemoryFailureLimiter(
    private val allowedRecoverableFailures: Int = 3,
    private val maxTrackedTurns: Int = 256
) {
    private val failuresByTurn = ConcurrentHashMap<String, Int>()

    fun isTerminalAfterFailure(turnKey: String): Boolean {
        if (failuresByTurn.size >= maxTrackedTurns && !failuresByTurn.containsKey(turnKey)) {
            failuresByTurn.clear()
        }
        val failures = failuresByTurn.merge(turnKey, 1, Int::plus) ?: 1
        return failures > allowedRecoverableFailures
    }

    fun reset(turnKey: String) {
        failuresByTurn.remove(turnKey)
    }
}

data class CortexMemoryToolResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)
