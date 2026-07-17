package com.aiagents.app.presentation.workspace_detail

import com.aiagents.app.data.model.TodoEntity
import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ToolResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoPlanCompletionPolicyTest {
    @Test
    fun `successful work after a final one-step plan completes and hides it`() {
        val todos = listOf(todo(status = "in_progress"))
        val messages = listOf(
            toolMessage(ToolResult("plan", "todo_write", "Plan updated: 0/1 completed.")),
            toolMessage(ToolResult("write", "write_file", "Archivo creado exitosamente"))
        )

        assertTrue(TodoPlanCompletionPolicy.shouldAutoComplete(todos, messages))
        assertTrue(TodoPlanCompletionPolicy.activeTodos(listOf(todo(status = "completed"))).isEmpty())
    }

    @Test
    fun `pending steps and user confirmation keep the plan active`() {
        val withPending = listOf(todo(status = "completed"), todo(id = 2, status = "pending"))
        val waitingForUser = listOf(
            toolMessage(ToolResult("plan", "todo_write", "Plan updated")),
            toolMessage(ToolResult("share", "device_control", "El usuario debe confirmar el envío."))
        )

        assertFalse(TodoPlanCompletionPolicy.shouldAutoComplete(withPending, waitingForUser))
        assertFalse(TodoPlanCompletionPolicy.shouldAutoComplete(listOf(todo()), waitingForUser))
    }

    private fun todo(id: Long = 1, status: String = "in_progress") = TodoEntity(
        id = id,
        conversationId = 1,
        position = id.toInt(),
        content = "Preparar entrega",
        status = status
    )

    private fun toolMessage(result: ToolResult) = Message(
        role = MessageRole.TOOL,
        content = result.content,
        toolResults = listOf(result)
    )
}
