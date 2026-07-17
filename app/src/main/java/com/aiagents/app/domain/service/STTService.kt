package com.aiagents.app.domain.service

import kotlinx.coroutines.flow.StateFlow

interface STTService {
    val isListening: StateFlow<Boolean>
    val transcription: StateFlow<String>

    suspend fun startListening(language: String = "auto")
    suspend fun stopListening()
    suspend fun transcribeAudio(audioData: ByteArray): Result<String>
    fun release()
}

sealed class STTResult {
    data class Success(val text: String) : STTResult()
    data class Error(val message: String, val exception: Exception? = null) : STTResult()
    object Listening : STTResult()
    object Stopped : STTResult()
}

data class STTConfig(
    val mode: STTMode,
    val language: String = "auto",
    val apiKey: String = "",
    val cloudProvider: CloudSTTProvider = CloudSTTProvider.ANDROID_SPEECH_RECOGNIZER,
    val localEngine: LocalSTTEngine = LocalSTTEngine.AUTO,
    val remoteEndpointUrl: String = "",
    val remoteModel: String = "whisper-1"
) {
    enum class STTMode {
        OFF, LOCAL, CLOUD
    }

    enum class CloudSTTProvider {
        ANDROID_SPEECH_RECOGNIZER,
        WHISPER_API,
        SELF_HOSTED,
        GOOGLE_SPEECH,
        ASSEMBLY_AI,
        DEEPGRAM
    }

    enum class LocalSTTEngine {
        AUTO,
        SHERPA_ONNX
    }
}
