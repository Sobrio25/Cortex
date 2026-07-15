package com.aiagents.app.presentation.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.repository.AgentRepository
import com.aiagents.app.domain.model.Conversation
import com.aiagents.app.domain.model.Workspace
import com.aiagents.app.domain.model.isOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val repository: AgentRepository,
    private val securePreferences: SecurePreferences
) : ViewModel() {

    companion object {
        /** Name used for the hidden system workspace. Never shown to the user. */
        const val GLOBAL_WORKSPACE_NAME = "__global__"
    }

    private val _activeWorkspaceId = MutableStateFlow(securePreferences.getActiveWorkspaceId())
    val activeWorkspaceId: StateFlow<Long> = _activeWorkspaceId.asStateFlow()

    private val _activeConversationId = MutableStateFlow<Long?>(null)
    val activeConversationId: StateFlow<Long?> = _activeConversationId.asStateFlow()

    /** Whether the user is in global chat mode (not inside a specific workspace). */
    private val _isGlobalMode = MutableStateFlow(true)
    val isGlobalMode: StateFlow<Boolean> = _isGlobalMode.asStateFlow()

    val assistantName: StateFlow<String> = repository.getAllAgents()
        .map { agents ->
            agents.firstOrNull { it.isOrchestrator }?.name
                ?: securePreferences.getAssistantName()
                ?: "Assistant"
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            securePreferences.getAssistantName() ?: "Assistant"
        )

    /** All user-visible workspaces (excludes hidden system workspace). */
    val allWorkspaces: StateFlow<List<Workspace>> = repository.getAllWorkspaces()
        .map { list -> list.filter { it.name != GLOBAL_WORKSPACE_NAME } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activeWorkspace: StateFlow<Workspace?> = _activeWorkspaceId.flatMapLatest { id ->
        if (id <= 0) flowOf(null)
        else repository.getAllWorkspaces().map { list -> list.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val conversations: StateFlow<List<Conversation>> = _activeWorkspaceId.flatMapLatest { id ->
        if (id <= 0) flowOf(emptyList())
        else repository.getConversationsForWorkspace(id)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            ensureGlobalWorkspace()
        }
    }

    /**
     * Makes sure the hidden system workspace exists and sets it as active
     * if the user hasn't explicitly entered a user-created workspace.
     */
    private suspend fun ensureGlobalWorkspace() {
        val globalWs = getOrCreateGlobalWorkspace()

        val currentId = _activeWorkspaceId.value
        if (currentId > 0) {
            val exists = repository.getWorkspaceById(currentId)
            if (exists != null) {
                _isGlobalMode.value = exists.name == GLOBAL_WORKSPACE_NAME
                return
            }
        }
        // No valid workspace saved → use the global one
        setActiveWorkspace(globalWs.id, isGlobal = true)
    }

    private suspend fun getOrCreateGlobalWorkspace(): Workspace {
        // Check if it already exists
        val all = repository.getAllWorkspaces()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
            .value
        val existing = all.find { it.name == GLOBAL_WORKSPACE_NAME }
        if (existing != null) return existing

        val id = repository.createWorkspace(
            Workspace(name = GLOBAL_WORKSPACE_NAME, description = "")
        )
        return repository.getWorkspaceById(id)!!
    }

    fun setActiveWorkspace(id: Long, isGlobal: Boolean = false) {
        _activeWorkspaceId.value = id
        _activeConversationId.value = null
        _isGlobalMode.value = isGlobal
        securePreferences.setActiveWorkspaceId(id)
    }

    /** Switch back to global chat mode. */
    fun goToGlobalMode() {
        viewModelScope.launch {
            val globalWs = getOrCreateGlobalWorkspace()
            setActiveWorkspace(globalWs.id, isGlobal = true)
        }
    }

    fun setActiveConversation(id: Long?) {
        _activeConversationId.value = id
    }

    fun createNewConversation(onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val wsId = _activeWorkspaceId.value
            if (wsId <= 0) return@launch
            val id = repository.createConversation(
                Conversation(workspaceId = wsId, title = "Nuevo chat")
            )
            _activeConversationId.value = id
            onCreated(id)
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_activeConversationId.value == id) {
                _activeConversationId.value = null
            }
        }
    }

    fun renameConversation(id: Long, title: String) {
        viewModelScope.launch {
            repository.updateConversationTitle(id, title)
        }
    }
}
