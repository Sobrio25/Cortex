package com.aiagents.app.presentation.assistant

import com.aiagents.app.domain.model.formatTemperatureValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Converts structured weather data into sentences meant to be heard, never visual shorthand. */
object AssistantWeatherSpeechFormatter {
    fun format(weatherJson: String, locale: Locale): String? = runCatching {
        val root = JsonParser.parseString(weatherJson)
            .takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: return null
        val spanish = locale.language.equals("es", ignoreCase = true)
        when (root.string("type")) {
            "current" -> formatCurrent(root, spanish)
            "forecast" -> formatForecast(root, spanish)
            "air_quality" -> formatAirQuality(root, spanish)
            "error" -> root.string("message")
            else -> null
        }
    }.getOrNull()?.trim()?.takeIf(String::isNotBlank)

    private fun formatCurrent(root: JsonObject, spanish: Boolean): String? {
        val temperature = root.double("temp") ?: return null
        val location = root.location(spanish)
        val description = root.string("description").orEmpty().sentenceFragment()
        val unit = spokenUnit(root.string("unitSymbol"), spanish)
        val minimum = root.double("minTemp")
        val maximum = root.double("maxTemp")
        val conditionId = root.int("conditionId")

        return if (spanish) {
            val condition = when {
                description.isBlank() -> ""
                conditionId != null && conditionId in 800..899 ->
                    "El cielo en $location está $description. "
                else -> "En $location hay $description. "
            }
            buildString {
                append(condition)
                append("La temperatura es de ${formatTemperatureValue(temperature)} $unit.")
                if (minimum != null && maximum != null) {
                    append(
                        " Hoy, la mínima es de ${formatTemperatureValue(minimum)} y " +
                            "la máxima de ${formatTemperatureValue(maximum)} $unit."
                    )
                }
            }
        } else {
            buildString {
                if (description.isNotBlank()) {
                    append("In $location, conditions are $description. ")
                }
                append("The temperature is ${formatTemperatureValue(temperature)} $unit.")
                if (minimum != null && maximum != null) {
                    append(
                        " Today's low is ${formatTemperatureValue(minimum)} and " +
                            "the high is ${formatTemperatureValue(maximum)} $unit."
                    )
                }
            }
        }
    }

    private fun formatForecast(root: JsonObject, spanish: Boolean): String? {
        val days = root.get("days")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
            .orEmpty()
        if (days.isEmpty()) return null

        val location = root.location(spanish)
        val unit = spokenUnit(root.string("unitSymbol"), spanish)
        val dayOffset = root.int("dayOffset")
        return days.mapIndexedNotNull { index, day ->
            val minimum = day.double("minTemp") ?: return@mapIndexedNotNull null
            val maximum = day.double("maxTemp") ?: return@mapIndexedNotNull null
            val rainChance = day.int("maxPop")?.coerceIn(0, 100)
            val description = day.string("description").orEmpty().sentenceFragment()
            val label = when {
                days.size == 1 && dayOffset == 0 -> if (spanish) "Hoy" else "Today"
                days.size == 1 && dayOffset == 1 -> if (spanish) "Mañana" else "Tomorrow"
                else -> spokenDate(day.string("isoDate"), spanish)
                    ?: day.string("date").orEmpty().ifBlank {
                        if (spanish) "Día ${index + 1}" else "Day ${index + 1}"
                    }
            }

            if (spanish) {
                buildString {
                    append("$label en $location")
                    if (description.isNotBlank()) append(", el pronóstico indica $description")
                    append(
                        ". La mínima será de ${formatTemperatureValue(minimum)} y " +
                            "la máxima de ${formatTemperatureValue(maximum)} $unit"
                    )
                    if (rainChance != null) {
                        append(", con $rainChance por ciento de probabilidad de lluvia")
                    }
                    append('.')
                }
            } else {
                buildString {
                    append("$label in $location")
                    if (description.isNotBlank()) append(", the forecast is $description")
                    append(
                        ". The low will be ${formatTemperatureValue(minimum)} and " +
                            "the high ${formatTemperatureValue(maximum)} $unit"
                    )
                    if (rainChance != null) append(", with a $rainChance percent chance of rain")
                    append('.')
                }
            }
        }.joinToString(" ").takeIf(String::isNotBlank)
    }

    private fun formatAirQuality(root: JsonObject, spanish: Boolean): String? {
        val aqi = root.int("aqi") ?: return null
        val location = root.location(spanish)
        val label = root.string("aqiLabel").orEmpty().sentenceFragment()
        val recommendation = root.string("recommendation").orEmpty().trim()
        return if (spanish) {
            buildString {
                append("La calidad del aire en $location es ${label.ifBlank { "sin clasificar" }}, nivel $aqi de 5.")
                if (recommendation.isNotBlank()) append(" $recommendation")
            }
        } else {
            buildString {
                append("Air quality in $location is ${label.ifBlank { "unclassified" }}, level $aqi out of 5.")
                if (recommendation.isNotBlank()) append(" $recommendation")
            }
        }
    }

    private fun JsonObject.location(spanish: Boolean): String {
        val city = string("city").orEmpty().ifBlank {
            if (spanish) "tu ubicación" else "your location"
        }
        val country = string("country").orEmpty()
        return if (country.isBlank() || city.contains(country, ignoreCase = true)) city
        else "$city, $country"
    }

    private fun spokenUnit(symbol: String?, spanish: Boolean): String = when {
        symbol.orEmpty().contains('F', ignoreCase = true) ->
            if (spanish) "grados Fahrenheit" else "degrees Fahrenheit"
        symbol.orEmpty().equals("K", ignoreCase = true) -> "kelvin"
        else -> if (spanish) "grados Celsius" else "degrees Celsius"
    }

    private fun spokenDate(isoDate: String?, spanish: Boolean): String? = runCatching {
        val locale = if (spanish) Locale.forLanguageTag("es-MX") else Locale.ENGLISH
        val pattern = if (spanish) "EEEE d 'de' MMMM" else "EEEE, MMMM d"
        LocalDate.parse(isoDate.orEmpty()).format(DateTimeFormatter.ofPattern(pattern, locale))
            .replaceFirstChar { it.uppercase() }
    }.getOrNull()

    private fun String.sentenceFragment(): String = trim()
        .trimEnd('.', ',', ';', ':')
        .replaceFirstChar { char -> char.lowercase() }

    private fun JsonObject.string(name: String): String? = runCatching {
        get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString
    }.getOrNull()

    private fun JsonObject.double(name: String): Double? = runCatching {
        get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asDouble
    }.getOrNull()?.takeIf(Double::isFinite)

    private fun JsonObject.int(name: String): Int? = double(name)?.toInt()
}
