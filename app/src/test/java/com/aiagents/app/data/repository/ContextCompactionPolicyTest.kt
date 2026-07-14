package com.aiagents.app.data.repository

import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextCompactionPolicyTest {
    @Test
    fun `visible history retains every original message and hides only checkpoint`() {
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
