package com.aiagents.app.presentation.workspace_detail

import com.aiagents.app.domain.model.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelSelectionPolicyTest {
    @Test
    fun `parses provider and model id without losing model separators`() {
        val fullKey = "OPENROUTER|anthropic/claude-3.5-sonnet"

        assertEquals("anthropic/claude-3.5-sonnet", ModelSelectionPolicy.modelId(fullKey))
        assertEquals(ProviderType.OPENROUTER, ModelSelectionPolicy.provider(fullKey))
        assertEquals(fullKey, ModelSelectionPolicy.modelInfo(fullKey)?.fullKey)
    }

    @Test
    fun `legacy id without provider remains usable`() {
        assertEquals("legacy-model", ModelSelectionPolicy.modelId("legacy-model"))
        assertNull(ModelSelectionPolicy.provider("legacy-model"))
        assertNull(ModelSelectionPolicy.modelInfo("legacy-model"))
    }

    @Test
    fun `unknown provider is rejected without throwing`() {
        assertNull(ModelSelectionPolicy.provider("UNKNOWN|model"))
        assertNull(ModelSelectionPolicy.modelInfo("UNKNOWN|model"))
    }

    @Test
    fun `managed automatic model uses product labels instead of internal routing labels`() {
        val managed = requireNotNull(ModelSelectionPolicy.modelInfo("MANAGED|auto"))
        val byok = requireNotNull(ModelSelectionPolicy.modelInfo("OPENAI|gpt-5"))

        assertEquals("Free", managed.displayModelName())
        assertEquals("Cortex Gateway", managed.displayProviderName())
        assertEquals("gpt-5", byok.displayModelName())
        assertEquals("OPENAI", byok.displayProviderName())
    }
}
