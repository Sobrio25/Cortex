package com.aiagents.app.data.speech

import android.content.Context
import android.util.Log
import com.aiagents.app.data.local.GlobalSttSettings
import com.aiagents.app.data.local.VoicePreferences
import com.aiagents.app.domain.service.STTConfig
import com.aiagents.app.domain.service.STTService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Owns the single global STT engine shared by chat and the system assistant. */
@Singleton
class STTManager @Inject constructor(
    @ApplicationContext private val context: Context,
    voicePreferences: VoicePreferences,
    private val selfHostedVoiceApi: SelfHostedVoiceApi
) {
    private val _currentService = MutableStateFlow<STTService?>(null)
    val currentService: StateFlow<STTService?> = _currentService.asStateFlow()

    private val _currentConfig = MutableStateFlow<STTConfig?>(null)
    val currentConfig: StateFlow<STTConfig?> = _currentConfig.asStateFlow()

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch {
            voicePreferences.sttSettings.collect { settings ->
                replaceService(settings.toRuntimeConfig())
            }
        }
    }

    @Synchronized
    private fun replaceService(config: STTConfig) {
        if (_currentConfig.value == config && _currentService.value != null) return
        releaseService()
        val service = when (config.mode) {
            STTConfig.STTMode.OFF -> null
            STTConfig.STTMode.CLOUD -> createCloudService(config)
            STTConfig.STTMode.LOCAL -> createLocalService(config)
        }
        _currentService.value = service
        _currentConfig.value = config
        _isEnabled.value = service != null
        Log.d(
            "STTManager",
            "Global STT initialized: ${config.mode} - " +
                "engine=${config.localEngine} provider=${config.cloudProvider}"
        )
    }

    private fun GlobalSttSettings.toRuntimeConfig(): STTConfig {
        val provider = when (mode) {
            AssistantSttMode.ANDROID,
            AssistantSttMode.WHISPER_TINY -> STTConfig.CloudSTTProvider.ANDROID_SPEECH_RECOGNIZER
            AssistantSttMode.REMOTE_SERVER -> STTConfig.CloudSTTProvider.SELF_HOSTED
            AssistantSttMode.OPENAI_WHISPER -> STTConfig.CloudSTTProvider.WHISPER_API
            AssistantSttMode.ASSEMBLY_AI -> STTConfig.CloudSTTProvider.ASSEMBLY_AI
            AssistantSttMode.DEEPGRAM -> STTConfig.CloudSTTProvider.DEEPGRAM
            AssistantSttMode.GOOGLE_CLOUD -> STTConfig.CloudSTTProvider.GOOGLE_SPEECH
        }
        return STTConfig(
            mode = if (mode == AssistantSttMode.ANDROID ||
                mode == AssistantSttMode.WHISPER_TINY
            ) {
                STTConfig.STTMode.LOCAL
            } else {
                STTConfig.STTMode.CLOUD
            },
            language = language,
            apiKey = if (mode == AssistantSttMode.REMOTE_SERVER) remoteConfig.apiKey else apiKey,
            cloudProvider = provider,
            localEngine = if (mode == AssistantSttMode.WHISPER_TINY) {
                STTConfig.LocalSTTEngine.SHERPA_ONNX
            } else {
                STTConfig.LocalSTTEngine.AUTO
            },
            remoteEndpointUrl = remoteConfig.endpointUrl,
            remoteModel = remoteConfig.model
        )
    }

    private fun createCloudService(config: STTConfig): STTService =
        when (config.cloudProvider) {
            STTConfig.CloudSTTProvider.ANDROID_SPEECH_RECOGNIZER ->
                AndroidSpeechRecognizerSTTService(context)
            else -> WhisperCloudSTTService(
                context = context,
                apiKey = config.apiKey,
                provider = config.cloudProvider,
                remoteEndpointUrl = config.remoteEndpointUrl,
                remoteModel = config.remoteModel,
                selfHostedVoiceApi = selfHostedVoiceApi
            )
        }

    private fun createLocalService(config: STTConfig): STTService {
        if (config.localEngine == STTConfig.LocalSTTEngine.SHERPA_ONNX) {
            OnDemandVoiceFeatureLoader.createOfflineService(context, config.language)?.let {
                return it
            }
            Log.w("STTManager", "Whisper is unavailable; using Android recognition")
        }
        return when {
            AndroidSpeechRecognizerSTTService.isOnDeviceRecognitionAvailable(context) ->
                AndroidSpeechRecognizerSTTService(
                    context,
                    onDeviceOnly = true,
                    fallbackToSystemRecognizer = true
                )
            else -> AndroidSpeechRecognizerSTTService(context)
        }
    }

    fun isOnDeviceRecognitionAvailable(): Boolean =
        AndroidSpeechRecognizerSTTService.isOnDeviceRecognitionAvailable(context)

    fun isSystemRecognitionAvailable(): Boolean =
        AndroidSpeechRecognizerSTTService.isSystemRecognitionAvailable(context)

    @Synchronized
    private fun releaseService() {
        _currentService.value?.release()
        _currentService.value = null
    }

    fun release() = releaseService()

    companion object {
        val FREE_CLOUD_PROVIDERS = listOf(
            CloudProviderInfo(
                provider = STTConfig.CloudSTTProvider.ANDROID_SPEECH_RECOGNIZER,
                name = "Google Integrado",
                description = "Gratis, sin API key, sin limites",
                website = "",
                requiresCard = false,
                requiresApiKey = false
            ),
            CloudProviderInfo(
                provider = STTConfig.CloudSTTProvider.ASSEMBLY_AI,
                name = "AssemblyAI",
                description = "100 horas/mes gratis",
                website = "https://www.assemblyai.com/",
                requiresCard = false
            ),
            CloudProviderInfo(
                provider = STTConfig.CloudSTTProvider.DEEPGRAM,
                name = "Deepgram",
                description = "Credito inicial disponible",
                website = "https://deepgram.com/",
                requiresCard = true
            ),
            CloudProviderInfo(
                provider = STTConfig.CloudSTTProvider.GOOGLE_SPEECH,
                name = "Google Cloud STT",
                description = "Reconocimiento administrado en Google Cloud",
                website = "https://cloud.google.com/speech-to-text",
                requiresCard = true
            ),
            CloudProviderInfo(
                provider = STTConfig.CloudSTTProvider.WHISPER_API,
                name = "OpenAI Whisper",
                description = "Transcripcion mediante API",
                website = "https://platform.openai.com/",
                requiresCard = true
            )
        )
    }
}

data class CloudProviderInfo(
    val provider: STTConfig.CloudSTTProvider,
    val name: String,
    val description: String,
    val website: String,
    val requiresCard: Boolean,
    val requiresApiKey: Boolean = true
)
