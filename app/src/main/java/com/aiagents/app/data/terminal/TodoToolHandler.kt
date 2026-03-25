package com.aiagents.app.data.terminal

import android.util.Log
import com.aiagents.app.data.local.TodoDao
import com.aiagents.app.data.model.TodoEntity
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

data class TodoToolResult(
    val toolCallId: String,
    val toolName: String,
    val success: Boolean,
    val content: String
)

@Singleton
class TodoToolHandler @Inject constructor(
    private val todoDao: TodoDao
) {
    companion object {
        private const val TAG = "TodoToolHandler"
        const val TOOL_WRITE = "todo_write"
        const val TOOL_READ = "todo_read"

        val ALL_TOOL_NAMES = setOf(TOOL_WRITE, TOOL_READ)

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_WRITE,
                    "description" to """Update the task plan for the current conversation. Use this for complex multi-step tasks (3+ steps) to show the user your progress.

Rules:
- Create a plan BEFORE starting work on complex tasks
- Keep ONE task "in_progress" at a time
- Update status to "completed" as you finish each step
- Keep items concise (< 80 chars)
- Send the FULL array every time (replaces previous list)

Each item: {"content": "...", "status": "pending|in_progress|completed|cancelled", "priority": "high|medium|low"}""",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "todos" to mapOf(
                                "type" to "array",
                                "items" to mapOf(
                                    "type" to "object",
                                    "properties" to mapOf(
                                        "content" to mapOf("type" to "string"),
                                        "status" to mapOf(
                                            "type" to "string",
                                            "enum" to listOf("pending", "in_progress", "completed", "cancelled")
                                        ),
                                        "priority" to mapOf(
                                            "type" to "string",
                                            "enum" to listOf("high", "medium", "low")
                                        )
                                    ),
                                    "required" to listOf("content", "status")
                                )
                            )
                        ),
                        "required" to listOf("todos")
                    )
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_READ,
                    "description" to "Read the current task plan to check progress before updating.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to emptyMap<String, Any>()
                    )
                )
            )
        )
    }

    private val gson = Gson()

    suspend fun executeTool(
        toolCallId: String,
        toolName: String,
        arguments: String,
        conversationId: Long
    ): TodoToolResult {
        return try {
            when (toolName) {
                TOOL_WRITE -> writeTodos(toolCallId, arguments, conversationId)
                TOOL_READ -> readTodos(toolCallId, conversationId)
                else -> TodoToolResult(toolCallId, toolName, false, "Unknown tool: $toolName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in $toolName", e)
            TodoToolResult(toolCallId, toolName, false, "Error: ${e.message}")
        }
    }

    private suspend fun writeTodos(
        toolCallId: String,
        arguments: String,
        conversationId: Long
    ): TodoToolResult {
        val args = gson.fromJson(arguments, JsonObject::class.java)
        val todosArray = args.getAsJsonArray("todos")
            ?: return TodoToolResult(toolCallId, TOOL_WRITE, false, "Missing 'todos' array.")

        val now = System.currentTimeMillis()
        val entities = todosArray.mapIndexed { index, element ->
            val obj = element.asJsonObject
            TodoEntity(
                conversationId = conversationId,
                position = index,
                content = obj.get("content")?.asString ?: "",
                status = obj.get("status")?.asString ?: "pending",
                priority = obj.get("priority")?.asString ?: "medium",
                createdAt = now,
                updatedAt = now
            )
        }

        // Atomic replace: delete old, insert new
        todoDao.deleteAllForConversation(conversationId)
        todoDao.insertAll(entities)

        val completed = entities.count { it.status == "completed" }
        val total = entities.size
        Log.d(TAG, "Updated todos for conversation $conversationId: $completed/$total done")

        return TodoToolResult(toolCallId, TOOL_WRITE, true, "Plan updated: $completed/$total completed.")
    }

    private suspend fun readTodos(toolCallId: String, conversationId: Long): TodoToolResult {
        val todos = todoDao.getTodos(conversationId)
        if (todos.isEmpty()) {
            return TodoToolResult(toolCallId, TOOL_READ, true, "No active plan.")
        }

        val summary = buildString {
            todos.forEachIndexed { i, todo ->
                val icon = when (todo.status) {
                    "completed" -> "[x]"
                    "in_progress" -> "[>]"
                    "cancelled" -> "[-]"
                    else -> "[ ]"
                }
                appendLine("${i + 1}. $icon ${todo.content} (${todo.priority})")
            }
        }
        return TodoToolResult(toolCallId, TOOL_READ, true, summary.trim())
    }
}
