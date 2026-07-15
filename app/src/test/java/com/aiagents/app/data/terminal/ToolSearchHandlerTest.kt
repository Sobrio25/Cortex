package com.aiagents.app.data.terminal

import org.junit.Assert.assertFalse
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
