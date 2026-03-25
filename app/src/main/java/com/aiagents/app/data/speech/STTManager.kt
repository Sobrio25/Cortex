package com.aiagents.app.data.speech

import android.content.Context
import android.util.Log
import com.aiagents.app.data.model.CloudSTTProvider
import java.io.File
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
    @ApplicationContext private val context: Context
) {
    private val _currentService = MutableStateFlow<STTService?>(null)
    val currentService: StateFlow<STTService?> = _currentService.asStateFlow()

    private val _currentConfig = MutableStateFlow<STTConfig?>(null)
    val currentConfig: StateFlow<STTConfig?> = _currentConfig.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    fun initializeFromSettings(settings: STTSettingsEntity?) {
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

        val config = STTConfig(
            mode = mode,
            language = settings.language,
            apiKey = settings.apiKey,
            cloudProvider = cloudProvider,
            localEngine = localEngine
        )

        initializeService(config)
    }

    /**
     * Maps CloudSTTProvider enum names from the data model to the domain STTConfig enum names.
     */
    private fun mapCloudProvider(providerName: String): String {
        return when (providerName) {
            "ANDROID_SPEECH_RECOGNIZER" -> "ANDROID_SPEECH_RECOGNIZER"
            "WHISPER_API" -> "WHISPER_API"
            "GOOGLE_FREE" -> "GOOGLE_SPEECH"
            "ASSEMBLY_AI" -> "ASSEMBLY_AI"
            "DEEPGRAM" -> "DEEPGRAM"
            else -> "ANDROID_SPEECH_RECOGNIZER"
        }
    }

    fun initializeService(config: STTConfig) {
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
                WhisperCloudSTTService(context, config.apiKey, config.cloudProvider)
        }
    }

    private fun createLocalService(config: STTConfig): STTService {
        val modelInfo = ModelDownloader.getVoskModelInfo(config.voskModelId)
        val dirName = modelInfo?.dirName ?: "vosk-model-small-es"
        val modelPath = VoskSTTService.getModelPath(context, dirName)
        return VoskSTTService(context, modelPath)
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
