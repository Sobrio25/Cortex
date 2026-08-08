package com.aiagents.app.data.auth

import com.aiagents.app.domain.model.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderEndpointPolicyTest {
    @Test
    fun `remote providers require https`() {
        assertTrue(
            runCatching {
                ProviderEndpointPolicy.validate(
                    ProviderType.ANTHROPIC,
                    "http://api.example.com/v1"
                )
            }.exceptionOrNull() is UnsafeProviderEndpointException
        )
        assertEquals(
            "https://api.example.com/v1",
            ProviderEndpointPolicy.validate(
                ProviderType.ANTHROPIC,
                " https://api.example.com/v1 "
            )
        )
    }

    @Test
    fun `local engines may use cleartext only on local hosts`() {
        listOf(
            "http://localhost:11434",
            "http://10.0.2.2:1234/v1",
            "http://192.168.1.20:11434",
            "http://cortex-server.local:11434",
            "http://[::1]:11434"
        ).forEach { endpoint ->
            assertEquals(
                endpoint,
                ProviderEndpointPolicy.validate(ProviderType.OLLAMA, endpoint)
            )
        }

        assertTrue(
            runCatching {
                ProviderEndpointPolicy.validate(
                    ProviderType.OLLAMA,
                    "http://ollama.example.com:11434"
                )
            }.isFailure
        )
    }

    @Test
    fun `endpoint cannot embed credentials`() {
        assertTrue(
            runCatching {
                ProviderEndpointPolicy.validate(
                    ProviderType.LM_STUDIO,
                    "https://user:password@example.com/v1"
                )
            }.isFailure
        )
    }

    @Test
    fun `local host classifier rejects public and malformed addresses`() {
        assertFalse(ProviderEndpointPolicy.isLocalHost("8.8.8.8"))
        assertFalse(ProviderEndpointPolicy.isLocalHost("999.1.1.1"))
        assertFalse(ProviderEndpointPolicy.isLocalHost("example.com"))
        assertTrue(ProviderEndpointPolicy.isLocalHost("172.16.0.4"))
        assertTrue(ProviderEndpointPolicy.isLocalHost("fd12::1"))
    }
}
