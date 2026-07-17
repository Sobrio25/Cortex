package com.aiagents.app.presentation.stt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.diagnostics.AppErrorReporter
import com.aiagents.app.data.diagnostics.ErrorReportContext
import com.aiagents.app.data.local.VoicePreferences
import com.aiagents.app.data.speech.STTManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class STTViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sttManager: STTManager,
    private val voicePreferences: VoicePreferences,
    private val errorReporter: AppErrorReporter
) : ViewModel() {
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

    val isSTTEnabled: StateFlow<Boolean> = sttManager.isEnabled
    val currentConfig = sttManager.currentConfig
    val ttsMode = voicePreferences.ttsMode
    private var listeningJob: Job? = null

    fun hasRecordAudioPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    fun startListening() {
        if (!hasRecordAudioPermission()) {
            _error.value = "Se requiere permiso de microfono"
            return
        }
        if (listeningJob?.isActive == true || _isListening.value) return

        listeningJob = viewModelScope.launch {
            var liveTranscriptionJob: Job? = null
            try {
                _isListening.value = true
                _isProcessing.value = false
                _transcription.value = ""
                _pendingTranscription.value = null

                val service = sttManager.currentService.value ?: withTimeoutOrNull(2_000) {
                    sttManager.currentService.filterNotNull().first()
                }
                if (service == null) {
                    _error.value = voiceError(
                        IllegalStateException("STT service was not initialized"),
                        "voice_start"
                    )
                    return@launch
                }

                liveTranscriptionJob = launch {
                    service.transcription.collect { partial ->
                        if (partial.isNotBlank() &&
                            !partial.startsWith("Error:", ignoreCase = true)
                        ) {
                            _transcription.value = partial
                        }
                    }
                }

                service.startListening(sttManager.currentConfig.value?.language ?: "auto")
                if (!service.isListening.value) {
                    val detail = service.transcription.value.takeIf(String::isNotBlank)
                        ?: "Voice recording did not start"
                    _error.value = voiceError(IllegalStateException(detail), "voice_start")
                    return@launch
                }

                service.isListening.first { !it }
                _isListening.value = false
                _isProcessing.value = true
                service.stopListening()

                val result = service.transcription.value.takeIf {
                    it.isNotBlank() && !it.startsWith("Error:", ignoreCase = true)
                }
                if (result != null) {
                    _transcription.value = result
                    _pendingTranscription.value = result
                } else if (service.transcription.value.startsWith("Error:", ignoreCase = true)) {
                    _error.value = voiceError(
                        IllegalStateException(service.transcription.value),
                        "voice_transcription"
                    )
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Expected when the screen or active recording is cancelled.
            } catch (error: Exception) {
                Log.e("STTViewModel", "Error in STT", error)
                _error.value = voiceError(error, "voice_transcription")
            } finally {
                liveTranscriptionJob?.cancel()
                _isListening.value = false
                _isProcessing.value = false
            }
        }
    }

    fun stopListening() {
        viewModelScope.launch {
            try {
                sttManager.currentService.value?.stopListening()
            } catch (error: Exception) {
                Log.e("STTViewModel", "Error stopping listening", error)
                errorReporter.record(error, ErrorReportContext("stt", "voice_stop"))
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

    fun toggleTts() {
        voicePreferences.toggleTts()
    }

    private fun voiceError(error: Throwable, operation: String): String =
        errorReporter.present(
            error,
            ErrorReportContext(component = "stt", operation = operation)
        ).displayMessage
}
