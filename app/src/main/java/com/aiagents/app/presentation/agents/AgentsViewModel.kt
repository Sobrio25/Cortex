package com.aiagents.app.presentation.agents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.repository.AgentRepository
import com.aiagents.app.domain.model.Agent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AgentFormState(
    val name: String = "",
    val role: String = "",
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val enableTerminal: Boolean = true,
    val whenToUse: String = "",
    val enabledTools: String = ""
)

data class AutoCreateState(
    val isCreating: Boolean = false,
    val result: String? = null,
    val error: String? = null
)

@HiltViewModel
class AgentsViewModel @Inject constructor(
    private val repository: AgentRepository
) : ViewModel() {

    val agents: StateFlow<List<Agent>> = repository.getAllAgents()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _formState = MutableStateFlow(AgentFormState())
    val formState: StateFlow<AgentFormState> = _formState.asStateFlow()

    val configuredMcpTools: List<Pair<String, String>> = repository.getConfiguredMcpTools()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _autoCreateState = MutableStateFlow(AutoCreateState())
    val autoCreateState: StateFlow<AutoCreateState> = _autoCreateState.asStateFlow()

    fun updateFormState(state: AgentFormState) {
        _formState.value = state
    }

    fun createAgent(onSuccess: (Long) -> Unit) {
        viewModelScope.launch {
            val state = _formState.value

            val agent = Agent(
                name = state.name,
                role = state.role,
                systemPrompt = state.systemPrompt.ifEmpty {
                    "Eres un asistente especializado en ${state.role}. Ayuda al usuario con sus consultas de manera profesional y detallada."
                },
                temperature = state.temperature,
                maxTokens = state.maxTokens,
                folderPath = "agents/",
                enableTerminal = state.enableTerminal,
                whenToUse = state.whenToUse,
                enabledTools = state.enabledTools
            )
            val id = repository.createAgent(agent)
            _formState.value = AgentFormState()
            onSuccess(id)
        }
    }

    fun autoCreateAgent(description: String) {
        viewModelScope.launch {
            _autoCreateState.value = AutoCreateState(isCreating = true)
            repository.autoCreateAgent(description)
                .onSuccess { msg ->
                    _autoCreateState.value = AutoCreateState(result = msg)
                }
                .onFailure { e ->
                    _autoCreateState.value = AutoCreateState(error = e.message ?: "Error desconocido")
                }
        }
    }

    fun clearAutoCreateState() {
        _autoCreateState.value = AutoCreateState()
    }

    fun deleteAgent(id: Long) {
        viewModelScope.launch {
            repository.deleteAgent(id)
        }
    }

    fun updateAgent(agent: Agent, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.updateAgent(agent)
            onSuccess()
        }
    }
}
