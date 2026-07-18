package com.aiagents.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.local.ChatPreferences
import com.aiagents.app.data.local.LocalModelRepository
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.repository.AgentRepository
import com.aiagents.app.data.repository.SubscriptionRepository
import com.aiagents.app.domain.model.ProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatModelOption(
    val key: String,
    val provider: String,
    val model: String
)

@HiltViewModel
class DefaultChatModelViewModel @Inject constructor(
    private val repository: AgentRepository,
    private val localModelRepository: LocalModelRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val securePreferences: SecurePreferences,
    private val chatPreferences: ChatPreferences
) : ViewModel() {
    val selectedModel: StateFlow<String> = chatPreferences.defaultModel

    private val _models = MutableStateFlow<List<ChatModelOption>>(emptyList())
    val models: StateFlow<List<ChatModelOption>> = _models.asStateFlow()
    private var initialDefaultResolved = false

    init {
        refreshModels()
        viewModelScope.launch {
            initializeDefaultModel()
            repository.selectedModelsFlow.collect {
                refreshModels()
                ensureDefaultFromAvailableModels()
            }
        }
        viewModelScope.launch {
            subscriptionRepository.models.collect {
                refreshModels()
                if (initialDefaultResolved) ensureDefaultFromAvailableModels()
            }
        }
    }

    fun setDefaultModel(modelKey: String) {
        if (modelKey in _models.value.map(ChatModelOption::key)) {
            chatPreferences.setDefaultModel(modelKey)
        }
    }

    private fun refreshModels() {
        val keys = repository.getSelectedModels().toMutableSet()
        localModelRepository.getDownloadedModels().forEach { model ->
            keys += "${ProviderType.LOCAL.name}|${model.id}"
        }
        if (securePreferences.isManagedPrivacyAccepted()) {
            subscriptionRepository.models.value.forEach { model ->
                keys += "${ProviderType.MANAGED.name}|${model.id}"
            }
        }
        chatPreferences.defaultModel.value.takeIf(String::isNotBlank)?.let(keys::add)

        _models.value = keys.mapNotNull { key ->
            val separator = key.indexOf('|')
            if (separator <= 0 || separator == key.lastIndex) return@mapNotNull null
            ChatModelOption(
                key = key,
                provider = key.substring(0, separator),
                model = key.substring(separator + 1)
            )
        }.sortedWith(compareBy(ChatModelOption::provider, ChatModelOption::model))

    }

    private suspend fun initializeDefaultModel() {
        if (chatPreferences.defaultModel.value.isBlank()) {
            val legacyModel = securePreferences.getActiveWorkspaceId()
                .takeIf { it > 0 }
                ?.let { repository.getWorkspaceById(it)?.selectedModel }
                .orEmpty()
            val initialModel = legacyModel.takeIf(String::isNotBlank)
                ?: _models.value.firstOrNull()?.key.orEmpty()
            if (initialModel.isNotBlank()) {
                chatPreferences.setDefaultModel(initialModel)
                refreshModels()
            }
        }
        initialDefaultResolved = true
    }

    private fun ensureDefaultFromAvailableModels() {
        if (chatPreferences.defaultModel.value.isBlank()) {
            _models.value.firstOrNull()?.let { chatPreferences.setDefaultModel(it.key) }
        }
    }
}
