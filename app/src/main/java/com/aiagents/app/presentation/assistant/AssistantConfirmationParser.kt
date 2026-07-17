package com.aiagents.app.presentation.assistant

import java.text.Normalizer

enum class AssistantConfirmationDecision { CONFIRM, CANCEL, UNKNOWN }

object AssistantConfirmationParser {
    private val confirmations = setOf(
        "si", "si envia", "si envialo", "envia", "envialo", "mandalo", "confirmar",
        "yes", "send", "send it"
    )
    private val cancellations = setOf(
        "no", "no lo envies", "cancela", "cancelar", "olvidalo", "cancel", "dont send",
        "do not send"
    )

    fun parse(text: String): AssistantConfirmationDecision {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return when (normalized) {
            in confirmations -> AssistantConfirmationDecision.CONFIRM
            in cancellations -> AssistantConfirmationDecision.CANCEL
            else -> AssistantConfirmationDecision.UNKNOWN
        }
    }
}
