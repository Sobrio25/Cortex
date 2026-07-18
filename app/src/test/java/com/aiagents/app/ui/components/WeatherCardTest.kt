package com.aiagents.app.ui.components

import com.aiagents.app.data.terminal.WeatherToolHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherCardTest {

    @Test
    fun `weather tools allow an implicit device location or explicit coordinates`() {
        @Suppress("UNCHECKED_CAST")
        val currentFunction = WeatherToolHandler.getToolDefinitionsJson()
            .first()
            .getValue("function") as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val parameters = currentFunction.getValue("parameters") as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val properties = parameters.getValue("properties") as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val required = parameters.getValue("required") as List<String>

        assertTrue(required.isEmpty())
        assertTrue("location" in properties)
        assertTrue("lat" in properties)
        assertTrue("lon" in properties)
    }

    @Test
    fun `forecast schema exposes exact relative and calendar date selection`() {
        @Suppress("UNCHECKED_CAST")
        val function = WeatherToolHandler.getToolDefinitionsJson()[1].getValue("function") as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val parameters = function.getValue("parameters") as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val properties = parameters.getValue("properties") as Map<String, Any>

        assertTrue("day_offset" in properties)
        assertTrue("target_date" in properties)
    }

    @Test
    fun `weather marker extraction supports multiline payloads`() {
        val content = """Resultado
            <!--WEATHER_DATA:{
              "type":"error",
              "code":"NETWORK_UNAVAILABLE",
              "message":"Sin red"
            }-->
        """.trimIndent()

        val extracted = requireNotNull(extractWeatherDataJson(content))

        assertTrue(extracted.contains("NETWORK_UNAVAILABLE"))
    }

    @Test
    fun `weather condition mapping keeps day and night clear states distinct`() {
        assertEquals(WeatherCondition.CLEAR_DAY, mapCondition(800, "01d"))
        assertEquals(WeatherCondition.CLEAR_NIGHT, mapCondition(800, "01n"))
        assertEquals(WeatherCondition.THUNDERSTORM, mapCondition(211, "11d"))
    }

    @Test
    fun `single day forecast always uses the centered featured layout`() {
        assertEquals(
            ForecastLayoutMode.FEATURED,
            resolveForecastLayoutMode(dayCount = 1, availableWidthDp = 240f)
        )
        assertEquals(
            ForecastLayoutMode.FEATURED,
            resolveForecastLayoutMode(dayCount = 1, availableWidthDp = 800f)
        )
    }

    @Test
    fun `multiple forecast days distribute when they fit and scroll when they do not`() {
        assertEquals(
            ForecastLayoutMode.DISTRIBUTED,
            resolveForecastLayoutMode(dayCount = 2, availableWidthDp = 320f)
        )
        assertEquals(
            ForecastLayoutMode.SCROLLING,
            resolveForecastLayoutMode(dayCount = 3, availableWidthDp = 320f)
        )
        assertEquals(
            ForecastLayoutMode.DISTRIBUTED,
            resolveForecastLayoutMode(dayCount = 3, availableWidthDp = 500f)
        )
    }

    @Test
    fun `empty forecast has a balanced placeholder layout`() {
        assertEquals(
            ForecastLayoutMode.EMPTY,
            resolveForecastLayoutMode(dayCount = 0, availableWidthDp = 320f)
        )
    }

    @Test
    fun `Open Meteo WMO codes preserve the native widget condition families`() {
        assertEquals(800, WeatherToolHandler.mapWmoToConditionId(0))
        assertEquals(741, WeatherToolHandler.mapWmoToConditionId(45))
        assertEquals(502, WeatherToolHandler.mapWmoToConditionId(65))
        assertEquals(602, WeatherToolHandler.mapWmoToConditionId(75))
        assertEquals(202, WeatherToolHandler.mapWmoToConditionId(99))
    }

    @Test
    fun `Open Meteo tools do not expose an API key parameter`() {
        val serialized = WeatherToolHandler.getToolDefinitionsJson().toString()

        assertTrue(serialized.contains("Open-Meteo"))
        assertFalse(serialized.contains("apiKey", ignoreCase = true))
    }

    @Test
    fun `European AQI is normalized to the five point widget scale`() {
        assertEquals(1, WeatherToolHandler.mapEuropeanAqiToFivePointScale(20.0))
        assertEquals(2, WeatherToolHandler.mapEuropeanAqiToFivePointScale(34.0))
        assertEquals(3, WeatherToolHandler.mapEuropeanAqiToFivePointScale(60.0))
        assertEquals(4, WeatherToolHandler.mapEuropeanAqiToFivePointScale(80.0))
        assertEquals(5, WeatherToolHandler.mapEuropeanAqiToFivePointScale(101.0))
    }
}
