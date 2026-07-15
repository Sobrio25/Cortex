package com.aiagents.app.data.repository

import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextCompactionPolicyTest {
    @Test
    fun `visible history retains completed messages and hides checkpoint`() {
        val original = (1L..6L).map { id ->
            Message(
                id = id,
                role = if (id % 2L == 0L) MessageRole.ASSISTANT else MessageRole.USER,
                content = "message-$id",
                timestamp = id
            )
        }
        val checkpoint = Message(
            id = 99,
            role = MessageRole.SYSTEM,
            content = ContextCompactionPolicy.checkpointContent("summary"),
            timestamp = 4
        )
        val persisted = original.take(4) + checkpoint + original.drop(4)

        assertEquals(original.map { it.id }, ContextCompactionPolicy.visibleHistory(persisted).map { it.id })
    }

    @Test
    fun `visible history hides tool protocol turns but keeps final response`() {
        val user = Message(1, MessageRole.USER, "List my skills", timestamp = 1)
        val preamble = Message(
            id = 2,
            role = MessageRole.ASSISTANT,
            content = "I'll list them now.",
            timestamp = 2,
            toolCalls = listOf(
                ToolCall("call-1", function = ToolFunction("skill_list", "{}"))
            )
        )
        val tool = Message(3, MessageRole.TOOL, "skill-a", timestamp = 3)
        val final = Message(4, MessageRole.ASSISTANT, "You have skill-a.", timestamp = 4)

        val visible = ContextCompactionPolicy.visibleHistory(listOf(user, preamble, tool, final))

        assertEquals(listOf(1L, 4L), visible.map { it.id })
    }

    @Test
    fun `command view includes assistant calls and tool responses`() {
        val preamble = Message(
            id = 1,
            role = MessageRole.ASSISTANT,
            content = "Checking.",
            toolCalls = listOf(
                ToolCall("call-1", function = ToolFunction("skill_list", "{}"))
            )
        )
        val tool = Message(2, MessageRole.TOOL, "skill-a", timestamp = 2)

        assertEquals(
            listOf(preamble, tool),
            ContextCompactionPolicy.visibleHistory(
                listOf(preamble, tool),
                includeInternalActions = true
            )
        )
    }

    @Test
    fun `model history starts at latest checkpoint and keeps recent tail verbatim`() {
        val old = Message(1, MessageRole.USER, "old", timestamp = 1)
        val firstCheckpoint = Message(
            2,
            MessageRole.SYSTEM,
            ContextCompactionPolicy.checkpointContent("first"),
            timestamp = 2
        )
        val between = Message(3, MessageRole.ASSISTANT, "between", timestamp = 3)
        val latestCheckpoint = Message(
            4,
            MessageRole.SYSTEM,
            ContextCompactionPolicy.checkpointContent("latest"),
            timestamp = 4
        )
        val recentUser = Message(5, MessageRole.USER, "recent", timestamp = 5)
        val result = ContextCompactionPolicy.modelHistory(
            listOf(old, firstCheckpoint, between, latestCheckpoint, recentUser)
        )

        assertEquals(listOf(4L, 5L), result.map { it.id })
        assertEquals(MessageRole.USER, result.first().role)
        assertTrue(result.first().content.contains("latest"))
        assertFalse(result.any { it.content.contains("first") })
    }

    @Test
    fun `legacy destructive summary remains visible and still acts as checkpoint`() {
        val legacy = Message(
            1,
            MessageRole.SYSTEM,
            "[Context Compacted] Previous conversation summary:\nold summary",
            timestamp = 1
        )
        val recent = Message(2, MessageRole.USER, "continue", timestamp = 2)

        assertEquals(listOf(legacy, recent), ContextCompactionPolicy.visibleHistory(listOf(legacy, recent)))
        val modelHistory = ContextCompactionPolicy.modelHistory(listOf(legacy, recent))
        assertEquals(listOf(1L, 2L), modelHistory.map { it.id })
        assertEquals(MessageRole.USER, modelHistory.first().role)
    }
}
