package com.aiagents.app.presentation.providers

import com.aiagents.app.domain.model.MoonshotEndpointType
import com.aiagents.app.domain.model.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProviderModelCatalogTest {

    @Test
    fun `normalization removes blanks and case insensitive duplicates`() {
        assertEquals(
            listOf("claude-3", "Gemini-Pro", "gpt-4o"),
            normalizeProviderModels(
                listOf(" gpt-4o ", "", "Gemini-Pro", "gemini-pro", "claude-3")
            )
        )
    }

    @Test
    fun `search matches every term without changing catalog order`() {
        val models = listOf("alpha-code-large", "alpha-chat", "beta-code-large")

        assertEquals(
            listOf("alpha-code-large"),
            filterProviderModels(models, " LARGE alpha ")
        )
        assertEquals(models, filterProviderModels(models, "  "))
    }

    @Test
    fun `every remote provider reports a dynamic catalog source`() {
        assertEquals(
            ModelCatalogSource.PROVIDER_API,
            expectedCatalogSource(ProviderType.OPENAI)
        )
        assertEquals(
            ModelCatalogSource.PROVIDER_API,
            expectedCatalogSource(ProviderType.OPENCODE)
        )
        assertEquals(
            ModelCatalogSource.PROVIDER_API,
            expectedCatalogSource(ProviderType.ZAI)
        )
        assertEquals(
            ModelCatalogSource.PROVIDER_API,
            expectedCatalogSource(ProviderType.MOONSHOT, MoonshotEndpointType.CODING)
        )
    }

    @Test
    fun `cache keys isolate providers and endpoints`() {
        val openAi = ProviderCatalogKey(ProviderType.OPENAI, "https://api.example/v1/")
        val kilo = ProviderCatalogKey(ProviderType.KILO, "https://api.example/v1/")
        val otherOpenAiEndpoint = ProviderCatalogKey(ProviderType.OPENAI, "https://other.example/v1/")

        assertNotEquals(openAi.storageKey, kilo.storageKey)
        assertNotEquals(openAi.storageKey, otherOpenAiEndpoint.storageKey)
    }
}
