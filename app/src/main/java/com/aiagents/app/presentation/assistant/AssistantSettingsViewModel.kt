package com.aiagents.app.presentation.assistant

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.local.AssistantPreferences
import com.aiagents.app.data.speech.AndroidTextToSpeechManager
import com.aiagents.app.data.speech.ModelDownloader
import com.aiagents.app.data.speech.STTManager
import com.aiagents.app.data.speech.VoskSTTService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AssistantSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AssistantPreferences,
    private val sttManager: STTManager,
    private val textToSpeech: AndroidTextToSpeechManager
) : ViewModel() {
    private val fallbackModelId = if (Locale.getDefault().language.equals("en", ignoreCase = true)) {
        "vosk-small-en"
    } else {
        "vosk-small-es"
    }
    private val fallbackModelDirectory = ModelDownloader.getVoskModelInfo(fallbackModelId)?.dirName
        ?: "vosk-model-small-es"

    val autoListen = preferences.autoListen
    val speakResponses = preferences.speakResponses
    val offlineVoiceAvailable = textToSpeech.offlineVoiceAvailable

    private val _voskModelDownloaded = MutableStateFlow(
        VoskSTTService.isModelDownloaded(context, fallbackModelDirectory)
    )
    val voskModelDownloaded: StateFlow<Boolean> = _voskModelDownloaded.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val onDeviceRecognitionAvailable: Boolean
        get() = sttManager.isOnDeviceRecognitionAvailable()

    val systemRecognitionAvailable: Boolean
        get() = sttManager.isSystemRecognitionAvailable()

    fun setAutoListen(enabled: Boolean) = preferences.setAutoListen(enabled)

    fun setSpeakResponses(enabled: Boolean) = preferences.setSpeakResponses(enabled)

    fun downloadSpanishVoiceModel() {
        if (_isDownloading.value) return
        viewModelScope.launch {
            _isDownloading.value = true
            _error.value = null
            _downloadProgress.value = 0f
            val result = ModelDownloader.downloadVoskModel(context, fallbackModelId) {
                _downloadProgress.value = it
            }
            result.onSuccess {
                _voskModelDownloaded.value =
                    VoskSTTService.isModelDownloaded(context, fallbackModelDirectory)
            }.onFailure {
                _error.value = it.message ?: "No se pudo descargar el modelo de voz"
            }
            _isDownloading.value = false
        }
    }

    fun createInstallVoiceIntent(): Intent = textToSpeech.createInstallVoiceIntent()

    fun refreshOfflineVoice() = textToSpeech.refreshVoice()

    fun dismissError() {
        _error.value = null
    }
}
