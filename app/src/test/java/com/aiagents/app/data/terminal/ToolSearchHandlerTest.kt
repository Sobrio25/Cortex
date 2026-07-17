package com.aiagents.app.data.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSearchHandlerTest {
    private val handler = ToolSearchHandler()

    @Test
    fun financeToolsStayDeferredToKeepTheBasePromptCompact() {
        assertFalse(
            ToolSearchHandler.CORE_TOOL_NAMES.any { it in FinanceToolHandler.ALL_TOOL_NAMES }
        )
    }

    @Test
    fun coreSchemaSetRemainsSmall() {
        assertTrue(ToolSearchHandler.CORE_TOOL_NAMES.size <= 8)
        assertTrue(ToolSearchHandler.MAX_PREACTIVATED_TOOLS in 3..8)
        assertFalse("execute_command" in ToolSearchHandler.CORE_TOOL_NAMES)
        assertFalse("web_fetch" in ToolSearchHandler.CORE_TOOL_NAMES)
    }

    @Test
    fun weatherRequestCanBePreRoutedWithoutExposingOtherCategories() {
        val available = WeatherToolHandler.ALL_TOOL_NAMES + FinanceToolHandler.ALL_TOOL_NAMES

        val result = handler.search("pronóstico del clima", available)

        assertTrue(result.found)
        assertEquals(WeatherToolHandler.ALL_TOOL_NAMES, result.toolNames)
    }

    @Test
    fun specificActionRanksItsSchemaBeforeTheRestOfTheCategory() {
        val result = handler.search("merge pull request", GitHubToolHandler.ALL_TOOL_NAMES)

        assertTrue(result.found)
        assertEquals(GitHubToolHandler.TOOL_MERGE_PULL, result.rankedToolNames.first())
    }

    @Test
    fun spanishEditRequestDiscoversTheCompleteFinanceCategory() {
        val result = handler.search(
            query = "corrige la cantidad y descripción de mi gasto",
            availableToolNames = FinanceToolHandler.ALL_TOOL_NAMES
        )

        assertTrue(result.found)
        assertTrue(FinanceToolHandler.TOOL_UPDATE_TRANSACTION in result.toolNames)
        assertTrue(result.toolNames.containsAll(FinanceToolHandler.ALL_TOOL_NAMES))
    }

    @Test
    fun financeCannotBeDiscoveredWhenTheUserDisabledIt() {
        val result = handler.search(
            query = "registrar un gasto",
            availableToolNames = emptySet()
        )

        assertFalse(result.found)
        assertTrue(result.toolNames.isEmpty())
    }
}
