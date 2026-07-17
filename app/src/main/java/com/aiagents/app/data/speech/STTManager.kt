package com.aiagents.app.data.speech

import android.content.Context
import android.util.Log
import com.aiagents.app.data.local.AssistantPreferences
import com.aiagents.app.data.model.CloudSTTProvider
import com.aiagents.app.data.model.STTMode
import com.aiagents.app.data.model.STTSettingsEntity
import com.aiagents.app.domain.service.STTConfig
import com.aiagents.app.domain.service.STTService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class STTManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assistantPreferences: AssistantPreferences,
    private val selfHostedVoiceApi: SelfHostedVoiceApi
) {
    private val _currentService = MutableStateFlow<STTService?>(null)
    val currentService: StateFlow<STTService?> = _currentService.asStateFlow()

    private val _currentConfig = MutableStateFlow<STTConfig?>(null)
    val currentConfig: StateFlow<STTConfig?> = _currentConfig.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    @Volatile
    private var assistantSessionActive = false
    private var lastWorkspaceSettings: STTSettingsEntity? = null

    @Synchronized
    fun initializeFromSettings(settings: STTSettingsEntity?) {
        lastWorkspaceSettings = settings
        if (assistantSessionActive) {
            Log.d("STTManager", "Workspace STT update deferred while assistant owns the microphone")
            return
        }
        initializeFromSettingsInternal(settings)
    }

    @Synchronized
    fun beginAssistantSession(settings: STTSettingsEntity) {
        assistantSessionActive = true
        initializeFromSettingsInternal(settings)
        Log.d("STTManager", "Assistant acquired exclusive STT session")
    }

    @Synchronized
    fun endAssistantSession() {
        if (!assistantSessionActive) return
        assistantSessionActive = false
        initializeFromSettingsInternal(lastWorkspaceSettings)
        Log.d("STTManager", "Assistant released STT session")
    }

    private fun initializeFromSettingsInternal(settings: STTSettingsEntity?) {
        if (settings == null) {
            _isEnabled.value = false
            releaseService()
            return
        }

        _isEnabled.value = settings.enabled

        if (!settings.enabled) {
            releaseService()
            return
        }

        val cloudProvider = try {
            STTConfig.CloudSTTProvider.valueOf(
                mapCloudProvider(settings.cloudProvider)
            )
        } catch (e: Exception) {
            STTConfig.CloudSTTProvider.ANDROID_SPEECH_RECOGNIZER
        }

        val localEngine = try {
            STTConfig.LocalSTTEngine.valueOf(settings.localEngine)
        } catch (e: Exception) {
            STTConfig.LocalSTTEngine.AUTO
        }

        val mode = try {
            STTConfig.STTMode.valueOf(settings.mode)
        } catch (e: Exception) {
            STTConfig.STTMode.LOCAL
        }

        val remoteConfig = assistantPreferences.remoteSttConfig.value
        val config = STTConfig(
            mode = mode,
            language = settings.language,
            apiKey = if (cloudProvider == STTConfig.CloudSTTProvider.SELF_HOSTED) {
                remoteConfig.apiKey
            } else {
                settings.apiKey
            },
            cloudProvider = cloudProvider,
            localEngine = localEngine,
            remoteEndpointUrl = remoteConfig.endpointUrl,
            remoteModel = remoteConfig.model
        )

        replaceService(config)
    }

    /**
     * Maps CloudSTTProvider enum names from the data model to the domain STTConfig enum names.
     */
    private fun mapCloudProvider(providerName: String): String {
        return when (providerName) {
            "ANDROID_SPEECH_RECOGNIZER" -> "ANDROID_SPEECH_RECOGNIZER"
            "WHISPER_API" -> "WHISPER_API"
            "SELF_HOSTED", "FASTER_WHISPER" -> "SELF_HOSTED"
            "GOOGLE_FREE" -> "GOOGLE_SPEECH"
            "ASSEMBLY_AI" -> "ASSEMBLY_AI"
            "DEEPGRAM" -> "DEEPGRAM"
            else -> "ANDROID_SPEECH_RECOGNIZER"
        }
    }

    @Synchronized
    fun initializeService(config: STTConfig) {
        if (assistantSessionActive) {
            Log.d("STTManager", "Direct workspace STT update deferred while assistant is active")
            return
        }
        replaceService(config)
    }

    private fun replaceService(config: STTConfig) {
        releaseService()

        val service = when (config.mode) {
            STTConfig.STTMode.OFF -> null
            STTConfig.STTMode.CLOUD -> createCloudService(config)
            STTConfig.STTMode.LOCAL -> createLocalService(config)
        }

        _currentService.value = service
        _currentConfig.value = config
        _isEnabled.value = config.mode != STTConfig.STTMode.OFF

        Log.d("STTManager", "Servicio inicializado: ${config.mode} - engine=${config.localEngine} provider=${config.cloudProvider}")
    }

    private fun createCloudService(config: STTConfig): STTService {
        return when (config.cloudProvider) {
            STTConfig.CloudSTTProvider.ANDROID_SPEECH_RECOGNIZER ->
                AndroidSpeechRecognizerSTTService(context)
            else ->
                WhisperCloudSTTService(
                    context = context,
                    apiKey = config.apiKey,
                    provider = config.cloudProvider,
                    remoteEndpointUrl = config.remoteEndpointUrl,
                    remoteModel = config.remoteModel,
                    selfHostedVoiceApi = selfHostedVoiceApi
                )
        }
    }

    private fun createLocalService(config: STTConfig): STTService {
        if (config.localEngine == STTConfig.LocalSTTEngine.SHERPA_ONNX) {
            OnDemandVoiceFeatureLoader.createOfflineService(context, config.language)?.let { service ->
                return service
            }
            Log.w("STTManager", "Offline STT was selected but its feature or model is unavailable")
        }

        return when {
            AndroidSpeechRecognizerSTTService.isOnDeviceRecognitionAvailable(context) ->
                AndroidSpeechRecognizerSTTService(
                    context,
                    onDeviceOnly = true,
                    fallbackToSystemRecognizer = true
                )
            AndroidSpeechRecognizerSTTService.isSystemRecognitionAvailable(context) ->
                AndroidSpeechRecognizerSTTService(context)
            else -> AndroidSpeechRecognizerSTTService(context)
        }
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        if (!enabled) {
            releaseService()
        }
    }

    fun getDefaultConfig(): STTConfig {
        return STTConfig(
            mode = STTConfig.STTMode.LOCAL,
            language = "es",
            apiKey = "",
            cloudProvider = STTConfig.CloudSTTProvider.ANDROID_SPEECH_RECOGNIZER,
            localEngine = STTConfig.LocalSTTEngine.AUTO
        )
    }

    fun isOnDeviceRecognitionAvailable(): Boolean =
        AndroidSpeechRecognizerSTTService.isOnDeviceRecognitionAvailable(context)

    fun isSystemRecognitionAvailable(): Boolean =
        AndroidSpeechRecognizerSTTService.isSystemRecognitionAvailable(context)

    private fun releaseService() {
        _currentService.value?.release()
        _currentService.value = null
    }

    fun release() {
        releaseService()
    }

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
                requiresCard = false,
                requiresApiKey = true
            ),
            CloudProviderInfo(
                provider = STTConfig.CloudSTTProvider.DEEPGRAM,
                name = "Deepgram",
                description = "$200 creditos iniciales (~45h)",
                website = "https://deepgram.com/",
                requiresCard = true,
                requiresApiKey = true
            ),
            CloudProviderInfo(
                provider = STTConfig.CloudSTTProvider.GOOGLE_SPEECH,
                name = "Google Cloud STT",
                description = "60 minutos/mes gratis",
                website = "https://cloud.google.com/speech-to-text",
                requiresCard = true,
                requiresApiKey = true
            ),
            CloudProviderInfo(
                provider = STTConfig.CloudSTTProvider.WHISPER_API,
                name = "OpenAI Whisper",
                description = "$5 creditos iniciales",
                website = "https://platform.openai.com/",
                requiresCard = true,
                requiresApiKey = true
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
