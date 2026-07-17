package com.aiagents.app.presentation.voice

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.diagnostics.AppErrorReporter
import com.aiagents.app.data.diagnostics.ErrorReportContext
import com.aiagents.app.data.local.AssistantPreferences
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.local.VoicePreferences
import com.aiagents.app.data.speech.AndroidTextToSpeechManager
import com.aiagents.app.data.speech.AssistantSttMode
import com.aiagents.app.data.speech.AssistantTtsMode
import com.aiagents.app.data.speech.GoogleTtsVoice
import com.aiagents.app.data.speech.OnDemandVoiceFeatureLoader
import com.aiagents.app.data.speech.Qwen3TtsVoiceSkill
import com.aiagents.app.data.speech.RemoteSttConfig
import com.aiagents.app.data.speech.RemoteTtsAudioMode
import com.aiagents.app.data.speech.RemoteTtsConfig
import com.aiagents.app.data.speech.SelfHostedVoiceApi
import com.aiagents.app.data.speech.STTManager
import com.aiagents.app.data.speech.VoiceCatalog
import com.aiagents.app.data.speech.VoiceFeatureInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

data class VoiceAssetUiState(
    val installed: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f
)

@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val voicePreferences: VoicePreferences,
    private val assistantPreferences: AssistantPreferences,
    securePreferences: SecurePreferences,
    private val sttManager: STTManager,
    private val textToSpeech: AndroidTextToSpeechManager,
    private val voiceFeatureInstaller: VoiceFeatureInstaller,
    private val errorReporter: AppErrorReporter
) : ViewModel() {
    private val locale = Locale.forLanguageTag(securePreferences.getAppLanguage())

    val sttMode = voicePreferences.sttMode
    val sttLanguage = voicePreferences.sttLanguage
    val ttsMode = voicePreferences.ttsMode
    val selectedGoogleVoiceId = voicePreferences.googleVoiceId
    val remoteSttConfig = voicePreferences.remoteSttConfig
    val remoteTtsConfig = voicePreferences.remoteTtsConfig
    val googleVoices: StateFlow<List<GoogleTtsVoice>> = textToSpeech.googleVoices
    val voiceFeatureState = voiceFeatureInstaller.state

    private val _voiceAssets = MutableStateFlow(
        VoiceCatalog.downloadableAssets.associate { it.id to VoiceAssetUiState() }
    )
    val voiceAssets: StateFlow<Map<String, VoiceAssetUiState>> = _voiceAssets.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val voicePackPreferences = context.getSharedPreferences(
        "cortex_voice_pack",
        Context.MODE_PRIVATE
    )
    private var pendingAssetAfterModuleInstall: String? =
        voicePackPreferences.getString(PENDING_VOICE_ASSET_KEY, null)
    private var activeDownloadAssetId: String? = null

    init {
        textToSpeech.refreshVoice(locale)
        refreshAssetStates()
        viewModelScope.launch {
            voiceFeatureInstaller.state.collect { state ->
                if (state.installed) {
                    refreshAssetStates()
                    pendingAssetAfterModuleInstall?.let { assetId ->
                        clearPendingVoiceAsset()
                        startAssetDownload(assetId)
                    }
                }
                state.error?.let {
                    pendingAssetAfterModuleInstall?.let { assetId ->
                        updateAsset(assetId) { copy(downloading = false, progress = 0f) }
                    }
                    clearPendingVoiceAsset()
                    _error.value = voiceError(
                        IllegalStateException(it),
                        "voice_feature_install"
                    )
                }
            }
        }
        viewModelScope.launch {
            textToSpeech.error.collect { error ->
                if (error != null) {
                    _error.value = voiceError(IllegalStateException(error), "voice_playback")
                }
            }
        }
    }

    val onDeviceRecognitionAvailable: Boolean
        get() = sttManager.isOnDeviceRecognitionAvailable()

    val systemRecognitionAvailable: Boolean
        get() = sttManager.isSystemRecognitionAvailable()

    fun selectSttMode(mode: AssistantSttMode) {
        if (mode == AssistantSttMode.WHISPER_TINY &&
            !isAssetInstalled(VoiceCatalog.WHISPER_TINY_ID)
        ) {
            _error.value = "Descarga Whisper Tiny antes de seleccionarlo"
            return
        }
        if (mode == AssistantSttMode.REMOTE_SERVER &&
            !SelfHostedVoiceApi.isConfigured(voicePreferences.remoteSttConfig.value)
        ) {
            _error.value = "Configura la URL y el modelo del servidor Whisper"
            return
        }
        if (mode in API_STT_MODES && voicePreferences.getSttApiKey(mode).isBlank()) {
            _error.value = "Guarda la API key antes de seleccionar este proveedor"
            return
        }
        voicePreferences.setSttMode(mode)
    }

    fun setSttLanguage(language: String) = voicePreferences.setSttLanguage(language)

    fun saveCloudSttProvider(mode: AssistantSttMode, apiKey: String) {
        if (mode !in API_STT_MODES || apiKey.isBlank()) {
            _error.value = "Ingresa una API key valida"
            return
        }
        voicePreferences.setSttApiKey(mode, apiKey)
        voicePreferences.setSttMode(mode)
        _error.value = null
    }

    fun getSttApiKey(mode: AssistantSttMode): String = voicePreferences.getSttApiKey(mode)

    fun saveRemoteSttConfig(config: RemoteSttConfig) {
        val error = SelfHostedVoiceApi.endpointValidationError(config.endpointUrl)
        if (error != null || config.model.isBlank()) {
            _error.value = error ?: "Especifica el modelo Whisper del servidor"
            return
        }
        voicePreferences.setRemoteSttConfig(config)
        voicePreferences.setSttMode(AssistantSttMode.REMOTE_SERVER)
        _error.value = null
    }

    fun selectTtsMode(mode: AssistantTtsMode) {
        val assetId = mode.assetId
        if (assetId != null && !isAssetInstalled(assetId)) {
            _error.value = "Descarga esa voz Piper antes de seleccionarla"
            return
        }
        if (mode == AssistantTtsMode.REMOTE_SERVER &&
            !SelfHostedVoiceApi.isConfigured(voicePreferences.remoteTtsConfig.value)
        ) {
            _error.value = "Configura los datos requeridos del servidor TTS"
            return
        }
        voicePreferences.setTtsMode(mode)
        if (mode == AssistantTtsMode.NONE) {
            assistantPreferences.setSpeakResponses(false)
            textToSpeech.stop()
        } else {
            textToSpeech.refreshVoice(locale)
        }
    }

    fun selectGoogleVoice(voiceId: String) {
        if (textToSpeech.selectGoogleVoice(voiceId)) {
            voicePreferences.setTtsMode(AssistantTtsMode.GOOGLE)
        }
    }

    fun previewGoogleVoice(voiceId: String) = textToSpeech.previewGoogleVoice(voiceId)

    fun previewPiperVoice(mode: AssistantTtsMode) {
        if (mode.assetId?.let(::isAssetInstalled) != true) {
            _error.value = "Descarga esa voz antes de probarla"
            return
        }
        textToSpeech.previewPiperVoice(mode)
    }

    fun saveRemoteTtsConfig(config: RemoteTtsConfig, preview: Boolean = false) {
        val error = SelfHostedVoiceApi.endpointValidationError(config.endpointUrl)
        if (error != null || config.model.isBlank() ||
            Qwen3TtsVoiceSkill.requiresVoice(config) && config.voice.isBlank() ||
            config.audioMode == RemoteTtsAudioMode.STREAMING_PCM &&
            config.pcmSampleRate !in 8_000..96_000
        ) {
            _error.value = error ?: "Completa los datos requeridos del servidor TTS"
            return
        }
        voicePreferences.setRemoteTtsConfig(config)
        voicePreferences.setTtsMode(AssistantTtsMode.REMOTE_SERVER)
        _error.value = null
        if (preview) textToSpeech.previewRemoteVoice()
    }

    fun downloadAsset(assetId: String) {
        if (VoiceCatalog.find(assetId) == null || isAssetInstalled(assetId)) return
        if (activeDownloadAssetId?.let { it != assetId } == true ||
            pendingAssetAfterModuleInstall?.let { it != assetId } == true
        ) {
            _error.value = "Espera a que termine la descarga actual"
            return
        }
        if (!voiceFeatureInstaller.state.value.installed) {
            setPendingVoiceAsset(assetId)
            updateAsset(assetId) { copy(downloading = true, progress = 0f) }
            voiceFeatureInstaller.requestInstall()
            return
        }
        startAssetDownload(assetId)
    }

    fun deleteAsset(assetId: String) {
        if (!isAssetInstalled(assetId)) return
        viewModelScope.launch {
            textToSpeech.stop()
            val result = withContext(Dispatchers.IO) {
                OnDemandVoiceFeatureLoader.deleteAsset(context, assetId)
            }
            result.onSuccess {
                if (voicePreferences.sttMode.value.id == assetId) {
                    voicePreferences.setSttMode(AssistantSttMode.ANDROID)
                }
                if (voicePreferences.ttsMode.value.assetId == assetId) {
                    voicePreferences.setTtsMode(AssistantTtsMode.NONE)
                    assistantPreferences.setSpeakResponses(false)
                }
                refreshAssetStates()
            }.onFailure { _error.value = voiceError(it, "voice_model_delete") }
        }
    }

    fun confirmVoiceFeatureInstall(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        if (!voiceFeatureInstaller.requestConfirmation(launcher)) {
            _error.value = "No se pudo abrir la confirmacion de descarga"
        }
    }

    fun createVoicePackInstallPermissionIntent(): Intent? =
        voiceFeatureInstaller.externalInstallPermissionIntent()

    fun resumeVoicePackInstall() = voiceFeatureInstaller.resumeExternalInstall()

    fun createInstallVoiceIntent(): Intent = textToSpeech.createInstallVoiceIntent()

    fun refreshGoogleVoices() = textToSpeech.refreshVoice(locale)

    fun dismissError() {
        _error.value = null
        textToSpeech.dismissError()
    }

    private fun startAssetDownload(assetId: String) {
        if (activeDownloadAssetId != null) return
        activeDownloadAssetId = assetId
        _voiceAssets.value = _voiceAssets.value.mapValues { (id, state) ->
            if (id == assetId) state.copy(downloading = true, progress = 0f) else state
        }
        viewModelScope.launch {
            val result = OnDemandVoiceFeatureLoader.downloadAsset(context, assetId) { progress ->
                updateAsset(assetId) { copy(downloading = true, progress = progress) }
            }
            result.onSuccess {
                updateAsset(assetId) { VoiceAssetUiState(installed = true, progress = 1f) }
            }.onFailure {
                updateAsset(assetId) { copy(downloading = false) }
                _error.value = voiceError(it, "voice_model_download")
            }
            activeDownloadAssetId = null
            refreshAssetStates()
        }
    }

    private fun refreshAssetStates() {
        val featureInstalled = voiceFeatureInstaller.state.value.installed ||
            OnDemandVoiceFeatureLoader.isFeatureAvailable()
        _voiceAssets.value = VoiceCatalog.downloadableAssets.associate { descriptor ->
            val previous = _voiceAssets.value[descriptor.id]
            descriptor.id to if (previous?.downloading == true) {
                previous
            } else {
                VoiceAssetUiState(
                    installed = featureInstalled &&
                        OnDemandVoiceFeatureLoader.isAssetReady(context, descriptor.id)
                )
            }
        }
        textToSpeech.refreshVoice(locale)
    }

    private fun isAssetInstalled(assetId: String): Boolean =
        _voiceAssets.value[assetId]?.installed == true

    private fun updateAsset(assetId: String, update: VoiceAssetUiState.() -> VoiceAssetUiState) {
        _voiceAssets.value = _voiceAssets.value.toMutableMap().apply {
            this[assetId] = (this[assetId] ?: VoiceAssetUiState()).update()
        }
    }

    private fun setPendingVoiceAsset(assetId: String) {
        pendingAssetAfterModuleInstall = assetId
        voicePackPreferences.edit().putString(PENDING_VOICE_ASSET_KEY, assetId).apply()
    }

    private fun clearPendingVoiceAsset() {
        pendingAssetAfterModuleInstall = null
        voicePackPreferences.edit().remove(PENDING_VOICE_ASSET_KEY).apply()
    }

    private fun voiceError(error: Throwable, operation: String): String =
        errorReporter.present(
            error,
            ErrorReportContext(component = "voice_settings", operation = operation)
        ).displayMessage

    private companion object {
        const val PENDING_VOICE_ASSET_KEY = "pending_voice_asset"
        val API_STT_MODES = setOf(
            AssistantSttMode.OPENAI_WHISPER,
            AssistantSttMode.ASSEMBLY_AI,
            AssistantSttMode.DEEPGRAM,
            AssistantSttMode.GOOGLE_CLOUD
        )
    }
}
