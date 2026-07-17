package com.aiagents.app.presentation.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.diagnostics.AppErrorReporter
import com.aiagents.app.data.diagnostics.ErrorReportContext
import com.aiagents.app.data.identity.AssistantIdentityManager
import com.aiagents.app.data.local.AssistantPreferences
import com.aiagents.app.data.local.VoicePreferences
import com.aiagents.app.data.memory.CortexProfileStore
import com.aiagents.app.data.repository.AgentRepository
import com.aiagents.app.data.speech.AndroidTextToSpeechManager
import com.aiagents.app.domain.model.isOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssistantSettingsViewModel @Inject constructor(
    private val preferences: AssistantPreferences,
    voicePreferences: VoicePreferences,
    private val repository: AgentRepository,
    private val identityManager: AssistantIdentityManager,
    private val cortexProfileStore: CortexProfileStore,
    private val textToSpeech: AndroidTextToSpeechManager,
    private val errorReporter: AppErrorReporter
) : ViewModel() {
    val speakResponses = preferences.speakResponses
    val assistantModel = preferences.modelKey
    val ttsMode = voicePreferences.ttsMode
    val assistantSoul = cortexProfileStore.assistantSoulSnapshots

    val assistantName: StateFlow<String> = repository.getAllAgents()
        .map { agents ->
            agents.firstOrNull { it.isOrchestrator }?.name ?: identityManager.configuredName()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, identityManager.configuredName())

    private val _isSavingName = MutableStateFlow(false)
    val isSavingName: StateFlow<Boolean> = _isSavingName.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _availableModels = MutableStateFlow(repository.getSelectedModels().sorted())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    init {
        viewModelScope.launch {
            repository.selectedModelsFlow.collect { selected ->
                val models = selected.sorted()
                _availableModels.value = models
                if (preferences.modelKey.value.isNotBlank() &&
                    preferences.modelKey.value !in models
                ) {
                    preferences.setModel("")
                }
            }
        }
    }

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

    fun dismissError() {
        _error.value = null
        textToSpeech.dismissError()
    }

    private fun assistantError(error: Throwable, operation: String): String =
        errorReporter.present(
            error,
            ErrorReportContext(component = "assistant_settings", operation = operation)
        ).displayMessage
}
