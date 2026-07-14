package com.aiagents.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAIEndpointPolicyTest {
    @Test
    fun normalizesValidBackendUrl() {
        assertEquals(
            "https://agent.example.com/openai/v1/",
            OpenAIEndpointPolicy.normalizeBackendBaseUrl(" https://agent.example.com/openai/v1 ")
        )
    }

    @Test
    fun rejectsOpenAiOfficialUrlAsBackend() {
        assertNull(OpenAIEndpointPolicy.normalizeBackendBaseUrl("https://api.openai.com/v1"))
        assertNull(OpenAIEndpointPolicy.normalizeBackendBaseUrl("https://api.openai.com./v1"))
    }

    @Test
    fun rejectsUrlCredentialsQueryAndFragment() {
        assertNull(OpenAIEndpointPolicy.normalizeBackendBaseUrl("https://user:pass@example.com/v1"))
        assertNull(OpenAIEndpointPolicy.normalizeBackendBaseUrl("https://example.com/v1?token=secret"))
        assertNull(OpenAIEndpointPolicy.normalizeBackendBaseUrl("https://example.com/v1#token"))
    }

    @Test
    fun rejectsCleartextBackend() {
        assertNull(OpenAIEndpointPolicy.normalizeBackendBaseUrl("http://example.com/v1"))
    }
}
