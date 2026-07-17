package com.aiagents.app.data.speech

enum class VoiceAssetKind {
    STT_MODEL,
    TTS_VOICE
}

data class VoiceAssetDescriptor(
    val id: String,
    val kind: VoiceAssetKind,
    val title: String,
    val description: String,
    val localeTag: String,
    val downloadBytes: Long,
    val installedBytes: Long
)

/** Metadata only. Model binaries are downloaded by the optional :voice module. */
object VoiceCatalog {
    const val WHISPER_TINY_ID = "whisper_tiny_int8"
    const val PIPER_ALD_ID = "piper_es_mx_ald_int8"
    const val PIPER_CLAUDE_ID = "piper_es_mx_claude_int8"

    val whisperTiny = VoiceAssetDescriptor(
        id = WHISPER_TINY_ID,
        kind = VoiceAssetKind.STT_MODEL,
        title = "Whisper Tiny",
        description = "Transcripcion multilingue sin conexion",
        localeTag = "multi",
        downloadBytes = 116_204_861L,
        installedBytes = 103_609_903L
    )

    val piperAld = VoiceAssetDescriptor(
        id = PIPER_ALD_ID,
        kind = VoiceAssetKind.TTS_VOICE,
        title = "Ald",
        description = "Voz Piper en espanol de Mexico, calidad media",
        localeTag = "es-MX",
        downloadBytes = 21_283_187L,
        installedBytes = 25_000_000L
    )

    val piperClaude = VoiceAssetDescriptor(
        id = PIPER_CLAUDE_ID,
        kind = VoiceAssetKind.TTS_VOICE,
        title = "Claude",
        description = "Voz Piper en espanol de Mexico, calidad alta",
        localeTag = "es-MX",
        downloadBytes = 21_216_685L,
        installedBytes = 25_000_000L
    )

    val downloadableAssets = listOf(whisperTiny, piperAld, piperClaude)

    fun find(id: String): VoiceAssetDescriptor? = downloadableAssets.firstOrNull { it.id == id }
}

enum class AssistantSttMode(val id: String) {
    ANDROID("android"),
    WHISPER_TINY(VoiceCatalog.WHISPER_TINY_ID),
    REMOTE_SERVER("remote_whisper"),
    OPENAI_WHISPER("openai_whisper"),
    ASSEMBLY_AI("assembly_ai"),
    DEEPGRAM("deepgram"),
    GOOGLE_CLOUD("google_cloud");

    companion object {
        fun fromId(id: String?): AssistantSttMode =
            entries.firstOrNull { it.id == id } ?: ANDROID
    }
}

enum class AssistantTtsMode(val id: String, val assetId: String? = null) {
    NONE("none"),
    GOOGLE("google_tts"),
    PIPER_ALD(VoiceCatalog.PIPER_ALD_ID, VoiceCatalog.PIPER_ALD_ID),
    PIPER_CLAUDE(VoiceCatalog.PIPER_CLAUDE_ID, VoiceCatalog.PIPER_CLAUDE_ID),
    REMOTE_SERVER("remote_tts");

    companion object {
        fun fromId(id: String?): AssistantTtsMode = entries.firstOrNull { it.id == id } ?: NONE
    }
}

data class RemoteSttConfig(
    val endpointUrl: String = "",
    val model: String = "whisper-1",
    val apiKey: String = ""
)

data class RemoteTtsConfig(
    val endpointUrl: String = "",
    val model: String = "tts-1",
    val voice: String = "alloy",
    val apiKey: String = "",
    val apiFlavor: RemoteTtsApiFlavor = RemoteTtsApiFlavor.AUTO,
    val language: String = "Auto",
    val voiceDescription: String = Qwen3TtsVoiceSkill.DEFAULT_VOICE_DESCRIPTION,
    val adaptiveStyle: Boolean = true,
    val audioMode: RemoteTtsAudioMode = RemoteTtsAudioMode.BUFFERED_WAV,
    val pcmSampleRate: Int = 24_000
)

enum class RemoteTtsAudioMode(val id: String) {
    BUFFERED_WAV("buffered_wav"),
    STREAMING_PCM("streaming_pcm");

    companion object {
        fun fromId(id: String?): RemoteTtsAudioMode =
            entries.firstOrNull { it.id == id } ?: BUFFERED_WAV
    }
}

/** Wire format used by a self-hosted TTS server. */
enum class RemoteTtsApiFlavor(val id: String) {
    AUTO("auto"),
    OPENAI("openai"),
    QWEN3_CUSTOM_VOICE("qwen3_custom_voice"),
    QWEN3_VOICE_DESIGN("qwen3_voice_design");

    companion object {
        fun fromId(id: String?): RemoteTtsApiFlavor =
            entries.firstOrNull { it.id == id } ?: AUTO
    }
}

data class RemoteTtsAudio(
    val bytes: ByteArray,
    val fileExtension: String
)

data class RemotePcmStreamResult(
    val bytesReceived: Long
)

data class GoogleTtsVoice(
    val id: String,
    val displayName: String,
    val languageTag: String,
    val quality: Int,
    val latency: Int,
    val requiresNetwork: Boolean,
    val installed: Boolean
)

data class OfflineTtsAudio(
    val samples: FloatArray,
    val sampleRate: Int
)
