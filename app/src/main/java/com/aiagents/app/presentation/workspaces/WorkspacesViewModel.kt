package com.aiagents.app.presentation.workspaces

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.MainActivity
import com.aiagents.app.R
import com.aiagents.app.data.remote.ChatMessage
import com.aiagents.app.data.repository.AgentRepository
import com.aiagents.app.data.repository.FileRepository
import com.aiagents.app.domain.model.Agent
import com.aiagents.app.domain.model.ProviderType
import com.aiagents.app.domain.model.Workspace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkspaceFormState(
    val name: String = "",
    val description: String = "",
    val selectedAgentId: Long? = null,
    val systemPrompt: String = "",
    val useExternalStorage: Boolean = false,
    val externalStorageUri: String? = null,
    val externalStorageDisplayName: String? = null
)

data class WorkspaceUiState(
    val formState: WorkspaceFormState = WorkspaceFormState(),
    val isLoading: Boolean = false,
    val showCreateDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showRenameDialog: Boolean = false,
    val showContextMenu: Boolean = false,
    val showChangeStorageDialog: Boolean = false,
    val workspaceToDelete: Workspace? = null,
    val workspaceToRename: Workspace? = null,
    val workspaceToChangeStorage: Workspace? = null,
    val selectedWorkspace: Workspace? = null,
    val exportSuccess: Boolean = false,
    val exportPath: String? = null,
    val exportingWorkspaceId: Long? = null
)

@HiltViewModel
class WorkspacesViewModel @Inject constructor(
    private val repository: AgentRepository,
    private val fileRepository: FileRepository,
    private val application: Application
) : AndroidViewModel(application) {

    val workspaces: StateFlow<List<Workspace>> = repository.getAllWorkspaces()
        .map { list -> list.filter { it.name != "__global__" } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val agents: StateFlow<List<Agent>> = repository.getAllAgents()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
    }

    fun hideCreateDialog() {
        _uiState.value = _uiState.value.copy(
            showCreateDialog = false,
            formState = WorkspaceFormState()
        )
    }

    fun updateFormState(formState: WorkspaceFormState) {
        _uiState.value = _uiState.value.copy(formState = formState)
    }

    fun createWorkspace(onSuccess: (Long) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val state = _uiState.value.formState
            val workspace = Workspace(
                name = state.name,
                description = state.description,
                activeAgentId = state.selectedAgentId,
                systemPrompt = state.systemPrompt,
                externalStorageUri = if (state.useExternalStorage) state.externalStorageUri else null
            )
            val id = repository.createWorkspace(workspace)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                showCreateDialog = false,
                formState = WorkspaceFormState()
            )
            onSuccess(id)
        }
    }

    fun showDeleteDialog(workspace: Workspace) {
        _uiState.value = _uiState.value.copy(
            showDeleteDialog = true,
            workspaceToDelete = workspace,
            showContextMenu = false
        )
    }

    fun hideDeleteDialog() {
        _uiState.value = _uiState.value.copy(
            showDeleteDialog = false,
            workspaceToDelete = null
        )
    }

    fun deleteWorkspace() {
        viewModelScope.launch {
            _uiState.value.workspaceToDelete?.let { workspace ->
                repository.deleteWorkspace(workspace.id)
            }
            hideDeleteDialog()
        }
    }

    // Context Menu
    fun showContextMenu(workspace: Workspace) {
        _uiState.value = _uiState.value.copy(
            showContextMenu = true,
            selectedWorkspace = workspace
        )
    }

    fun hideContextMenu() {
        _uiState.value = _uiState.value.copy(
            showContextMenu = false,
            selectedWorkspace = null
        )
    }

    // Rename functionality
    fun showRenameDialog(workspace: Workspace) {
        _uiState.value = _uiState.value.copy(
            showRenameDialog = true,
            workspaceToRename = workspace,
            formState = WorkspaceFormState(
                name = workspace.name,
                description = workspace.description
            ),
            showContextMenu = false
        )
    }

    fun hideRenameDialog() {
        _uiState.value = _uiState.value.copy(
            showRenameDialog = false,
            workspaceToRename = null,
            formState = WorkspaceFormState()
        )
    }

    fun renameWorkspace() {
        viewModelScope.launch {
            val workspaceToRename = _uiState.value.workspaceToRename
            val formState = _uiState.value.formState
            
            if (workspaceToRename != null && formState.name.isNotBlank()) {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                val updatedWorkspace = workspaceToRename.copy(
                    name = formState.name,
                    description = formState.description
                )
                repository.updateWorkspace(updatedWorkspace)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    showRenameDialog = false,
                    workspaceToRename = null,
                    formState = WorkspaceFormState()
                )
                
                Toast.makeText(
                    getApplication(),
                    "Workspace renombrado exitosamente",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Change storage
    fun showChangeStorageDialog(workspace: Workspace) {
        _uiState.value = _uiState.value.copy(
            showChangeStorageDialog = true,
            workspaceToChangeStorage = workspace,
            formState = WorkspaceFormState(
                useExternalStorage = workspace.externalStorageUri != null,
                externalStorageUri = workspace.externalStorageUri
            ),
            showContextMenu = false
        )
    }

    fun hideChangeStorageDialog() {
        _uiState.value = _uiState.value.copy(
            showChangeStorageDialog = false,
            workspaceToChangeStorage = null,
            formState = WorkspaceFormState()
        )
    }

    fun updateWorkspaceStorage() {
        viewModelScope.launch {
            val workspace = _uiState.value.workspaceToChangeStorage ?: return@launch
            val formState = _uiState.value.formState
            _uiState.value = _uiState.value.copy(isLoading = true)

            val newUri = if (formState.useExternalStorage) formState.externalStorageUri else null
            repository.setExternalStorageUri(workspace.id, newUri)

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                showChangeStorageDialog = false,
                workspaceToChangeStorage = null,
                formState = WorkspaceFormState()
            )

            Toast.makeText(
                getApplication(),
                "Almacenamiento actualizado",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ── AGENTES.md export ─────────────────────────────────────────────────
    // Uses an application-scoped coroutine so the generation survives
    // screen changes and even the ViewModel being cleared.

    companion object {
        private const val EXPORT_CHANNEL_ID = "agentes_md_export"
        private const val EXPORT_NOTIFICATION_ID = 60000
        /** Application-scoped coroutine — survives ViewModel & Activity lifecycle */
        private val exportScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    init {
        createExportNotificationChannel()
    }

    private fun createExportNotificationChannel() {
        val channel = NotificationChannel(
            EXPORT_CHANNEL_ID,
            "Generación de AGENTES.md",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progreso de la generación del archivo AGENTES.md"
        }
        val nm = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun showExportNotification(title: String, text: String, ongoing: Boolean, progress: Boolean = false) {
        val intent = Intent(application, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            application, EXPORT_NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(application, EXPORT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)

        if (progress) {
            builder.setProgress(0, 0, true) // indeterminate
        }

        try {
            val nm = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(EXPORT_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) { /* no notification permission */ }
    }

    private fun dismissExportNotification() {
        val nm = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(EXPORT_NOTIFICATION_ID)
    }

    fun exportWorkspace(workspace: Workspace) {
        // Prevent double-launch
        if (_uiState.value.exportingWorkspaceId != null) return

        _uiState.value = _uiState.value.copy(
            showContextMenu = false,
            exportingWorkspaceId = workspace.id
        )

        showExportNotification(
            "Generando AGENTES.md",
            "Analizando workspace \"${workspace.name}\"...",
            ongoing = true,
            progress = true
        )

        // Launch in application scope so it survives navigation & ViewModel clearing
        exportScope.launch {
            try {
                // 1. Gather all workspace data
                val messages = repository.getMessagesForWorkspace(workspace.id).first()
                val files = repository.getFilesForWorkspace(workspace.id).first()
                val agentName = workspace.activeAgentId?.let { id ->
                    repository.getAgentById(id)?.name
                }

                // 2. Build raw context for the LLM
                val rawContext = fileRepository.buildWorkspaceRawContext(
                    workspaceName = workspace.name,
                    workspaceDescription = workspace.description,
                    messages = messages,
                    files = files,
                    agentName = agentName
                )

                // 3. Resolve model
                val selectedModels = repository.getSelectedModels()
                val fullKey = selectedModels.firstOrNull() ?: ""
                val modelId = if ("|" in fullKey) fullKey.substringAfter("|") else fullKey
                val provider: ProviderType? = if ("|" in fullKey)
                    runCatching { ProviderType.valueOf(fullKey.substringBefore("|")) }.getOrNull()
                else null

                showExportNotification(
                    "Generando AGENTES.md",
                    "Modelo analizando conversaciones...",
                    ongoing = true,
                    progress = true
                )

                // 4. Call the LLM
                val systemPrompt = """You are a context extraction agent. Analyze ALL conversations and files in this workspace to produce an AGENTES.md file. This file is consumed exclusively by AI agents to have full context when assisting the user. It is NOT for human reading — optimize for maximum information density with minimum tokens.

Review every conversation thoroughly. Extract all factual information the agent needs to continue assisting effectively, regardless of the domain (coding, research, planning, personal advice, learning, etc.).

Structure:

# [Workspace Name]

## What this is about
One paragraph. What is the user working on or trying to accomplish in this workspace.

## Key facts
Bullet list of all factual information extracted from conversations: decisions made, preferences stated, constraints, requirements, names, dates, numbers, services, tools, APIs (by name, never actual keys), dependencies, people involved, specific details the user has shared. Include everything an agent would need to not ask the user to repeat themselves.

## Current state
What has been done. What worked, what didn't. Key conclusions reached.

## Files
List workspace files with a one-line description of each (inferred from conversations).

## Pending
Tasks discussed but not completed. Next steps mentioned. Open questions.

Rules:
- Write in the language the user used in conversations
- ONLY factual information derived from the provided data — no assumptions, no filler
- Be dense — no decorative text, no transition sentences, no redundancy
- Every line must carry information. If a section has nothing to report, omit it
- Do NOT include conversation transcripts — only extracted knowledge"""

                val chatMessages = listOf(
                    ChatMessage(
                        role = "user",
                        content = "Analyze this workspace and generate the AGENTES.md:\n\n$rawContext"
                    )
                )

                val result = repository.chat(
                    model = modelId,
                    messages = chatMessages,
                    systemPrompt = systemPrompt,
                    temperature = 0.3f,
                    maxTokens = 4096,
                    provider = provider
                )

                result.onSuccess { generatedContent ->
                    val writeResult = fileRepository.writeCortexFile(workspace.id, generatedContent)

                    writeResult.onSuccess { file ->
                        _uiState.value = _uiState.value.copy(
                            exportingWorkspaceId = null,
                            exportSuccess = true,
                            exportPath = file.absolutePath
                        )
                        showExportNotification(
                            "AGENTES.md listo",
                            "\"${workspace.name}\" — archivo generado exitosamente",
                            ongoing = false
                        )
                        kotlinx.coroutines.delay(3000)
                        _uiState.value = _uiState.value.copy(exportSuccess = false, exportPath = null)
                    }.onFailure { error ->
                        _uiState.value = _uiState.value.copy(exportingWorkspaceId = null)
                        showExportNotification(
                            "Error al guardar AGENTES.md",
                            error.message ?: "Error desconocido",
                            ongoing = false
                        )
                    }
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(exportingWorkspaceId = null)
                    showExportNotification(
                        "Error generando AGENTES.md",
                        error.message ?: "Error desconocido",
                        ongoing = false
                    )
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(exportingWorkspaceId = null)
                showExportNotification(
                    "Error generando AGENTES.md",
                    e.message ?: "Error desconocido",
                    ongoing = false
                )
            }
        }
    }
}
