package com.aiagents.app.data.model

import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import com.aiagents.app.domain.model.ToolResult
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageEntityTest {
    @Test
    fun `persistence writes stable tool history keys`() {
        val entity = MessageEntity.fromDomain(
            message = Message(
                role = MessageRole.ASSISTANT,
                content = "",
                toolCalls = listOf(
                    ToolCall(
                        id = "call-1",
                        function = ToolFunction("weather", "{\"city\":\"Monterrey\"}")
                    )
                ),
                toolResults = listOf(ToolResult("call-1", "weather", "soleado"))
            ),
            workspaceId = 1
        )

        val call = JsonParser.parseString(entity.toolCallsJson).asJsonArray[0].asJsonObject
        assertTrue(call.has("id"))
        assertTrue(call.has("function"))
        assertFalse(call.has("a"))
        assertEquals("call-1", entity.toDomain().toolCalls.single().id)
        assertEquals("soleado", entity.toDomain().toolResults.single().content)
    }

    @Test
    fun `persistence reads tool history written by previous obfuscated release`() {
        val entity = MessageEntity(
            workspaceId = 1,
            role = MessageRole.ASSISTANT.name,
            content = "",
            toolCallsJson = """[{"a":"call-old","b":"function","c":{"a":"weather","b":"{}"},"d":"signature"}]""",
            toolResultsJson = """[{"a":"call-old","b":"weather","c":"nublado"}]"""
        )

        val restored = entity.toDomain()

        assertEquals("call-old", restored.toolCalls.single().id)
        assertEquals("function", restored.toolCalls.single().type)
        assertEquals("weather", restored.toolCalls.single().function.name)
        assertEquals("{}", restored.toolCalls.single().function.arguments)
        assertEquals("signature", restored.toolCalls.single().thoughtSignature)
        assertEquals("call-old", restored.toolResults.single().toolCallId)
        assertEquals("weather", restored.toolResults.single().name)
        assertEquals("nublado", restored.toolResults.single().content)
    }
}
