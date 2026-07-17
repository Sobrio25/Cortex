package com.aiagents.app.presentation.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AssistantWeatherSpeechFormatterTest {
    private val spanish = Locale.forLanguageTag("es-MX")

    @Test
    fun `current weather becomes natural Spanish without visual shorthand`() {
        val spoken = requireNotNull(
            AssistantWeatherSpeechFormatter.format(
                """{
                    "type":"current",
                    "city":"Ixtapaluca",
                    "country":"México",
                    "conditionId":800,
                    "description":"Despejado",
                    "temp":31.6,
                    "minTemp":18.2,
                    "maxTemp":32.4,
                    "unitSymbol":"°C"
                }""".trimIndent(),
                spanish
            )
        )

        assertEquals(
            "El cielo en Ixtapaluca, México está despejado. La temperatura es de 32 grados Celsius. " +
                "Hoy, la mínima es de 18 y la máxima de 32 grados Celsius.",
            spoken
        )
        assertFalse(spoken.contains("°"))
        assertFalse(spoken.contains("·"))
        assertFalse(spoken.contains("máx."))
    }

    @Test
    fun `forecast spells out rain percentage for speech`() {
        val spoken = requireNotNull(
            AssistantWeatherSpeechFormatter.format(
                """{
                    "type":"forecast",
                    "city":"Ixtapaluca",
                    "country":"México",
                    "unitSymbol":"°C",
                    "dayOffset":1,
                    "days":[{
                        "date":"viernes 17 de julio",
                        "minTemp":13.2,
                        "maxTemp":22.7,
                        "maxPop":76,
                        "description":"Llovizna ligera"
                    }]
                }""".trimIndent(),
                spanish
            )
        )

        assertTrue(spoken.startsWith("Mañana en Ixtapaluca, México"))
        assertTrue(spoken.contains("máxima de 23 grados Celsius"))
        assertTrue(spoken.contains("76 por ciento de probabilidad de lluvia"))
        assertFalse(spoken.contains("%"))
        assertFalse(spoken.contains("°C"))
    }

    @Test
    fun `exact forecast date is expanded instead of reading an abbreviation`() {
        val spoken = requireNotNull(
            AssistantWeatherSpeechFormatter.format(
                """{
                    "type":"forecast",
                    "city":"Puebla",
                    "unitSymbol":"°C",
                    "days":[{
                        "date":"vie 17 jul",
                        "isoDate":"2026-07-17",
                        "minTemp":12,
                        "maxTemp":24,
                        "maxPop":10,
                        "description":"Parcialmente nublado"
                    }]
                }""".trimIndent(),
                spanish
            )
        )

        assertTrue(spoken.startsWith("Viernes 17 de julio en Puebla"))
        assertFalse(spoken.contains("vie 17 jul"))
    }
}
