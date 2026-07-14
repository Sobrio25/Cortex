package com.aiagents.app.data.skills

import com.aiagents.app.domain.model.WeatherWidgetsBuiltin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherWidgetsBuiltinTest {
    @Test
    fun `weather widget skill routes every weather intent through native tools`() {
        assertEquals("weather-widgets", WeatherWidgetsBuiltin.SLUG)
        assertTrue(WeatherWidgetsBuiltin.WHEN_TO_USE.contains("clima"))
        assertTrue(WeatherWidgetsBuiltin.instructions.contains("weather_current"))
        assertTrue(WeatherWidgetsBuiltin.instructions.contains("weather_forecast"))
        assertTrue(WeatherWidgetsBuiltin.instructions.contains("weather_air_quality"))
        assertTrue(WeatherWidgetsBuiltin.instructions.contains("WEATHER_DATA"))
        assertTrue(WeatherWidgetsBuiltin.instructions.contains("no copies"))
        assertTrue(WeatherWidgetsBuiltin.instructions.contains("sin escribir ninguna respuesta adicional"))
    }
}
