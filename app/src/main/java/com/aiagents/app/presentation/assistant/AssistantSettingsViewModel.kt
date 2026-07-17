package com.aiagents.app.presentation.assistant

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.diagnostics.AppErrorReporter
import com.aiagents.app.data.diagnostics.ErrorReportContext
import com.aiagents.app.data.identity.AssistantIdentityManager
import com.aiagents.app.data.local.AssistantPreferences
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.memory.CortexProfileStore
import com.aiagents.app.data.repository.AgentRepository
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
import com.aiagents.app.domain.model.isOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
class AssistantSettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: AssistantPreferences,
    private val securePreferences: SecurePreferences,
    private val repository: AgentRepository,
    private val identityManager: AssistantIdentityManager,
    private val cortexProfileStore: CortexProfileStore,
    private val sttManager: STTManager,
    private val textToSpeech: AndroidTextToSpeechManager,
    private val voiceFeatureInstaller: VoiceFeatureInstaller,
    private val errorReporter: AppErrorReporter
) : ViewModel() {
    private val assistantLanguageTag = CortexAssistantPrompt.normalizeLanguageTag(
        securePreferences.getAppLanguage()
    )
    private val assistantLocale = Locale.forLanguageTag(assistantLanguageTag)

    val autoListen = preferences.autoListen
    val speakResponses = preferences.speakResponses
    val assistantModel = preferences.modelKey
    val sttMode = preferences.sttMode
    val ttsMode = preferences.ttsMode
    val selectedGoogleVoiceId = preferences.googleVoiceId
    val remoteSttConfig = preferences.remoteSttConfig
    val remoteTtsConfig = preferences.remoteTtsConfig
    val assistantSoul = cortexProfileStore.assistantSoulSnapshots
    val googleVoices: StateFlow<List<GoogleTtsVoice>> = textToSpeech.googleVoices
    val voiceFeatureState = voiceFeatureInstaller.state

    val assistantName: StateFlow<String> = repository.getAllAgents()
        .map { agents ->
            agents.firstOrNull { it.isOrchestrator }?.name ?: identityManager.configuredName()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, identityManager.configuredName())

    private val _isSavingName = MutableStateFlow(false)
    val isSavingName: StateFlow<Boolean> = _isSavingName.asStateFlow()

    private val _voiceAssets = MutableStateFlow(
        VoiceCatalog.downloadableAssets.associate { descriptor ->
            descriptor.id to VoiceAssetUiState()
        }
    )
    val voiceAssets: StateFlow<Map<String, VoiceAssetUiState>> = _voiceAssets.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _availableModels = MutableStateFlow(repository.getSelectedModels().sorted())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val voicePackPreferences = context.getSharedPreferences(
        "cortex_voice_pack",
        Context.MODE_PRIVATE
    )
    private var pendingAssetAfterModuleInstall: String? =
        voicePackPreferences.getString(PENDING_VOICE_ASSET_KEY, null)
    private var activeDownloadAssetId: String? = null

    init {
        textToSpeech.refreshVoice(assistantLocale)
        refreshAssetStates()

        viewModelScope.launch {
            repository.selectedModelsFlow.collect { selected ->
                val models = selected.sorted()
                _availableModels.value = models
                if (preferences.modelKey.value.isNotBlank() && preferences.modelKey.value !in models) {
                    preferences.setModel("")
                }
            }
        }
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
                    _error.value = assistantError(
                        IllegalStateException(it),
                        "voice_feature_install"
                    )
                }
            }
        }
        viewModelScope.launch {
            textToSpeech.error.collect { managerError ->
                if (managerError != null) {
                    _error.value = assistantError(
                        IllegalStateException(managerError),
                        "voice_playback"
                    )
                }
            }
        }
    }

    val onDeviceRecognitionAvailable: Boolean
        get() = sttManager.isOnDeviceRecognitionAvailable()

    val systemRecognitionAvailable: Boolean
        get() = sttManager.isSystemRecognitionAvailable()

    fun setAutoListen(enabled: Boolean) = preferences.setAutoListen(enabled)

    fun setSpeakResponses(enabled: Boolean) {
        preferences.setSpeakResponses(enabled)
        if (!enabled) textToSpeech.stop()
    }

    fun setAssistantModel(modelKey: String) = preferences.setModel(modelKey)

    fun saveAssistantName(name: String) {
        if (_isSavingName.value) return
        viewModelScope.launch {
            _isSavingName.value = true
            _error.value = null
            identityManager.rename(name).onFailure {
                _error.value = assistantError(it, "assistant_rename")
            }
            _isSavingName.value = false
        }
    }

    fun saveAssistantSoul(markdown: String, expectedRevision: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = cortexProfileStore.replaceAssistantSoul(markdown, expectedRevision)
            if (!result.success) {
                _error.value = assistantError(
                    IllegalStateException(result.message ?: "Assistant profile update failed"),
                    "assistant_profile_save"
                )
            }
        }
    }

    fun selectSttMode(mode: AssistantSttMode) {
        if (mode == AssistantSttMode.WHISPER_TINY && !isAssetInstalled(VoiceCatalog.WHISPER_TINY_ID)) {
            _error.value = "Descarga Whisper Tiny antes de seleccionarlo"
            return
        }
        if (mode == AssistantSttMode.REMOTE_SERVER &&
            !SelfHostedVoiceApi.isConfigured(preferences.remoteSttConfig.value)
        ) {
            _error.value = "Configura la URL y el modelo del servidor Whisper"
            return
        }
        preferences.setSttMode(mode)
        if (mode == AssistantSttMode.NONE) preferences.setAutoListen(false)
    }

    fun selectTtsMode(mode: AssistantTtsMode) {
        val assetId = mode.assetId
        if (assetId != null && !isAssetInstalled(assetId)) {
            _error.value = "Descarga esa voz Piper antes de seleccionarla"
            return
        }
        if (mode == AssistantTtsMode.REMOTE_SERVER &&
            !SelfHostedVoiceApi.isConfigured(preferences.remoteTtsConfig.value)
        ) {
            _error.value = "Configura los datos requeridos del servidor TTS"
            return
        }
        preferences.setTtsMode(mode)
        if (mode == AssistantTtsMode.NONE) {
            preferences.setSpeakResponses(false)
            textToSpeech.stop()
        } else {
            preferences.setSpeakResponses(true)
            textToSpeech.refreshVoice(assistantLocale)
        }
    }

    fun selectGoogleVoice(voiceId: String) {
        if (textToSpeech.selectGoogleVoice(voiceId)) {
            preferences.setTtsMode(AssistantTtsMode.GOOGLE)
            preferences.setSpeakResponses(true)
        }
    }

    fun previewGoogleVoice(voiceId: String) = textToSpeech.previewGoogleVoice(voiceId)

    fun previewPiperVoice(mode: AssistantTtsMode) {
        val assetId = mode.assetId ?: return
        if (!isAssetInstalled(assetId)) {
            _error.value = "Descarga esa voz antes de probarla"
            return
        }
        textToSpeech.previewPiperVoice(mode)
    }

    fun saveRemoteSttConfig(config: RemoteSttConfig) {
        val error = SelfHostedVoiceApi.endpointValidationError(config.endpointUrl)
        if (error != null || config.model.isBlank()) {
            _error.value = error ?: "Especifica el modelo Whisper del servidor"
            return
        }
        preferences.setRemoteSttConfig(config)
        preferences.setSttMode(AssistantSttMode.REMOTE_SERVER)
        _error.value = null
    }

    fun saveRemoteTtsConfig(config: RemoteTtsConfig, preview: Boolean = false) {
        val error = SelfHostedVoiceApi.endpointValidationError(config.endpointUrl)
        if (error != null ||
            config.model.isBlank() ||
            Qwen3TtsVoiceSkill.requiresVoice(config) && config.voice.isBlank() ||
            config.audioMode == RemoteTtsAudioMode.STREAMING_PCM &&
            config.pcmSampleRate !in 8_000..96_000
        ) {
            _error.value = error ?: if (config.audioMode == RemoteTtsAudioMode.STREAMING_PCM &&
                config.pcmSampleRate !in 8_000..96_000
            ) {
                "La frecuencia PCM debe estar entre 8000 y 96000 Hz"
            } else {
                "Completa los datos requeridos del servidor TTS"
            }
            return
        }
        preferences.setRemoteTtsConfig(config)
        preferences.setTtsMode(AssistantTtsMode.REMOTE_SERVER)
        preferences.setSpeakResponses(true)
        _error.value = null
        if (preview) textToSpeech.previewRemoteVoice()
    }

    fun downloadAsset(assetId: String) {
        if (VoiceCatalog.find(assetId) == null) return
        if (isAssetInstalled(assetId)) return
        val anotherDownloadActive = activeDownloadAssetId?.let { it != assetId } == true
        val anotherModuleDownloadPending = pendingAssetAfterModuleInstall?.let { it != assetId } == true
        if (anotherDownloadActive || anotherModuleDownloadPending) {
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
                if (preferences.sttMode.value.id == assetId) {
                    preferences.setSttMode(AssistantSttMode.NONE)
                    preferences.setAutoListen(false)
                }
                if (preferences.ttsMode.value.assetId == assetId) {
                    preferences.setTtsMode(AssistantTtsMode.NONE)
                    preferences.setSpeakResponses(false)
                }
                refreshAssetStates()
            }.onFailure {
                _error.value = assistantError(it, "voice_model_delete")
            }
        }
    }

    fun confirmVoiceFeatureInstall(
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        if (!voiceFeatureInstaller.requestConfirmation(launcher)) {
            _error.value = "No se pudo abrir la confirmacion de descarga"
        }
    }

    fun createVoicePackInstallPermissionIntent(): Intent? =
        voiceFeatureInstaller.externalInstallPermissionIntent()

    fun resumeVoicePackInstall() {
        voiceFeatureInstaller.resumeExternalInstall()
    }

    fun createInstallVoiceIntent(): Intent = textToSpeech.createInstallVoiceIntent()

    fun refreshGoogleVoices() = textToSpeech.refreshVoice(assistantLocale)

    fun dismissError() {
        _error.value = null
        textToSpeech.dismissError()
    }

    private fun startAssetDownload(assetId: String) {
        if (activeDownloadAssetId != null) return
        activeDownloadAssetId = assetId
        _voiceAssets.value = _voiceAssets.value.mapValues { (id, state) ->
            when {
                id == assetId -> state.copy(downloading = true, progress = 0f)
                state.downloading && !state.installed -> state.copy(
                    downloading = false,
                    progress = 0f
                )
                else -> state
            }
        }
        viewModelScope.launch {
            val result = OnDemandVoiceFeatureLoader.downloadAsset(context, assetId) { progress ->
                updateAsset(assetId) { copy(downloading = true, progress = progress) }
            }
            result.onSuccess {
                updateAsset(assetId) { VoiceAssetUiState(installed = true, progress = 1f) }
            }.onFailure {
                updateAsset(assetId) { copy(downloading = false) }
                _error.value = assistantError(it, "voice_model_download")
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
        textToSpeech.refreshVoice(assistantLocale)
    }

    private fun assistantError(error: Throwable, operation: String): String =
        errorReporter.present(
            error,
            ErrorReportContext(component = "assistant_settings", operation = operation)
        ).displayMessage

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

    private companion object {
        const val PENDING_VOICE_ASSET_KEY = "pending_voice_asset"
    }
}
