package com.aiagents.app.data.repository

import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ProviderType
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import com.aiagents.app.domain.model.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenCounterTest {

    @Test
    fun `provider reported context always wins`() {
        assertEquals(
            262_144,
            TokenCounter.getContextWindowForModel(
                modelName = "vendor/model-without-size-in-name",
                provider = ProviderType.OPENROUTER,
                reportedContextWindow = 262_144
            )
        )
    }

    @Test
    fun `context hint and provider fallback do not collapse to eight thousand`() {
        assertEquals(
            256_000,
            TokenCounter.getContextWindowForModel("vendor/custom-256k-chat", ProviderType.OPENROUTER)
        )
        assertEquals(
            32_768,
            TokenCounter.getContextWindowForModel("vendor/custom-chat", ProviderType.OPENROUTER)
        )
        assertEquals(
            32_768,
            TokenCounter.getContextWindowForModel("local/custom-chat", ProviderType.LM_STUDIO)
        )
    }

    @Test
    fun `specific model match wins over shorter model family`() {
        assertEquals(128_000, TokenCounter.getContextWindowForModel("openai/gpt-4o"))
        assertEquals(8_192, TokenCounter.getContextWindowForModel("openai/gpt-4"))
    }

    @Test
    fun `compaction budget reserves response and safety capacity`() {
        val budget = ContextWindowPolicy.budget(
            contextWindow = 256_000,
            desiredResponseTokens = 8_192
        )

        assertTrue(budget.warningTokens > 200_000)
        assertTrue(budget.criticalTokens > budget.warningTokens)
        assertTrue(budget.promptLimit + budget.responseReserve + budget.safetyReserve <= 256_000)

        val info = ContextInfo(
            currentTokens = 8_000,
            maxTokens = 256_000,
            usagePercentage = 3.125f,
            availableTokens = 248_000,
            supportsVision = false,
            supportsDocuments = false,
            compactionWarningTokens = budget.warningTokens,
            compactionCriticalTokens = budget.criticalTokens
        )
        assertFalse(info.shouldPromptCompaction)
    }

    @Test
    fun `compaction planner summarizes old messages and keeps recent messages verbatim`() {
        val messages = (1..10).map { index ->
            Message(
                id = index.toLong(),
                role = if (index % 2 == 0) MessageRole.ASSISTANT else MessageRole.USER,
                content = "x".repeat(5_000),
                timestamp = index.toLong()
            )
        }

        val plan = ContextCompactionPlanner.plan(messages, contextWindow = 32_768)

        assertTrue(plan.messagesToSummarize.isNotEmpty())
        assertEquals(listOf(7L, 8L, 9L, 10L), plan.messagesToKeep.map { it.id })
        assertEquals(messages, plan.messagesToSummarize + plan.messagesToKeep)
    }

    @Test
    fun `model input estimate excludes ui reasoning and duplicate tool metadata`() {
        val base = Message(
            role = MessageRole.TOOL,
            content = "Documento creado con id 123"
        )
        val withUiOnlyFields = base.copy(
            reasoning = "r".repeat(20_000),
            toolResults = listOf(
                ToolResult("call-1", "gws_docs_create", base.content.repeat(20))
            )
        )

        assertEquals(
            TokenCounter.estimateModelInputTokens(listOf(base)),
            TokenCounter.estimateModelInputTokens(listOf(withUiOnlyFields))
        )

        val withSerializedToolCall = base.copy(
            role = MessageRole.ASSISTANT,
            toolCalls = listOf(
                ToolCall("call-1", function = ToolFunction("gws_docs_create", "{\"title\":\"A\"}"))
            )
        )
        assertTrue(
            TokenCounter.estimateModelInputTokens(listOf(withSerializedToolCall)) >
                TokenCounter.estimateModelInputTokens(listOf(base))
        )
    }
}
