package com.aiagents.app.data.diagnostics

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class UserFacingErrorMapperTest {
    @Test
    fun `network errors become actionable and hide raw host details`() {
        val result = UserFacingErrorMapper.map(
            UnknownHostException("private-model-host.internal"),
            "chat_response"
        )

        assertEquals(UserErrorCategory.NETWORK, result.category)
        assertEquals("CHAT-RESPONSE-RED", result.code)
        assertTrue(result.retryable)
        assertFalse(result.displayMessage.contains("private-model-host"))
    }

    @Test
    fun `timeout has stable code and retry guidance`() {
        val result = UserFacingErrorMapper.map(
            SocketTimeoutException("provider timeout after 60000 ms"),
            "voice_transcription"
        )

        assertEquals(UserErrorCategory.TIMEOUT, result.category)
        assertEquals("VOICE-TRANSCRIPTION-TIEMPO", result.code)
        assertTrue(result.message.contains("Inténtalo de nuevo"))
    }

    @Test
    fun `authentication message never exposes provider detail`() {
        val result = UserFacingErrorMapper.map(
            IllegalStateException("HTTP 401: invalid api key sk-super-secret-value"),
            "chat_response"
        )

        assertEquals(UserErrorCategory.AUTHENTICATION, result.category)
        assertEquals("CHAT-RESPONSE-ACCESO", result.code)
        assertFalse(result.displayMessage.contains("sk-super-secret"))
    }

    @Test
    fun `large context explains the recovery action`() {
        val result = UserFacingErrorMapper.map(
            IllegalArgumentException("maximum context length exceeded"),
            "context_summary"
        )

        assertEquals(UserErrorCategory.REQUEST_TOO_LARGE, result.category)
        assertTrue(result.message.contains("compacta el contexto"))
    }

    @Test
    fun `normal cancellation is classified but not retryable`() {
        val result = UserFacingErrorMapper.map(
            CancellationException("screen closed"),
            "chat_response"
        )

        assertEquals(UserErrorCategory.CANCELLED, result.category)
        assertFalse(result.retryable)
    }
}
