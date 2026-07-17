package com.aiagents.app.data.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolOutputBudgetTest {
    @Test
    fun `short output is unchanged`() {
        assertEquals("done", ToolOutputBudget.compactForProvider("done", maxChars = 256))
    }

    @Test
    fun `large output keeps head and tail within budget`() {
        val content = "HEAD" + "x".repeat(1_000) + "TAIL"

        val compact = ToolOutputBudget.compactForProvider(content, maxChars = 300)

        assertTrue(compact.length <= 300)
        assertTrue(compact.startsWith("HEAD"))
        assertTrue(compact.endsWith("TAIL"))
        assertTrue(compact.contains("tool output truncated"))
    }

    @Test
    fun `image data remains intact for provider vision blocks`() {
        val dataUri = "data:image/png;base64," + "a".repeat(1_000)

        assertEquals(dataUri, ToolOutputBudget.compactForProvider(dataUri, maxChars = 256))
    }
}
