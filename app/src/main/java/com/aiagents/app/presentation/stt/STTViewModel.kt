package com.aiagents.app.presentation.stt

import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.model.CloudSTTProvider
import com.aiagents.app.data.model.STTMode
import com.aiagents.app.data.model.STTSettingsEntity
import com.aiagents.app.data.speech.ModelDownloader
import com.aiagents.app.data.speech.STTManager
import com.aiagents.app.data.speech.VoskSTTService
import com.aiagents.app.data.local.STTSettingsDao
import com.aiagents.app.domain.service.STTConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class STTViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sttManager: STTManager,
    private val sttSettingsDao: STTSettingsDao
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

    private val _isVoskModelDownloaded = MutableStateFlow(VoskSTTService.isModelDownloaded(context))
    val isVoskModelDownloaded: StateFlow<Boolean> = _isVoskModelDownloaded.asStateFlow()

    private val _downloadedVoskModels = MutableStateFlow(VoskSTTService.getDownloadedModels(context))
    val downloadedVoskModels: StateFlow<Set<String>> = _downloadedVoskModels.asStateFlow()

    private val _downloadingModelId = MutableStateFlow<String?>(null)
    val downloadingModelId: StateFlow<String?> = _downloadingModelId.asStateFlow()

    // Tracks the active listening coroutine so it can be cancelled on stop
    private var listeningJob: Job? = null

    val isSTTEnabled: StateFlow<Boolean> = sttManager.isEnabled

    val sttService = sttManager.currentService

    fun loadSettingsForWorkspace(workspaceId: Long) {
        viewModelScope.launch {
            sttSettingsDao.observeSettingsForWorkspace(workspaceId.toInt())
                .collect { settings ->
                    _currentSettings.value = settings
                    sttManager.initializeFromSettings(settings)
                }
        }
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

    fun saveSettings(workspaceId: Long, uiState: STTSettingsUiState) {
        viewModelScope.launch {
            val entity = STTSettingsEntity(
                id = _currentSettings.value?.id ?: 0,
                workspaceId = workspaceId.toInt(),
                enabled = _currentSettings.value?.enabled ?: false,
                mode = uiState.mode.name,
                cloudProvider = uiState.cloudProvider.name,
                apiKey = uiState.apiKey,
                localModelType = uiState.localModelType.name,
                localEngine = uiState.localEngine.name,
                language = uiState.language,
                updatedAt = System.currentTimeMillis()
            )
            sttSettingsDao.insertSettings(entity)

            // Map to domain config and reinitialize
            val cloudProvider = try {
                STTConfig.CloudSTTProvider.valueOf(
                    when (uiState.cloudProvider) {
                        CloudSTTProvider.ANDROID_SPEECH_RECOGNIZER -> "ANDROID_SPEECH_RECOGNIZER"
                        CloudSTTProvider.WHISPER_API -> "WHISPER_API"
                        CloudSTTProvider.GOOGLE_FREE -> "GOOGLE_SPEECH"
                        CloudSTTProvider.ASSEMBLY_AI -> "ASSEMBLY_AI"
                        CloudSTTProvider.DEEPGRAM -> "DEEPGRAM"
                        else -> "ANDROID_SPEECH_RECOGNIZER"
                    }
                )
            } catch (e: Exception) {
                STTConfig.CloudSTTProvider.ANDROID_SPEECH_RECOGNIZER
            }

            val localEngine = try {
                STTConfig.LocalSTTEngine.valueOf(uiState.localEngine.name)
            } catch (e: Exception) {
                STTConfig.LocalSTTEngine.AUTO
            }

            val config = STTConfig(
                mode = STTConfig.STTMode.valueOf(uiState.mode.name),
                language = uiState.language,
                apiKey = uiState.apiKey,
                cloudProvider = cloudProvider,
                localEngine = localEngine
            )
            sttManager.initializeService(config)
        }
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

        listeningJob?.cancel()
        listeningJob = viewModelScope.launch {
            try {
                _isListening.value = true
                _transcription.value = ""
                _pendingTranscription.value = null

                val service = sttManager.currentService.value
                if (service == null) {
                    _error.value = "Servicio STT no inicializado"
                    _isListening.value = false
                    return@launch
                }

                service.startListening(_currentSettings.value?.language ?: "es")

                // Verify the service actually started
                if (!service.isListening.value) {
                    _error.value = service.transcription.value.takeIf { it.isNotBlank() }
                        ?: "No se pudo iniciar la grabacion"
                    _isListening.value = false
                    return@launch
                }

                // Wait for the service to stop listening.
                // This triggers from silence detection (auto-stop) OR manual stopListening().
                service.isListening.first { !it }

                // Recording stopped — wait for transcription to complete.
                // stopListening() calls recordingJob.join() which waits for Vosk to finish.
                service.stopListening()

                _isListening.value = false

                // Read the transcription result directly from the service StateFlow.
                val result = service.transcription.value.takeIf { it.isNotBlank() }
                if (!result.isNullOrBlank()) {
                    _transcription.value = result
                    _pendingTranscription.value = result
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal cancellation
            } catch (e: Exception) {
                Log.e("STTViewModel", "Error in STT", e)
                _error.value = "Error: ${e.message}"
            } finally {
                _isListening.value = false
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

    fun downloadModel() {
        downloadModel("vosk-small-es")
    }

    fun downloadModel(modelId: String) {
        if (_isDownloading.value) return
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadingModelId.value = modelId
            _downloadProgress.value = 0f
            try {
                val result = ModelDownloader.downloadVoskModel(context, modelId) { progress ->
                    _downloadProgress.value = progress
                }

                if (result.isSuccess) {
                    _downloadedVoskModels.value = VoskSTTService.getDownloadedModels(context)
                    _isVoskModelDownloaded.value = true
                    _error.value = "Modelo descargado correctamente"
                    _currentSettings.value?.let { sttManager.initializeFromSettings(it) }
                } else {
                    _error.value = "Error al descargar el modelo"
                }
            } catch (e: Exception) {
                Log.e("STTViewModel", "Error downloading model", e)
                _error.value = "Error al descargar: ${e.message}"
            } finally {
                _isDownloading.value = false
                _downloadingModelId.value = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Don't release manager here - it's a singleton shared across screens
    }
}
