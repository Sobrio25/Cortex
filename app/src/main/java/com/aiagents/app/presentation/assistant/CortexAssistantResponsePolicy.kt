package com.aiagents.app.presentation.assistant

import com.aiagents.app.data.terminal.ReminderToolHandler
import com.aiagents.app.data.terminal.SystemAppToolHandler
import com.aiagents.app.data.terminal.WeatherToolHandler
import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ToolResult
import com.aiagents.app.ui.components.extractWeatherDataJson
import java.util.Locale

/** Keeps verified assistant tool results compact and removes the model's redundant follow-up. */
object CortexAssistantResponsePolicy {
    private val directResultToolNames = WeatherToolHandler.ALL_TOOL_NAMES + setOf(
        ReminderToolHandler.TOOL_SET_REMINDER,
        ReminderToolHandler.TOOL_CANCEL_REMINDER,
        ReminderToolHandler.TOOL_SET_ALARM,
        "add_calendar_event",
        SystemAppToolHandler.TOOL_NAME
    )

    fun isDirectResult(message: Message): Boolean = directResult(message) != null

    fun weatherContent(message: Message): String? = message.toolResults.firstOrNull { result ->
        result.name in WeatherToolHandler.ALL_TOOL_NAMES &&
            extractWeatherDataJson(result.content) != null
    }?.content

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
                    add(message.copy(content = compactDirectResult(message, locale)))
                }
                message.role == MessageRole.ASSISTANT && directResultSeenInTurn -> Unit
                else -> add(message)
            }
        }
    }

    private fun compactDirectResult(message: Message, locale: Locale): String {
        val result = directResult(message) ?: return message.content
        return when (result.name) {
            in WeatherToolHandler.ALL_TOOL_NAMES -> compactWeather(result.content)
            ReminderToolHandler.TOOL_SET_REMINDER -> compactReminder(result.content, locale)
            "add_calendar_event" -> compactCalendarEvent(result.content, locale)
            else -> result.content.firstUsefulLine().limitWords(18)
        }
    }

    private fun directResult(message: Message): ToolResult? = message.toolResults.firstOrNull { result ->
        result.name in directResultToolNames
    }

    private fun compactWeather(content: String): String {
        val lines = content.substringBefore("<!--WEATHER_DATA:")
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toList()
        if (lines.isEmpty()) return ""
        return when {
            lines.first().contains("Pronóstico", ignoreCase = true) && lines.size >= 4 -> {
                val location = lines[0].substringAfter("para", lines[0]).cleanMarkdown()
                val date = lines[1].cleanMarkdown()
                val condition = lines[2].cleanMarkdown()
                "$date en $location: $condition, ${lines[3].cleanMarkdown()}".limitWords(18)
            }
            lines.first().contains("Clima actual", ignoreCase = true) && lines.size >= 3 -> {
                val location = lines[0].substringAfter("en", lines[0]).cleanMarkdown()
                "$location: ${lines[1].cleanMarkdown()}, ${lines[2].cleanMarkdown()}".limitWords(18)
            }
            lines.first().contains("Calidad del aire", ignoreCase = true) && lines.size >= 2 -> {
                val location = lines[0].substringAfter("en", lines[0]).cleanMarkdown()
                "$location: ${lines[1].cleanMarkdown()}".limitWords(18)
            }
            else -> lines.take(2).joinToString(" · ").cleanMarkdown().limitWords(18)
        }
    }

    private fun compactReminder(content: String, locale: Locale): String {
        val scheduled = content.lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("Programado para:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
        if (scheduled.isNullOrBlank()) return content.firstUsefulLine().limitWords(15)
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
        if (title.isNullOrBlank() || start.isNullOrBlank()) return content.firstUsefulLine().limitWords(15)
        return if (locale.language.equals("es", ignoreCase = true)) {
            "Evento «$title» agregado para $start."
        } else {
            "Event “$title” added for $start."
        }
    }

    private fun String.firstUsefulLine(): String = lineSequence()
        .map(String::trim)
        .firstOrNull(String::isNotBlank)
        .orEmpty()

    private fun String.cleanMarkdown(): String = replace("**", "").trim()

    private fun String.limitWords(maxWords: Int): String {
        val words = trim().split(Regex("\\s+")).filter(String::isNotBlank)
        return if (words.size <= maxWords) trim() else words.take(maxWords).joinToString(" ") + "…"
    }
}
