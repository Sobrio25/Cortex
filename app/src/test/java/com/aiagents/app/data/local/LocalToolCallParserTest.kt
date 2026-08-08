package com.aiagents.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalToolCallParserTest {
    @Test
    fun parsesNestedArgumentsAndBracesInsideStrings() {
        val response = """
            Necesito consultar la web.
            TOOL_CALL: {"name":"web_search","arguments":{"query":"Kotlin {latest} release","count":3}}
        """.trimIndent()

        val calls = LocalToolCallParser.parse(response)

        assertEquals(1, calls.size)
        assertEquals("web_search", calls.single().function.name)
        assertTrue(calls.single().function.arguments.contains("Kotlin {latest}"))
    }

    @Test
    fun parsesMultipleCallsAndRejectsNonWebTools() {
        val response = """
            TOOL_CALL: {"name":"web_search","arguments":{"query":"first"}}
            TOOL_CALL: {"name":"shell","arguments":{"command":"whoami"}}
            ```
            TOOL_CALL: {"name":"web_fetch","arguments":{"url":"https://example.com"}}
            ```
        """.trimIndent()

        val calls = LocalToolCallParser.parse(response)

        assertEquals(listOf("web_search", "web_fetch"), calls.map { it.function.name })
    }

    @Test
    fun ignoresMalformedCallsAndCallsWithoutObjectArguments() {
        val response = """
            TOOL_CALL: {"name":"web_search","arguments":"not-an-object"}
            TOOL_CALL: {"name":"web_fetch","arguments":{"url":"https://example.com"}
            TOOL_CALL: {"name":"web_search","arguments":{"query":"valid"}}
        """.trimIndent()

        val calls = LocalToolCallParser.parse(response)

        assertEquals(1, calls.size)
        assertEquals("valid", calls.single().function.arguments.substringAfter("\"query\":\"").substringBefore("\""))
    }
}
