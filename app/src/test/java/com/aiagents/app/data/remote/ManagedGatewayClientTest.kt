package com.aiagents.app.data.remote

import com.aiagents.app.domain.model.ManagedCapabilitySupport
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedGatewayClientTest {
    @Test
    fun managedQuotaStatusSurvivesTheDefaultStreamingAdapter() = runBlocking {
        val client = object : AIClient {
            override suspend fun chat(
                model: String,
                messages: List<ChatMessage>,
                systemPrompt: String,
                temperature: Float,
                maxTokens: Int
            ): Result<String> = Result.failure(UnsupportedOperationException())

            override suspend fun chatWithTools(
                model: String,
                messages: List<ChatMessage>,
                systemPrompt: String,
                temperature: Float,
                maxTokens: Int,
                tools: List<Map<String, Any>>
            ): Result<ChatResponseWithTools> = Result.failure(
                ManagedGatewayException(429, "Cuota semanal agotada")
            )

            override suspend fun getAvailableModels(): Result<List<String>> = Result.success(emptyList())
        }

        val chunk = client.chatWithToolsStreaming("auto", emptyList(), "", 0f, 32, emptyList())
            .toList()
            .single()

        assertEquals("Cuota semanal agotada", chunk.error)
        assertEquals(429, chunk.errorStatusCode)
    }

    @Test
    fun deserializesTypedManagedCatalog() {
        val models = parseManagedModels(
            Gson(),
            """
            [{
              "id":"auto",
              "displayName":"Auto",
              "minimumPlan":"FREE",
              "contextWindow":128000,
              "capabilities":{
                "tools":"BEST_EFFORT",
                "vision":"UNKNOWN",
                "streaming":"UNSUPPORTED",
                "reasoning":"UNKNOWN"
              },
              "pricing":{"inputMicrosPerToken":null,"outputMicrosPerToken":null},
              "available":true,
              "selectable":true
            }]
            """.trimIndent()
        )

        assertEquals(ManagedCapabilitySupport.BEST_EFFORT, models.single().capabilities.tools)
        assertEquals(128_000, models.single().contextWindow)
        assertTrue(models.single().requiresFreeToolsWarning)
        assertTrue(models.single().selectable)
    }

    @Test
    fun legacyCatalogWithoutCapabilitiesUsesSafeDefaults() {
        val models = parseManagedModels(
            Gson(),
            """
            [
              {
                "id":"auto",
                "displayName":"Auto",
                "minimumPlan":"FREE",
                "contextWindow":128000
              },
              {"displayName":"Fila inválida"}
            ]
            """.trimIndent()
        )

        val model = models.single()
        assertEquals(ManagedCapabilitySupport.UNKNOWN, model.capabilities.tools)
        assertEquals(null, model.pricing.inputMicrosPerToken)
        assertEquals(null, model.pricing.outputMicrosPerToken)
        assertTrue(model.available)
        assertTrue(model.selectable)
    }

    @Test
    fun partialCapabilitiesUseUnknownInsteadOfNull() {
        val model = parseManagedModels(
            Gson(),
            """
            [{
              "id":"partial",
              "minimumPlan":"PRO",
              "capabilities":{"tools":"SUPPORTED"}
            }]
            """.trimIndent()
        ).single()

        assertEquals(ManagedCapabilitySupport.SUPPORTED, model.capabilities.tools)
        assertEquals(ManagedCapabilitySupport.UNKNOWN, model.capabilities.vision)
        assertEquals(ManagedCapabilitySupport.UNKNOWN, model.capabilities.streaming)
        assertEquals(ManagedCapabilitySupport.UNKNOWN, model.capabilities.reasoning)
    }

    @Test
    fun parsesManagedUsageAndTransparentFallbackMetadata() {
        val root = JsonParser.parseString(
            """
            {
              "response": { "content": "ok" },
              "usage": {
                "promptTokens": 120,
                "completionTokens": 30,
                "totalTokens": 150,
                "estimated": false,
                "costMicros": 300,
                "free": false,
                "requestedModel": "gpt-5.6-luna",
                "modelUsed": "deepseek/deepseek-v4-pro",
                "provider": "DeepSeek",
                "gateway": "Vercel AI Gateway",
                "fallback": true,
                "fallbackCategory": "CAPACITY",
                "fallbackReason": "El proveedor no tenía capacidad disponible"
              }
            }
            """.trimIndent()
        ).asJsonObject

        val usage = requireNotNull(parseManagedInferenceUsage(root))

        assertEquals(150L, usage.totalTokens)
        assertEquals(300L, usage.costMicros)
        assertEquals("deepseek/deepseek-v4-pro", usage.modelUsed)
        assertEquals("CAPACITY", usage.fallbackCategory)
        assertTrue(usage.fallback)
        assertFalse(usage.estimated)
    }

    @Test
    fun managedUsageParsingToleratesMissingAndMalformedOptionalMetadata() {
        val root = JsonParser.parseString(
            """
            {
              "response": { "content": "ok" },
              "usage": {
                "promptTokens": 12,
                "completionTokens": 3,
                "totalTokens": "invalid",
                "costMicros": null,
                "fallback": null
              }
            }
            """.trimIndent()
        ).asJsonObject

        val usage = requireNotNull(parseManagedInferenceUsage(root))

        assertEquals(12L, usage.promptTokens)
        assertEquals(3L, usage.completionTokens)
        assertEquals(15L, usage.totalTokens)
        assertEquals(null, usage.costMicros)
        assertFalse(usage.fallback)
    }
}
