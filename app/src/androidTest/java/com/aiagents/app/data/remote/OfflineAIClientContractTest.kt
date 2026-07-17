package com.aiagents.app.data.remote

import java.io.IOException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the account-free fallback used when a provider cannot be reached. */
class OfflineAIClientContractTest {
    @Test
    fun offlineFailureBecomesARecoverableStreamingChunkInsteadOfEscaping() = runBlocking {
        val client = OfflineClient()

        val chunks = client.chatWithToolsStreaming(
            model = "offline-test",
            messages = listOf(ChatMessage("user", "hola")),
            systemPrompt = "",
            temperature = 0f,
            maxTokens = 32,
            tools = emptyList()
        ).toList()

        assertEquals(1, chunks.size)
        assertEquals(OFFLINE_MESSAGE, chunks.single().error)
        assertFalse(chunks.single().done)
        assertNull(chunks.single().content)
        assertNull(chunks.single().toolCalls)
    }

    @Test
    fun aNewCollectionCanRecoverAfterAOneShotOfflineFailure() = runBlocking {
        val client = RecoveringClient()

        val first = collect(client)
        val second = collect(client)

        assertEquals(OFFLINE_MESSAGE, first.single().error)
        assertTrue(second.last().done)
        assertEquals("recuperado", second.first().content)
        assertNull(second.last().error)
    }

    private suspend fun collect(client: AIClient): List<StreamingChunk> =
        client.chatWithToolsStreaming(
            model = "offline-test",
            messages = listOf(ChatMessage("user", "hola")),
            systemPrompt = "",
            temperature = 0f,
            maxTokens = 32,
            tools = emptyList()
        ).toList()

    private class OfflineClient : StubAIClient() {
        override suspend fun chatWithTools(
            model: String,
            messages: List<ChatMessage>,
            systemPrompt: String,
            temperature: Float,
            maxTokens: Int,
            tools: List<Map<String, Any>>
        ): Result<ChatResponseWithTools> = Result.failure(IOException(OFFLINE_MESSAGE))
    }

    private class RecoveringClient : StubAIClient() {
        private var calls = 0

        override suspend fun chatWithTools(
            model: String,
            messages: List<ChatMessage>,
            systemPrompt: String,
            temperature: Float,
            maxTokens: Int,
            tools: List<Map<String, Any>>
        ): Result<ChatResponseWithTools> {
            calls += 1
            return if (calls == 1) {
                Result.failure(IOException(OFFLINE_MESSAGE))
            } else {
                Result.success(ChatResponseWithTools("recuperado", null, "stop"))
            }
        }
    }

    private abstract class StubAIClient : AIClient {
        override suspend fun chat(
            model: String,
            messages: List<ChatMessage>,
            systemPrompt: String,
            temperature: Float,
            maxTokens: Int
        ): Result<String> = Result.failure(UnsupportedOperationException())

        override suspend fun getAvailableModels(): Result<List<String>> = Result.success(emptyList())
    }

    private companion object {
        const val OFFLINE_MESSAGE = "Sin conexión"
    }
}
