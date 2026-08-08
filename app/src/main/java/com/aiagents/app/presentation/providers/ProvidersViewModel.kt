package com.aiagents.app.presentation.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.diagnostics.AppErrorReporter
import com.aiagents.app.data.diagnostics.ErrorReportContext
import com.aiagents.app.data.local.ProviderModelCatalogCache
import com.aiagents.app.data.repository.AgentRepository
import com.aiagents.app.domain.model.MoonshotEndpointType
import com.aiagents.app.domain.model.NvidiaProviderConfig
import com.aiagents.app.domain.model.OpenCodeVariantType
import com.aiagents.app.domain.model.ProviderType
import com.aiagents.app.domain.model.ZAIPlanType
import android.content.Context
import com.aiagents.app.data.auth.OpenAIEndpointPolicy
import com.aiagents.app.data.auth.UnsafeProviderEndpointException
import com.aiagents.app.data.auth.AnthropicOAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderState(
    val type: ProviderType,
    val apiKey: String = "",
    val baseUrl: String = "",
    val isConfigured: Boolean = false,
    val availableModels: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val catalogSource: ModelCatalogSource? = null,
    val catalogError: String? = null,
    val catalogUpdatedAtEpochMillis: Long? = null,
    val loadedCatalogKey: String? = null,
    // Campos específicos para Moonshot
    val moonshotEndpoint: MoonshotEndpointType = MoonshotEndpointType.GLOBAL,
    val moonshotApiKeys: Map<MoonshotEndpointType, String> = emptyMap(),
    // Campos específicos para Z.AI
    val zaiPlan: ZAIPlanType = ZAIPlanType.STANDARD,
    val zaiApiKeys: Map<ZAIPlanType, String> = emptyMap(),
    // Campos específicos para OpenCode
    val openCodeVariant: OpenCodeVariantType = OpenCodeVariantType.ZEN,
    val openCodeApiKeys: Map<OpenCodeVariantType, String> = emptyMap()
)

@HiltViewModel
class ProvidersViewModel @Inject constructor(
    private val repository: AgentRepository,
    private val anthropicOAuthManager: AnthropicOAuthManager,
    private val modelCatalogCache: ProviderModelCatalogCache,
    private val errorReporter: AppErrorReporter
) : ViewModel() {

    private val modelLoadJobs = mutableMapOf<ProviderType, Job>()
    private val modelLoadRequestIds = mutableMapOf<ProviderType, Long>()

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
            ProviderType.ZAI -> {
                val activePlan = repository.getActiveZAIPlan()
                val apiKeys = ZAIPlanType.entries.associateWith { plan ->
                    repository.getZAIApiKey(plan) ?: ""
                }
                currentStates[type] = currentStates[type]!!.copy(
                    apiKey = repository.getZAIApiKey(activePlan) ?: "",
                    baseUrl = activePlan.baseUrl,
                    zaiPlan = activePlan,
                    zaiApiKeys = apiKeys
                )
            }
            ProviderType.OPENCODE -> {
                val activeVariant = repository.getActiveOpenCodeVariant()
                val apiKeys = OpenCodeVariantType.entries.associateWith { variant ->
                    repository.getOpenCodeApiKey(variant) ?: ""
                }
                currentStates[type] = currentStates[type]!!.copy(
                    apiKey = repository.getOpenCodeApiKey(activeVariant) ?: "",
                    baseUrl = activeVariant.baseUrl,
                    openCodeVariant = activeVariant,
                    openCodeApiKeys = apiKeys
                )
            }
            ProviderType.OPENAI -> {
                currentStates[type] = currentStates[type]!!.copy(
                    apiKey = repository.getApiKey(type) ?: "",
                    baseUrl = OpenAIEndpointPolicy.OFFICIAL_API_BASE_URL
                )
            }
            else -> {
                val savedBaseUrl = repository.getBaseUrl(type)
                currentStates[type] = currentStates[type]!!.copy(
                    apiKey = repository.getApiKey(type) ?: "",
                    baseUrl = savedBaseUrl ?: ""
                )
            }
        }
        _providerStates.value = currentStates
    }

    /** Shows a scoped cache immediately and refreshes every time the dialog is opened. */
    fun loadModelsForDialog(type: ProviderType) {
        val state = _providerStates.value[type] ?: return
        if (state.isConfigured && !state.isLoading) {
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

        val updatedState = moonshotState.copy(
            moonshotEndpoint = endpointType,
            apiKey = moonshotState.moonshotApiKeys[endpointType] ?: "",
            baseUrl = endpointType.baseUrl,
            availableModels = emptyList(),
            isLoading = false,
            catalogSource = null,
            catalogError = null,
            catalogUpdatedAtEpochMillis = null,
            loadedCatalogKey = null
        )
        currentStates[ProviderType.MOONSHOT] = withCachedCatalog(updatedState)
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
                val previousScope = repository.getStoredCredentialScope(ProviderType.MOONSHOT)
                repository.saveMoonshotApiKey(endpointType, apiKey)
                repository.setActiveMoonshotEndpoint(endpointType)
                clearSelectionsIfCredentialChanged(ProviderType.MOONSHOT, previousScope)

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

    // ── Métodos específicos para Z.AI ─────────────────────────────────────
    fun setZAIPlan(planType: ZAIPlanType) {
        val currentStates = _providerStates.value.toMutableMap()
        val zaiState = currentStates[ProviderType.ZAI] ?: return

        repository.setActiveZAIPlan(planType)

        val updatedState = zaiState.copy(
            zaiPlan = planType,
            apiKey = zaiState.zaiApiKeys[planType] ?: "",
            baseUrl = planType.baseUrl,
            availableModels = emptyList(),
            isLoading = false,
            catalogSource = null,
            catalogError = null,
            catalogUpdatedAtEpochMillis = null,
            loadedCatalogKey = null
        )
        currentStates[ProviderType.ZAI] = withCachedCatalog(updatedState)
        _providerStates.value = currentStates

        // Recargar modelos para el nuevo plan
        if (zaiState.zaiApiKeys[planType]?.isNotBlank() == true) {
            loadModels(ProviderType.ZAI)
        }
    }

    fun updateZAIApiKey(planType: ZAIPlanType, apiKey: String) {
        val currentStates = _providerStates.value.toMutableMap()
        val zaiState = currentStates[ProviderType.ZAI] ?: return

        val updatedApiKeys = zaiState.zaiApiKeys.toMutableMap()
        updatedApiKeys[planType] = apiKey

        currentStates[ProviderType.ZAI] = zaiState.copy(
            zaiApiKeys = updatedApiKeys,
            apiKey = if (zaiState.zaiPlan == planType) apiKey else zaiState.apiKey
        )
        _providerStates.value = currentStates
    }

    fun saveZAIConfig(planType: ZAIPlanType, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val state = _providerStates.value[ProviderType.ZAI] ?: return@launch
            val apiKey = state.zaiApiKeys[planType] ?: ""

            if (apiKey.isNotBlank()) {
                val previousScope = repository.getStoredCredentialScope(ProviderType.ZAI)
                repository.saveZAIApiKey(planType, apiKey)
                repository.setActiveZAIPlan(planType)
                clearSelectionsIfCredentialChanged(ProviderType.ZAI, previousScope)

                val currentStates = _providerStates.value.toMutableMap()
                currentStates[ProviderType.ZAI] = state.copy(
                    isConfigured = repository.isAnyZAIPlanConfigured(),
                    zaiPlan = planType,
                    apiKey = apiKey,
                    baseUrl = planType.baseUrl
                )
                _providerStates.value = currentStates

                loadModels(ProviderType.ZAI)
                onSuccess()
            }
        }
    }

    // ── Métodos específicos para OpenCode ──────────────────────────────────
    fun setOpenCodeVariant(variantType: OpenCodeVariantType) {
        val currentStates = _providerStates.value.toMutableMap()
        val openCodeState = currentStates[ProviderType.OPENCODE] ?: return

        repository.setActiveOpenCodeVariant(variantType)

        val updatedState = openCodeState.copy(
            openCodeVariant = variantType,
            apiKey = openCodeState.openCodeApiKeys[variantType] ?: "",
            baseUrl = variantType.baseUrl,
            availableModels = emptyList(),
            isLoading = false,
            catalogSource = null,
            catalogError = null,
            catalogUpdatedAtEpochMillis = null,
            loadedCatalogKey = null
        )
        currentStates[ProviderType.OPENCODE] = withCachedCatalog(updatedState)
        _providerStates.value = currentStates

        if (openCodeState.openCodeApiKeys[variantType]?.isNotBlank() == true) {
            loadModels(ProviderType.OPENCODE)
        }
    }

    fun updateOpenCodeApiKey(variantType: OpenCodeVariantType, apiKey: String) {
        val currentStates = _providerStates.value.toMutableMap()
        val openCodeState = currentStates[ProviderType.OPENCODE] ?: return

        val updatedApiKeys = openCodeState.openCodeApiKeys.toMutableMap()
        updatedApiKeys[variantType] = apiKey

        currentStates[ProviderType.OPENCODE] = openCodeState.copy(
            openCodeApiKeys = updatedApiKeys,
            apiKey = if (openCodeState.openCodeVariant == variantType) apiKey else openCodeState.apiKey
        )
        _providerStates.value = currentStates
    }

    fun saveOpenCodeConfig(variantType: OpenCodeVariantType, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val state = _providerStates.value[ProviderType.OPENCODE] ?: return@launch
            val apiKey = state.openCodeApiKeys[variantType]?.trim().orEmpty()

            if (apiKey.isNotBlank()) {
                updateProviderState(
                    ProviderType.OPENCODE,
                    state.copy(isLoading = true, catalogError = null)
                )
                val models = repository.validateOpenCodeApiKey(variantType, apiKey)
                    .map(::normalizeProviderModels)
                    .mapCatching { catalog ->
                        catalog.also {
                            check(it.isNotEmpty()) {
                                "OpenCode no devolvió modelos para ${variantType.displayName}"
                            }
                        }
                    }
                    .getOrElse { error ->
                        val currentState = _providerStates.value[ProviderType.OPENCODE] ?: state
                        updateProviderState(
                            ProviderType.OPENCODE,
                            currentState.copy(
                                isLoading = false,
                                catalogError = providerError(
                                    error,
                                    "provider_catalog_load",
                                    ProviderType.OPENCODE
                                )
                            )
                        )
                        return@launch
                    }

                val previousScope = repository.getStoredCredentialScope(ProviderType.OPENCODE)
                repository.saveOpenCodeApiKey(variantType, apiKey)
                repository.setActiveOpenCodeVariant(variantType)
                clearSelectionsIfCredentialChanged(ProviderType.OPENCODE, previousScope)

                val updatedAt = System.currentTimeMillis()
                val updatedState = state.copy(
                    isConfigured = repository.isAnyOpenCodeVariantConfigured(),
                    openCodeVariant = variantType,
                    apiKey = apiKey,
                    baseUrl = variantType.baseUrl,
                    availableModels = models,
                    isLoading = false,
                    catalogSource = ModelCatalogSource.PROVIDER_API,
                    catalogError = null,
                    catalogUpdatedAtEpochMillis = updatedAt
                )
                val requestKey = catalogKey(updatedState)
                modelCatalogCache.write(requestKey.storageKey, models, updatedAt)
                updateProviderState(
                    ProviderType.OPENCODE,
                    updatedState.copy(loadedCatalogKey = requestKey.storageKey)
                )

                onSuccess()
            }
        }
    }

    fun saveProviderConfig(type: ProviderType, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val state = _providerStates.value[type] ?: return@launch
            try {
                if (type == ProviderType.OPENAI) {
                    val previousScope = repository.getStoredCredentialScope(type)
                    repository.saveOpenAIProviderApiKey(state.apiKey)
                    clearSelectionsIfCredentialChanged(type, previousScope)
                } else {
                    val previousScope = repository.getStoredCredentialScope(type)
                    // Validate and persist the destination before accepting a credential for it.
                    // An unsafe URL must not leave a half-saved provider configuration behind.
                    repository.saveBaseUrl(type, state.baseUrl.trim())
                    repository.saveApiKey(type, state.apiKey)
                    clearSelectionsIfCredentialChanged(type, previousScope)
                }
            } catch (error: Exception) {
                updateProviderState(
                    type,
                    state.copy(
                        isLoading = false,
                        catalogError = if (error is UnsafeProviderEndpointException) {
                            error.message
                        } else {
                            providerError(error, "provider_config_save", type)
                        }
                    )
                )
                return@launch
            }

            val currentStates = _providerStates.value.toMutableMap()
            currentStates[type] = state.copy(isConfigured = repository.hasApiKey(type))
            _providerStates.value = currentStates

            loadModels(type)
            onSuccess()
        }
    }

    fun reloadModels(type: ProviderType) {
        loadModels(type)
    }

    private suspend fun clearSelectionsIfCredentialChanged(
        type: ProviderType,
        previousScope: String?
    ) {
        if (previousScope != repository.getStoredCredentialScope(type)) {
            repository.clearSelectedModelsForProvider(type)
            _selectedModels.value = repository.getSelectedModels()
        }
    }

    fun refreshProviderStates() {
        val previousStates = _providerStates.value
        val refreshedStates = ProviderType.entries.associateWith { type ->
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
                        moonshotEndpoint = activeEndpoint,
                        moonshotApiKeys = apiKeys
                    )
                }
                ProviderType.ZAI -> {
                    // Cargar estado especial para Z.AI con múltiples planes
                    val activePlan = repository.getActiveZAIPlan()
                    val apiKeys = ZAIPlanType.entries.associateWith { plan ->
                        repository.getZAIApiKey(plan) ?: ""
                    }
                    ProviderState(
                        type = type,
                        apiKey = repository.getZAIApiKey(activePlan) ?: "",
                        baseUrl = activePlan.baseUrl,
                        isConfigured = repository.isAnyZAIPlanConfigured(),
                        zaiPlan = activePlan,
                        zaiApiKeys = apiKeys
                    )
                }
                ProviderType.OPENCODE -> {
                    val activeVariant = repository.getActiveOpenCodeVariant()
                    val apiKeys = OpenCodeVariantType.entries.associateWith { variant ->
                        repository.getOpenCodeApiKey(variant) ?: ""
                    }
                    ProviderState(
                        type = type,
                        apiKey = repository.getOpenCodeApiKey(activeVariant) ?: "",
                        baseUrl = activeVariant.baseUrl,
                        isConfigured = repository.isAnyOpenCodeVariantConfigured(),
                        openCodeVariant = activeVariant,
                        openCodeApiKeys = apiKeys
                    )
                }
                ProviderType.OPENAI -> {
                    ProviderState(
                        type = type,
                        apiKey = repository.getApiKey(type) ?: "",
                        baseUrl = OpenAIEndpointPolicy.OFFICIAL_API_BASE_URL,
                        isConfigured = repository.hasApiKey(type)
                    )
                }
                else -> {
                    ProviderState(
                        type = type,
                        apiKey = repository.getApiKey(type) ?: "",
                        baseUrl = repository.getBaseUrl(type) ?: "",
                        isConfigured = repository.hasApiKey(type)
                    )
                }
            }
        }
        _providerStates.value = refreshedStates.mapValues { (type, refreshedState) ->
            val previousState = previousStates[type]
            val refreshedKey = catalogKey(refreshedState).storageKey
            if (previousState?.loadedCatalogKey == refreshedKey) {
                refreshedState.copy(
                    availableModels = previousState.availableModels,
                    isLoading = previousState.isLoading,
                    catalogSource = previousState.catalogSource,
                    catalogError = previousState.catalogError,
                    catalogUpdatedAtEpochMillis = previousState.catalogUpdatedAtEpochMillis,
                    loadedCatalogKey = previousState.loadedCatalogKey
                )
            } else {
                withCachedCatalog(refreshedState)
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
        val requestState = _providerStates.value[type] ?: return
        val requestKey = catalogKey(requestState)
        val requestId = (modelLoadRequestIds[type] ?: 0L) + 1L
        modelLoadRequestIds[type] = requestId
        modelLoadJobs.remove(type)?.cancel()

        val cachedCatalog = modelCatalogCache.read(requestKey.storageKey)
        val currentCatalogMatches = requestState.loadedCatalogKey == requestKey.storageKey
        val loadingState = when {
            currentCatalogMatches && requestState.availableModels.isNotEmpty() -> requestState.copy(
                isLoading = true,
                catalogError = null
            )
            cachedCatalog != null -> requestState.copy(
                availableModels = normalizeProviderModels(cachedCatalog.models),
                isLoading = true,
                catalogSource = ModelCatalogSource.CACHE,
                catalogError = null,
                catalogUpdatedAtEpochMillis = cachedCatalog.updatedAtEpochMillis,
                loadedCatalogKey = requestKey.storageKey
            )
            else -> requestState.copy(
                availableModels = emptyList(),
                isLoading = true,
                catalogSource = null,
                catalogError = null,
                catalogUpdatedAtEpochMillis = null,
                loadedCatalogKey = requestKey.storageKey
            )
        }
        updateProviderState(type, loadingState)

        modelLoadJobs[type] = viewModelScope.launch {
            runCatching { repository.getAvailableModels(type) }
                .fold(
                    onSuccess = { it },
                    onFailure = { Result.failure(it) }
                )
                .mapCatching { models ->
                    normalizeProviderModels(models).also { normalized ->
                        check(normalized.isNotEmpty()) {
                            "El proveedor no devolvió ningún modelo"
                        }
                    }
                }
                .onSuccess { models ->
                    if (!isCurrentCatalogRequest(type, requestKey, requestId)) return@onSuccess
                    val updatedAt = System.currentTimeMillis()
                    modelCatalogCache.write(requestKey.storageKey, models, updatedAt)
                    val currentState = _providerStates.value[type] ?: return@onSuccess
                    updateProviderState(
                        type,
                        currentState.copy(
                            availableModels = models,
                            isLoading = false,
                            catalogSource = expectedCatalogSource(type, requestState.moonshotEndpoint),
                            catalogError = null,
                            catalogUpdatedAtEpochMillis = updatedAt,
                            loadedCatalogKey = requestKey.storageKey
                        )
                    )
                }
                .onFailure { error ->
                    if (!isCurrentCatalogRequest(type, requestKey, requestId)) return@onFailure
                    val currentState = _providerStates.value[type] ?: return@onFailure
                    updateProviderState(
                        type,
                        currentState.copy(
                            isLoading = false,
                            catalogSource = if (currentState.availableModels.isNotEmpty()) {
                                ModelCatalogSource.CACHE
                            } else {
                                null
                            },
                            catalogError = providerError(error, "provider_catalog_load", type)
                        )
                    )
                }
        }
    }

    private fun catalogKey(state: ProviderState): ProviderCatalogKey {
        val destination = when (state.type) {
            ProviderType.MOONSHOT -> "${state.moonshotEndpoint.name}|${state.moonshotEndpoint.baseUrl}"
            ProviderType.ZAI -> "${state.zaiPlan.name}|${state.zaiPlan.baseUrl}"
            ProviderType.OPENCODE -> "remote-v2|${state.openCodeVariant.name}|${state.openCodeVariant.baseUrl}"
            else -> state.baseUrl.ifBlank { getDefaultBaseUrl(state.type) }
        }.trim().trimEnd('/')
        val scope = repository.getCredentialScope(state.type, state.apiKey, destination)
        return ProviderCatalogKey(state.type, scope)
    }

    private fun withCachedCatalog(state: ProviderState): ProviderState {
        val key = catalogKey(state)
        val cached = modelCatalogCache.read(key.storageKey) ?: return state
        return state.copy(
            availableModels = normalizeProviderModels(cached.models),
            catalogSource = ModelCatalogSource.CACHE,
            catalogUpdatedAtEpochMillis = cached.updatedAtEpochMillis,
            loadedCatalogKey = key.storageKey
        )
    }

    private fun isCurrentCatalogRequest(
        type: ProviderType,
        requestKey: ProviderCatalogKey,
        requestId: Long
    ): Boolean {
        val currentState = _providerStates.value[type] ?: return false
        return modelLoadRequestIds[type] == requestId &&
            catalogKey(currentState) == requestKey
    }

    private fun updateProviderState(type: ProviderType, state: ProviderState) {
        _providerStates.value = _providerStates.value.toMutableMap().apply {
            this[type] = state
        }
    }

    private fun providerError(
        error: Throwable,
        operation: String,
        provider: ProviderType
    ): String = errorReporter.present(
        error,
        ErrorReportContext(
            component = "providers",
            operation = operation,
            provider = provider.name
        )
    ).displayMessage

    fun getDefaultBaseUrl(type: ProviderType): String {
        return when (type) {
            ProviderType.MANAGED -> ""
            ProviderType.OPENROUTER -> "https://openrouter.ai/api/v1/"
            ProviderType.GOOGLE_AI -> "https://generativelanguage.googleapis.com/v1beta/"
            ProviderType.OPENAI -> "https://api.openai.com/v1/"
            ProviderType.NVIDIA -> NvidiaProviderConfig.API_BASE_URL
            ProviderType.OLLAMA -> "http://localhost:11434/"
            ProviderType.LM_STUDIO -> "http://10.0.2.2:1234/v1/"
            ProviderType.MINIMAX -> "https://api.minimax.io/v1/"
            ProviderType.MOONSHOT -> "https://api.moonshot.ai/v1/"
            ProviderType.ANTHROPIC -> "https://api.anthropic.com/v1/"
            ProviderType.DEEPSEEK -> "https://api.deepseek.com/"
            ProviderType.GROK -> "https://api.x.ai/"
            ProviderType.KILO -> "https://api.kilo.ai/api/gateway/"
            ProviderType.ALIBABA -> "https://dashscope.aliyuncs.com/compatible-mode/v1/"
            ProviderType.OPENCODE -> "https://opencode.ai/zen/v1/"
            ProviderType.ZAI -> ZAIPlanType.STANDARD.baseUrl
            ProviderType.LOCAL -> ""
        }
    }

    fun getProviderDisplayName(type: ProviderType): String {
        return when (type) {
            ProviderType.MANAGED -> "Plan administrado"
            ProviderType.OPENROUTER -> "OpenRouter"
            ProviderType.GOOGLE_AI -> "Google AI (Gemini)"
            ProviderType.OPENAI -> "OpenAI"
            ProviderType.NVIDIA -> "NVIDIA NIM"
            ProviderType.OLLAMA -> "Ollama (Local)"
            ProviderType.LM_STUDIO -> "LM Studio (Local)"
            ProviderType.MINIMAX -> "MiniMax"
            ProviderType.MOONSHOT -> "Moonshot AI"
            ProviderType.ANTHROPIC -> "Anthropic (Claude)"
            ProviderType.DEEPSEEK -> "DeepSeek"
            ProviderType.GROK -> "Grok (xAI)"
            ProviderType.KILO -> "Kilo AI Gateway"
            ProviderType.ALIBABA -> "Alibaba Cloud (Qwen)"
            ProviderType.OPENCODE -> "OpenCode"
            ProviderType.ZAI -> "z.AI (GLM)"
            ProviderType.LOCAL -> "Local (On-Device)"
        }
    }

    // ── Anthropic OAuth (2-step: open browser → paste code) ────────────

    private val _anthropicOAuthState = MutableStateFlow(AnthropicOAuthState())
    val anthropicOAuthState: StateFlow<AnthropicOAuthState> = _anthropicOAuthState.asStateFlow()

    fun loadAnthropicOAuthState() {
        _anthropicOAuthState.value = AnthropicOAuthState(
            isConnected = anthropicOAuthManager.isConnected()
        )
    }

    /** Step 1: open browser to Anthropic authorization page */
    fun openAnthropicOAuth(context: Context) {
        anthropicOAuthManager.openAuthorizationPage(context)
        _anthropicOAuthState.value = _anthropicOAuthState.value.copy(
            waitingForCode = true,
            error = null
        )
    }

    fun updateAnthropicCode(code: String) {
        _anthropicOAuthState.value = _anthropicOAuthState.value.copy(code = code)
    }

    /** Step 2: exchange the pasted code for tokens and create API key */
    fun submitAnthropicCode() {
        val code = _anthropicOAuthState.value.code.trim()
        if (code.isBlank()) return

        _anthropicOAuthState.value = _anthropicOAuthState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            anthropicOAuthManager.exchangeCodeAndCreateKey(code)
                .onSuccess {
                    _anthropicOAuthState.value = AnthropicOAuthState(isConnected = true)
                    // Mark Anthropic as configured and refresh the API key in state
                    val currentStates = _providerStates.value.toMutableMap()
                    currentStates[ProviderType.ANTHROPIC] = currentStates[ProviderType.ANTHROPIC]!!.copy(
                        isConfigured = true,
                        apiKey = repository.getApiKey(ProviderType.ANTHROPIC) ?: ""
                    )
                    _providerStates.value = currentStates
                    // Load models now that we're configured
                    loadModels(ProviderType.ANTHROPIC)
                }
                .onFailure { e ->
                    _anthropicOAuthState.value = _anthropicOAuthState.value.copy(
                        isLoading = false,
                        error = providerError(
                            e,
                            "anthropic_oauth_exchange",
                            ProviderType.ANTHROPIC
                        )
                    )
                }
        }
    }

    fun disconnectAnthropicOAuth() {
        anthropicOAuthManager.clearAuth()
        _anthropicOAuthState.value = AnthropicOAuthState()
    }
}

data class AnthropicOAuthState(
    val isConnected: Boolean = false,
    val waitingForCode: Boolean = false,
    val code: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
