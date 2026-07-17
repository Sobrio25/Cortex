package com.aiagents.app.presentation.assistant

import com.aiagents.app.domain.model.Message
import com.aiagents.app.presentation.tool_results.DirectToolResultPayload
import com.aiagents.app.presentation.tool_results.DirectToolResultPolicy
import java.util.Locale

/** Assistant-specific facade over the shared direct-result policy. */
object CortexAssistantResponsePolicy {
    fun isDirectResult(message: Message): Boolean = DirectToolResultPolicy.isDirectResult(message)

    fun weatherContent(message: Message): String? = DirectToolResultPolicy.weatherContent(message)

    fun payload(message: Message, locale: Locale): DirectToolResultPayload? =
        DirectToolResultPolicy.primaryPayload(message, locale)

    fun payloads(message: Message, locale: Locale): List<DirectToolResultPayload> =
        DirectToolResultPolicy.payloads(message, locale)

    /** Text for TTS is independent from the compact text shown inside native result cards. */
    fun spokenResponse(message: Message, locale: Locale): String {
        val directPayloads = payloads(message, locale)
        if (directPayloads.isEmpty()) return message.content
        return directPayloads.joinToString(" ") { payload ->
            when (payload.kind) {
                DirectToolResultPayload.Kind.WEATHER -> payload.weatherJson
                    ?.let { AssistantWeatherSpeechFormatter.format(it, locale) }
                    ?: payload.summary
                else -> payload.summary
            }
        }.ifBlank { message.content }
    }

    fun prepareVisible(messages: List<Message>, locale: Locale): List<Message> =
        DirectToolResultPolicy.prepareVisible(messages, locale)
}
