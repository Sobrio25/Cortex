package com.aiagents.app.presentation.tool_results

import com.aiagents.app.data.terminal.ReminderToolHandler
import com.aiagents.app.data.terminal.SystemAppToolHandler
import com.aiagents.app.data.terminal.WeatherToolHandler
import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ToolResult
import com.aiagents.app.ui.components.extractWeatherDataJson
import java.util.Locale

/**
 * Typed, deterministic representation of tool results that are already a complete answer.
 *
 * These payloads come from the verified tool output, never from the model's follow-up prose.
 * Both chat surfaces consume this policy so adding a direct-result family cannot silently
 * produce different behavior in the normal chat and the system assistant.
 */
data class DirectToolResultPayload(
    val kind: Kind,
    val toolName: String,
    val title: String,
    val summary: String,
    val rawContent: String,
    val weatherJson: String? = null
) {
    enum class Kind { WEATHER, REMINDER, CALENDAR, DEVICE_ACTION }
}

object DirectToolResultPolicy {
    private const val TOOL_READ_CALENDAR = "read_calendar_events"
    private const val TOOL_ADD_CALENDAR = "add_calendar_event"

    val supportedToolNames: Set<String> =
        WeatherToolHandler.ALL_TOOL_NAMES +
            ReminderToolHandler.ALL_TOOL_NAMES +
            setOf(TOOL_READ_CALENDAR, TOOL_ADD_CALENDAR, SystemAppToolHandler.TOOL_NAME)

    fun payloads(message: Message, locale: Locale): List<DirectToolResultPayload> =
        message.toolResults.mapNotNull { payload(it, locale) }

    fun primaryPayload(message: Message, locale: Locale): DirectToolResultPayload? =
        message.toolResults.firstNotNullOfOrNull { payload(it, locale) }

    fun isDirectResult(message: Message): Boolean =
        message.toolResults.any { result ->
            result.name in supportedToolNames &&
                (result.name !in WeatherToolHandler.ALL_TOOL_NAMES || extractWeatherDataJson(result.content) != null)
        }

    fun weatherContent(message: Message): String? = message.toolResults.firstOrNull { result ->
        result.name in WeatherToolHandler.ALL_TOOL_NAMES &&
            extractWeatherDataJson(result.content) != null
    }?.content

    fun payload(result: ToolResult, locale: Locale): DirectToolResultPayload? = when (result.name) {
        in WeatherToolHandler.ALL_TOOL_NAMES -> weatherPayload(result, locale)
        in ReminderToolHandler.ALL_TOOL_NAMES -> reminderPayload(result, locale)
        TOOL_READ_CALENDAR, TOOL_ADD_CALENDAR -> calendarPayload(result, locale)
        SystemAppToolHandler.TOOL_NAME -> devicePayload(result, locale)
        else -> null
    }

    /**
     * Replaces raw direct-result message text with a deterministic summary and removes the
     * model's redundant follow-up until the next user turn. The typed payload remains attached
     * to [Message.toolResults] for native card rendering.
     */
    fun prepareVisible(messages: List<Message>, locale: Locale): List<Message> = buildList {
        var directResultSeenInTurn = false
        messages.forEach { message ->
            when {
                message.role == MessageRole.USER -> {
                    directResultSeenInTurn = false
                    add(message)
                }

                isDirectResult(message) -> {
                    directResultSeenInTurn = true
                    val summary = payloads(message, locale)
                        .joinToString("\n") { it.summary }
                        .ifBlank { message.content }
                    add(message.copy(content = summary))
                }

                message.role == MessageRole.ASSISTANT && directResultSeenInTurn -> Unit
                else -> add(message)
            }
        }
    }

    private fun weatherPayload(result: ToolResult, locale: Locale): DirectToolResultPayload? {
        val weatherJson = extractWeatherDataJson(result.content) ?: return null
        return DirectToolResultPayload(
            kind = DirectToolResultPayload.Kind.WEATHER,
            toolName = result.name,
            title = localized(locale, "Clima", "Weather"),
            summary = compactWeather(result.content),
            rawContent = result.content,
            weatherJson = weatherJson
        )
    }

    private fun reminderPayload(result: ToolResult, locale: Locale): DirectToolResultPayload {
        val (titleEs, titleEn, summary) = when (result.name) {
            ReminderToolHandler.TOOL_SET_REMINDER -> Triple(
                "Recordatorio creado",
                "Reminder created",
                compactReminder(result.content, locale)
            )

            ReminderToolHandler.TOOL_SET_ALARM -> Triple(
                "Alarma",
                "Alarm",
                result.content.firstUsefulLine().compact(18)
            )

            ReminderToolHandler.TOOL_CANCEL_REMINDER -> Triple(
                "Recordatorio cancelado",
                "Reminder cancelled",
                result.content.firstUsefulLine().compact(18)
            )

            else -> Triple(
                "Recordatorios",
                "Reminders",
                result.content.usefulLines(3).compact(28)
            )
        }
        return DirectToolResultPayload(
            kind = DirectToolResultPayload.Kind.REMINDER,
            toolName = result.name,
            title = localized(locale, titleEs, titleEn),
            summary = summary,
            rawContent = result.content
        )
    }

    private fun calendarPayload(result: ToolResult, locale: Locale): DirectToolResultPayload {
        val isAdd = result.name == TOOL_ADD_CALENDAR
        return DirectToolResultPayload(
            kind = DirectToolResultPayload.Kind.CALENDAR,
            toolName = result.name,
            title = if (isAdd) localized(locale, "Evento agregado", "Event added")
            else localized(locale, "Calendario", "Calendar"),
            summary = if (isAdd) compactCalendarEvent(result.content, locale)
            else result.content.usefulLines(3).compact(30),
            rawContent = result.content
        )
    }

    private fun devicePayload(result: ToolResult, locale: Locale): DirectToolResultPayload =
        DirectToolResultPayload(
            kind = DirectToolResultPayload.Kind.DEVICE_ACTION,
            toolName = result.name,
            title = localized(locale, "Acción del dispositivo", "Device action"),
            summary = result.content.firstUsefulLine().compact(24),
            rawContent = result.content
        )

    private fun compactWeather(content: String): String {
        val lines = content.substringBefore("<!--WEATHER_DATA:")
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toList()
        if (lines.isEmpty()) return ""
        return when {
            lines.first().contains("Pronóstico", ignoreCase = true) && lines.size >= 4 -> {
                val location = lines[0].substringAfter("para", lines[0]).clean()
                val date = lines[1].clean()
                val condition = lines[2].clean()
                "$date en $location: $condition, ${lines[3].clean()}".compact(18)
            }

            lines.first().contains("Clima actual", ignoreCase = true) && lines.size >= 3 -> {
                val location = lines[0].substringAfter("en", lines[0]).clean()
                "$location: ${lines[1].clean()}, ${lines[2].clean()}".compact(18)
            }

            lines.first().contains("Calidad del aire", ignoreCase = true) && lines.size >= 2 -> {
                val location = lines[0].substringAfter("en", lines[0]).clean()
                "$location: ${lines[1].clean()}".compact(18)
            }

            else -> lines.take(2).joinToString(" · ").clean().compact(18)
        }
    }

    private fun compactReminder(content: String, locale: Locale): String {
        val scheduled = content.lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("Programado para:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
        if (scheduled.isNullOrBlank()) return content.firstUsefulLine().compact(18)
        return if (locale.language.equals("es", ignoreCase = true)) {
            "Recordatorio creado para $scheduled."
        } else {
            "Reminder set for $scheduled."
        }
    }

    private fun compactCalendarEvent(content: String, locale: Locale): String {
        val lines = content.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val title = lines.firstOrNull { it.startsWith("Título:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()
        val start = lines.firstOrNull { it.startsWith("Inicio:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()
        if (title.isNullOrBlank() || start.isNullOrBlank()) return content.firstUsefulLine().compact(18)
        return if (locale.language.equals("es", ignoreCase = true)) {
            "Evento «$title» agregado para $start."
        } else {
            "Event “$title” added for $start."
        }
    }

    private fun localized(locale: Locale, spanish: String, english: String): String =
        if (locale.language.equals("es", ignoreCase = true)) spanish else english

    private fun String.firstUsefulLine(): String = lineSequence()
        .map(String::trim)
        .firstOrNull(String::isNotBlank)
        .orEmpty()

    private fun String.usefulLines(max: Int): String = lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .take(max)
        .joinToString(" · ")

    private fun String.clean(): String = replace("**", "")
        .withoutEmojis()
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun String.compact(maxWords: Int): String {
        val clean = clean()
        val words = clean.split(Regex("\\s+")).filter(String::isNotBlank)
        return if (words.size <= maxWords) clean else words.take(maxWords).joinToString(" ") + "…"
    }

    private fun String.withoutEmojis(): String = buildString {
        this@withoutEmojis.codePoints().forEach { codePoint ->
            val isEmoji = codePoint in 0x1F000..0x1FAFF ||
                codePoint in 0x2600..0x27BF ||
                codePoint == 0xFE0F ||
                codePoint == 0x200D
            if (!isEmoji) appendCodePoint(codePoint)
        }
    }
}
