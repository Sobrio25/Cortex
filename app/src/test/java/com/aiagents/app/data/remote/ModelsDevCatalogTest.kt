package com.aiagents.app.data.remote

import com.aiagents.app.domain.model.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelsDevCatalogTest {
    @Test
    fun `parses model ids and context limits for every provider`() {
        val providers = ModelsDevCatalogParser.parse(
            """
            {
              "anthropic": {
                "models": {
                  "claude-example": {"limit": {"context": 1000000}}
                }
              },
              "zai": {
                "models": {
                  "glm-example": {"id": "glm-real-id", "limit": {"context": 262144}}
                }
              }
            }
            """.trimIndent()
        )

        assertEquals(RemoteModelInfo("claude-example", 1_000_000), providers.getValue("anthropic").single())
        assertEquals(RemoteModelInfo("glm-real-id", 262_144), providers.getValue("zai").single())
    }

    @Test
    fun `provider metadata has priority and public metadata fills gaps`() {
        val enriched = ProviderModelMetadataMerger.enrich(
            providerModels = listOf(
                RemoteModelInfo("reported", 300_000),
                RemoteModelInfo("missing")
            ),
            publicModels = listOf(
                RemoteModelInfo("reported", 200_000),
                RemoteModelInfo("missing", 1_000_000)
            )
        )

        assertEquals(300_000, enriched[0].contextWindow)
        assertEquals(1_000_000, enriched[1].contextWindow)
    }

    @Test
    fun `catalog provider mapping follows endpoint variants`() {
        assertEquals("moonshotai-cn", modelsDevProviderId(ProviderType.MOONSHOT, "https://api.moonshot.cn/v1/"))
        assertEquals("zai-coding-plan", modelsDevProviderId(ProviderType.ZAI, "https://api.z.ai/api/coding/paas/v4/"))
        assertEquals("kilo", modelsDevProviderId(ProviderType.KILO, null))
        assertNull(modelsDevProviderId(ProviderType.OPENAI, "https://llm.example/v1/"))
        assertNull(modelsDevProviderId(ProviderType.OLLAMA, "http://localhost:11434"))
        assertNull(modelsDevProviderId(ProviderType.LM_STUDIO, "http://localhost:1234/v1"))
    }
}
