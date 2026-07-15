package com.aiagents.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManagedGatewayClientTest {
    @Test
    fun extractsConfiguredAssistantNameFromRuntimeContext() {
        val prompt = """
            ## ASSISTANT_RUNTIME_CONTEXT
            - Current agent: Clawdy (Agent Orchestrator)
            - Runtime: Android 16 / API 36 on Google Pixel 6a
        """.trimIndent()

        assertEquals("Clawdy", extractManagedAgentName(prompt))
    }

    @Test
    fun returnsNullWhenRuntimeIdentityIsMissing() {
        assertNull(extractManagedAgentName("You are a helpful assistant."))
    }
}
