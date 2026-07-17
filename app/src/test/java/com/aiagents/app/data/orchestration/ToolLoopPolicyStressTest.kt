package com.aiagents.app.data.orchestration

import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import com.aiagents.app.domain.model.ToolResult
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ToolLoopPolicyStressTest {
    @Test
    fun successfulWriteIsFoundAfterAThousandReadOnlyToolRounds() {
        val messages = buildList {
            add(Message(1, MessageRole.USER, "Actualiza el archivo reporte.html", timestamp = 1))
            repeat(1_000) { round ->
                val callId = "read-$round"
                add(
                    Message(
                        id = round * 2L + 2,
                        role = MessageRole.ASSISTANT,
                        content = "",
                        toolCalls = listOf(
                            ToolCall(callId, function = ToolFunction("read_file", "{}"))
                        )
                    )
                )
                add(
                    Message(
                        id = round * 2L + 3,
                        role = MessageRole.TOOL,
                        content = "ok",
                        toolResults = listOf(ToolResult(callId, "read_file", "ok"))
                    )
                )
            }
            val writeId = "write-final"
            add(
                Message(
                    id = 3_000,
                    role = MessageRole.ASSISTANT,
                    content = "",
                    toolCalls = listOf(
                        ToolCall(writeId, function = ToolFunction("write_file", "{}"))
                    )
                )
            )
            add(
                Message(
                    id = 3_001,
                    role = MessageRole.TOOL,
                    content = "Archivo actualizado",
                    toolResults = listOf(ToolResult(writeId, "write_file", "Archivo actualizado"))
                )
            )
        }

        assertNull(ToolCompletionPolicy.recoveryInstruction(messages, "Listo"))
    }

    @Test
    fun failedWritesAcrossALongTranscriptStillRequireRecovery() {
        val messages = buildList {
            add(Message(1, MessageRole.USER, "Corrige el documento largo.md", timestamp = 1))
            repeat(500) { round ->
                val callId = "write-$round"
                add(
                    Message(
                        id = round * 2L + 2,
                        role = MessageRole.ASSISTANT,
                        content = "",
                        toolCalls = listOf(
                            ToolCall(callId, function = ToolFunction("write_file", "{}"))
                        )
                    )
                )
                add(
                    Message(
                        id = round * 2L + 3,
                        role = MessageRole.TOOL,
                        content = "Error: sin espacio",
                        toolResults = listOf(ToolResult(callId, "write_file", "Error: sin espacio"))
                    )
                )
            }
        }

        assertNotNull(ToolCompletionPolicy.recoveryInstruction(messages, "Terminado"))
    }
}
