package com.aiagents.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stt_settings")
data class STTSettingsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val workspaceId: Int,
    val enabled: Boolean = false,
    val mode: String = STTMode.LOCAL.name,
    val localModelType: String = LocalModelType.AUTO.name,
    val localEngine: String = LocalSTTEngine.AUTO.name,
    val cloudProvider: String = CloudSTTProvider.ANDROID_SPEECH_RECOGNIZER.name,
    val apiKey: String = "",
    val language: String = "es",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class STTMode {
    OFF,
    LOCAL,
    CLOUD
}

enum class LocalModelType {
    AUTO,
    SNAPDRAGON_NPU,
    CPU_TINY,
    CPU_BASE,
    CPU_SMALL
}

enum class LocalSTTEngine {
    AUTO,
    VOSK
}

enum class CloudSTTProvider {
    ANDROID_SPEECH_RECOGNIZER,  // Google integrado - gratis, sin API key
    WHISPER_API,                // OpenAI Whisper API
    FASTER_WHISPER,             // Self-hosted
    GOOGLE_FREE,                // Google Speech Recognition (API key)
    ASSEMBLY_AI,                // AssemblyAI
    DEEPGRAM                    // Deepgram
}
