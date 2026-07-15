package com.aiagents.app.presentation.workspace_detail

import com.aiagents.app.ui.theme.CortexColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentGlowColorsTest {
    @Test
    fun `cortex work lights the composer when no delegated agent is reported`() {
        assertEquals(
            listOf(CortexColors.Violet),
            workingAgentGlowColors(isLoading = true, workingAgents = emptyList())
        )
    }

    @Test
    fun `idle chat has no activity glow`() {
        assertTrue(
            workingAgentGlowColors(isLoading = false, workingAgents = emptyList()).isEmpty()
        )
    }

    @Test
    fun `each distinct working agent contributes its color`() {
        val colors = workingAgentGlowColors(
            isLoading = true,
            workingAgents = listOf("Cortex", "Programmer", "Writer", "programmer")
        )

        assertEquals(
            listOf(agentColor("Cortex"), agentColor("Programmer"), agentColor("Writer")),
            colors
        )
    }
}
