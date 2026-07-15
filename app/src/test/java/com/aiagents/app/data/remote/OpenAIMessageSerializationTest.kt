package com.aiagents.app.data.remote

import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIMessageSerializationTest {
    private val gson = Gson()
    private val toolCall = ToolCall(
        id = "call-1",
        function = ToolFunction(name = "read_text_file", arguments = "{\"file_name\":\"notes.txt\"}")
    )

    @Test
    fun `non streaming omits blank content from assistant tool call turn`() {
        val serialized = ChatMessage(
            role = "assistant",
            content = "",
            toolCalls = listOf(toolCall)
        ).toRequestFormat()

        assertNull(serialized.content)
        assertEquals("call-1", serialized.toolCalls?.single()?.get("id"))
    }

    @Test
    fun `streaming omits blank content from assistant tool call turn`() {
        val serialized = ChatMessage(
            role = "assistant",
            content = "  ",
            toolCalls = listOf(toolCall)
        ).toStreamingMap()

        assertFalse(serialized.containsKey("content"))
        assertTrue(serialized.containsKey("tool_calls"))
    }

    @Test
    fun `LM Studio keeps string content on assistant tool call turn`() {
        val message = ChatMessage(
            role = "assistant",
            content = "",
            toolCalls = listOf(toolCall)
        )

        val regular = message.toRequestFormat(requireStringContentForToolCalls = true)
        val streaming = message.toStreamingMap(requireStringContentForToolCalls = true)

        assertEquals("", regular.content)
        assertTrue(streaming.containsKey("content"))
        assertEquals("", streaming["content"])
    }

    @Test
    fun `tool messages omit legacy name but preserve call id and content`() {
        val message = ChatMessage(
            role = "tool",
            content = "resultado",
            toolCallId = "call-1",
            name = "read_text_file"
        )

        val regular = message.toRequestFormat()
        val streaming = message.toStreamingMap()

        assertNull(regular.name)
        assertEquals("call-1", regular.toolCallId)
        assertEquals("resultado", regular.content)
        assertFalse(streaming.containsKey("name"))
        assertEquals("call-1", streaming["tool_call_id"])
        assertEquals("resultado", streaming["content"])
    }

    @Test
    fun `ordinary assistant content is preserved`() {
        val serialized = ChatMessage(role = "assistant", content = "Respuesta final").toStreamingMap()

        assertEquals("Respuesta final", serialized["content"])
    }

    @Test
    fun `tool calls use stable OpenAI keys in regular and streaming JSON`() {
        val message = ChatMessage(
            role = "assistant",
            content = "",
            toolCalls = listOf(toolCall.copy(type = "tool_use", thoughtSignature = "internal"))
        )

        listOf(gson.toJson(message.toRequestFormat()), gson.toJson(message.toStreamingMap()))
            .forEach { json ->
                val call = JsonParser.parseString(json).asJsonObject
                    .getAsJsonArray("tool_calls")[0].asJsonObject
                assertEquals("call-1", call.get("id").asString)
                assertEquals("function", call.get("type").asString)
                assertEquals("read_text_file", call.getAsJsonObject("function").get("name").asString)
                assertEquals(
                    "{\"file_name\":\"notes.txt\"}",
                    call.getAsJsonObject("function").get("arguments").asString
                )
                assertEquals(setOf("id", "type", "function"), call.keySet())
            }
    }
}
