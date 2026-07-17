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
        assertFalse(spoken.contains("https://"))
        assertFalse(spoken.contains("secret"))
    }

    @Test
    fun `clean removes emphasis markers before speech`() {
        val spoken = SpokenTextFormatter.clean(
            "**Respuesta:** usa *modo local*, __sin cuotas__ y ~~nunca~~ comparte la API key."
        )

        assertEquals(
            "Respuesta: usa modo local, sin cuotas y nunca comparte la API key.",
            spoken
        )
        assertFalse(spoken.any { it == '*' || it == '_' || it == '~' })
    }

    @Test
    fun `clean keeps list meaning without speaking markdown punctuation`() {
        val spoken = SpokenTextFormatter.clean(
            "# Pasos\n- **Abre** Cortex\n- Toca [Ajustes](https://example.com)\n1. Confirma"
        )

        assertEquals("Pasos\nAbre Cortex\nToca Ajustes\n1. Confirma", spoken)
        assertFalse(spoken.contains("#"))
        assertFalse(spoken.contains("https://"))
    }

    @Test
    fun `chunk keeps every chunk within the engine limit`() {
        val input = "Primera oración. Segunda oración bastante larga. Tercera oración final."
        val chunks = SpokenTextFormatter.chunk(input, maxLength = 32)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 32 })
        assertEquals(SpokenTextFormatter.clean(input), chunks.joinToString(" "))
    }

    @Test
    fun `clean removes emoji urls and structured payload while preserving measurements`() {
        val spoken = SpokenTextFormatter.clean(
            "🌦️ Mañana: **23 °C**, viento de 12 km/h. " +
                "Más datos: https://weather.example/path?q=1. WEATHER_DATA {\\\"temp\\\":23.4}"
        )

        assertEquals("Mañana: 23 °C, viento de 12 km/h. Más datos:", spoken)
        assertTrue(spoken.contains("23 °C"))
        assertTrue(spoken.contains("12 km/h"))
        assertFalse(spoken.contains("🌦"))
        assertFalse(spoken.contains("http"))
        assertFalse(spoken.contains("WEATHER_DATA"))
    }
}
