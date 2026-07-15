package com.aiagents.app.data.remote

import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolHistoryFallbackTest {
    @Test
    fun `flattens native tool exchange without losing call or result`() {
        val messages = listOf(
            ChatMessage(role = "user", content = "Revisa el proyecto"),
            ChatMessage(
                role = "assistant",
                content = "",
                toolCalls = listOf(
                    ToolCall(
                        id = "provider-specific-id",
                        function = ToolFunction("list_files", "{\"path\":\".\"}")
                    )
                )
            ),
            ChatMessage(
                role = "tool",
                content = "index.html",
                toolCallId = "provider-specific-id",
                name = "list_files"
            )
        )

        val flattened = flattenToolHistoryForCompatibility(messages)

        assertEquals(1, flattened.size)
        assertEquals("user", flattened[0].role)
        assertTrue(flattened[0].content.contains("Revisa el proyecto"))
        assertTrue(flattened[0].content.contains("APPLICATION TOOL RESULT from list_files"))
        assertTrue(flattened[0].content.contains("index.html"))
        assertNull(flattened[0].toolCallId)
        assertNull(flattened[0].name)
    }

    @Test
    fun `detects only http 400 failures`() {
        assertTrue(Exception("HTTP 400: invalid request").isHttp400())
        assertEquals(false, Exception("HTTP 429").isHttp400())
    }
}
