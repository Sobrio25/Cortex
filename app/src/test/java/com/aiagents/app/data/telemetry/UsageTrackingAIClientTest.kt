package com.aiagents.app.data.telemetry

import com.aiagents.app.data.remote.AIClient
import com.aiagents.app.data.remote.ChatMessage
import com.aiagents.app.data.remote.ChatResponseWithTools
import com.aiagents.app.data.remote.StreamingChunk
import com.aiagents.app.data.remote.TokenUsage
import com.aiagents.app.domain.model.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageTrackingAIClientTest {
    @Test
    fun recordsOnlyNumericMetadataForSuccessfulByokChat() = runBlocking {
        val events = mutableListOf<AppUsageEvent>()
        val client = UsageTrackingAIClient(
            delegate = FakeClient(chatResult = Result.success("respuesta privada")),
            sink = AppUsageSink(events::add),
            providerType = ProviderType.OPENAI
        )

        val result = client.chat(
            model = "gpt-test",
            messages = listOf(ChatMessage("user", "prompt ultrasecreto")),
            systemPrompt = "instrucción privada",
            temperature = 0.2f,
            maxTokens = 100
        )

        assertTrue(result.isSuccess)
        val event = events.single()
        assertEquals("byok", event.source)
        assertEquals("OpenAI", event.provider)
        assertEquals("gpt-test", event.model)
        assertEquals("success", event.status)
        assertTrue(event.promptTokens > 0)
        assertTrue(event.completionTokens > 0)
        val fieldNames = AppUsageEvent::class.java.declaredFields.map { it.name }.toSet()
        assertFalse("prompt" in fieldNames)
        assertFalse("response" in fieldNames)
        assertFalse("apiKey" in fieldNames)
    }

    @Test
    fun recordsProviderFailuresWithoutChangingTheResult() = runBlocking {
        val events = mutableListOf<AppUsageEvent>()
        val client = UsageTrackingAIClient(
            delegate = FakeClient(chatResult = Result.failure(IllegalStateException("falló"))),
            sink = AppUsageSink(events::add),
            providerType = ProviderType.LOCAL
        )

        val result = client.chat("local.gguf", emptyList(), "", 0f, 50)

        assertTrue(result.isFailure)
        assertEquals("local", events.single().source)
        assertEquals("error", events.single().status)
        assertEquals(0L, events.single().completionTokens)
    }

    @Test
    fun providerReportedUsageReplacesEstimatesAndIsLabeledActual() = runBlocking {
        val events = mutableListOf<AppUsageEvent>()
        val client = UsageTrackingAIClient(
            delegate = FakeClient(
                toolResult = Result.success(
                    ChatResponseWithTools(
                        content = "ok",
                        toolCalls = null,
                        finishReason = "stop",
                        usage = TokenUsage(inputTokens = 123, outputTokens = 45, totalTokens = 168)
                    )
                )
            ),
            sink = AppUsageSink(events::add),
            providerType = ProviderType.DEEPSEEK
        )

        client.chatWithTools("deepseek-test", emptyList(), "system", 0f, 50, emptyList())

        val event = events.single()
        assertEquals(123L, event.promptTokens)
        assertEquals(45L, event.completionTokens)
        assertFalse(event.usageEstimated)
    }

    @Test
    fun streamingProducesOneUsageEventWhenTheResponseFinishes() = runBlocking {
        val events = mutableListOf<AppUsageEvent>()
        val client = UsageTrackingAIClient(
            delegate = FakeClient(
                stream = flowOf(
                    StreamingChunk(content = "hola "),
                    StreamingChunk(content = "mundo", done = true)
                )
            ),
            sink = AppUsageSink(events::add),
            providerType = ProviderType.ANTHROPIC
        )

        val chunks = client.chatWithToolsStreaming("claude-test", emptyList(), "", 0f, 50, emptyList()).toList()

        assertEquals(2, chunks.size)
        assertEquals(1, events.size)
        assertEquals("stream", events.single().operation)
        assertEquals("success", events.single().status)
        assertTrue(events.single().completionTokens > 0)
    }

    @Test
    fun tokenEstimatorCountsImagePlaceholdersWithoutReadingBase64AsText() {
        val shortImage = AppUsageTokenEstimator.prompt(
            "",
            listOf(ChatMessage(role = "user", content = "foto", imageDataUri = "data:image/png;base64,a")),
            emptyList()
        )
        val hugeImage = AppUsageTokenEstimator.prompt(
            "",
            listOf(ChatMessage(role = "user", content = "foto", imageDataUri = "data:image/png;base64,${"a".repeat(20_000)}")),
            emptyList()
        )

        assertEquals(shortImage, hugeImage)
    }

    private class FakeClient(
        private val chatResult: Result<String> = Result.success("ok"),
        private val toolResult: Result<ChatResponseWithTools> =
            Result.success(ChatResponseWithTools("ok", null, "stop")),
        private val stream: Flow<StreamingChunk>? = null
    ) : AIClient {
        override suspend fun chat(
            model: String,
            messages: List<ChatMessage>,
            systemPrompt: String,
            temperature: Float,
            maxTokens: Int
        ): Result<String> = chatResult

        override suspend fun chatWithTools(
            model: String,
            messages: List<ChatMessage>,
            systemPrompt: String,
            temperature: Float,
            maxTokens: Int,
            tools: List<Map<String, Any>>
        ): Result<ChatResponseWithTools> = toolResult

        override suspend fun getAvailableModels(): Result<List<String>> = Result.success(emptyList())

        override fun chatWithToolsStreaming(
            model: String,
            messages: List<ChatMessage>,
            systemPrompt: String,
            temperature: Float,
            maxTokens: Int,
            tools: List<Map<String, Any>>
        ): Flow<StreamingChunk> = stream ?: super.chatWithToolsStreaming(
            model,
            messages,
            systemPrompt,
            temperature,
            maxTokens,
            tools
        )
    }
}
