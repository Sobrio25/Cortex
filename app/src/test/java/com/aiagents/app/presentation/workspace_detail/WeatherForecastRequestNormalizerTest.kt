package com.aiagents.app.presentation.workspace_detail

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherForecastRequestNormalizerTest {
    @Test
    fun `tomorrow replaces legacy multi-day window with exact day`() {
        val result = WeatherForecastRequestNormalizer.normalize(
            arguments = """{"location":"Madrid","days":2}""",
            latestUserText = "¿Qué tiempo hará mañana?"
        )

        val json = JsonParser.parseString(result).asJsonObject
        assertEquals("Madrid", json["location"].asString)
        assertEquals(1, json["day_offset"].asInt)
        assertEquals(1, json["days"].asInt)
    }

    @Test
    fun `relative Spanish dates map to supported offsets`() {
        assertEquals(0, WeatherForecastRequestNormalizer.inferDayOffset("clima para hoy"))
        assertEquals(1, WeatherForecastRequestNormalizer.inferDayOffset("mañana en Puebla"))
        assertEquals(2, WeatherForecastRequestNormalizer.inferDayOffset("el pasado mañana"))
        assertEquals(4, WeatherForecastRequestNormalizer.inferDayOffset("clima en 4 días"))
    }

    @Test
    fun `explicit day offset is never overwritten`() {
        val arguments = """{"days":5,"day_offset":3}"""
        assertEquals(
            arguments,
            WeatherForecastRequestNormalizer.normalize(arguments, "mañana")
        )
    }

    @Test
    fun `explicit target date is never overwritten`() {
        val arguments = """{"target_date":"2026-07-20","days":2}"""
        assertEquals(
            arguments,
            WeatherForecastRequestNormalizer.normalize(arguments, "pasado mañana")
        )
    }

    @Test
    fun `ambiguous or out of-range text leaves legacy arguments unchanged`() {
        val arguments = """{"days":2}"""
        assertEquals(arguments, WeatherForecastRequestNormalizer.normalize(arguments, "esta semana"))
        assertEquals(arguments, WeatherForecastRequestNormalizer.normalize(arguments, "en 7 días"))
    }

    @Test
    fun `malformed arguments remain untouched`() {
        assertEquals("not-json", WeatherForecastRequestNormalizer.normalize("not-json", "mañana"))
    }
}
