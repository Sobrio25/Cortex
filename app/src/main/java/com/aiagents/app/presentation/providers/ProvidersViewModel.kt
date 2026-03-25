package com.aiagents.app.presentation.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.repository.AgentRepository
import com.aiagents.app.domain.model.MoonshotEndpointType
import com.aiagents.app.domain.model.ProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderState(
    val type: ProviderType,
    val apiKey: String = "",
    val baseUrl: String = "",
    val isConfigured: Boolean = false,
    val availableModels: List<String> = emptyList(),
    val isLoading: Boolean = false,
    // Campos específicos para Moonshot
    val moonshotEndpoint: MoonshotEndpointType = MoonshotEndpointType.GLOBAL,
    val moonshotApiKeys: Map<MoonshotEndpointType, String> = emptyMap()
)

@HiltViewModel
class ProvidersViewModel @Inject constructor(
    private val repository: AgentRepository
) : ViewModel() {

    private val _providerStates = MutableStateFlow(
        ProviderType.entries.associateWith { type ->
            ProviderState(
                type = type,
                isConfigured = repository.hasApiKey(type)
            )
        }
    )
    val providerStates: StateFlow<Map<ProviderType, ProviderState>> = _providerStates.asStateFlow()

    // Modelos seleccionados por el usuario (persistidos)
    private val _selectedModels = MutableStateFlow<Set<String>>(repository.getSelectedModels())
    val selectedModels: StateFlow<Set<String>> = _selectedModels.asStateFlow()

    private fun loadProviderConfig(type: ProviderType) {
        val currentStates = _providerStates.value.toMutableMap()

        when (type) {
            ProviderType.MOONSHOT -> {
                val activeEndpoint = repository.getActiveMoonshotEndpoint()
                val apiKeys = MoonshotEndpointType.entries.associateWith { endpoint ->
                    repository.getMoonshotApiKey(endpoint) ?: ""
                }
                currentStates[type] = currentStates[type]!!.copy(
                    apiKey = repository.getMoonshotApiKey(activeEndpoint) ?: "",
                    baseUrl = activeEndpoint.baseUrl,
                    moonshotEndpoint = activeEndpoint,
                    moonshotApiKeys = apiKeys
                )
            }
            else -> {
                // Para OpenAI, usar siempre la URL por defecto
                val savedBaseUrl = repository.getBaseUrl(type)
                val baseUrl = when (type) {
                    ProviderType.OPENAI -> getDefaultBaseUrl(type)
                    else -> savedBaseUrl ?: ""
                }
                currentStates[type] = currentStates[type]!!.copy(
                    apiKey = repository.getApiKey(type) ?: "",
                    baseUrl = baseUrl
                )
            }
        }
        _providerStates.value = currentStates
    }

    /** Carga la lista de modelos disponibles cuando se abre el diálogo de configuración */
    fun loadModelsForDialog(type: ProviderType) {
        val state = _providerStates.value[type] ?: return
        // Solo cargar si el proveedor está configurado y no tiene modelos aún
        if (state.isConfigured && state.availableModels.isEmpty() && !state.isLoading) {
            loadModels(type)
        }
    }

    fun updateApiKey(type: ProviderType, apiKey: String) {
        val currentStates = _providerStates.value.toMutableMap()
        currentStates[type] = currentStates[type]!!.copy(apiKey = apiKey)
        _providerStates.value = currentStates
    }

    fun updateBaseUrl(type: ProviderType, baseUrl: String) {
        val currentStates = _providerStates.value.toMutableMap()
        currentStates[type] = currentStates[type]!!.copy(baseUrl = baseUrl)
        _providerStates.value = currentStates
    }

    // ── Métodos específicos para Moonshot ──────────────────────────────────
    fun setMoonshotEndpoint(endpointType: MoonshotEndpointType) {
        val currentStates = _providerStates.value.toMutableMap()
        val moonshotState = currentStates[ProviderType.MOONSHOT] ?: return

        repository.setActiveMoonshotEndpoint(endpointType)

        currentStates[ProviderType.MOONSHOT] = moonshotState.copy(
            moonshotEndpoint = endpointType,
            apiKey = moonshotState.moonshotApiKeys[endpointType] ?: "",
            baseUrl = endpointType.baseUrl,
            availableModels = emptyList()
        )
        _providerStates.value = currentStates

        // Recargar modelos para el nuevo endpoint
        if (moonshotState.moonshotApiKeys[endpointType]?.isNotBlank() == true) {
            loadModels(ProviderType.MOONSHOT)
        }
    }

    fun updateMoonshotApiKey(endpointType: MoonshotEndpointType, apiKey: String) {
        val currentStates = _providerStates.value.toMutableMap()
        val moonshotState = currentStates[ProviderType.MOONSHOT] ?: return

        val updatedApiKeys = moonshotState.moonshotApiKeys.toMutableMap()
        updatedApiKeys[endpointType] = apiKey

        currentStates[ProviderType.MOONSHOT] = moonshotState.copy(
            moonshotApiKeys = updatedApiKeys,
            apiKey = if (moonshotState.moonshotEndpoint == endpointType) apiKey else moonshotState.apiKey
        )
        _providerStates.value = currentStates
    }

    fun saveMoonshotConfig(endpointType: MoonshotEndpointType, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val state = _providerStates.value[ProviderType.MOONSHOT] ?: return@launch
            val apiKey = state.moonshotApiKeys[endpointType] ?: ""

            if (apiKey.isNotBlank()) {
                repository.saveMoonshotApiKey(endpointType, apiKey)
                repository.setActiveMoonshotEndpoint(endpointType)

                val currentStates = _providerStates.value.toMutableMap()
                currentStates[ProviderType.MOONSHOT] = state.copy(
                    isConfigured = repository.isAnyMoonshotEndpointConfigured(),
                    moonshotEndpoint = endpointType,
                    apiKey = apiKey,
                    baseUrl = endpointType.baseUrl
                )
                _providerStates.value = currentStates

                loadModels(ProviderType.MOONSHOT)
                onSuccess()
            }
        }
    }

    fun saveProviderConfig(type: ProviderType, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val state = _providerStates.value[type] ?: return@launch

            repository.saveApiKey(type, state.apiKey)
            // Para OpenAI, siempre guardar la URL por defecto
            val baseUrlToSave = if (type == ProviderType.OPENAI) {
                getDefaultBaseUrl(type)
            } else {
                state.baseUrl
            }
            repository.saveBaseUrl(type, baseUrlToSave)

            val currentStates = _providerStates.value.toMutableMap()
            currentStates[type] = state.copy(isConfigured = true)
            _providerStates.value = currentStates

            loadModels(type)
            onSuccess()
        }
    }

    fun reloadModels(type: ProviderType) {
        loadModels(type)
    }

    fun refreshProviderStates() {
        _providerStates.value = ProviderType.entries.associateWith { type ->
            when (type) {
                ProviderType.MOONSHOT -> {
                    // Cargar estado especial para Moonshot con múltiples endpoints
                    val activeEndpoint = repository.getActiveMoonshotEndpoint()
                    val apiKeys = MoonshotEndpointType.entries.associateWith { endpoint ->
                        repository.getMoonshotApiKey(endpoint) ?: ""
                    }
                    ProviderState(
                        type = type,
                        apiKey = repository.getMoonshotApiKey(activeEndpoint) ?: "",
                        baseUrl = activeEndpoint.baseUrl,
                        isConfigured = repository.isAnyMoonshotEndpointConfigured(),
                        availableModels = _providerStates.value[type]?.availableModels ?: emptyList(),
                        moonshotEndpoint = activeEndpoint,
                        moonshotApiKeys = apiKeys
                    )
                }
                else -> {
                    // Para OpenAI, usar siempre la URL por defecto
                    val baseUrl = when (type) {
                        ProviderType.OPENAI -> getDefaultBaseUrl(type)
                        else -> repository.getBaseUrl(type) ?: ""
                    }
                    ProviderState(
                        type = type,
                        apiKey = repository.getApiKey(type) ?: "",
                        baseUrl = baseUrl,
                        isConfigured = repository.hasApiKey(type),
                        availableModels = _providerStates.value[type]?.availableModels ?: emptyList()
                    )
                }
            }
        }
        _selectedModels.value = repository.getSelectedModels()
    }

    fun toggleModelSelection(type: ProviderType, modelId: String) {
        if (repository.isModelSelected(type, modelId)) {
            repository.removeSelectedModel(type, modelId)
        } else {
            repository.addSelectedModel(type, modelId)
        }
        _selectedModels.value = repository.getSelectedModels()
    }

    fun isModelSelected(type: ProviderType, modelId: String): Boolean =
        _selectedModels.value.contains("${type.name}|$modelId")

    private fun loadModels(type: ProviderType) {
        viewModelScope.launch {
            val currentStates = _providerStates.value.toMutableMap()
            currentStates[type] = currentStates[type]!!.copy(isLoading = true)
            _providerStates.value = currentStates

            repository.getAvailableModels(type).onSuccess { models ->
                val updatedStates = _providerStates.value.toMutableMap()
                updatedStates[type] = updatedStates[type]!!.copy(
                    availableModels = models.sorted(),
                    isLoading = false
                )
                _providerStates.value = updatedStates
            }.onFailure {
                val updatedStates = _providerStates.value.toMutableMap()
                updatedStates[type] = updatedStates[type]!!.copy(isLoading = false)
                _providerStates.value = updatedStates
            }
        }
    }

    fun getDefaultBaseUrl(type: ProviderType): String {
        return when (type) {
            ProviderType.OPENROUTER -> "https://openrouter.ai/api/v1/"
            ProviderType.GOOGLE_AI -> "https://generativelanguage.googleapis.com/v1beta/"
            ProviderType.OPENAI -> "https://api.openai.com/v1/"
            ProviderType.OLLAMA -> "http://localhost:11434/"
            ProviderType.MINIMAX -> "https://api.minimax.io/v1/"
            ProviderType.MOONSHOT -> "https://api.moonshot.ai/v1/"
            ProviderType.ANTHROPIC -> "https://api.anthropic.com/v1/"
            ProviderType.DEEPSEEK -> "https://api.deepseek.com/"
            ProviderType.GROK -> "https://api.x.ai/"
            ProviderType.KILO -> "https://api.kilo.ai/api/gateway/"
            ProviderType.ALIBABA -> "https://dashscope.aliyuncs.com/compatible-mode/v1/"
            ProviderType.OPENCODE -> "https://opencode.ai/zen/v1/"
            ProviderType.ZAI -> "https://api.z.ai/v1/"
            ProviderType.LOCAL -> ""
        }
    }

    fun getProviderDisplayName(type: ProviderType): String {
        return when (type) {
            ProviderType.OPENROUTER -> "OpenRouter"
            ProviderType.GOOGLE_AI -> "Google AI (Gemini)"
            ProviderType.OPENAI -> "OpenAI"
            ProviderType.OLLAMA -> "Ollama (Local)"
            ProviderType.MINIMAX -> "MiniMax"
            ProviderType.MOONSHOT -> "Moonshot AI"
            ProviderType.ANTHROPIC -> "Anthropic (Claude)"
            ProviderType.DEEPSEEK -> "DeepSeek"
            ProviderType.GROK -> "Grok (xAI)"
            ProviderType.KILO -> "Kilo AI Gateway"
            ProviderType.ALIBABA -> "Alibaba Cloud (Qwen)"
            ProviderType.OPENCODE -> "OpenCode Zen"
            ProviderType.ZAI -> "z.AI (GLM)"
            ProviderType.LOCAL -> "Local (On-Device)"
        }
    }
}
