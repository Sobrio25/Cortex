package com.aiagents.app.data.speech

/**
 * Internal request skill for Qwen3-TTS.
 *
 * It is intentionally kept out of the conversational skill/memory runtime: voice metadata is
 * transport configuration and must never appear in the assistant's visible answer. The skill is
 * activated by the selected API flavor, or automatically when the model/endpoint identifies a
 * Qwen3-TTS CustomVoice or VoiceDesign checkpoint.
 */
object Qwen3TtsVoiceSkill {
    const val DEFAULT_VOICE_DESCRIPTION =
        "Voz adulta, calida y natural, clara y segura, con acento latinoamericano neutro"

    fun resolveApiFlavor(config: RemoteTtsConfig): RemoteTtsApiFlavor {
        if (config.apiFlavor != RemoteTtsApiFlavor.AUTO) return config.apiFlavor
        val signature = "${config.model} ${config.endpointUrl}".lowercase()
        return when {
            signature.contains("voicedesign") ||
                signature.contains("voice-design") ||
                signature.contains("/speech/design") -> RemoteTtsApiFlavor.QWEN3_VOICE_DESIGN
            signature.contains("qwen3") && signature.contains("tts") ->
                RemoteTtsApiFlavor.QWEN3_CUSTOM_VOICE
            else -> RemoteTtsApiFlavor.OPENAI
        }
    }

    fun isActive(config: RemoteTtsConfig): Boolean =
        resolveApiFlavor(config) == RemoteTtsApiFlavor.QWEN3_CUSTOM_VOICE ||
            resolveApiFlavor(config) == RemoteTtsApiFlavor.QWEN3_VOICE_DESIGN

    fun requiresVoice(config: RemoteTtsConfig): Boolean =
        resolveApiFlavor(config) != RemoteTtsApiFlavor.QWEN3_VOICE_DESIGN

    /** Creates the JSON fields expected by OpenAI-compatible and extended Qwen3-TTS servers. */
    fun requestFields(text: String, config: RemoteTtsConfig): Map<String, String> {
        val cleanText = SpokenTextFormatter.clean(text)
        val fields = linkedMapOf(
            "model" to config.model.trim(),
            "input" to cleanText,
            "response_format" to when (config.audioMode) {
                RemoteTtsAudioMode.BUFFERED_WAV -> "wav"
                RemoteTtsAudioMode.STREAMING_PCM -> "pcm"
            }
        )
        if (config.audioMode == RemoteTtsAudioMode.STREAMING_PCM) {
            fields["stream_format"] = "audio"
        }
        when (resolveApiFlavor(config)) {
            RemoteTtsApiFlavor.QWEN3_CUSTOM_VOICE -> {
                fields["voice"] = config.voice.trim()
                fields["language"] = config.language.normalizedLanguage()
                val instruction = buildInstruction(cleanText, config)
                // Qwen's Python API calls this `instruct`; several OpenAI-compatible wrappers
                // expose the same value as `instructions`. Sending both keeps either adapter
                // functional and unknown JSON fields are ignored by the supported servers.
                fields["instruct"] = instruction
                fields["instructions"] = instruction
            }
            RemoteTtsApiFlavor.QWEN3_VOICE_DESIGN -> {
                fields["language"] = config.language.normalizedLanguage()
                fields["voice_description"] = buildInstruction(cleanText, config)
            }
            RemoteTtsApiFlavor.AUTO,
            RemoteTtsApiFlavor.OPENAI -> fields["voice"] = config.voice.trim()
        }
        return fields
    }

    fun buildInstruction(text: String, config: RemoteTtsConfig): String {
        val identity = config.voiceDescription.trim().ifBlank { DEFAULT_VOICE_DESCRIPTION }
        if (!config.adaptiveStyle) return identity
        return "$identity. ${deliveryFor(text)} Mantener un ritmo natural y una diccion clara."
    }

    private fun deliveryFor(text: String): String {
        val normalized = text.lowercase()
        return when {
            normalized.containsAny(
                "advertencia", "cuidado", "riesgo", "urgente", "error", "fallo", "falló",
                "problema", "no se pudo", "no pude"
            ) -> "Expresarse con seriedad calmada, sin sonar alarmista."
            normalized.containsAny(
                "lo siento", "lamento", "entiendo que", "te preocupa", "puede ser dificil",
                "puede ser difícil"
            ) -> "Usar un tono empatico, suave y cercano."
            '!' in text || normalized.containsAny("excelente", "perfecto", "listo", "genial") ->
                "Usar entusiasmo moderado y amable."
            '?' in text -> "Usar una entonacion atenta y ligeramente inquisitiva."
            normalized.containsAny("primero", "segundo", "paso ", "1. ", "2. ") ->
                "Explicar de forma pausada, ordenada y didactica."
            else -> "Hablar con confianza tranquila y tono conversacional."
        }
    }

    private fun String.containsAny(vararg candidates: String): Boolean =
        candidates.any(::contains)

    private fun String.normalizedLanguage(): String = trim().ifBlank { "Auto" }
}
