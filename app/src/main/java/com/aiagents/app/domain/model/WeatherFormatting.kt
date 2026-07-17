package com.aiagents.app.domain.model

import kotlin.math.roundToInt

/** User-facing temperatures are intentionally whole numbers in cards, text and TTS. */
fun formatTemperatureValue(value: Double): String = value.roundToInt().toString()
