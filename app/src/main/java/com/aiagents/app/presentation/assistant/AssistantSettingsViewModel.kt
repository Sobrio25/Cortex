package com.aiagents.app.presentation.assistant

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.local.AssistantPreferences
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.identity.AssistantIdentityManager
import com.aiagents.app.data.repository.AgentRepository
import com.aiagents.app.data.speech.AndroidTextToSpeechManager
import com.aiagents.app.data.speech.ModelDownloader
import com.aiagents.app.data.speech.STTManager
import com.aiagents.app.data.speech.VoskSTTService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import com.aiagents.app.domain.model.isOrchestrator

@HiltViewModel
class AssistantSettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: AssistantPreferences,
    private val securePreferences: SecurePreferences,
    private val repository: AgentRepository,
    private val identityManager: AssistantIdentityManager,
    private val sttManager: STTManager,
    private val textToSpeech: AndroidTextToSpeechManager
) : ViewModel() {
    private val assistantLanguageTag = CortexAssistantPrompt.normalizeLanguageTag(
        securePreferences.getAppLanguage()
    )
    private val assistantLocale = Locale.forLanguageTag(assistantLanguageTag)
    private val fallbackModelId = if (assistantLanguageTag.substringBefore('-').equals("en", ignoreCase = true)) {
        "vosk-small-en"
    } else {
        "vosk-small-es"
    }
    private val fallbackModelDirectory = ModelDownloader.getVoskModelInfo(fallbackModelId)?.dirName
        ?: "vosk-model-small-es"

    val autoListen = preferences.autoListen
    val speakResponses = preferences.speakResponses
    val assistantModel = preferences.modelKey
    val offlineVoiceAvailable = textToSpeech.offlineVoiceAvailable
    val assistantName: StateFlow<String> = repository.getAllAgents()
        .map { agents ->
            agents.firstOrNull { it.isOrchestrator }?.name ?: identityManager.configuredName()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, identityManager.configuredName())

    private val _isSavingName = MutableStateFlow(false)
    val isSavingName: StateFlow<Boolean> = _isSavingName.asStateFlow()

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

    private val _availableModels = MutableStateFlow(repository.getSelectedModels().sorted())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    init {
        textToSpeech.refreshVoice(assistantLocale)
        viewModelScope.launch {
            repository.selectedModelsFlow.collect { selected ->
                val models = selected.sorted()
                _availableModels.value = models
                if (preferences.modelKey.value.isNotBlank() && preferences.modelKey.value !in models) {
                    preferences.setModel("")
                }
            }
        }
    }

    val onDeviceRecognitionAvailable: Boolean
        get() = sttManager.isOnDeviceRecognitionAvailable()

    val systemRecognitionAvailable: Boolean
        get() = sttManager.isSystemRecognitionAvailable()

    fun setAutoListen(enabled: Boolean) = preferences.setAutoListen(enabled)

    fun setSpeakResponses(enabled: Boolean) = preferences.setSpeakResponses(enabled)

    fun setAssistantModel(modelKey: String) = preferences.setModel(modelKey)

    fun saveAssistantName(name: String) {
        if (_isSavingName.value) return
        viewModelScope.launch {
            _isSavingName.value = true
            _error.value = null
            identityManager.rename(name).onFailure {
                _error.value = it.message ?: "No se pudo cambiar el nombre del asistente"
            }
            _isSavingName.value = false
        }
    }

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

    fun refreshOfflineVoice() = textToSpeech.refreshVoice(assistantLocale)

    fun dismissError() {
        _error.value = null
    }
}
