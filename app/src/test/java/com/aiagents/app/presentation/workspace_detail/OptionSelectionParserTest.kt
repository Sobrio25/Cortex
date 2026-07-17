package com.aiagents.app.presentation.workspace_detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionSelectionParserTest {
    @Test
    fun `parses Spanish and English option blocks and removes protocol markup`() {
        val content = """
            Elige las opciones:
            <ask_options titulo="Formato">
            - Breve
            - Detallado
            </ask_options>
            <ask_options title="Tone">
            * Formal
            * Casual
            </ask_options>
        """.trimIndent()

        val (requests, clean) = OptionSelectionParser.parseAll(content)

        assertEquals(listOf("Formato", "Tone"), requests.map { it.title })
        assertEquals(listOf("Breve", "Detallado"), requests.first().options)
        assertEquals("Elige las opciones:", clean)
    }

    @Test
    fun `ignores blocks without selectable items`() {
        val (requests, clean) = OptionSelectionParser.parseAll(
            "Antes<ask_options title=\"Empty\">none</ask_options>Después"
        )

        assertTrue(requests.isEmpty())
        assertEquals("AntesDespués", clean)
    }

    @Test
    fun `attaches visible response only to first question`() {
        val requests = listOf(
            OptionSelectionRequest("One", listOf("A")),
            OptionSelectionRequest("Two", listOf("B"))
        )

        val result = OptionSelectionParser.attachMessageToFirst(requests, "Contexto")

        assertEquals("Contexto", result.first().messageContent)
        assertEquals("", result.last().messageContent)
    }

    @Test
    fun `formats only paired answers`() {
        val questions = listOf(
            OptionSelectionRequest("Formato", listOf("Breve")),
            OptionSelectionRequest("Tono", listOf("Formal"))
        )

        assertEquals(
            "**Formato**: Breve",
            OptionSelectionParser.formatAnswers(questions, listOf("Breve"))
        )
    }
}
