package com.aiagents.app.presentation.subscription

import com.aiagents.app.domain.model.ManagedCapabilitySupport
import com.aiagents.app.domain.model.ManagedInferenceUsage
import com.aiagents.app.domain.model.ManagedModel
import java.text.NumberFormat
import java.util.Locale

internal fun ManagedCapabilitySupport.displayLabel(): String = when (this) {
    ManagedCapabilitySupport.SUPPORTED -> "Sí"
    ManagedCapabilitySupport.UNSUPPORTED -> "No"
    ManagedCapabilitySupport.BEST_EFFORT -> "Mejor esfuerzo"
    ManagedCapabilitySupport.UNKNOWN -> "Desconocido"
}

internal fun ManagedModel.contextLabel(): String = contextWindow?.let {
    "${NumberFormat.getIntegerInstance().format(it)} tokens"
} ?: "Desconocido"

internal fun ManagedModel.priceLabel(): String {
    val input = pricing.inputMicrosPerToken
    val output = pricing.outputMicrosPerToken
    if (input == null || output == null) return "Precio desconocido"
    // One micro-dollar/token is exactly one dollar per one million tokens.
    return "Entrada ${usdPerMillion(input)} · salida ${usdPerMillion(output)} / 1 M tokens"
}

internal fun ManagedInferenceUsage.costLabel(): String = when {
    free -> "Sin coste facturado"
    costMicros == null -> "Coste no informado"
    else -> String.format(Locale.US, "$%.6f", costMicros / 1_000_000.0)
}

private fun usdPerMillion(value: Double): String = String.format(Locale.US, "$%.3f", value)
