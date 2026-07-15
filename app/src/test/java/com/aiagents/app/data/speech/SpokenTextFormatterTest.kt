package com.aiagents.app.data.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenTextFormatterTest {
    @Test
    fun `clean removes markdown destinations and code fences`() {
        val spoken = SpokenTextFormatter.clean(
            "Mira [la guía](https://example.com). `Aceptar` funciona.```kotlin\\nval secret = 1\\n```"
        )

        assertTrue(spoken.contains("la guía"))
        assertTrue(spoken.contains("Aceptar"))
        assertTrue(spoken.contains("Código omitido"))
        assertFalse(spoken.contains("https://"))
        assertFalse(spoken.contains("secret"))
    }

    @Test
    fun `chunk keeps every chunk within the engine limit`() {
        val input = "Primera oración. Segunda oración bastante larga. Tercera oración final."
        val chunks = SpokenTextFormatter.chunk(input, maxLength = 32)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 32 })
        assertEquals(SpokenTextFormatter.clean(input), chunks.joinToString(" "))
    }
}
