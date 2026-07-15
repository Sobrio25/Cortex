package com.aiagents.app.data.orchestration

import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import com.aiagents.app.domain.model.ToolResult
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ToolCompletionPolicyTest {
    @Test
    fun `requires a write before completing a requested html update`() {
        val messages = listOf(
            Message(role = MessageRole.USER, content = "Actualiza el documento HTML con noticias de hoy"),
            Message(role = MessageRole.ASSISTANT, content = "Encontré noticias")
        )

        assertNotNull(ToolCompletionPolicy.recoveryInstruction(messages, "Resumen terminado"))
    }

    @Test
    fun `accepts completion after successful write result`() {
        val call = ToolCall("write-1", function = ToolFunction("write_file", "{}"))
        val messages = listOf(
            Message(role = MessageRole.USER, content = "Actualiza el documento HTML"),
            Message(role = MessageRole.ASSISTANT, content = "", toolCalls = listOf(call)),
            Message(
                role = MessageRole.TOOL,
                content = "Archivo actualizado",
                toolResults = listOf(ToolResult("write-1", "write_file", "Archivo actualizado"))
            )
        )

        assertNull(ToolCompletionPolicy.recoveryInstruction(messages, "Actualización terminada"))
    }

    @Test
    fun `rejects compatibility transcript as final output`() {
        val messages = listOf(Message(role = MessageRole.USER, content = "Revisa el archivo"))

        assertNotNull(
            ToolCompletionPolicy.recoveryInstruction(
                messages,
                "The assistant requested these application tools:\n- web_fetch({})"
            )
        )
    }
}
