package com.aiagents.app.data.local

import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import com.google.gson.JsonParser

/**
 * Parses the small, explicit tool-call protocol used by MediaPipe local models.
 *
 * Local models do not receive the full cloud tool surface. Keeping the allow-list
 * here makes it impossible for a prompt hallucination to dispatch an arbitrary
 * application tool.
 */
object LocalToolCallParser {
    private const val MARKER = "TOOL_CALL:"
    private val allowedToolNames = setOf("web_search", "web_fetch")

    fun parse(response: String): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        var searchFrom = 0

        while (searchFrom < response.length) {
            val markerStart = response.indexOf(MARKER, searchFrom)
            if (markerStart < 0) break

            val objectStart = response.indexOf('{', markerStart + MARKER.length)
            if (objectStart < 0) break

            val objectEnd = findJsonObjectEnd(response, objectStart)
            if (objectEnd < 0) {
                // Continue looking in case the model emitted another marker later.
                searchFrom = markerStart + MARKER.length
                continue
            }

            val json = response.substring(objectStart, objectEnd + 1)
            runCatching { JsonParser.parseString(json).asJsonObject }
                .getOrNull()
                ?.let { callJson ->
                    val name = callJson.get("name")?.takeIf { it.isJsonPrimitive }?.asString
                    val arguments = callJson.get("arguments")
                        ?.takeIf { it.isJsonObject }
                        ?.asJsonObject

                    if (name in allowedToolNames && arguments != null) {
                        calls += ToolCall(
                            id = "local_${System.currentTimeMillis()}_${calls.size}",
                            function = ToolFunction(
                                name = name!!,
                                arguments = arguments.toString()
                            )
                        )
                    }
                }

            searchFrom = objectEnd + 1
        }

        return calls
    }

    /** Finds the end of a JSON object while respecting nested objects and strings. */
    private fun findJsonObjectEnd(value: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false

        for (index in start until value.length) {
            val character = value[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (character == '\\') {
                    escaped = true
                } else if (character == '"') {
                    inString = false
                }
                continue
            }

            when (character) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }

        return -1
    }
}
