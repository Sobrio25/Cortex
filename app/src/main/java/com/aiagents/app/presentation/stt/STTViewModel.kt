package com.aiagents.app.presentation.stt

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.diagnostics.AppErrorReporter
import com.aiagents.app.data.diagnostics.ErrorReportContext
import com.aiagents.app.data.model.CloudSTTProvider
import com.aiagents.app.data.model.STTMode
import com.aiagents.app.data.model.STTSettingsEntity
import com.aiagents.app.data.speech.OnDemandVoiceFeatureLoader
import com.aiagents.app.data.speech.Qwen3TtsVoiceSkill
import com.aiagents.app.data.speech.STTManager
import com.aiagents.app.data.speech.VoiceFeatureInstaller
import com.aiagents.app.data.local.STTSettingsDao
import com.aiagents.app.data.local.AssistantPreferences
import com.aiagents.app.data.speech.AssistantSttMode
import com.aiagents.app.data.speech.AssistantTtsMode
import com.aiagents.app.data.speech.RemoteSttConfig
import com.aiagents.app.data.speech.RemoteTtsAudioMode
import com.aiagents.app.data.speech.RemoteTtsConfig
import com.aiagents.app.data.speech.SelfHostedVoiceApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class STTViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sttManager: STTManager,
    private val sttSettingsDao: STTSettingsDao,
    private val voiceFeatureInstaller: VoiceFeatureInstaller,
    private val assistantPreferences: AssistantPreferences,
    private val errorReporter: AppErrorReporter
) : ViewModel() {

    private val _currentSettings = MutableStateFlow<STTSettingsEntity?>(null)
    val currentSettings: StateFlow<STTSettingsEntity?> = _currentSettings.asStateFlow()

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _pendingTranscription = MutableStateFlow<String?>(null)
    val pendingTranscription: StateFlow<String?> = _pendingTranscription.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    val voiceFeatureState = voiceFeatureInstaller.state
    private val _isOfflineModelReady = MutableStateFlow(
        OnDemandVoiceFeatureLoader.isInstalledAndReady(context)
    )
    val isOfflineModelReady: StateFlow<Boolean> = _isOfflineModelReady.asStateFlow()

    // Tracks the active listening coroutine so it can be cancelled on stop
    private var listeningJob: Job? = null

    val isSTTEnabled: StateFlow<Boolean> = sttManager.isEnabled

    val sttService = sttManager.currentService
    val remoteSttConfig = assistantPreferences.remoteSttConfig
    val remoteTtsConfig = assistantPreferences.remoteTtsConfig
    val ttsMode = assistantPreferences.ttsMode

    init {
        viewModelScope.launch {
            voiceFeatureInstaller.state.collect { state ->
                if (state.installed) {
                    _isOfflineModelReady.value = OnDemandVoiceFeatureLoader.isInstalledAndReady(context)
                }
                state.error?.let { message ->
                    _error.value = voiceError(
                        IllegalStateException(message),
                        "voice_feature_install"
                    )
                }
            }
        }
    }

    fun loadSettingsForWorkspace(workspaceId: Long) {
        viewModelScope.launch {
            sttSettingsDao.observeSettingsForWorkspace(workspaceId.toInt())
                .collect { settings ->
                    _currentSettings.value = settings
                    sttManager.initializeFromSettings(settings)
                }
        }
    }

    /** Prepares the hidden global workspace with the voice engine selected for the assistant. */
    fun prepareAssistantVoice(workspaceId: Long, language: String) {
        viewModelScope.launch {
            val existing = sttSettingsDao.getSettingsForWorkspace(workspaceId.toInt())
            val selectedMode = assistantPreferences.sttMode.value
            val localEngine = when (selectedMode) {
                AssistantSttMode.WHISPER_TINY ->
                    com.aiagents.app.data.model.LocalSTTEngine.SHERPA_ONNX
                AssistantSttMode.REMOTE_SERVER,
                AssistantSttMode.ANDROID,
                AssistantSttMode.NONE -> com.aiagents.app.data.model.LocalSTTEngine.AUTO
            }
            val settings = (existing ?: STTSettingsEntity(workspaceId = workspaceId.toInt())).copy(
                enabled = selectedMode != AssistantSttMode.NONE,
                mode = if (selectedMode == AssistantSttMode.REMOTE_SERVER) {
                    STTMode.CLOUD.name
                } else {
                    STTMode.LOCAL.name
                },
                localEngine = localEngine.name,
                cloudProvider = if (selectedMode == AssistantSttMode.REMOTE_SERVER) {
                    CloudSTTProvider.SELF_HOSTED.name
                } else {
                    CloudSTTProvider.ANDROID_SPEECH_RECOGNIZER.name
                },
                apiKey = "",
                language = language,
                updatedAt = System.currentTimeMillis()
            )
            sttSettingsDao.insertSettings(settings)
            sttManager.beginAssistantSession(settings)
            _currentSettings.value = settings
        }
    }

    fun endAssistantVoiceSession() {
        sttManager.endAssistantSession()
    }

    fun toggleSTTEnabled(workspaceId: Long, enabled: Boolean) {
        viewModelScope.launch {
            val current = _currentSettings.value
            if (current != null) {
                val updated = current.copy(
                    enabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
                sttSettingsDao.updateSettings(updated)
            } else {
                // Create settings with enabled flag
                val entity = STTSettingsEntity(
                    workspaceId = workspaceId.toInt(),
                    enabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
                sttSettingsDao.insertSettings(entity)
            }
            sttManager.setEnabled(enabled)
        }
    }

    fun saveSettings(workspaceId: Long, uiState: STTSettingsUiState): Boolean {
        voiceSettingsValidationError(uiState)?.let { error ->
            _error.value = error
            return false
        }

        viewModelScope.launch {
            if (uiState.mode == STTMode.CLOUD &&
                uiState.cloudProvider == CloudSTTProvider.SELF_HOSTED
            ) {
                assistantPreferences.setRemoteSttConfig(
                    RemoteSttConfig(
                        endpointUrl = uiState.remoteSttEndpoint,
                        model = uiState.remoteSttModel,
                        apiKey = uiState.remoteSttApiKey
                    )
                )
            }

            if (uiState.useRemoteTts) {
                assistantPreferences.setRemoteTtsConfig(
                    RemoteTtsConfig(
                        endpointUrl = uiState.remoteTtsEndpoint,
                        model = uiState.remoteTtsModel,
                        voice = uiState.remoteTtsVoice,
                        apiKey = uiState.remoteTtsApiKey,
                        apiFlavor = uiState.remoteTtsApiFlavor,
                        language = uiState.remoteTtsLanguage,
                        voiceDescription = uiState.remoteTtsVoiceDescription,
                        adaptiveStyle = uiState.remoteTtsAdaptiveStyle,
                        audioMode = uiState.remoteTtsAudioMode,
                        pcmSampleRate = uiState.remoteTtsPcmSampleRate
                    )
                )
                assistantPreferences.setTtsMode(AssistantTtsMode.REMOTE_SERVER)
                assistantPreferences.setSpeakResponses(true)
            } else if (assistantPreferences.ttsMode.value == AssistantTtsMode.REMOTE_SERVER) {
                assistantPreferences.setTtsMode(AssistantTtsMode.NONE)
                assistantPreferences.setSpeakResponses(false)
            }

            val entity = STTSettingsEntity(
                id = _currentSettings.value?.id ?: 0,
                workspaceId = workspaceId.toInt(),
                enabled = _currentSettings.value?.enabled ?: false,
                mode = uiState.mode.name,
                cloudProvider = uiState.cloudProvider.name,
                apiKey = if (uiState.cloudProvider == CloudSTTProvider.SELF_HOSTED) {
                    ""
                } else {
                    uiState.apiKey
                },
                localModelType = uiState.localModelType.name,
                localEngine = uiState.localEngine.name,
                language = uiState.language,
                updatedAt = System.currentTimeMillis()
            )
            sttSettingsDao.insertSettings(entity)
            _currentSettings.value = entity
            sttManager.initializeFromSettings(entity)
        }
        return true
    }

    private fun voiceSettingsValidationError(uiState: STTSettingsUiState): String? {
        if (uiState.mode == STTMode.CLOUD &&
            uiState.cloudProvider == CloudSTTProvider.SELF_HOSTED
        ) {
            SelfHostedVoiceApi.endpointValidationError(uiState.remoteSttEndpoint)?.let {
                return "Servidor Whisper: $it"
            }
            if (uiState.remoteSttModel.isBlank()) {
                return "Especifica el modelo Whisper del servidor"
            }
        }
        if (uiState.useRemoteTts) {
            SelfHostedVoiceApi.endpointValidationError(uiState.remoteTtsEndpoint)?.let {
                return "Servidor TTS: $it"
            }
            val config = RemoteTtsConfig(
                endpointUrl = uiState.remoteTtsEndpoint,
                model = uiState.remoteTtsModel,
                voice = uiState.remoteTtsVoice,
                apiKey = uiState.remoteTtsApiKey,
                apiFlavor = uiState.remoteTtsApiFlavor,
                language = uiState.remoteTtsLanguage,
                voiceDescription = uiState.remoteTtsVoiceDescription,
                adaptiveStyle = uiState.remoteTtsAdaptiveStyle,
                audioMode = uiState.remoteTtsAudioMode,
                pcmSampleRate = uiState.remoteTtsPcmSampleRate
            )
            if (uiState.remoteTtsModel.isBlank() ||
                Qwen3TtsVoiceSkill.requiresVoice(config) && uiState.remoteTtsVoice.isBlank()
            ) {
                return "Completa los datos requeridos del servidor TTS"
            }
            if (uiState.remoteTtsAudioMode == RemoteTtsAudioMode.STREAMING_PCM &&
                uiState.remoteTtsPcmSampleRate !in 8_000..96_000
            ) {
                return "La frecuencia PCM debe estar entre 8000 y 96000 Hz"
            }
        }
        return null
    }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startListening() {
        if (!hasRecordAudioPermission()) {
            _error.value = "Se requiere permiso de microfono"
            return
        }

        // Repeated UI/lifecycle events must observe the active session instead of replacing it.
        if (listeningJob?.isActive == true || _isListening.value) return
        listeningJob = viewModelScope.launch {
            var liveTranscriptionJob: Job? = null
            try {
                _isListening.value = true
                _isProcessing.value = false
                _transcription.value = ""
                _pendingTranscription.value = null

                val service = sttManager.currentService.value ?: _currentSettings.value?.let { settings ->
                    // A different screen can release the shared manager while the translucent
                    // assistant is in front. Rehydrate it at the actual point of use.
                    sttManager.initializeFromSettings(settings)
                    sttManager.currentService.value
                }
                if (service == null) {
                    _error.value = voiceError(
                        IllegalStateException("STT service was not initialized"),
                        "voice_start"
                    )
                    _isListening.value = false
                    return@launch
                }

                liveTranscriptionJob = launch {
                    service.transcription.collect { partial ->
                        if (partial.isNotBlank() && !partial.startsWith("Error:", ignoreCase = true)) {
                            _transcription.value = partial
                        }
                    }
                }

                service.startListening(_currentSettings.value?.language ?: "es")

                // Verify the service actually started
                if (!service.isListening.value) {
                    val detail = service.transcription.value.takeIf { it.isNotBlank() }
                        ?: "Voice recording did not start"
                    _error.value = voiceError(IllegalStateException(detail), "voice_start")
                    _isListening.value = false
                    return@launch
                }

                // Wait for the service to stop listening.
                // This triggers from silence detection (auto-stop) OR manual stopListening().
                service.isListening.first { !it }

                // The microphone is already closed. Keep the network/model work in a distinct
                // state so the UI never claims that Cortex is still recording the user.
                _isListening.value = false
                _isProcessing.value = true

                // Wait for the recognizer/server to finish its transcription.
                service.stopListening()

                // Read the transcription result directly from the service StateFlow.
                val result = service.transcription.value.takeIf {
                    it.isNotBlank() && !it.startsWith("Error:", ignoreCase = true)
                }
                if (!result.isNullOrBlank()) {
                    _transcription.value = result
                    _pendingTranscription.value = result
                } else if (service.transcription.value.startsWith("Error:", ignoreCase = true)) {
                    _error.value = voiceError(
                        IllegalStateException(service.transcription.value),
                        "voice_transcription"
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal cancellation
            } catch (e: Exception) {
                Log.e("STTViewModel", "Error in STT", e)
                _error.value = voiceError(e, "voice_transcription")
            } finally {
                liveTranscriptionJob?.cancel()
                _isListening.value = false
                _isProcessing.value = false
            }
        }
    }

    fun stopListening() {
        // Signal the service to stop recording. The startListening() coroutine
        // observes service.isListening and handles transcription + pendingTranscription.
        viewModelScope.launch {
            try {
                sttManager.currentService.value?.stopListening()
            } catch (e: Exception) {
                Log.e("STTViewModel", "Error stopping listening", e)
                errorReporter.record(
                    e,
                    ErrorReportContext("stt", "voice_stop")
                )
            }
        }
    }

    fun acceptTranscription(): String? {
        val text = _pendingTranscription.value
        _pendingTranscription.value = null
        _transcription.value = ""
        return text
    }

    fun cancelTranscription() {
        _pendingTranscription.value = null
        _transcription.value = ""
    }

    fun dismissError() {
        _error.value = null
    }

    fun isOnDeviceRecognitionAvailable(): Boolean =
        sttManager.isOnDeviceRecognitionAvailable()

    fun installOfflineEngine() {
        voiceFeatureInstaller.requestInstall()
    }

    fun createVoicePackInstallPermissionIntent(): Intent? =
        voiceFeatureInstaller.externalInstallPermissionIntent()

    fun resumeVoicePackInstall() {
        voiceFeatureInstaller.resumeExternalInstall()
    }

    fun downloadModel() {
        if (_isDownloading.value) return
        if (!voiceFeatureInstaller.state.value.installed) {
            voiceFeatureInstaller.requestInstall()
            return
        }
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadProgress.value = 0f
            try {
                val result = OnDemandVoiceFeatureLoader.downloadDefaultModel(context) { progress ->
                    _downloadProgress.value = progress
                }

                if (result.isSuccess) {
                    _isOfflineModelReady.value = OnDemandVoiceFeatureLoader.isInstalledAndReady(context)
                    _error.value = "Modelo Whisper descargado correctamente"
                    _currentSettings.value?.let { current ->
                        val updated = current.copy(
                            mode = STTMode.LOCAL.name,
                            localEngine = com.aiagents.app.data.model.LocalSTTEngine.SHERPA_ONNX.name,
                            updatedAt = System.currentTimeMillis()
                        )
                        sttSettingsDao.insertSettings(updated)
                        sttManager.initializeFromSettings(updated)
                    }
                } else {
                    _error.value = voiceError(
                        IllegalStateException("Offline voice model download failed"),
                        "voice_model_download"
                    )
                }
            } catch (e: Exception) {
                Log.e("STTViewModel", "Error downloading model", e)
                _error.value = voiceError(e, "voice_model_download")
            } finally {
                _isDownloading.value = false
            }
        }
    }

    private fun voiceError(error: Throwable, operation: String): String =
        errorReporter.present(
            error,
            ErrorReportContext(component = "stt", operation = operation)
        ).displayMessage

    override fun onCleared() {
        super.onCleared()
        // Don't release manager here - it's a singleton shared across screens
    }
}
