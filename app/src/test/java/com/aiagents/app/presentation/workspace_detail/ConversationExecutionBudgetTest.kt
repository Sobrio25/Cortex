package com.aiagents.app.presentation.workspace_detail

import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationExecutionBudgetTest {
    @Test
    fun `autonomous execution stays deliberately bounded`() {
        assertTrue(ConversationExecutionBudget.MAX_TOOL_ROUNDS <= 10)
        assertTrue(ConversationExecutionBudget.MAX_AUTO_CONTINUES <= 2)
    }
}
