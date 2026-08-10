package com.aiagents.app.data.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeBaseToolHandlerTest {

    @Test
    fun `tool definitions are consistent with ALL_TOOL_NAMES`() {
        val definitions = KnowledgeBaseToolHandler.getToolDefinitionsJson()
        val definedNames = definitions.mapNotNull { definition ->
            @Suppress("UNCHECKED_CAST")
            ((definition["function"] as? Map<String, Any>)?.get("name") as? String)
        }.toSet()

        assertEquals(KnowledgeBaseToolHandler.ALL_TOOL_NAMES, definedNames)
        assertTrue(definedNames.contains("search_knowledge_base"))
        assertTrue(definedNames.contains("knowledge_base_status"))
    }

    @Test
    fun `search definition requires a query parameter`() {
        val definitions = KnowledgeBaseToolHandler.getToolDefinitionsJson()
        val search = definitions.first {
            @Suppress("UNCHECKED_CAST")
            ((it["function"] as? Map<String, Any>)?.get("name") as? String) == "search_knowledge_base"
        }
        @Suppress("UNCHECKED_CAST")
        val function = search["function"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val parameters = function["parameters"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val required = (parameters["required"] as? List<String>) ?: emptyList()
        assertTrue("query must be required", required.contains("query"))
    }

    @Test
    fun `search results format is a JSON array with citations`() {
        val json = KnowledgeBaseToolHandler.formatSearchResults(
            listOf(
                Triple("Manual de producto", 0, 0.87f),
                Triple("Manual de producto", 3, 0.62f)
            )
        )
        assertTrue(json.startsWith("["))
        assertTrue(json.contains("\"document\":\"Manual de producto\""))
        assertTrue(json.contains("\"chunk\":3"))
        assertTrue(json.contains("\"score\":0.87"))
    }
}
