package com.aiagents.app.data.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WeatherForecastSelectionTest {
    private val dates = listOf("2026-07-15", "2026-07-16", "2026-07-17")

    @Test
    fun `today selects only index zero`() {
        val selection = WeatherForecastSelection.resolve(days = null, dayOffset = 0, targetDate = null)

        assertEquals(1, selection.queryDays)
        assertEquals(listOf(0), selection.selectedIndices(dates))
    }

    @Test
    fun `tomorrow requests enough horizon and selects only tomorrow`() {
        val selection = WeatherForecastSelection.resolve(days = 5, dayOffset = 1, targetDate = null)

        assertEquals(2, selection.queryDays)
        assertEquals(listOf(1), selection.selectedIndices(dates))
    }

    @Test
    fun `exact date selects only the matching calendar date`() {
        val selection = WeatherForecastSelection.resolve(
            days = 5,
            dayOffset = null,
            targetDate = "2026-07-17"
        )

        assertEquals("2026-07-17", selection.targetDate)
        assertNull(selection.dayOffset)
        assertEquals(listOf(2), selection.selectedIndices(dates))
    }

    @Test
    fun `invalid explicit dates and offsets are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            WeatherForecastSelection.resolve(null, 5, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WeatherForecastSelection.resolve(null, null, "17-07-2026")
        }
    }
}
