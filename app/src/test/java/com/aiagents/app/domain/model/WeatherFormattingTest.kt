package com.aiagents.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherFormattingTest {
    @Test
    fun `visible temperatures are rounded consistently to whole numbers`() {
        assertEquals("23", formatTemperatureValue(22.6))
        assertEquals("22", formatTemperatureValue(22.4))
        assertEquals("-3", formatTemperatureValue(-2.6))
        assertEquals("0", formatTemperatureValue(0.0))
    }
}
