package com.aiagents.app.data.repository

import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextCompactionStressTest {
    @Test
    fun latestCheckpointKeepsOnlyTheLargeVerbatimTailForTheModel() {
        val messages = buildList {
            repeat(10_000) { index ->
                if (index % 1_000 == 0) {
                    add(
                        Message(
                            id = index.toLong(),
                            role = MessageRole.SYSTEM,
                            content = ContextCompactionPolicy.checkpointContent("checkpoint-$index"),
                            timestamp = index.toLong()
                        )
                    )
                } else {
                    add(
                        Message(
                            id = index.toLong(),
                            role = if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
                            content = "turn-$index-${"x".repeat(256)}",
                            timestamp = index.toLong()
                        )
                    )
                }
            }
        }

        val compacted = ContextCompactionPolicy.modelHistory(messages)

        assertEquals(1_000, compacted.size)
        assertEquals(MessageRole.USER, compacted.first().role)
        assertTrue(compacted.first().content.contains("checkpoint-9000"))
        assertEquals("turn-9999-${"x".repeat(256)}", compacted.last().content)
        assertFalse(compacted.any { it.content.contains("checkpoint-8000") })
    }

    @Test
    fun visibleHistoryFiltersThousandsOfProtocolRowsWithoutReorderingUserTurns() {
        val messages = buildList {
            repeat(2_000) { round ->
                val base = round * 4L
                add(Message(base, MessageRole.USER, "request-$round", timestamp = base))
                add(
                    Message(
                        id = base + 1,
                        role = MessageRole.ASSISTANT,
                        content = "calling",
                        timestamp = base + 1,
                        toolCalls = listOf(
                            ToolCall("call-$round", function = ToolFunction("read_file", "{}"))
                        )
                    )
                )
                add(Message(base + 2, MessageRole.TOOL, "tool-$round", timestamp = base + 2))
                add(Message(base + 3, MessageRole.ASSISTANT, "answer-$round", timestamp = base + 3))
            }
        }

        val visible = ContextCompactionPolicy.visibleHistory(messages)

        assertEquals(4_000, visible.size)
        assertEquals("request-0", visible.first().content)
        assertEquals("answer-1999", visible.last().content)
        assertTrue(visible.none { it.role == MessageRole.TOOL || it.toolCalls.isNotEmpty() })
    }
}
