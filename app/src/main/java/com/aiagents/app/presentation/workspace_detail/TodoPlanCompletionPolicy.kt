package com.aiagents.app.presentation.workspace_detail

import com.aiagents.app.data.model.TodoEntity
import com.aiagents.app.domain.model.Message

/** Rules that keep a completed agent plan out of the active chat surface. */
internal object TodoPlanCompletionPolicy {
    private val terminalStatuses = setOf("completed", "cancelled")
    private val userActionPattern = Regex(
        """(?i)\b(usuario debe|debe confirmar|requiere permiso|se necesita permiso|""" +
            """selector de android abierto|di[aá]logo .+ abierto)\b"""
    )
    private val failurePattern = Regex("""(?i)^\s*(error|failed|fallo|timeout)\b""")

    fun activeTodos(todos: List<TodoEntity>): List<TodoEntity> =
        todos.takeIf { plan -> plan.any { it.status !in terminalStatuses } }.orEmpty()

    /**
     * Completes only the final in-progress item after a successful non-planning action. Pending
     * steps remain visible, and actions waiting for user confirmation never complete a plan.
     */
    fun shouldAutoComplete(todos: List<TodoEntity>, messages: List<Message>): Boolean {
        if (todos.isEmpty() || todos.any { it.status == "pending" }) return false
        if (todos.none { it.status == "in_progress" }) return false

        val latestPlanUpdate = messages.indexOfLast { message ->
            message.toolResults.any { it.name == "todo_write" }
        }
        if (latestPlanUpdate < 0) return false

        return messages.drop(latestPlanUpdate + 1)
            .flatMap { it.toolResults }
            .any { result ->
                result.name !in setOf("todo_write", "todo_read") &&
                    !failurePattern.containsMatchIn(result.content) &&
                    !userActionPattern.containsMatchIn(result.content)
            }
    }
}
