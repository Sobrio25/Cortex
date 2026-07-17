package com.aiagents.app.presentation.workspace_detail

import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import com.aiagents.app.domain.model.ToolResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactionTranscriptBuilderTest {
    @Test
    fun `includes file and tool context needed for handoff`() {
        val transcript = CompactionTranscriptBuilder.build(
            messages = listOf(
                Message(
                    role = MessageRole.USER,
                    content = "Analiza el archivo",
                    attachedFiles = listOf("informe.pdf"),
                    toolCalls = listOf(
                        ToolCall("call-1", function = ToolFunction("read_file", "{}"))
                    )
                )
            ),
            tokenBudget = 4_096
        )

        assertTrue(transcript.contains("[USER]: Analiza el archivo"))
        assertTrue(transcript.contains("Attached files: informe.pdf"))
        assertTrue(transcript.contains("Tool call read_file: {}"))
    }

    @Test
    fun `uses persisted tool content without duplicating metadata`() {
        val transcript = CompactionTranscriptBuilder.build(
            messages = listOf(
                Message(
                    role = MessageRole.TOOL,
                    content = "resultado visible",
                    toolResults = listOf(ToolResult("call-1", "search", "resultado metadata"))
                )
            ),
            tokenBudget = 4_096
        )

        assertTrue(transcript.contains("resultado visible"))
        assertFalse(transcript.contains("resultado metadata"))
    }

    @Test
    fun `honors bounded transcript size`() {
        val transcript = CompactionTranscriptBuilder.build(
            messages = listOf(Message(role = MessageRole.USER, content = "x".repeat(50_000))),
            tokenBudget = 1
        )

        assertTrue(transcript.length <= 12_000)
    }
}
