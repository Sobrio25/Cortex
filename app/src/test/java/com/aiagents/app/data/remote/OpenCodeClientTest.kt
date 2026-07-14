package com.aiagents.app.data.remote

import com.aiagents.app.domain.model.OpenCodeVariantType
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenCodeClientTest {

    @Test
    fun `normalizes OpenCode config prefixes for direct gateway requests`() {
        assertEquals(
            "qwen3.6-plus",
            OpenCodeProtocolResolver.normalizeModelId("opencode-go/qwen3.6-plus")
        )
        assertEquals(
            "gpt-5.4",
            OpenCodeProtocolResolver.normalizeModelId("opencode/gpt-5.4")
        )
    }

    @Test
    fun `routes Zen model families to their published protocols`() {
        assertEquals(
            OpenCodeApiProtocol.RESPONSES,
            OpenCodeProtocolResolver.resolve(OpenCodeVariantType.ZEN, "gpt-5.4")
        )
        assertEquals(
            OpenCodeApiProtocol.ANTHROPIC_MESSAGES,
            OpenCodeProtocolResolver.resolve(OpenCodeVariantType.ZEN, "claude-sonnet-4-6")
        )
        assertEquals(
            OpenCodeApiProtocol.ANTHROPIC_MESSAGES,
            OpenCodeProtocolResolver.resolve(OpenCodeVariantType.ZEN, "qwen3.7-plus")
        )
        assertEquals(
            OpenCodeApiProtocol.GEMINI,
            OpenCodeProtocolResolver.resolve(OpenCodeVariantType.ZEN, "gemini-3-flash")
        )
        assertEquals(
            OpenCodeApiProtocol.CHAT_COMPLETIONS,
            OpenCodeProtocolResolver.resolve(OpenCodeVariantType.ZEN, "minimax-m3")
        )
    }

    @Test
    fun `routes Go MiniMax and Qwen through Anthropic messages`() {
        assertEquals(
            OpenCodeApiProtocol.ANTHROPIC_MESSAGES,
            OpenCodeProtocolResolver.resolve(OpenCodeVariantType.GO, "minimax-m2.7")
        )
        assertEquals(
            OpenCodeApiProtocol.ANTHROPIC_MESSAGES,
            OpenCodeProtocolResolver.resolve(OpenCodeVariantType.GO, "qwen3.6-plus")
        )
        assertEquals(
            OpenCodeApiProtocol.CHAT_COMPLETIONS,
            OpenCodeProtocolResolver.resolve(OpenCodeVariantType.GO, "glm-5.2")
        )
    }

    @Test
    fun `parses OpenAI catalog and context metadata`() {
        val models = OpenCodeCatalogParser.parse(
            """{"data":[{"id":"qwen3.6-plus","context_length":1000000}]}"""
        )

        assertEquals("qwen3.6-plus", models.single().id)
        assertEquals(1_000_000, models.single().contextWindow)
    }

    @Test
    fun `parses keyed model metadata with nested context limit`() {
        val models = OpenCodeCatalogParser.parse(
            """{"models":{"gpt-5.4":{"limit":{"context":272000}}}}"""
        )

        assertEquals("gpt-5.4", models.single().id)
        assertEquals(272_000, models.single().contextWindow)
    }

    @Test
    fun `models dev metadata keeps Zen and Go context limits isolated`() {
        val metadata = ModelsDevOpenCodeMetadataParser.parse(
            """
            {
              "opencode": {
                "models": {
                  "qwen3.6-plus": {"limit": {"context": 262144}}
                }
              },
              "opencode-go": {
                "models": {
                  "qwen3.6-plus": {"limit": {"context": 1000000}}
                }
              }
            }
            """.trimIndent()
        )

        assertEquals(262_144, metadata.getValue(OpenCodeVariantType.ZEN)["qwen3.6-plus"])
        assertEquals(1_000_000, metadata.getValue(OpenCodeVariantType.GO)["qwen3.6-plus"])
    }

    @Test
    fun `gateway context wins and models dev fills missing metadata`() {
        val enriched = OpenCodeMetadataMerger.enrich(
            models = listOf(
                RemoteModelInfo("gateway-specific", 300_000),
                RemoteModelInfo("qwen3.6-plus")
            ),
            officialContextWindows = mapOf(
                "gateway-specific" to 200_000,
                "qwen3.6-plus" to 1_000_000
            )
        )

        assertEquals(300_000, enriched[0].contextWindow)
        assertEquals(1_000_000, enriched[1].contextWindow)
    }
}
