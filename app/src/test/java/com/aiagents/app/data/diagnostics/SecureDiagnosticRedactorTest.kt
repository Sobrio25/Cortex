package com.aiagents.app.data.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureDiagnosticRedactorTest {
    @Test
    fun `redacts credentials headers prompts and content`() {
        val source = """
            {
              "authorization":"Bearer secret-token-123",
              "api_key":"sk-supersecret12345",
              "prompt":"private medical question",
              "messages":[{"role":"user","content":"private message"}],
              "safe":"OPENAI gpt-test"
            }
        """.trimIndent()

        val redacted = SecureDiagnosticRedactor.redact(source)

        assertFalse(redacted.contains("secret-token"))
        assertFalse(redacted.contains("sk-supersecret"))
        assertFalse(redacted.contains("private medical"))
        assertFalse(redacted.contains("private message"))
        assertTrue(redacted.contains("[REDACTED]"))
        assertTrue(redacted.contains("OPENAI gpt-test"))
    }

    @Test
    fun `redacts secrets from unstructured log lines and URLs`() {
        val source = "Authorization: Bearer token-123 apiKey=AIza123456789012345678 " +
            "url=https://example.test?q=ok&access_token=hidden-value"

        val redacted = SecureDiagnosticRedactor.redact(source)

        assertFalse(redacted.contains("token-123"))
        assertFalse(redacted.contains("AIza123"))
        assertFalse(redacted.contains("hidden-value"))
        assertTrue(redacted.contains("example.test"))
    }
}
