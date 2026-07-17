package com.aiagents.app.presentation.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantConfirmationParserTest {
    @Test
    fun `accepts short explicit confirmations in Spanish and English`() {
        assertEquals(
            AssistantConfirmationDecision.CONFIRM,
            AssistantConfirmationParser.parse("Sí, envíalo")
        )
        assertEquals(
            AssistantConfirmationDecision.CONFIRM,
            AssistantConfirmationParser.parse("send it")
        )
    }

    @Test
    fun `accepts explicit cancellation`() {
        assertEquals(
            AssistantConfirmationDecision.CANCEL,
            AssistantConfirmationParser.parse("No")
        )
        assertEquals(
            AssistantConfirmationDecision.CANCEL,
            AssistantConfirmationParser.parse("No lo envíes")
        )
    }

    @Test
    fun `does not consume unrelated follow up text`() {
        assertEquals(
            AssistantConfirmationDecision.UNKNOWN,
            AssistantConfirmationParser.parse("Sí, pero cambia la hora para mañana")
        )
    }
}
