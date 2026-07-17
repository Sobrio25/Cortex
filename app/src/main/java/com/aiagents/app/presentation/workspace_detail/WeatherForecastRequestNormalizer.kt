package com.aiagents.app.presentation.workspace_detail

import com.google.gson.JsonParser
import java.text.Normalizer
import java.util.Locale

/**
 * Repairs legacy forecast calls from models that only send a broad `days` window.
 * Explicit target arguments always win; inference is limited to unambiguous Spanish phrases.
 */
internal object WeatherForecastRequestNormalizer {
    private val inDaysRegex = Regex("""\ben\s+([0-4])\s+dias?\b""")

    fun normalize(arguments: String, latestUserText: String?): String {
        val root = runCatching { JsonParser.parseString(arguments).asJsonObject }.getOrNull()
            ?: return arguments
        if (root.has("day_offset") || root.has("target_date")) return arguments

        val offset = inferDayOffset(latestUserText) ?: return arguments
        root.addProperty("day_offset", offset)
        // A single requested date must never be rendered as an accidental multi-day forecast.
        root.addProperty("days", 1)
        return root.toString()
    }

    internal fun inferDayOffset(userText: String?): Int? {
        val normalized = userText
            ?.lowercase(Locale.ROOT)
            ?.let { Normalizer.normalize(it, Normalizer.Form.NFD) }
            ?.replace(Regex("\\p{M}+"), "")
            ?: return null

        return when {
            Regex("""\bpasado\s+manana\b""").containsMatchIn(normalized) -> 2
            Regex("""\bmanana\b""").containsMatchIn(normalized) -> 1
            Regex("""\bhoy\b""").containsMatchIn(normalized) -> 0
            else -> inDaysRegex.find(normalized)?.groupValues?.get(1)?.toIntOrNull()
        }
    }
}
