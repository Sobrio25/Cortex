package com.aiagents.app.data.terminal

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Deterministic forecast range requested by the model/tool caller. */
data class WeatherForecastSelection(
    val requestedDays: Int,
    val dayOffset: Int?,
    val targetDate: String?
) {
    val queryDays: Int = when {
        targetDate != null -> 1
        dayOffset != null -> dayOffset + 1
        else -> requestedDays
    }

    fun selectedIndices(isoDates: List<String>): List<Int> = when {
        targetDate != null -> isoDates.indices.filter { isoDates[it] == targetDate }.take(1)
        dayOffset != null -> listOf(dayOffset).filter { it in isoDates.indices }
        else -> isoDates.indices.take(requestedDays)
    }

    companion object {
        fun resolve(days: Int?, dayOffset: Int?, targetDate: String?): WeatherForecastSelection {
            val normalizedDate = targetDate?.trim()?.takeIf(String::isNotEmpty)
            if (normalizedDate != null) {
                try {
                    LocalDate.parse(normalizedDate)
                } catch (_: DateTimeParseException) {
                    throw IllegalArgumentException("target_date debe usar el formato yyyy-MM-dd.")
                }
            }
            if (dayOffset != null && dayOffset !in 0..4) {
                throw IllegalArgumentException("day_offset debe estar entre 0 y 4.")
            }
            return WeatherForecastSelection(
                requestedDays = days?.coerceIn(1, 5) ?: 3,
                // An exact date is unambiguous and takes precedence over a redundant offset.
                dayOffset = dayOffset.takeIf { normalizedDate == null },
                targetDate = normalizedDate
            )
        }
    }
}
