package com.aiagents.app.data.skills

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillTranscriptRedactorTest {
    @Test
    fun `redacts secrets and excludes privileged roles`() {
        val transcript = SkillTranscriptRedactor.redact(
            listOf(
                SkillTranscriptMessage(SkillTranscriptRole.SYSTEM, "system secret should disappear"),
                SkillTranscriptMessage(SkillTranscriptRole.TOOL, "raw tool output should disappear"),
                SkillTranscriptMessage(
                    SkillTranscriptRole.USER,
                    "Crea un reporte. api_key=abc123456789 correo yo@example.com https://example.com/private"
                ),
                SkillTranscriptMessage(SkillTranscriptRole.ASSISTANT, "Prepararé el reporte semanal")
            )
        )

        assertFalse(transcript.contains("system secret"))
        assertFalse(transcript.contains("raw tool output"))
        assertFalse(transcript.contains("abc123456789"))
        assertFalse(transcript.contains("yo@example.com"))
        assertFalse(transcript.contains("example.com"))
        assertTrue(transcript.contains("[REDACTADO]"))
        assertTrue(transcript.contains("reporte semanal"))
    }

    @Test
    fun `bounds persisted transcript`() {
        val messages = (1..30).map {
            SkillTranscriptMessage(SkillTranscriptRole.USER, "mensaje $it " + "x".repeat(1_000))
        }

        val transcript = SkillTranscriptRedactor.redact(messages)

        assertTrue(transcript.length <= 20_000)
        assertFalse(transcript.contains("mensaje 1 "))
        assertTrue(transcript.contains("mensaje 30 "))
    }
}
