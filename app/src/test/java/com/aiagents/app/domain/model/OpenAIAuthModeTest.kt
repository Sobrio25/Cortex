package com.aiagents.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAIAuthModeTest {
    @Test
    fun readsCurrentOAuthBackendValue() {
        assertEquals(
            OpenAIAuthMode.OAUTH_BACKEND,
            OpenAIAuthMode.fromStoredValue("OAUTH_BACKEND")
        )
    }

    @Test
    fun migratesLegacyBackendProxyValue() {
        assertEquals(
            OpenAIAuthMode.OAUTH_BACKEND,
            OpenAIAuthMode.fromStoredValue("BACKEND_PROXY")
        )
    }

    @Test
    fun defaultsUnknownOrMissingValuesToApiKey() {
        assertEquals(OpenAIAuthMode.API_KEY, OpenAIAuthMode.fromStoredValue(null))
        assertEquals(OpenAIAuthMode.API_KEY, OpenAIAuthMode.fromStoredValue("UNKNOWN"))
    }
}
