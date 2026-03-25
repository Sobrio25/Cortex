package com.aiagents.app.presentation.workspace_detail

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.local.LocalLLMClient
import com.aiagents.app.data.local.LocalModelRepository
import com.aiagents.app.data.model.PermissionLevel
import com.aiagents.app.data.orchestration.AgentOrchestrator
import com.aiagents.app.data.orchestration.DelegationResult
import com.aiagents.app.data.orchestration.IsolatedAgentExecutor
import com.aiagents.app.data.orchestration.IsolatedExecutionResult
import com.aiagents.app.data.orchestration.SubAgentProgress
import com.aiagents.app.data.orchestration.ParallelDelegationEntry
import com.aiagents.app.data.remote.ChatMessage
import com.aiagents.app.data.remote.ChatResponseWithTools
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.repository.AgentRepository
import com.aiagents.app.data.repository.ContextInfo
import com.aiagents.app.data.repository.FileRepository
import com.aiagents.app.data.repository.TokenCounter
import com.aiagents.app.data.terminal.BraveSearchToolHandler
import com.aiagents.app.data.terminal.DuckDuckGoSearchToolHandler
import com.aiagents.app.data.terminal.GoogleMapsToolHandler
import com.aiagents.app.data.terminal.SerpAPIToolHandler
import com.aiagents.app.data.terminal.AgentCreatorToolHandler
import com.aiagents.app.data.terminal.CanvaToolHandler
import com.aiagents.app.data.terminal.FinanceToolHandler
import com.aiagents.app.data.terminal.PubMedToolHandler
import com.aiagents.app.data.terminal.LocationToolHandler
import com.aiagents.app.data.terminal.ObsidianToolHandler
import com.aiagents.app.data.terminal.GitHubToolHandler
import com.aiagents.app.data.terminal.NotionToolHandler
import com.aiagents.app.data.terminal.SlackToolHandler
import com.aiagents.app.data.terminal.GoogleDriveToolHandler
import com.aiagents.app.data.terminal.GoogleWorkspaceToolHandler
import com.aiagents.app.data.terminal.ReminderToolHandler
import com.aiagents.app.data.terminal.CodeExecutionHandler
import com.aiagents.app.data.terminal.PresentationToolHandler
import com.aiagents.app.data.terminal.MemoryToolHandler
import com.aiagents.app.data.terminal.AppControlToolHandler
import com.aiagents.app.data.terminal.ScheduledTaskToolHandler
import com.aiagents.app.data.terminal.TodoToolHandler
import com.aiagents.app.data.terminal.ToolSearchHandler
import com.aiagents.app.data.terminal.AcademicSearchToolHandler
import com.aiagents.app.data.terminal.WeatherToolHandler
import com.aiagents.app.data.terminal.ImageGenerationToolHandler
import com.aiagents.app.data.remote.StreamingChunk
import com.aiagents.app.data.terminal.MemoryExtractor
import com.aiagents.app.data.terminal.TaskCompletionNotifier
import com.aiagents.app.data.terminal.SubtaskToolHandler
import com.aiagents.app.data.terminal.DelegationToolHandler
import com.aiagents.app.data.terminal.PermissionRequest
import com.aiagents.app.data.terminal.ToolExecutionRequest
import com.aiagents.app.data.terminal.ToolExecutionResult
import com.aiagents.app.domain.model.Agent
import com.aiagents.app.domain.model.AgentFile
import com.aiagents.app.domain.model.Conversation
import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ProviderType
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolResult
import com.aiagents.app.domain.model.Workspace
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.aiagents.app.data.terminal.SystemAppToolHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

data class OptionSelectionRequest(
    val title: String,
    val options: List<String>,
    val messageContent: String = ""
)

data class MultiQuestionQueue(
    val questions: List<OptionSelectionRequest>,
    val answers: List<String> = emptyList(),
    val currentIndex: Int = 0
) {
    val currentQuestion: OptionSelectionRequest get() = questions[currentIndex]
    val isLastQuestion: Boolean get() = currentIndex >= questions.size - 1
    val progress: String get() = "${currentIndex + 1}/${questions.size}"
}

data class AgentBoardMessage(
    val agentName: String,
    val message: String,
    val timestamp: Long
)

data class TerminalEntry(
    val command: String,
    val output: String,
    val isSuccess: Boolean,
    val executionTimeMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

data class WorkspaceDetailState(
    val inputText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val activeTab: WorkspaceTab = WorkspaceTab.Chat,
    val attachedFiles: List<AttachedFile> = emptyList(),
    val showInfoDialog: Boolean = false,
    val showModelSelector: Boolean = false,
    val availableModels: List<String> = emptyList(),
    val pendingPermissionRequest: PermissionRequest? = null,
    val pendingToolExecution: ToolExecutionRequest? = null,
    val executingCommand: String? = null,
    val terminalHistory: List<TerminalEntry> = emptyList(),
    val terminalInput: String = "",
    val terminalIsExecuting: Boolean = false,
    val terminalPendingCommand: String? = null,
    val pendingCalendarPermission: Boolean = false,
    val pendingCalendarToolCall: ToolCall? = null,
    val pendingCalendarAgent: Agent? = null,
    val pendingCameraPermission: Boolean = false,
    val pendingCameraToolCall: ToolCall? = null,
    val pendingCameraAgent: Agent? = null,
    val pendingCameraCapture: Boolean = false,
    val showReasoning: Boolean = false,
    val showCommands: Boolean = false,
    val currentReasoning: String? = null,
    val pendingOptionSelection: OptionSelectionRequest? = null,
    val pendingQuestionQueue: MultiQuestionQueue? = null,
    val showContextCompactionDialog: Boolean = false,
    val isCompacting: Boolean = false,
    val pendingLocationPermission: Boolean = false,
    val pendingLocationToolCall: ToolCall? = null,
    val pendingLocationAgent: Agent? = null,
    val webPreviewHtml: String? = null,
    val webPreviewUrl: String? = null,
    val webPreviewTitle: String = "Preview",
    val streamingContent: String? = null,
    val streamingReasoning: String? = null,
    val todos: List<com.aiagents.app.data.model.TodoEntity> = emptyList()
)

data class AttachedFile(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long = 0
)

data class ModelInfo(
    val provider: ProviderType,
    val modelId: String,
    val fullKey: String = "${provider.name}|$modelId" // "PROVIDER|modelId"
)

enum class WorkspaceTab {
    Chat, Files
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkspaceDetailViewModel @Inject constructor(
    private val repository: AgentRepository,
    private val fileRepository: FileRepository,
    private val agentOrchestrator: AgentOrchestrator,
    private val localModelRepository: LocalModelRepository,
    private val securePreferences: SecurePreferences,
    private val memoryExtractor: MemoryExtractor,
    private val taskCompletionNotifier: TaskCompletionNotifier,
    private val isolatedAgentExecutor: IsolatedAgentExecutor,
    private val codeExecutionHandler: CodeExecutionHandler,
    private val todoDao: com.aiagents.app.data.local.TodoDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // workspaceId: from nav arg first, then from active workspace preference
    private val workspaceId: Long = (savedStateHandle.get<Long>("workspaceId") ?: 0L).let { navId ->
        if (navId > 0) navId else securePreferences.getActiveWorkspaceId().let { if (it > 0) it else 0L }
    }

    private val gson = Gson()

    // conversationId: from nav arg (chat/{conversationId} route)
    private val _conversationId = MutableStateFlow<Long?>(savedStateHandle.get<Long>("conversationId"))
    val conversationId: StateFlow<Long?> = _conversationId.asStateFlow()

    private val _workspace = MutableStateFlow<Workspace?>(null)
    val workspace: StateFlow<Workspace?> = _workspace.asStateFlow()

    private val _activeAgent = MutableStateFlow<Agent?>(null)
    val activeAgent: StateFlow<Agent?> = _activeAgent.asStateFlow()

    private val _selectedModel = MutableStateFlow<String>("")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    val agents: StateFlow<List<Agent>> = repository.getAllAgents()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val messages: StateFlow<List<Message>> = _conversationId.flatMapLatest { convId ->
        if (convId != null && convId > 0) {
            repository.getMessagesForConversation(convId)
        } else {
            // No conversation selected: show empty (new chat state)
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _uiState = MutableStateFlow(WorkspaceDetailState())
    val uiState: StateFlow<WorkspaceDetailState> = _uiState.asStateFlow()

    // Observe todos for the active conversation, update UI state reactively
    init {
        viewModelScope.launch {
            _conversationId.flatMapLatest { convId ->
                if (convId != null && convId > 0) todoDao.observeTodos(convId)
                else flowOf(emptyList())
            }.collect { todos ->
                _uiState.value = _uiState.value.copy(todos = todos)
            }
        }
    }

    /** Returns a Flow of messages for a sub-conversation (for UI expansion) */
    fun getSubConversationMessages(subConversationId: Long): Flow<List<Message>> {
        return repository.getMessagesForConversation(subConversationId)
    }

    // Archivos de la base de datos
    private val dbFiles: StateFlow<List<AgentFile>> = repository.getFilesForWorkspace(workspaceId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Archivos escaneados del directorio en tiempo real
    private val _scannedFiles = MutableStateFlow<List<AgentFile>>(emptyList())

    // Combinación de archivos de BD y escaneados
    val files: StateFlow<List<AgentFile>> = combine(dbFiles, _scannedFiles) { db, scanned ->
        // Unir ambas listas, dando prioridad a los de BD (pueden tener más metadatos)
        val merged = mutableMapOf<String, AgentFile>()

        // Primero agregar los de BD
        db.forEach { file ->
            merged[file.name] = file
        }

        // Luego agregar los escaneados que no estén en BD
        scanned.forEach { file ->
            if (!merged.containsKey(file.name)) {
                merged[file.name] = file
            }
        }

        merged.values.toList().sortedByDescending { it.uploadedAt }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _workingAgents = MutableStateFlow<List<String>>(emptyList())
    val workingAgents: StateFlow<List<String>> = _workingAgents.asStateFlow()

    /** Per-agent activity status (what each agent is currently doing) */
    private val _agentStatuses = MutableStateFlow<Map<String, String>>(emptyMap())
    val agentStatuses: StateFlow<Map<String, String>> = _agentStatuses.asStateFlow()

    /** Shared message board for inter-agent communication during parallel work */
    private val _agentMessageBoard = MutableStateFlow<List<AgentBoardMessage>>(emptyList())

    fun updateAgentStatus(agentName: String, status: String) {
        _agentStatuses.value = _agentStatuses.value + (agentName to status)
    }

    private fun clearAgentStatus(agentName: String) {
        _agentStatuses.value = _agentStatuses.value - agentName
    }

    private fun clearAllAgentStatuses() {
        _agentStatuses.value = emptyMap()
        _agentMessageBoard.value = emptyList()
    }

    /** Post a message to the shared board (visible to other parallel agents) */
    private fun postToBoard(agentName: String, message: String) {
        val entry = AgentBoardMessage(agentName, message, System.currentTimeMillis())
        _agentMessageBoard.value = _agentMessageBoard.value + entry
    }

    /** Read recent board messages (excluding own) for inter-agent awareness */
    private fun readBoard(excludeAgent: String): String {
        val others = _agentMessageBoard.value.filter { it.agentName != excludeAgent }
        if (others.isEmpty()) return ""
        return others.joinToString("\n") { "- ${it.agentName}: ${it.message}" }
    }

    /** Timestamp when the current task started (for notification threshold) */
    private var taskStartTimeMs: Long = 0L

    /** Job for debounced draft auto-save */
    private var draftSaveJob: kotlinx.coroutines.Job? = null

    /** Current agent processing job — cancel to stop the agent */
    private var currentAgentJob: kotlinx.coroutines.Job? = null

    /** Current delegation depth for recursive sub-agent control (0 = top-level) */
    private var currentDelegationDepth: Int = 0

    /** Pending agent names for sequential delegation pipeline */
    private val _pendingSequentialAgents = MutableStateFlow<MutableList<String>>(mutableListOf())
    private val _sequentialUserMessage = MutableStateFlow<Message?>(null)

    val contextInfo: StateFlow<ContextInfo> = combine(
        messages,
        selectedModel
    ) { msgs, fullKey ->
        val modelId = if ("|" in fullKey) fullKey.substringAfter("|") else fullKey
        val currentTokens = TokenCounter.estimateMessagesTokens(msgs)
        ContextInfo(
            currentTokens = currentTokens,
            maxTokens = TokenCounter.getContextWindowForModel(modelId),
            usagePercentage = TokenCounter.getContextUsagePercentage(currentTokens, modelId),
            availableTokens = TokenCounter.getAvailableTokens(currentTokens, modelId),
            supportsVision = TokenCounter.supportsVision(modelId),
            supportsDocuments = TokenCounter.supportsDocuments(modelId)
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, ContextInfo(0, 8192, 0f, 8192, false, false))

    init {
        loadWorkspace()
        loadAvailableModels()
        observeSelectedModelsChanges()
        startFileScanning()
        loadShowReasoningPreference()
        loadShowCommandsPreference()
        restoreDraft()
    }

    private fun restoreDraft() {
        val draft = securePreferences.getDraft(workspaceId)
        if (draft.isNotBlank()) {
            _uiState.value = _uiState.value.copy(inputText = draft)
        }
    }
    
    private fun loadShowReasoningPreference() {
        val showReasoning = repository.getShowReasoning()
        _uiState.value = _uiState.value.copy(showReasoning = showReasoning)
    }
    
    fun setShowReasoning(enabled: Boolean) {
        repository.setShowReasoning(enabled)
        _uiState.value = _uiState.value.copy(showReasoning = enabled)
    }
    
    fun getShowReasoning(): Boolean {
        return repository.getShowReasoning()
    }

    private fun loadShowCommandsPreference() {
        val showCommands = repository.getShowCommands()
        _uiState.value = _uiState.value.copy(showCommands = showCommands)
    }

    fun setShowCommands(enabled: Boolean) {
        repository.setShowCommands(enabled)
        _uiState.value = _uiState.value.copy(showCommands = enabled)
    }

    fun getShowCommands(): Boolean {
        return repository.getShowCommands()
    }

    /** Notify user if the task took longer than the configured threshold */
    private fun notifyTaskCompletedIfLong(responsePreview: String) {
        if (taskStartTimeMs <= 0L) return
        val duration = System.currentTimeMillis() - taskStartTimeMs
        val agentName = _activeAgent.value?.name ?: "Agente"
        taskCompletionNotifier.notifyTaskCompleted(agentName, duration, responsePreview)
        taskStartTimeMs = 0L
    }
    
    private fun startFileScanning() {
        viewModelScope.launch {
            // Escanear cada 2 segundos
            while (true) {
                scanWorkspaceFiles()
                kotlinx.coroutines.delay(2000)
            }
        }
    }
    
    private suspend fun scanWorkspaceFiles() {
        try {
            val physicalFiles = fileRepository.listFiles(workspaceId)
            val scanned = physicalFiles.map { file ->
                AgentFile(
                    id = 0, // ID temporal para archivos no registrados en BD
                    workspaceId = workspaceId,
                    name = file.name,
                    path = file.absolutePath,
                    size = file.length(),
                    mimeType = getMimeType(file.name),
                    uploadedAt = file.lastModified(),
                    generatedByAI = file.name.startsWith("generated_") ||
                                   file.name.contains("cortex") ||
                                   file.name.substringAfterLast(".", "") in listOf("kt", "java", "py", "js", "ts", "html", "css", "json", "xml", "sql", "md", "txt")
                )
            }
            _scannedFiles.value = scanned
        } catch (e: Exception) {
            Log.e("WorkspaceDetailVM", "Error scanning files", e)
        }
    }
    
    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast(".", "").lowercase()) {
            "txt" -> "text/plain"
            "md" -> "text/markdown"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "ts" -> "application/typescript"
            "kt" -> "text/x-kotlin"
            "java" -> "text/x-java"
            "py" -> "text/x-python"
            "sql" -> "text/x-sql"
            "sh" -> "text/x-shellscript"
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }
    
    private val File.extension: String
        get() = name.substringAfterLast(".", "")
    
    fun refreshFiles() {
        viewModelScope.launch {
            scanWorkspaceFiles()
        }
    }

    private fun loadWorkspace() {
        viewModelScope.launch {
            val ws = repository.getWorkspaceById(workspaceId)
            _workspace.value = ws

            // Si hay un agente configurado, usarlo; si no, usar Cortex por defecto
            val agentId = ws?.activeAgentId
            if (agentId != null) {
                _activeAgent.value = repository.getAgentById(agentId)
            } else {
                // Buscar y asignar el orquestador como agente por defecto
                val orchestrator = repository.getOrchestratorAgent()
                if (orchestrator != null) {
                    _activeAgent.value = orchestrator
                    repository.setActiveAgent(workspaceId, orchestrator.id)
                }
            }

            if (!ws?.selectedModel.isNullOrEmpty()) {
                _selectedModel.value = ws?.selectedModel ?: ""
            } else {
                // Auto-select first available model if none is set
                val available = buildAvailableModelsList()
                if (available.isNotEmpty()) {
                    setSelectedModel(available.first())
                }
            }
        }
    }

    private fun loadAvailableModels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(availableModels = buildAvailableModelsList())
        }
    }

    private fun observeSelectedModelsChanges() {
        viewModelScope.launch {
            repository.selectedModelsFlow.collect {
                val available = buildAvailableModelsList()
                _uiState.value = _uiState.value.copy(availableModels = available)
                // Auto-select first model if none is currently selected
                if (_selectedModel.value.isEmpty() && available.isNotEmpty()) {
                    setSelectedModel(available.first())
                }
            }
        }
    }

    private suspend fun buildAvailableModelsList(): List<String> {
        val selected = repository.getSelectedModels().toMutableSet()
        val downloadedLocalModels = localModelRepository.getDownloadedModels()
        downloadedLocalModels.forEach { model ->
            selected.add("${ProviderType.LOCAL.name}|${model.id}")
        }
        return selected.toList().sorted()
    }

    /** Extrae solo el modelId del formato "PROVIDER|modelId" */
    private fun extractModelId(fullKey: String): String =
        if ("|" in fullKey) fullKey.substringAfter("|") else fullKey

    /** Extrae el ProviderType del formato "PROVIDER|modelId" */
    private fun extractProvider(fullKey: String): ProviderType? =
        if ("|" in fullKey) runCatching { ProviderType.valueOf(fullKey.substringBefore("|")) }.getOrNull()
        else null

    fun setActiveAgent(agentId: Long?) {
        viewModelScope.launch {
            repository.setActiveAgent(workspaceId, agentId)
            _activeAgent.value = agentId?.let { repository.getAgentById(it) }
        }
    }

    fun setSelectedModel(model: String) {
        _selectedModel.value = model
        viewModelScope.launch {
            repository.setSelectedModel(workspaceId, model)
        }
    }

    fun showModelSelector() {
        _uiState.value = _uiState.value.copy(showModelSelector = true)
    }

    fun hideModelSelector() {
        _uiState.value = _uiState.value.copy(showModelSelector = false)
    }

    fun getAllModels(): List<ModelInfo> {
        return _uiState.value.availableModels.mapNotNull { fullKey ->
            val provider = extractProvider(fullKey) ?: return@mapNotNull null
            val modelId = extractModelId(fullKey)
            ModelInfo(provider, modelId, fullKey)
        }
    }

    fun updateInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
        // Auto-save draft with debounce (500ms)
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            if (text.isNotBlank()) {
                securePreferences.saveDraft(workspaceId, text)
            } else {
                securePreferences.clearDraft(workspaceId)
            }
        }
    }

    // ── Option Selection ─────────────────────────────────────────────────────

    fun selectOption(option: String) {
        val queue = _uiState.value.pendingQuestionQueue
        if (queue != null) {
            val updatedAnswers = queue.answers + option
            if (queue.isLastQuestion) {
                // All questions answered — build combined response and send
                val combinedResponse = queue.questions.zip(updatedAnswers) { q, a ->
                    "**${q.title}**: $a"
                }.joinToString("\n")
                _uiState.value = _uiState.value.copy(
                    pendingOptionSelection = null,
                    pendingQuestionQueue = null,
                    inputText = combinedResponse
                )
                sendMessage()
            } else {
                // Show next question
                val nextIndex = queue.currentIndex + 1
                val updatedQueue = queue.copy(answers = updatedAnswers, currentIndex = nextIndex)
                _uiState.value = _uiState.value.copy(
                    pendingQuestionQueue = updatedQueue,
                    pendingOptionSelection = updatedQueue.currentQuestion
                )
            }
        } else {
            // Single question (legacy fallback)
            _uiState.value = _uiState.value.copy(
                pendingOptionSelection = null,
                inputText = option
            )
            sendMessage()
        }
    }

    fun dismissOptionSelection() {
        val queue = _uiState.value.pendingQuestionQueue
        if (queue != null && queue.answers.isNotEmpty()) {
            // Already answered some questions — send what we have
            val combinedResponse = queue.questions.take(queue.answers.size)
                .zip(queue.answers) { q, a -> "**${q.title}**: $a" }
                .joinToString("\n")
            _uiState.value = _uiState.value.copy(
                pendingOptionSelection = null,
                pendingQuestionQueue = null,
                inputText = combinedResponse
            )
            sendMessage()
        } else {
            _uiState.value = _uiState.value.copy(
                pendingOptionSelection = null,
                pendingQuestionQueue = null
            )
        }
    }

    private val OPTIONS_TAG_REGEX = Regex(
        """<ask_options\s+(?:titulo|title)="([^"]+)">([\s\S]*?)</ask_options>""",
        RegexOption.IGNORE_CASE
    )
    private val OPTION_ITEM_REGEX = Regex("""^\s*[-•*]\s+(.+)$""", RegexOption.MULTILINE)

    /**
     * Detecta todos los bloques <ask_options> en el contenido del agente.
     * Retorna la lista de requests y el contenido limpio (sin los bloques).
     */
    private fun parseAllOptions(content: String): Pair<List<OptionSelectionRequest>, String> {
        val matches = OPTIONS_TAG_REGEX.findAll(content).toList()
        if (matches.isEmpty()) return Pair(emptyList(), content)

        val requests = matches.mapNotNull { match ->
            val title = match.groupValues[1].trim()
            val optionsBlock = match.groupValues[2]
            val options = OPTION_ITEM_REGEX.findAll(optionsBlock)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotEmpty() }
                .toList()
            if (options.isNotEmpty()) OptionSelectionRequest(title, options) else null
        }

        var cleanContent = content
        for (match in matches) {
            cleanContent = cleanContent.replace(match.value, "")
        }
        return Pair(requests, cleanContent.trim())
    }

    /**
     * Parsea opciones del contenido, actualiza el estado si las encuentra y
     * retorna el contenido sin los bloques <ask_options>.
     * Soporta múltiples bloques: los encola y muestra uno por uno.
     */
    private fun checkAndSetOptions(content: String): String {
        val (requests, cleanContent) = parseAllOptions(content)
        if (requests.isNotEmpty()) {
            // Attach the clean message content to the first request so the bottom sheet can show it
            val requestsWithContent = requests.mapIndexed { index, req ->
                if (index == 0 && cleanContent.isNotBlank()) req.copy(messageContent = cleanContent)
                else req
            }
            if (requestsWithContent.size == 1) {
                // Single question — no queue needed
                _uiState.value = _uiState.value.copy(
                    pendingOptionSelection = requestsWithContent.first(),
                    pendingQuestionQueue = null
                )
            } else {
                // Multiple questions — use queue
                val queue = MultiQuestionQueue(questions = requestsWithContent)
                _uiState.value = _uiState.value.copy(
                    pendingOptionSelection = queue.currentQuestion,
                    pendingQuestionQueue = queue
                )
            }
        }
        return cleanContent
    }

    // ── Context Window Compaction ──────────────────────────────────────────

    /** Threshold: show compaction dialog when context usage exceeds this percentage */
    private val COMPACTION_THRESHOLD = 80f
    /** After compaction was dismissed, don't ask again until this percentage */
    private val COMPACTION_RE_ASK_THRESHOLD = 95f
    private var compactionDismissedAt: Float = 0f

    /**
     * Checks if context window is getting full and prompts the user.
     * Called after each API response.
     */
    private fun checkContextWindowUsage() {
        val info = contextInfo.value
        val threshold = if (compactionDismissedAt > 0f) COMPACTION_RE_ASK_THRESHOLD else COMPACTION_THRESHOLD
        if (info.usagePercentage >= threshold && !_uiState.value.showContextCompactionDialog && !_uiState.value.isCompacting) {
            _uiState.value = _uiState.value.copy(showContextCompactionDialog = true)
        }
    }

    fun acceptContextCompaction() {
        _uiState.value = _uiState.value.copy(showContextCompactionDialog = false, isCompacting = true)
        compactionDismissedAt = 0f
        viewModelScope.launch {
            try {
                performContextCompaction()
            } catch (e: Exception) {
                Log.e("WorkspaceDetailVM", "Context compaction failed", e)
                _uiState.value = _uiState.value.copy(
                    isCompacting = false,
                    error = "Error compacting context: ${e.message}"
                )
            }
        }
    }

    fun dismissContextCompaction() {
        compactionDismissedAt = contextInfo.value.usagePercentage
        _uiState.value = _uiState.value.copy(showContextCompactionDialog = false)
    }

    private suspend fun performContextCompaction() {
        val currentMessages = messages.value
        if (currentMessages.size < 4) {
            _uiState.value = _uiState.value.copy(isCompacting = false)
            return
        }

        val fullKey = _selectedModel.value
        val modelId = extractModelId(fullKey)
        val provider = extractProvider(fullKey)

        // Build a summarization prompt with the conversation content
        val conversationText = buildString {
            currentMessages.forEach { msg ->
                val role = msg.role.name.uppercase()
                val content = msg.content.take(2000) // Cap per message to avoid overflowing the summarization call itself
                if (content.isNotBlank()) {
                    appendLine("[$role]: $content")
                    appendLine()
                }
            }
        }

        // Use a compact summary prompt
        val summarySystemPrompt = """You are a conversation summarizer. Create a CONCISE summary of the conversation below.
RULES:
- Keep all KEY FACTS: names, decisions, code snippets, file paths, URLs, numbers, preferences
- Keep the USER'S ORIGINAL REQUEST and what was accomplished
- Keep any PENDING TASKS or unfinished work
- Keep tool results that contain important data
- Remove small talk, repetitive exchanges, and verbose tool call details
- Format as a structured summary with sections
- Write in the same language as the conversation
- Maximum 1500 words"""

        val summaryMessages = listOf(
            com.aiagents.app.data.remote.ChatMessage(
                role = "user",
                content = "Summarize this conversation:\n\n$conversationText"
            )
        )

        val result = repository.chat(
            model = modelId,
            messages = summaryMessages,
            systemPrompt = summarySystemPrompt,
            temperature = 0.2f,
            maxTokens = 4096,
            provider = provider
        )

        result.onSuccess { summary ->
            val summaryText = summary.ifBlank { "Conversation summary unavailable." }

            // Delete old messages for this conversation
            val convId = _conversationId.value
            if (convId != null) {
                repository.clearConversation(convId)
            }

            // Insert the summary as a system message so the agent has context
            val summaryMessage = Message(
                role = MessageRole.SYSTEM,
                content = "[Context Compacted] Previous conversation summary:\n\n$summaryText"
            )
            repository.addMessage(workspaceId, convId, summaryMessage, _activeAgent.value?.id ?: 0)

            // Insert a visible assistant message so the user sees what happened
            val visibleMessage = Message(
                role = MessageRole.ASSISTANT,
                content = "The context window has been compacted. I've summarized our previous conversation to free up space. All important details have been preserved — we can continue where we left off."
            )
            repository.addMessage(workspaceId, convId, visibleMessage, _activeAgent.value?.id ?: 0)

            _uiState.value = _uiState.value.copy(isCompacting = false)
        }.onFailure { error ->
            Log.e("WorkspaceDetailVM", "Summarization failed", error)
            _uiState.value = _uiState.value.copy(
                isCompacting = false,
                error = "Failed to summarize conversation: ${error.message}"
            )
        }
    }

    fun updateTerminalInput(text: String) {
        _uiState.value = _uiState.value.copy(terminalInput = text)
    }

    fun executeTerminalCommand() {
        val command = _uiState.value.terminalInput.trim()
        if (command.isEmpty() || _uiState.value.terminalIsExecuting) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                terminalIsExecuting = true,
                terminalInput = ""
            )

            val workspaceDir = fileRepository.getWorkspaceFolderPath(workspaceId)
            val handler = repository.getToolHandler()
            val request = ToolExecutionRequest(
                toolCallId = "terminal-${System.currentTimeMillis()}",
                command = command,
                workingDirectory = workspaceDir
            )

            val result = handler.executeWithPermission(request, workspaceDir)

            if (result.permissionRequired && result.permissionRequest != null) {
                _uiState.value = _uiState.value.copy(
                    terminalIsExecuting = false,
                    pendingPermissionRequest = result.permissionRequest,
                    terminalPendingCommand = command
                )
            } else {
                val entry = TerminalEntry(
                    command = command,
                    output = result.output.ifEmpty { "(sin salida)" },
                    isSuccess = result.success,
                    executionTimeMs = result.executionTimeMs
                )
                _uiState.value = _uiState.value.copy(
                    terminalIsExecuting = false,
                    terminalHistory = _uiState.value.terminalHistory + entry
                )
            }
        }
    }

    fun clearTerminalHistory() {
        _uiState.value = _uiState.value.copy(terminalHistory = emptyList())
    }

    fun setActiveTab(tab: WorkspaceTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun showInfoDialog() {
        _uiState.value = _uiState.value.copy(showInfoDialog = true)
    }

    fun hideInfoDialog() {
        _uiState.value = _uiState.value.copy(showInfoDialog = false)
    }

    fun addAttachedFile(file: AttachedFile) {
        val currentFiles = _uiState.value.attachedFiles
        _uiState.value = _uiState.value.copy(attachedFiles = currentFiles + file)
    }

    fun removeAttachedFile(index: Int) {
        val currentFiles = _uiState.value.attachedFiles.toMutableList()
        if (index in currentFiles.indices) {
            currentFiles.removeAt(index)
            _uiState.value = _uiState.value.copy(attachedFiles = currentFiles)
        }
    }

    fun clearAttachedFiles() {
        _uiState.value = _uiState.value.copy(attachedFiles = emptyList())
    }

    fun getSupportedFileTypes(): List<String> {
        return TokenCounter.getSupportedFileTypes(extractModelId(_selectedModel.value))
    }

    fun importFileToWorkspace(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val fileName = getFileName(context, uri)
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val size = getFileSize(context, uri)
                
                val result = fileRepository.copyFileToWorkspaceFolder(workspaceId, uri, fileName)
                
                result.onSuccess { file ->
                    val agentFile = AgentFile(
                        workspaceId = workspaceId,
                        name = fileName,
                        path = file.absolutePath,
                        mimeType = mimeType,
                        size = size,
                        generatedByAI = false
                    )
                    repository.addFile(agentFile)
                    Log.d("WorkspaceDetailVM", "File imported: $fileName")
                }.onFailure { error ->
                    Log.e("WorkspaceDetailVM", "Error importing file", error)
                    _uiState.value = _uiState.value.copy(error = "Error al importar archivo: ${error.message}")
                }
            } catch (e: Exception) {
                Log.e("WorkspaceDetailVM", "Error importing file", e)
                _uiState.value = _uiState.value.copy(error = "Error al importar archivo: ${e.message}")
            }
        }
    }

    private suspend fun readFileContent(file: AgentFile): String? {
        return try {
            when {
                file.mimeType.startsWith("text/") || 
                file.mimeType == "application/json" ||
                file.mimeType == "application/xml" ||
                file.name.endsWith(".md") ||
                file.name.endsWith(".csv") -> {
                    fileRepository.readFileContent(workspaceId, file.name)
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e("WorkspaceDetailVM", "Error reading file content", e)
            null
        }
    }

    private suspend fun buildAgentWithFileContext(agent: Agent): Agent {
        val extraSections = mutableListOf<String>()

        // Inject current date so all agents know today's date
        val currentDate = java.time.LocalDate.now().format(
            java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG)
                .withLocale(java.util.Locale("es", "MX"))
        )
        extraSections.add("## TIME AWARENESS\nCurrent date: $currentDate")

        // Contexto de archivos del workspace
        val workspaceFiles = files.value
        if (workspaceFiles.isNotEmpty()) {
            val fileList = workspaceFiles.joinToString("\n") { "- ${it.name}" }
            val workspacePath = fileRepository.getWorkspaceFolderPath(workspaceId)
            extraSections.add("""
ARCHIVOS DISPONIBLES EN EL WORKSPACE:
$fileList

Directorio de trabajo: $workspacePath""".trimIndent())
        }

        // Instrucciones para opciones interactivas
        extraSections.add("""
FORMATO DE OPCIONES INTERACTIVAS:
Cuando necesites que el usuario elija entre varias opciones, usa EXACTAMENTE este formato XML:

<ask_options titulo="Tu pregunta aquí">
- Opción 1
- Opción 2
- Opción 3
</ask_options>

Reglas:
- Usa entre 2 y 10 opciones por bloque.
- Puedes incluir MÚLTIPLES bloques <ask_options> en una respuesta si necesitas hacer varias preguntas. Se mostrarán una por una al usuario.
- Puedes incluir texto antes o después de los bloques.""".trimIndent())

        // Also replace {CURRENT_DATE} placeholder if present in the agent's own prompt
        val basePrompt = agent.systemPrompt.replace("{CURRENT_DATE}", currentDate)
        var enrichedAgent = if (extraSections.isEmpty()) agent.copy(systemPrompt = basePrompt)
            else agent.copy(systemPrompt = basePrompt + "\n\n" + extraSections.joinToString("\n\n"))

        // Share relevant memories with delegated agents (not Cortex — it has its own memory tools)
        if (agent.name != "Cortex") {
            val lastUserMsg = messages.value.lastOrNull { it.role == MessageRole.USER }?.content ?: ""
            if (lastUserMsg.isNotBlank()) {
                enrichedAgent = buildAgentWithMemoryContext(enrichedAgent, lastUserMsg)
            }
        }

        return enrichedAgent
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        val attachedFiles = _uiState.value.attachedFiles

        if (text.isEmpty() && attachedFiles.isEmpty()) return

        val agent = _activeAgent.value
        if (agent == null) {
            _uiState.value = _uiState.value.copy(error = "Selecciona un agente primero")
            return
        }

        val modelToUse = _selectedModel.value
        if (modelToUse.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Selecciona un modelo primero")
            return
        }

        // If agent is already working, just add message to context (don't start new processing)
        if (_uiState.value.isLoading) {
            viewModelScope.launch {
                val processedFiles = processAttachedFiles(attachedFiles)
                val contentParts = buildString {
                    if (processedFiles.isNotEmpty()) {
                        append(processedFiles.joinToString("\n") { "[Archivo adjunto: ${it.name}]" })
                        append("\n\n")
                    }
                    append(text)
                }
                val userMessage = Message(
                    role = MessageRole.USER,
                    content = contentParts,
                    attachedFiles = processedFiles.map { it.name }
                )
                val convId = ensureConversation(text)
                repository.addMessage(workspaceId, convId, userMessage, agent.id)
                _uiState.value = _uiState.value.copy(inputText = "", attachedFiles = emptyList())
                Log.d("WorkspaceDetailVM", "Message added to context while agent is working: ${text.take(50)}")
            }
            return
        }

        currentAgentJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            taskStartTimeMs = System.currentTimeMillis()
            securePreferences.clearDraft(workspaceId)
            repository.resetActivatedTools()
            autoContinueCount = 0
            clearAllAgentStatuses()
            Log.d("WorkspaceDetailVM", "Sending message with agent: ${agent.name}, model: $modelToUse, attachedFiles: ${attachedFiles.size}")

            // Procesar archivos adjuntos: copiarlos al workspace y extraer contenido
            val processedFiles = processAttachedFiles(attachedFiles)

            // Construir el mensaje del usuario con referencias a los archivos
            val contentParts = buildString {
                if (processedFiles.isNotEmpty()) {
                    append(processedFiles.joinToString("\n") { "[Archivo adjunto: ${it.name}]" })
                    append("\n\n")
                }
                append(text)
            }

            // Crear mensaje del usuario
            val userMessage = Message(
                role = MessageRole.USER,
                content = contentParts,
                attachedFiles = processedFiles.map { it.name }
            )

            // Auto-create conversation if none exists
            val convId = ensureConversation(text)

            try {
                repository.addMessage(workspaceId, convId, userMessage, agent.id)
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                Log.e("WorkspaceDetailVM", "FK constraint inserting message, resetting conversation", e)
                _conversationId.value = null
                val newConvId = ensureConversation(text)
                repository.addMessage(workspaceId, newConvId, userMessage, agent.id)
            }
            _uiState.value = _uiState.value.copy(inputText = "", attachedFiles = emptyList())

            // Trigger memory extraction from inactive conversations
            // This runs in background and doesn't block the user experience
            triggerInactiveConversationsExtraction()

            // Procesar imágenes para enviarlas al modelo como vision
            processImagesWithAssistant(agent, userMessage, processedFiles)
        }
    }

    fun stopAgent() {
        currentAgentJob?.cancel()
        currentAgentJob = null
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            executingCommand = null,
            currentReasoning = null
        )
        _workingAgents.value = emptyList()
        _pendingSequentialAgents.value = mutableListOf()
        Log.d("WorkspaceDetailVM", "Agent stopped by user")
    }

    /**
     * Procesa los archivos adjuntos:
     * - Copia los archivos al workspace
     * - Registra los archivos en la base de datos
     * - Retorna la lista de archivos procesados
     */
    private suspend fun processAttachedFiles(attachedFiles: List<AttachedFile>): List<AttachedFile> {
        val processed = mutableListOf<AttachedFile>()
        
        attachedFiles.forEach { file ->
            try {
                // Copiar archivo al workspace
                val result = fileRepository.copyFileToWorkspaceFolder(workspaceId, file.uri, file.name)
                
                result.onSuccess { copiedFile ->
                    // Registrar en la base de datos
                    val agentFile = AgentFile(
                        workspaceId = workspaceId,
                        name = file.name,
                        path = copiedFile.absolutePath,
                        mimeType = file.mimeType,
                        size = copiedFile.length(),
                        generatedByAI = false
                    )
                    repository.addFile(agentFile)
                    processed.add(file)
                    Log.d("WorkspaceDetailVM", "File processed and saved to workspace: ${file.name}")
                }.onFailure { error ->
                    Log.e("WorkspaceDetailVM", "Error copying file ${file.name}", error)
                }
            } catch (e: Exception) {
                Log.e("WorkspaceDetailVM", "Error processing attached file ${file.name}", e)
            }
        }
        
        return processed
    }

    /**
     * Procesa la respuesta del asistente, enviando las imágenes adjuntas como contenido vision
     * para que el modelo pueda analizarlas.
     */
    private suspend fun processImagesWithAssistant(agent: Agent, userMessage: Message, attachedFiles: List<AttachedFile>) {
        // Separar imágenes de otros archivos
        val imageFiles = attachedFiles.filter { it.mimeType.startsWith("image/") }
        val otherFiles = attachedFiles.filter { !it.mimeType.startsWith("image/") }
        
        // Si hay imágenes, necesitamos enviarlas como contenido vision
        if (imageFiles.isNotEmpty()) {
            // Leer las imágenes como base64
            val imageDataUris = imageFiles.mapNotNull { file ->
                fileRepository.readImageUriAsBase64(file.uri)
            }
            
            if (imageDataUris.isNotEmpty()) {
                Log.d("WorkspaceDetailVM", "Sending ${imageDataUris.size} images as vision content")
                // Crear mensajes con contenido de imágenes para el modelo
                processAssistantResponseWithImages(agent, userMessage, imageDataUris)
                return
            }
        }
        
        // Si no hay imágenes, procesar normalmente
        processAssistantResponse(agent, userMessage)
    }

    private suspend fun processAssistantResponseWithImages(agent: Agent, lastUserMessage: Message, imageDataUris: List<String>) {
        val agentWithContext = buildAgentWithFileContext(agent)
        val fullKey = _selectedModel.value
        val currentMessages = messages.value.filter { it.role != MessageRole.USER || it == lastUserMessage }
        
        // Agregar mensaje del usuario con imágenes
        val userMessageWithImages = lastUserMessage.copy(
            imageDataUris = imageDataUris
        )
        
        if (agent.name == "Cortex") {
            processWithOrchestratorAndImages(agentWithContext, userMessageWithImages, currentMessages + userMessageWithImages, imageDataUris)
        } else {
            _workingAgents.value = listOf(agent.name)
            processWithNormalAgentAndImages(agentWithContext, userMessageWithImages, currentMessages + userMessageWithImages, imageDataUris)
        }
    }

    private suspend fun processWithOrchestratorAndImages(orchestrator: Agent, userMessage: Message, messagesForApi: List<Message>, imageDataUris: List<String>) {
        _workingAgents.value = listOf(orchestrator.name)
        val fullKey = _selectedModel.value

        val enhancedPrompt = agentOrchestrator.buildPrompt(orchestrator)
        val agentWithEnhancedPrompt = orchestrator.copy(systemPrompt = enhancedPrompt)
        val workspacePath = fileRepository.getWorkspaceFolderPath(workspaceId)

        val result = repository.chatWithImages(
            agent = agentWithEnhancedPrompt,
            messages = messagesForApi,
            overrideModel = extractModelId(fullKey),
            overrideProvider = extractProvider(fullKey),
            enableTerminal = true,
            workspaceFolderPath = workspacePath,
            imageDataUris = imageDataUris
        )

        var shouldRestoreCortex = true

        result.onSuccess { response ->
            if (!response.reasoning.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(currentReasoning = response.reasoning)
            }
            val content = response.content ?: ""

            // ── PRIORITY 1: Tool-based delegation (delegate_to_agent) ──
            val hasDelegationToolCalls = response.toolCalls?.any { it.function.name == DelegationToolHandler.TOOL_NAME } == true

            if (hasDelegationToolCalls) {
                Log.d("WorkspaceDetailVM", "Orchestrator using delegate_to_agent tool (images path)")
                val cleanContent = agentOrchestrator.stripDelegationLines(content)
                repository.addMessage(workspaceId, _conversationId.value,
                    Message(role = MessageRole.ASSISTANT, content = cleanContent.ifBlank { content },
                        toolCalls = response.toolCalls ?: emptyList(), reasoning = response.reasoning),
                    orchestrator.id)
                shouldRestoreCortex = false
                handleToolCalls(agentWithEnhancedPrompt, response.toolCalls!!)
            } else {
                // ── PRIORITY 2: Text-based delegation (regex fallback) ──
                val parallelDelegation = agentOrchestrator.parseDelegationParallel(content)
                val sequentialDelegation = if (parallelDelegation == null) agentOrchestrator.parseDelegationSequence(content) else null
                val singleDelegation = if (parallelDelegation == null && sequentialDelegation == null) agentOrchestrator.parseDelegation(content) else null

                when {
                    parallelDelegation != null -> {
                        handleDelegationPreMessage(content, orchestrator)
                        Log.d("WorkspaceDetailVM", "Orchestrator parallel delegation (images, text fallback): $parallelDelegation")
                        val resolvedEntries = parallelDelegation.mapNotNull { entry ->
                            val agent = findAgentFuzzy(entry.agentName)
                            if (agent != null) Pair(agent, entry.subtask) else null
                        }
                        _workingAgents.value = resolvedEntries.map { it.first.name + (if (it.second != null) " #${resolvedEntries.indexOf(it) + 1}" else "") }
                        _uiState.value = _uiState.value.copy(isLoading = true)

                        val results = coroutineScope {
                            resolvedEntries.map { (targetAgent, subtask) ->
                                async {
                                    val agentWithCtx = buildAgentWithFileContext(targetAgent)
                                    val agentForTask = if (subtask != null) {
                                        agentWithCtx.copy(
                                            systemPrompt = agentWithCtx.systemPrompt +
                                                "\n\n## PARALLEL EXECUTION CONTEXT\nYou are one of ${resolvedEntries.size} agents working simultaneously on different parts of the same project. Focus ONLY on YOUR assigned subtask. Other parts are being handled by other agents."
                                        )
                                    } else agentWithCtx
                                    val taskMessage = if (subtask != null) userMessage.copy(content = subtask) else userMessage
                                    processWithNormalAgent(agentForTask, taskMessage)
                                }
                            }.awaitAll()
                        }
                        shouldRestoreCortex = results.all { it }
                    }

                    sequentialDelegation != null -> {
                        handleDelegationPreMessage(content, orchestrator)
                        Log.d("WorkspaceDetailVM", "Orchestrator sequential delegation (images, text fallback): $sequentialDelegation")
                        _pendingSequentialAgents.value = sequentialDelegation.toMutableList()
                        processNextSequentialAgent(userMessage)
                        shouldRestoreCortex = false
                    }

                    singleDelegation != null -> {
                        handleDelegationPreMessage(content, orchestrator)
                        val targetAgent = findAgentFuzzy(singleDelegation)
                        if (targetAgent != null) {
                            Log.d("WorkspaceDetailVM", "Orchestrator delegating (images, text fallback) to: ${targetAgent.name}")
                            _workingAgents.value = listOf(targetAgent.name)
                            val targetAgentWithContext = buildAgentWithFileContext(targetAgent)
                            val complete = processWithNormalAgent(targetAgentWithContext, userMessage)
                            shouldRestoreCortex = complete
                        } else {
                            Log.w("WorkspaceDetailVM", "Delegation target not found: $singleDelegation")
                            val cleanContent2 = agentOrchestrator.stripDelegationLines(content)
                            if (cleanContent2.isNotEmpty()) {
                                repository.addMessage(workspaceId, _conversationId.value, Message(role = MessageRole.ASSISTANT, content = cleanContent2), orchestrator.id)
                            }
                        }
                    }

                    else -> {
                        val stripped = agentOrchestrator.stripDelegationLines(content)
                        val finalContent = checkAndSetOptions(stripped.ifEmpty { content })
                        val assistantMessage = Message(
                            role = MessageRole.ASSISTANT,
                            content = finalContent,
                            toolCalls = response.toolCalls ?: emptyList(),
                            reasoning = response.reasoning
                        )
                        repository.addMessage(workspaceId, _conversationId.value, assistantMessage, orchestrator.id)

                        if (!response.toolCalls.isNullOrEmpty()) {
                            shouldRestoreCortex = false
                            handleToolCalls(agentWithEnhancedPrompt, response.toolCalls)
                        }
                    }
                }
            }
        }.onFailure { error ->
            handleError(error)
        }

        // Restaurar Cortex solo si no hay tool calls pendientes
        if (shouldRestoreCortex) {
            _workingAgents.value = emptyList()
            _activeAgent.value = orchestrator
            repository.setActiveAgent(workspaceId, orchestrator.id)
            _uiState.value = _uiState.value.copy(isLoading = false, currentReasoning = null)
            notifyTaskCompletedIfLong(messages.value.lastOrNull { it.role == MessageRole.ASSISTANT }?.content?.take(200) ?: "Tarea completada")
            checkContextWindowUsage()
        }
    }

    /**
     * Procesa la respuesta de un agente normal con imágenes.
     * @return true si el procesamiento completó (sin tool calls pendientes), false si está en progreso
     */
    private suspend fun processWithNormalAgentAndImages(agent: Agent, userMessage: Message, messagesForApi: List<Message>, imageDataUris: List<String>): Boolean {
        val fullKey = _selectedModel.value
        val workspacePath = fileRepository.getWorkspaceFolderPath(workspaceId)

        val result = repository.chatWithImages(
            agent = agent,
            messages = messagesForApi,
            overrideModel = extractModelId(fullKey),
            overrideProvider = extractProvider(fullKey),
            enableTerminal = true,
            workspaceFolderPath = workspacePath,
            imageDataUris = imageDataUris
        )

        var processingComplete = true

        result.onSuccess { response ->
            if (!response.reasoning.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(currentReasoning = response.reasoning)
            }
            if (response.toolCalls.isNullOrEmpty()) {
                // Check if response was truncated — auto-continue if so
                if (handleAutoContinue(agent, response)) {
                    processingComplete = false
                    return@onSuccess
                }
                val cleanContent = checkAndSetOptions(response.content ?: "")
                val assistantMessage = Message(
                    role = MessageRole.ASSISTANT,
                    content = cleanContent,
                    reasoning = response.reasoning
                )
                repository.addMessage(workspaceId, _conversationId.value, assistantMessage, agent.id)
                processingComplete = true
            } else {
                val assistantMessage = Message(
                    role = MessageRole.ASSISTANT,
                    content = response.content ?: "",
                    toolCalls = response.toolCalls,
                    reasoning = response.reasoning
                )
                repository.addMessage(workspaceId, _conversationId.value, assistantMessage, agent.id)

                // Tool calls iniciarán un flujo async, el procesamiento NO está completo
                processingComplete = false
                handleToolCalls(agent, response.toolCalls)
            }
        }.onFailure { error ->
            handleError(error)
            processingComplete = true
        }

        // Solo desactivar isLoading si el procesamiento está completo
        if (processingComplete) {
            _uiState.value = _uiState.value.copy(isLoading = false, currentReasoning = null)
            notifyTaskCompletedIfLong(messages.value.lastOrNull { it.role == MessageRole.ASSISTANT }?.content?.take(200) ?: "Tarea completada")
        }

        return processingComplete
    }

    private suspend fun processAssistantResponse(agent: Agent, lastUserMessage: Message) {
        val agentWithContext = buildAgentWithFileContext(agent)
        
        if (agent.name == "Cortex") {
            processWithOrchestrator(agentWithContext, lastUserMessage)
        } else {
            _workingAgents.value = listOf(agent.name)
            processWithNormalAgent(agentWithContext, lastUserMessage)
        }
    }

    /** Finds an agent by exact name first, then case-insensitive, then partial match. */
    private suspend fun findAgentFuzzy(name: String): Agent? {
        repository.getAgentByName(name)?.let { return it }
        val all = repository.getAllAgentsOnce()
        return all.find { it.name.equals(name, ignoreCase = true) }
            ?: all.find { it.name.contains(name, ignoreCase = true) || name.contains(it.name, ignoreCase = true) }
    }

    /**
     * Processes the next agent in a sequential delegation pipeline.
     * Each agent sees the full conversation (including previous agents' outputs).
     * Waits for each agent to fully complete (including tool calls) before moving to the next.
     */
    /** Extracts and saves any pre-delegation text from Cortex's response. */
    private suspend fun handleDelegationPreMessage(content: String, orchestrator: Agent) {
        val preMsg = agentOrchestrator.extractPreDelegationMessage(content)
        if (preMsg != null) {
            val processedPreMsg = checkAndSetOptions(preMsg)
            repository.addMessage(workspaceId, _conversationId.value, Message(role = MessageRole.ASSISTANT, content = processedPreMsg), orchestrator.id)
        }
    }

    private suspend fun processNextSequentialAgent(userMessage: Message) {
        _sequentialUserMessage.value = userMessage
        val pending = _pendingSequentialAgents.value
        if (pending.isEmpty()) {
            // All agents done, restore Cortex
            Log.d("WorkspaceDetailVM", "Sequential delegation complete")
            _workingAgents.value = emptyList()
            val orchestrator = repository.getOrchestratorAgent()
            if (orchestrator != null) {
                _activeAgent.value = orchestrator
                repository.setActiveAgent(workspaceId, orchestrator.id)
            }
            _uiState.value = _uiState.value.copy(isLoading = false, currentReasoning = null)
            return
        }

        val agentName = pending.removeFirst()
        _pendingSequentialAgents.value = pending
        Log.d("WorkspaceDetailVM", "Sequential: processing agent '$agentName', ${pending.size} remaining")

        val targetAgent = findAgentFuzzy(agentName)
        if (targetAgent == null) {
            Log.w("WorkspaceDetailVM", "Sequential delegation target not found: $agentName")
            processNextSequentialAgent(userMessage)
            return
        }

        _workingAgents.value = listOf(targetAgent.name)
        val targetAgentWithContext = buildAgentWithFileContext(targetAgent)
        _uiState.value = _uiState.value.copy(isLoading = true)
        val complete = processWithNormalAgent(targetAgentWithContext, userMessage)

        if (complete) {
            // Agent finished without tool calls, proceed to next
            processNextSequentialAgent(userMessage)
        }
        // If not complete, tool calls are in progress.
        // continueConversationAfterTools will call processNextSequentialAgent
        // when it detects pending sequential agents after the delegated agent finishes.
    }

    /**
     * Attempts to route the user's query using a local on-device model (e.g. Gemma 3 1B).
     * Returns a DelegationResult if successful, or null if local routing is unavailable/fails.
     */
    private suspend fun tryLocalRouting(userQuery: String): DelegationResult? {
        val downloadedModels = localModelRepository.getDownloadedModels()
        if (downloadedModels.isEmpty()) {
            Log.d("WorkspaceDetailVM", "Local routing: no downloaded models available")
            return null
        }

        // Use the first available local model for routing
        val routingModel = downloadedModels.first()

        return try {
            val localClient = LocalLLMClient(appContext, localModelRepository)
            val compactPrompt = agentOrchestrator.buildCompactDelegationPrompt(userQuery)

            Log.d("WorkspaceDetailVM", "Local routing: using model ${routingModel.id}")
            val startTime = System.currentTimeMillis()

            val result = localClient.chat(
                model = routingModel.id,
                messages = listOf(ChatMessage(role = "user", content = userQuery)),
                systemPrompt = compactPrompt,
                temperature = 0.1f,
                maxTokens = 128
            )

            val elapsed = System.currentTimeMillis() - startTime
            localClient.unloadModel()

            result.getOrNull()?.let { response ->
                Log.d("WorkspaceDetailVM", "Local routing response (${elapsed}ms): $response")
                agentOrchestrator.parseDelegationFromToolCall(response)
            }
        } catch (e: Exception) {
            Log.w("WorkspaceDetailVM", "Local routing failed: ${e.message}")
            null
        }
    }

    /**
     * Builds minimal context from the current conversation for a sub-agent.
     * Includes only the last few user/assistant messages to keep the sub-agent focused.
     */
    private fun buildDelegationContext(userMessage: Message): String {
        val recent = messages.value
            .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            .takeLast(3)
            .joinToString("\n") { "${it.role}: ${it.content.take(500)}" }
        return "## Contexto reciente\n$recent\n\n## Solicitud actual\n${userMessage.content}"
    }

    /**
     * Executes a single tool call for a sub-agent, returning the ToolResult.
     * This is the lambda passed to IsolatedAgentExecutor so it can execute tools
     * without knowing about the ViewModel's internal tool handlers.
     */
    private suspend fun executeToolForSubAgent(
        agent: Agent,
        toolCall: ToolCall,
        workspacePath: String,
        wsId: Long
    ): ToolResult {
        val toolHandler = repository.getToolHandler()
        return try {
            when (toolCall.function.name) {
                "execute_command" -> {
                    val request = toolHandler.parseToolCall(toolCall)
                        ?: return ToolResult(toolCall.id, "execute_command", "Error: no se pudo parsear el tool call")
                    val result = toolHandler.executeWithPermission(request, workspacePath)
                    if (result.permissionRequired) {
                        ToolResult(toolCall.id, "execute_command", "Permiso denegado para ejecutar: ${request.command}")
                    } else {
                        ToolResult(toolCall.id, "execute_command", toolHandler.formatResultForLLM(result))
                    }
                }
                in FILE_TOOL_NAMES -> {
                    val fileToolHandler = repository.getFileToolHandler()
                    val result = fileToolHandler.executeTool(toolCall.id, toolCall.function.name, toolCall.function.arguments, wsId)
                    ToolResult(toolCall.id, toolCall.function.name, if (result.success) result.content else "Error: ${result.content}")
                }
                "duckduckgo_search" -> {
                    val result = repository.getDuckDuckGoSearchToolHandler().executeTool(toolCall.id, toolCall.function.arguments)
                    ToolResult(toolCall.id, "duckduckgo_search", result.content)
                }
                "brave_web_search" -> {
                    val apiKey = repository.getBraveApiKey()
                    val result = repository.getBraveSearchToolHandler().executeTool(toolCall.id, toolCall.function.arguments, apiKey)
                    ToolResult(toolCall.id, "brave_web_search", result.content)
                }
                "serpapi_search" -> {
                    val apiKey = repository.getSerpApiKey()
                    val result = repository.getSerpAPIToolHandler().executeTool(toolCall.id, toolCall.function.arguments, apiKey)
                    ToolResult(toolCall.id, "serpapi_search", result.content)
                }
                SubtaskToolHandler.TOOL_NAME -> {
                    // Block recursive subtask spawning beyond max delegation depth
                    if (currentDelegationDepth >= IsolatedAgentExecutor.MAX_DELEGATION_DEPTH) {
                        ToolResult(toolCall.id, SubtaskToolHandler.TOOL_NAME,
                            "Error: maximum delegation depth (${IsolatedAgentExecutor.MAX_DELEGATION_DEPTH}) reached. Complete this task directly without spawning subtasks.")
                    } else {
                        val handler = repository.getSubtaskToolHandler()
                        val result = handler.executeTool(toolCall.id, toolCall.function.arguments, _selectedModel.value)
                        ToolResult(toolCall.id, SubtaskToolHandler.TOOL_NAME, result.content)
                    }
                }
                in CodeExecutionHandler.ALL_TOOL_NAMES -> {
                    val result = codeExecutionHandler.executeTool(toolCall.id, toolCall.function.name, toolCall.function.arguments, workspacePath)
                    // Surface project preview URL to UI from sub-agent context
                    if (result.projectPreviewUrl != null) {
                        _uiState.value = _uiState.value.copy(
                            webPreviewUrl = result.projectPreviewUrl,
                            webPreviewTitle = result.previewTitle ?: "Project Preview"
                        )
                    } else if (result.htmlPreview != null) {
                        _uiState.value = _uiState.value.copy(
                            webPreviewHtml = result.htmlPreview,
                            webPreviewTitle = result.previewTitle ?: "Preview"
                        )
                    }
                    ToolResult(toolCall.id, toolCall.function.name, result.content)
                }
                in MemoryToolHandler.ALL_TOOL_NAMES -> {
                    val handler = repository.getMemoryToolHandler()
                    val result = handler.executeTool(toolCall.id, toolCall.function.name, toolCall.function.arguments)
                    ToolResult(toolCall.id, toolCall.function.name, result.content)
                }
                AppControlToolHandler.TOOL_NAME -> {
                    val handler = repository.getAppControlToolHandler()
                    val result = handler.executeTool(toolCall.id, toolCall.function.arguments, wsId)
                    ToolResult(toolCall.id, AppControlToolHandler.TOOL_NAME, result.content)
                }
                in TodoToolHandler.ALL_TOOL_NAMES -> {
                    val handler = repository.getTodoToolHandler()
                    val convId = _conversationId.value ?: 0L
                    val result = handler.executeTool(toolCall.id, toolCall.function.name, toolCall.function.arguments, convId)
                    ToolResult(toolCall.id, toolCall.function.name, result.content)
                }
                ScheduledTaskToolHandler.TOOL_NAME -> {
                    val handler = repository.getScheduledTaskToolHandler()
                    val result = handler.executeTool(toolCall.id, toolCall.function.arguments, wsId)
                    ToolResult(toolCall.id, ScheduledTaskToolHandler.TOOL_NAME, result.content)
                }
                DelegationToolHandler.TOOL_NAME -> {
                    ToolResult(toolCall.id, DelegationToolHandler.TOOL_NAME,
                        "Error: sub-agents cannot delegate. Complete the task directly without spawning further delegations.")
                }
                else -> {
                    ToolResult(toolCall.id, toolCall.function.name, "Herramienta '${toolCall.function.name}' no disponible en modo sub-agente aislado")
                }
            }
        } catch (e: Exception) {
            Log.e("WorkspaceDetailVM", "Error executing tool for sub-agent: ${toolCall.function.name}", e)
            ToolResult(toolCall.id, toolCall.function.name, "Error: ${e.message}")
        }
    }

    /**
     * Handles progress updates from IsolatedAgentExecutor, updating UI state.
     */
    private fun handleSubAgentProgress(progress: SubAgentProgress) {
        when (progress) {
            is SubAgentProgress.Started -> {
                updateAgentStatus(progress.agentName, "Iniciando...")
            }
            is SubAgentProgress.Streaming -> {
                updateAgentStatus(progress.agentName, "Generando respuesta...")
            }
            is SubAgentProgress.ToolUse -> {
                updateAgentStatus(progress.agentName, "Usando: ${progress.toolName}")
            }
            is SubAgentProgress.Completed -> {
                clearAgentStatus(progress.agentName)
            }
            is SubAgentProgress.Failed -> {
                updateAgentStatus(progress.agentName, "Error: ${progress.error.take(50)}")
            }
        }
    }

    /**
     * Executes a delegated agent in isolation using IsolatedAgentExecutor
     * and returns the result. All intermediate work is in a sub-conversation.
     */
    private suspend fun executeDelegatedAgentIsolated(
        agent: Agent,
        userMessage: Message,
        extraContext: String = "",
        subtaskOverride: String? = null
    ): IsolatedExecutionResult {
        val fullKey = _selectedModel.value
        val context = buildDelegationContext(userMessage) +
            if (extraContext.isNotBlank()) "\n\n$extraContext" else ""
        // When a subtask is provided, use it as the primary task description
        // so the sub-agent focuses on its specific piece of work
        val taskDescription = subtaskOverride ?: userMessage.content
        val depth = currentDelegationDepth
        currentDelegationDepth++
        return try {
            isolatedAgentExecutor.executeInIsolation(
                agent = agent,
                taskDescription = taskDescription,
                context = context,
                parentConversationId = _conversationId.value!!,
                workspaceId = workspaceId,
                overrideModel = extractModelId(fullKey),
                overrideProvider = extractProvider(fullKey),
                workspacePath = fileRepository.getWorkspaceFolderPath(workspaceId),
                toolExecutor = { a, tc, wp, wsId -> executeToolForSubAgent(a, tc, wp, wsId) },
                progressCallback = { handleSubAgentProgress(it) },
                delegationDepth = depth
            )
        } finally {
            currentDelegationDepth--
        }
    }

    /**
     * After parallel agents complete, sends their combined output back to Cortex
     * for synthesis — review, merge, identify conflicts, and present a unified response.
     * Returns null if synthesis fails (caller should fall back to raw merged output).
     */
    private suspend fun synthesizeParallelResults(
        orchestrator: Agent,
        originalRequest: String,
        results: List<IsolatedExecutionResult>,
        conversationMessages: List<Message>
    ): String? {
        if (results.size < 2) return null // No synthesis needed for single result

        val fullKey = _selectedModel.value
        _workingAgents.value = listOf(orchestrator.name)
        updateAgentStatus(orchestrator.name, "Sintetizando resultados...")

        val agentOutputs = results.joinToString("\n\n---\n\n") { result ->
            "## Agent: ${result.agentName} (sub-conversation #${result.subConversationId})\nStatus: ${if (result.success) "SUCCESS" else "FAILED"}\n\n${result.finalContent}"
        }

        val synthesisPrompt = """You are synthesizing the output of ${results.size} agents that worked in parallel on subtasks of the user's request.

## Original User Request
$originalRequest

## Agent Outputs
$agentOutputs

## Your Task
1. Review all agent outputs for completeness and quality
2. Identify any conflicts, duplications, or missing integrations between outputs
3. Present a UNIFIED, coherent response to the user that combines all results
4. If agents produced code, ensure imports, interfaces, and naming are consistent
5. If there are integration points between components, note what needs to be connected
6. Keep the response focused and actionable — the user should be able to use this directly

Do NOT repeat the full code from each agent unless you need to show corrections. Summarize what was built, highlight any issues, and provide integration guidance."""

        return try {
            val synthesisMessages = conversationMessages + Message(
                role = MessageRole.USER,
                content = synthesisPrompt
            )

            val result = repository.chatWithTools(
                agent = orchestrator,
                messages = synthesisMessages,
                overrideModel = extractModelId(fullKey),
                overrideProvider = extractProvider(fullKey),
                enableTerminal = false,
                workspaceFolderPath = fileRepository.getWorkspaceFolderPath(workspaceId)
            )

            result.getOrNull()?.content?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w("WorkspaceDetailVM", "Parallel synthesis failed, using raw merge", e)
            null
        } finally {
            clearAgentStatus(orchestrator.name)
        }
    }

    private suspend fun processWithOrchestrator(orchestrator: Agent, userMessage: Message) {
        _workingAgents.value = listOf(orchestrator.name)

        // --- Hybrid routing: try local model first if enabled ---
        if (orchestrator.useLocalRouting) {
            val localResult = tryLocalRouting(userMessage.content)
            when (localResult) {
                is DelegationResult.DelegateToAgent -> {
                    Log.d("WorkspaceDetailVM", "Local routing delegated (isolated) to: ${localResult.agentName}")
                    val targetAgent = findAgentFuzzy(localResult.agentName)
                    if (targetAgent != null) {
                        _workingAgents.value = listOf(targetAgent.name)
                        val targetAgentWithContext = buildAgentWithFileContext(targetAgent)
                        val result = executeDelegatedAgentIsolated(targetAgentWithContext, userMessage)
                        // Only the final result goes to the main conversation
                        repository.addMessage(workspaceId, _conversationId.value,
                            Message(role = MessageRole.ASSISTANT, content = "[${targetAgent.name}]: ${result.finalContent}", subConversationId = result.subConversationId),
                            orchestrator.id
                        )
                        _workingAgents.value = emptyList()
                        _activeAgent.value = orchestrator
                        repository.setActiveAgent(workspaceId, orchestrator.id)
                        _uiState.value = _uiState.value.copy(isLoading = false, currentReasoning = null)
                        return
                    }
                    // Agent not found — fall through to cloud
                    Log.w("WorkspaceDetailVM", "Local routing agent not found: ${localResult.agentName}, falling back to cloud")
                }
                is DelegationResult.NoAgent -> {
                    // No suitable agent — fall through to cloud for direct response
                    Log.d("WorkspaceDetailVM", "Local routing: no agent (${localResult.reason}), falling back to cloud")
                }
                is DelegationResult.ParseError -> {
                    Log.w("WorkspaceDetailVM", "Local routing parse error, falling back to cloud")
                }
                null -> {
                    Log.d("WorkspaceDetailVM", "Local routing unavailable, using cloud")
                }
            }
        }

        // --- Cloud routing (streaming flow) ---
        val fullKey = _selectedModel.value
        val msgsFromDb = messages.value
        val currentMessages = if (msgsFromDb.lastOrNull()?.let {
            it.role == userMessage.role && it.content == userMessage.content
        } == true) msgsFromDb else msgsFromDb + userMessage

        val enhancedPrompt = agentOrchestrator.buildPrompt(orchestrator)
        val agentWithEnhancedPrompt = orchestrator.copy(systemPrompt = enhancedPrompt)
        val workspacePath = fileRepository.getWorkspaceFolderPath(workspaceId)

        // Use streaming so the user sees Cortex's response in real-time
        val contentBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        var toolCalls: List<ToolCall>? = null
        var hasErrors = false

        _uiState.value = _uiState.value.copy(streamingContent = "", streamingReasoning = null)
        updateAgentStatus(orchestrator.name, "Pensando...")

        val streamFlow = repository.chatWithToolsStreaming(
            agent = agentWithEnhancedPrompt,
            messages = currentMessages,
            overrideModel = extractModelId(fullKey),
            overrideProvider = extractProvider(fullKey),
            enableTerminal = true,
            workspaceFolderPath = workspacePath
        )

        streamFlow.collect { chunk ->
            if (chunk.error != null) {
                handleError(Exception(chunk.error))
                hasErrors = true
                return@collect
            }
            chunk.content?.let { delta ->
                contentBuilder.append(delta)
                _uiState.value = _uiState.value.copy(streamingContent = contentBuilder.toString())
            }
            chunk.reasoning?.let { delta ->
                reasoningBuilder.append(delta)
                _uiState.value = _uiState.value.copy(
                    streamingReasoning = reasoningBuilder.toString(),
                    currentReasoning = reasoningBuilder.toString()
                )
            }
            if (chunk.done) {
                toolCalls = chunk.toolCalls
                Log.d("WorkspaceDetailVM", "Cortex stream done: toolCalls=${chunk.toolCalls?.size ?: 0}, names=${chunk.toolCalls?.joinToString { it.function.name } ?: "none"}")
            }
        }

        // Clear streaming state
        _uiState.value = _uiState.value.copy(streamingContent = null, streamingReasoning = null)
        clearAgentStatus(orchestrator.name)

        if (hasErrors) {
            _workingAgents.value = emptyList()
            _uiState.value = _uiState.value.copy(isLoading = false, currentReasoning = null)
            return
        }

        val fullContent = contentBuilder.toString()
        val fullReasoning = reasoningBuilder.toString().ifBlank { null }

        // Handle <think> tags
        val thinkingFromTags = com.aiagents.app.data.remote.extractThinkingFromContent(fullContent)
        val content = com.aiagents.app.data.remote.removeThinkingTags(fullContent) ?: fullContent
        val finalReasoning = fullReasoning ?: thinkingFromTags

        var shouldRestoreCortex = true

        // ── PRIORITY 1: Tool-based delegation (delegate_to_agent) ──
        // Structured tool calls are the most reliable delegation mechanism.
        val capturedToolCalls = toolCalls // Capture for smart cast
        val hasDelegationToolCalls = capturedToolCalls?.any { it.function.name == DelegationToolHandler.TOOL_NAME } == true

        if (hasDelegationToolCalls && capturedToolCalls != null) {
            Log.d("WorkspaceDetailVM", "Orchestrator using delegate_to_agent tool (${capturedToolCalls.count { it.function.name == DelegationToolHandler.TOOL_NAME }} delegation(s))")
            // Save Cortex's message (clean content, no DELEGATE text to strip)
            val cleanContent = agentOrchestrator.stripDelegationLines(content)
            if (cleanContent.isNotBlank()) {
                repository.addMessage(workspaceId, _conversationId.value,
                    Message(role = MessageRole.ASSISTANT, content = cleanContent, toolCalls = capturedToolCalls, reasoning = finalReasoning),
                    orchestrator.id)
            } else {
                repository.addMessage(workspaceId, _conversationId.value,
                    Message(role = MessageRole.ASSISTANT, content = content, toolCalls = capturedToolCalls, reasoning = finalReasoning),
                    orchestrator.id)
            }
            shouldRestoreCortex = false
            handleToolCalls(agentWithEnhancedPrompt, capturedToolCalls)
        } else {
            // ── PRIORITY 2: Text-based delegation (regex fallback) ──
            val parallelDelegation = agentOrchestrator.parseDelegationParallel(content)
            val sequentialDelegation = if (parallelDelegation == null) agentOrchestrator.parseDelegationSequence(content) else null
            val singleDelegation = if (parallelDelegation == null && sequentialDelegation == null) agentOrchestrator.parseDelegation(content) else null

            when {
                parallelDelegation != null -> {
                    handleDelegationPreMessage(content, orchestrator)
                    Log.d("WorkspaceDetailVM", "Orchestrator parallel delegation (text fallback): $parallelDelegation")
                    val resolvedEntries = parallelDelegation.mapNotNull { entry ->
                        val agent = findAgentFuzzy(entry.agentName)
                        if (agent != null) Pair(agent, entry.subtask) else null
                    }
                    _workingAgents.value = resolvedEntries.map { it.first.name + (if (it.second != null) " #${resolvedEntries.indexOf(it) + 1}" else "") }
                    _uiState.value = _uiState.value.copy(isLoading = true)

                    val results = coroutineScope {
                        resolvedEntries.map { (targetAgent, subtask) ->
                            async {
                                val agentWithCtx = buildAgentWithFileContext(targetAgent)
                                val agentForTask = if (subtask != null) {
                                    agentWithCtx.copy(
                                        systemPrompt = agentWithCtx.systemPrompt +
                                            "\n\n## PARALLEL EXECUTION CONTEXT\nYou are one of ${resolvedEntries.size} agents working simultaneously on different parts of the same project. Focus ONLY on YOUR assigned subtask. Other parts are being handled by other agents."
                                    )
                                } else agentWithCtx
                                executeDelegatedAgentIsolated(agentForTask, userMessage, subtaskOverride = subtask)
                            }
                        }.awaitAll()
                    }

                    val merged = results.joinToString("\n\n---\n\n") { "[${it.agentName}]: ${it.finalContent}" }
                    val synthesized = synthesizeParallelResults(orchestrator, userMessage.content, results, currentMessages)
                    val finalContentResult = synthesized ?: merged
                    repository.addMessage(workspaceId, _conversationId.value,
                        Message(role = MessageRole.ASSISTANT, content = finalContentResult), orchestrator.id)
                    shouldRestoreCortex = true
                }

                sequentialDelegation != null -> {
                    handleDelegationPreMessage(content, orchestrator)
                    Log.d("WorkspaceDetailVM", "Orchestrator sequential delegation (text fallback): $sequentialDelegation")
                    _uiState.value = _uiState.value.copy(isLoading = true)

                    var previousResult = ""
                    for (agentName in sequentialDelegation) {
                        val targetAgent = findAgentFuzzy(agentName) ?: continue
                        _workingAgents.value = listOf(targetAgent.name)
                        val agentWithCtx = buildAgentWithFileContext(targetAgent)
                        val extraCtx = if (previousResult.isNotBlank()) "## Output del agente anterior\n$previousResult" else ""
                        val result = executeDelegatedAgentIsolated(agentWithCtx, userMessage, extraCtx)
                        previousResult = result.finalContent

                        repository.addMessage(workspaceId, _conversationId.value,
                            Message(role = MessageRole.ASSISTANT, content = "[${result.agentName}]: ${result.finalContent}", subConversationId = result.subConversationId),
                            orchestrator.id)
                    }
                    shouldRestoreCortex = true
                }

                singleDelegation != null -> {
                    handleDelegationPreMessage(content, orchestrator)
                    val targetAgent = findAgentFuzzy(singleDelegation)
                    if (targetAgent != null) {
                        Log.d("WorkspaceDetailVM", "Orchestrator delegating (text fallback) to: ${targetAgent.name}")
                        _workingAgents.value = listOf(targetAgent.name)
                        val targetAgentWithContext = buildAgentWithFileContext(targetAgent)
                        val result = executeDelegatedAgentIsolated(targetAgentWithContext, userMessage)
                        repository.addMessage(workspaceId, _conversationId.value,
                            Message(role = MessageRole.ASSISTANT, content = "[${targetAgent.name}]: ${result.finalContent}", subConversationId = result.subConversationId),
                            orchestrator.id)
                        shouldRestoreCortex = true
                    } else {
                        Log.w("WorkspaceDetailVM", "Delegation target not found: $singleDelegation")
                        val cleanContent = agentOrchestrator.stripDelegationLines(content)
                        if (cleanContent.isNotEmpty()) {
                            repository.addMessage(workspaceId, _conversationId.value, Message(role = MessageRole.ASSISTANT, content = cleanContent), orchestrator.id)
                        }
                    }
                }

                // No delegation: Cortex responds directly or uses other tools
                else -> {
                    val stripped = agentOrchestrator.stripDelegationLines(content)
                    val finalContentDirect = checkAndSetOptions(stripped.ifEmpty { content })
                    val assistantMessage = Message(
                        role = MessageRole.ASSISTANT,
                        content = finalContentDirect,
                        toolCalls = toolCalls ?: emptyList(),
                        reasoning = finalReasoning
                    )
                    repository.addMessage(workspaceId, _conversationId.value, assistantMessage, orchestrator.id)

                    if (!toolCalls.isNullOrEmpty()) {
                        shouldRestoreCortex = false
                        handleToolCalls(agentWithEnhancedPrompt, toolCalls!!)
                    }
                }
            }
        }

        // Restaurar Cortex solo si:
        // 1. No hay tool calls pendientes (flujo async no está activo)
        // 2. No hubo errores que ya manejaron el estado
        if (shouldRestoreCortex) {
            _workingAgents.value = emptyList()
            _activeAgent.value = orchestrator
            repository.setActiveAgent(workspaceId, orchestrator.id)
            _uiState.value = _uiState.value.copy(isLoading = false, currentReasoning = null)
        }
        if (shouldRestoreCortex) {
            checkContextWindowUsage()
        }
        // Si shouldRestoreCortex es false, el flujo async (handleToolCalls/continueConversationAfterTools)
        // se encargará de limpiar el estado cuando termine
    }

    /**
     * Procesa la respuesta de un agente normal (no Cortex).
     * @return true si el procesamiento completó (sin tool calls pendientes), false si está en progreso
     */
    private suspend fun processWithNormalAgent(agent: Agent, lastUserMessage: Message): Boolean {
        val fullKey = _selectedModel.value
        updateAgentStatus(agent.name, "Pensando...")
        // Small delay to ensure Room Flow has emitted latest messages
        kotlinx.coroutines.delay(50)
        val msgsFromDb = messages.value
        val currentMessages = if (msgsFromDb.lastOrNull()?.let {
            it.role == lastUserMessage.role && it.content == lastUserMessage.content
        } == true) msgsFromDb else msgsFromDb + lastUserMessage
        val workspacePath = fileRepository.getWorkspaceFolderPath(workspaceId)

        // Inject parallel context if other agents are working
        val boardMessages = readBoard(agent.name)
        val agentForApi = if (boardMessages.isNotEmpty()) {
            agent.copy(
                systemPrompt = agent.systemPrompt +
                    "\n\n## PARALLEL AGENTS STATUS\nOther agents are working simultaneously on related tasks. Their updates:\n$boardMessages\nCoordinate to avoid duplicating work."
            )
        } else agent

        // Use streaming
        val contentBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        var toolCalls: List<com.aiagents.app.domain.model.ToolCall>? = null
        var finishReason: String? = null
        var hasError = false

        _uiState.value = _uiState.value.copy(streamingContent = "", streamingReasoning = null)

        val streamFlow = repository.chatWithToolsStreaming(
            agent = agentForApi,
            messages = currentMessages,
            overrideModel = extractModelId(fullKey),
            overrideProvider = extractProvider(fullKey),
            enableTerminal = true,
            workspaceFolderPath = workspacePath
        )

        streamFlow.collect { chunk ->
            if (chunk.error != null) {
                handleError(Exception(chunk.error))
                hasError = true
                return@collect
            }

            chunk.content?.let { delta ->
                contentBuilder.append(delta)
                _uiState.value = _uiState.value.copy(streamingContent = contentBuilder.toString())
            }

            chunk.reasoning?.let { delta ->
                reasoningBuilder.append(delta)
                _uiState.value = _uiState.value.copy(
                    streamingReasoning = reasoningBuilder.toString(),
                    currentReasoning = reasoningBuilder.toString()
                )
            }

            if (chunk.done) {
                toolCalls = chunk.toolCalls
                finishReason = chunk.finishReason
            }
        }

        // Clear streaming state
        _uiState.value = _uiState.value.copy(streamingContent = null, streamingReasoning = null)

        if (hasError) return true

        val fullContent = contentBuilder.toString()
        val fullReasoning = reasoningBuilder.toString().ifBlank { null }

        // Handle <think> tags that may appear in streamed content
        val thinkingFromTags = com.aiagents.app.data.remote.extractThinkingFromContent(fullContent)
        val cleanedContent = com.aiagents.app.data.remote.removeThinkingTags(fullContent) ?: fullContent
        val finalReasoning = fullReasoning ?: thinkingFromTags

        var processingComplete = true

        if (toolCalls.isNullOrEmpty()) {
            // Check auto-continue
            val fakeResponse = com.aiagents.app.data.remote.ChatResponseWithTools(
                content = cleanedContent, toolCalls = null, finishReason = finishReason, reasoning = finalReasoning
            )
            if (handleAutoContinue(agent, fakeResponse)) {
                return false
            }
            val cleanContent = checkAndSetOptions(cleanedContent)
            val assistantMessage = Message(
                role = MessageRole.ASSISTANT,
                content = cleanContent,
                reasoning = finalReasoning
            )
            repository.addMessage(workspaceId, _conversationId.value, assistantMessage, agent.id)
            processingComplete = true
        } else {
            val assistantMessage = Message(
                role = MessageRole.ASSISTANT,
                content = cleanedContent,
                toolCalls = toolCalls!!,
                reasoning = finalReasoning
            )
            repository.addMessage(workspaceId, _conversationId.value, assistantMessage, agent.id)
            processingComplete = false
            handleToolCalls(agent, toolCalls!!)
        }

        if (processingComplete) {
            if (_workingAgents.value.size > 1 && cleanedContent.isNotEmpty()) {
                postToBoard(agent.name, "Completado: ${cleanedContent.take(100)}")
            }
            clearAgentStatus(agent.name)
            _uiState.value = _uiState.value.copy(isLoading = false, currentReasoning = null)
        }

        return processingComplete
    }

    // ── Agent status labels ────────────────────────────────────────────────

    private fun getToolStatusLabel(toolName: String): String = when {
        toolName == "execute_command" -> "Ejecutando comando..."
        toolName in FILE_TOOL_NAMES -> when (toolName) {
            "write_file" -> "Escribiendo archivo..."
            "read_text_file", "read_image_file", "read_pdf_file" -> "Leyendo archivo..."
            "list_files" -> "Listando archivos..."
            else -> "Operación de archivos..."
        }
        toolName == "select_agent" -> "Seleccionando agente..."
        toolName == "create_agent" -> "Creando agente..."
        toolName == "delete_agent" -> "Eliminando agente..."
        toolName in CALENDAR_TOOL_NAMES -> "Accediendo al calendario..."
        toolName == "brave_web_search" || toolName == "serpapi_search" || toolName == "duckduckgo_search" -> "Buscando en la web..."
        toolName in GOOGLE_MAPS_TOOL_NAMES -> "Consultando Google Maps..."
        toolName in CanvaToolHandler.ALL_TOOL_NAMES -> "Usando Canva..."
        toolName in PUBMED_TOOL_NAMES -> "Buscando en PubMed..."
        toolName in ObsidianToolHandler.ALL_TOOL_NAMES -> "Accediendo a Obsidian..."
        toolName in GitHubToolHandler.ALL_TOOL_NAMES -> "Operación en GitHub..."
        toolName in NotionToolHandler.ALL_TOOL_NAMES -> "Accediendo a Notion..."
        toolName in SlackToolHandler.ALL_TOOL_NAMES -> "Enviando en Slack..."
        toolName in GoogleDriveToolHandler.ALL_TOOL_NAMES -> "Accediendo a Google Drive..."
        toolName in GoogleWorkspaceToolHandler.ALL_TOOL_NAMES -> "Accediendo a Google Workspace..."
        toolName in ReminderToolHandler.ALL_TOOL_NAMES -> "Gestionando recordatorio..."
        toolName in MemoryToolHandler.ALL_TOOL_NAMES -> "Accediendo a memoria..."
        toolName in CodeExecutionHandler.ALL_TOOL_NAMES -> "Ejecutando código..."
        toolName in PresentationToolHandler.ALL_TOOL_NAMES -> "Creando presentación..."
        toolName in FinanceToolHandler.ALL_TOOL_NAMES -> "Registrando finanzas..."
        toolName in AcademicSearchToolHandler.ALL_TOOL_NAMES -> "Buscando en fuentes académicas..."
        toolName in WeatherToolHandler.ALL_TOOL_NAMES -> "Consultando el clima..."
        toolName in ImageGenerationToolHandler.ALL_TOOL_NAMES -> "Generando imagen con IA..."
        toolName == LocationToolHandler.TOOL_NAME -> "Obteniendo ubicación..."
        toolName == ToolSearchHandler.TOOL_NAME -> "Buscando herramientas..."
        toolName == SubtaskToolHandler.TOOL_NAME -> "Ejecutando subtarea..."
        toolName == DelegationToolHandler.TOOL_NAME -> "Delegando a agente..."
        toolName == AppControlToolHandler.TOOL_NAME -> "Configurando app..."
        toolName in TodoToolHandler.ALL_TOOL_NAMES -> "Actualizando plan..."
        toolName == ScheduledTaskToolHandler.TOOL_NAME -> "Programando tarea..."
        else -> "Usando $toolName..."
    }

    // ── Auto-continue on truncated responses ──────────────────────────────

    /** Max auto-continue attempts to prevent infinite loops */
    private val MAX_TOOL_CALL_DEPTH = 25
    private val MAX_AUTO_CONTINUES = 15
    private var autoContinueCount = 0

    /**
     * Returns true if the response was truncated (model hit max output tokens).
     * Different providers use different finish_reason values.
     */
    private fun isResponseTruncated(finishReason: String?): Boolean {
        if (finishReason == null) return false
        return finishReason.equals("length", ignoreCase = true) ||          // OpenAI, OpenRouter, Moonshot, MiniMax
               finishReason.equals("max_tokens", ignoreCase = true) ||      // Anthropic
               finishReason.equals("MAX_TOKENS", ignoreCase = true) ||      // Google AI
               finishReason.equals("model_length", ignoreCase = true)       // Ollama
    }

    /**
     * Auto-continues a truncated response by sending a "continue" message.
     * Returns true if auto-continue was triggered.
     */
    private suspend fun handleAutoContinue(agent: Agent, response: ChatResponseWithTools): Boolean {
        if (!isResponseTruncated(response.finishReason)) {
            autoContinueCount = 0
            return false
        }
        if (autoContinueCount >= MAX_AUTO_CONTINUES) {
            Log.w("WorkspaceDetailVM", "Max auto-continues ($MAX_AUTO_CONTINUES) reached, stopping")
            autoContinueCount = 0
            return false
        }
        autoContinueCount++
        Log.d("WorkspaceDetailVM", "Response truncated (finishReason=${response.finishReason}), auto-continuing ($autoContinueCount/$MAX_AUTO_CONTINUES)")

        // Save the truncated response as-is
        val assistantMessage = Message(
            role = MessageRole.ASSISTANT,
            content = response.content ?: "",
            reasoning = response.reasoning
        )
        repository.addMessage(workspaceId, _conversationId.value, assistantMessage, agent.id)

        // Send an automatic "continue" message
        val continueMessage = Message(role = MessageRole.USER, content = "Continúa donde te quedaste.")
        repository.addMessage(workspaceId, _conversationId.value, continueMessage, agent.id)

        // Small delay to ensure messages are saved
        kotlinx.coroutines.delay(50)

        // Re-invoke the agent
        processWithNormalAgent(agent, continueMessage)
        return true
    }

    private fun handleError(error: Throwable) {
        Log.e("WorkspaceDetailVM", "API error", error)
        val errorMessage = when {
            error.message?.contains("API Key") == true -> "API Key no configurada. Ve a Proveedores para agregarla."
            error.message?.contains("Unable to resolve host") == true -> "Sin conexion a internet"
            error.message?.contains("timeout") == true -> "Tiempo de espera agotado. Intenta de nuevo."
            error.message?.contains("Connection refused") == true -> "No se pudo conectar al servidor. Verifica que Ollama esté corriendo o la URL del proveedor sea correcta."
            error.message?.contains("Failed to connect") == true -> "No se pudo conectar al servidor. Verifica la URL y que el servidor esté activo."
            error.message?.contains("401") == true -> "API Key invalida o expirada. Si usas Ollama, verifica que esté corriendo en la URL correcta."
            error.message?.contains("403") == true -> "Acceso denegado. Verifica tu API Key."
            error.message?.contains("429") == true -> "Limite de peticiones excedido. Espera un momento."
            error.message?.contains("500") == true -> "Error del servidor. Intenta mas tarde."
            else -> "Error: ${error.message ?: "Desconocido"}"
        }
        _uiState.value = _uiState.value.copy(error = errorMessage, isLoading = false, currentReasoning = null)
    }

    private val FILE_TOOL_NAMES = setOf(
        "read_text_file", "read_image_file", "read_pdf_file", "write_file", "list_files"
    )

    private val CALENDAR_TOOL_NAMES = setOf(
        "read_calendar_events", "add_calendar_event"
    )

    private val SYSTEM_APP_TOOL_NAMES = setOf(
        // Legacy names kept for backward compat during transition
        "open_google_keep", "create_keep_note",
        "open_spotify", "play_spotify", "control_spotify_playback",
        "take_photo",
        // New unified skill
        SystemAppToolHandler.TOOL_NAME
    )

    private suspend fun handleToolCalls(agent: Agent, toolCalls: List<ToolCall>, depth: Int = 0) {
        if (depth >= MAX_TOOL_CALL_DEPTH) {
            Log.w("WorkspaceDetailVM", "Max tool call depth ($MAX_TOOL_CALL_DEPTH) reached, stopping")
            val limitMsg = Message(
                role = MessageRole.ASSISTANT,
                content = "⚠️ Se alcanzó el límite de $MAX_TOOL_CALL_DEPTH iteraciones de herramientas. Puedes enviar otro mensaje para que continúe."
            )
            repository.addMessage(workspaceId, _conversationId.value, limitMsg, agent.id)
            _uiState.value = _uiState.value.copy(isLoading = false, currentReasoning = null)
            notifyTaskCompletedIfLong("Límite de iteraciones alcanzado")
            return
        }

        Log.d("WorkspaceDetailVM", "Handling ${toolCalls.size} tool calls at depth $depth")

        val toolHandler = repository.getToolHandler()

        // Obtener el directorio del workspace como directorio de trabajo por defecto
        val workspaceDir = fileRepository.getWorkspaceFolderPath(workspaceId)

        // ── delegate_to_agent: structured agent delegation (like OpenCode's task tool) ──
        // Multiple calls in a single response → true parallel execution via IsolatedAgentExecutor.
        val delegationCalls = toolCalls.filter { it.function.name == DelegationToolHandler.TOOL_NAME }
        if (delegationCalls.isNotEmpty() && (agent.name == "Cortex" || agent.role == "Agent Orchestrator")) {
            handleDelegationToolCalls(agent, delegationCalls, toolCalls, depth)
            return
        }

        // Run execute_subtask calls in parallel — they are pure API calls with no UI side effects.
        // This enables multi-agent research/programming: one response can spawn N parallel subtasks.
        val subtaskCalls = toolCalls.filter { it.function.name == SubtaskToolHandler.TOOL_NAME }
        if (subtaskCalls.isNotEmpty()) {
            val label = if (subtaskCalls.size > 1)
                "Ejecutando ${subtaskCalls.size} subtareas en paralelo..."
            else "Ejecutando subtarea..."
            updateAgentStatus(agent.name, label)
            Log.d("WorkspaceDetailVM", "Running ${subtaskCalls.size} subtask(s) in parallel")
            coroutineScope {
                subtaskCalls.map { toolCall -> async { handleSubtaskToolCall(agent, toolCall) } }.awaitAll()
            }
        }

        try {
            for ((index, toolCall) in toolCalls.withIndex()) {
                // Already handled in parallel above
                if (toolCall.function.name == SubtaskToolHandler.TOOL_NAME) continue

                Log.d("WorkspaceDetailVM", "Processing tool call ${index + 1}/${toolCalls.size}: ${toolCall.function.name}")

                // Update agent activity status based on tool type
                val toolStatus = getToolStatusLabel(toolCall.function.name)
                updateAgentStatus(agent.name, toolStatus)

                when (toolCall.function.name) {
                    "execute_command" -> {
                        val request = toolHandler.parseToolCall(toolCall)
                        if (request == null) {
                            Log.e("WorkspaceDetailVM", "Failed to parse tool call: ${toolCall.function.arguments}")
                            continue
                        }

                        updateAgentStatus(agent.name, "Ejecutando: ${request.command.take(40)}")
                        _uiState.value = _uiState.value.copy(executingCommand = request.command)

                        Log.d("WorkspaceDetailVM", "Executing command with permission check: ${request.command}")
                        val result = toolHandler.executeWithPermission(request, workspaceDir)
                        Log.d("WorkspaceDetailVM", "Command result: success=${result.success}, permissionRequired=${result.permissionRequired}")

                        if (result.permissionRequired && result.permissionRequest != null) {
                            Log.d("WorkspaceDetailVM", "Permission required for command: ${request.command}")
                            _uiState.value = _uiState.value.copy(
                                pendingPermissionRequest = result.permissionRequest,
                                pendingToolExecution = request,
                                executingCommand = null
                            )
                            return
                        }

                        Log.d("WorkspaceDetailVM", "Command executed successfully: ${request.command}, success=${result.success}, output=${result.output.take(100)}...")
                        saveToolResult(agent, toolCall, result)
                    }

                    in FILE_TOOL_NAMES -> {
                        handleFileToolCall(agent, toolCall)
                    }

                    "select_agent" -> {
                        // Only Cortex should delegate via select_agent.
                        // If a delegated agent calls it, just save the result and continue.
                        if (agent.name == "Cortex") {
                            handleAgentSelectionToolCall(agent, toolCall)
                            return
                        } else {
                            Log.w("WorkspaceDetailVM", "Non-Cortex agent '${agent.name}' called select_agent, ignoring delegation")
                            val toolMessage = Message(
                                role = MessageRole.TOOL,
                                content = "You are already the selected agent. Proceed with the task directly.",
                                toolResults = listOf(ToolResult(toolCallId = toolCall.id, name = "select_agent", content = "You are already the selected agent. Proceed with the task directly."))
                            )
                            repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
                        }
                    }

                    in AgentCreatorToolHandler.ALL_TOOL_NAMES -> {
                        handleAgentCreatorToolCall(agent, toolCall)
                    }

                    in CALENDAR_TOOL_NAMES -> {
                        // Verificar si tenemos permisos de calendario antes de ejecutar
                        _uiState.value = _uiState.value.copy(
                            pendingCalendarPermission = true,
                            pendingCalendarToolCall = toolCall,
                            pendingCalendarAgent = agent
                        )
                        return
                    }

                    in SYSTEM_APP_TOOL_NAMES -> {
                        // Check if this is a take_photo action (legacy or via device_control)
                        val isTakePhoto = toolCall.function.name == "take_photo" ||
                            (toolCall.function.name == SystemAppToolHandler.TOOL_NAME &&
                                try {
                                    gson.fromJson(toolCall.function.arguments, JsonObject::class.java)
                                        ?.get("action")?.asString == "take_photo"
                                } catch (_: Exception) { false })

                        if (isTakePhoto) {
                            _uiState.value = _uiState.value.copy(
                                pendingCameraPermission = true,
                                pendingCameraToolCall = toolCall,
                                pendingCameraAgent = agent
                            )
                            return
                        }
                        handleSystemAppIntentToolCall(agent, toolCall)
                    }

                    "duckduckgo_search" -> {
                        handleDuckDuckGoSearchToolCall(agent, toolCall)
                    }

                    "brave_web_search" -> {
                        handleBraveSearchToolCall(agent, toolCall)
                    }

                    "serpapi_search" -> {
                        handleSerpAPIToolCall(agent, toolCall)
                    }

                    in GOOGLE_MAPS_TOOL_NAMES -> {
                        handleGoogleMapsToolCall(agent, toolCall)
                    }

                    in CanvaToolHandler.ALL_TOOL_NAMES -> {
                        handleCanvaToolCall(agent, toolCall)
                    }

                    in PUBMED_TOOL_NAMES -> {
                        handlePubMedToolCall(agent, toolCall)
                    }

                    in FinanceToolHandler.ALL_TOOL_NAMES -> {
                        handleFinanceToolCall(agent, toolCall)
                    }

                    in ObsidianToolHandler.ALL_TOOL_NAMES -> {
                        handleObsidianToolCall(agent, toolCall)
                    }

                    in GitHubToolHandler.ALL_TOOL_NAMES -> {
                        handleGitHubToolCall(agent, toolCall)
                    }

                    in NotionToolHandler.ALL_TOOL_NAMES -> {
                        handleNotionToolCall(agent, toolCall)
                    }

                    in SlackToolHandler.ALL_TOOL_NAMES -> {
                        handleSlackToolCall(agent, toolCall)
                    }

                    in GoogleDriveToolHandler.ALL_TOOL_NAMES -> {
                        handleGoogleDriveToolCall(agent, toolCall)
                    }

                    in GoogleWorkspaceToolHandler.ALL_TOOL_NAMES -> {
                        handleGoogleWorkspaceToolCall(agent, toolCall)
                    }

                    in ReminderToolHandler.ALL_TOOL_NAMES -> {
                        handleReminderToolCall(agent, toolCall)
                    }

                    in MemoryToolHandler.ALL_TOOL_NAMES -> {
                        handleMemoryToolCall(agent, toolCall)
                    }

                    in CodeExecutionHandler.ALL_TOOL_NAMES -> {
                        handleCodeExecutionToolCall(agent, toolCall)
                    }

                    in PresentationToolHandler.ALL_TOOL_NAMES -> {
                        handlePresentationToolCall(agent, toolCall)
                    }

                    in AcademicSearchToolHandler.ALL_TOOL_NAMES -> {
                        handleAcademicSearchToolCall(agent, toolCall)
                    }

                    in WeatherToolHandler.ALL_TOOL_NAMES -> {
                        handleWeatherToolCall(agent, toolCall)
                    }

                    ImageGenerationToolHandler.TOOL_NAME_DALLE -> {
                        handleImageGenerationToolCall(agent, toolCall, provider = "dalle")
                    }

                    ImageGenerationToolHandler.TOOL_NAME_GOOGLE_IMAGEN -> {
                        handleImageGenerationToolCall(agent, toolCall, provider = "google")
                    }

                    LocationToolHandler.TOOL_NAME -> {
                        val handler = repository.getLocationToolHandler()
                        if (!handler.hasPermission()) {
                            _uiState.value = _uiState.value.copy(
                                pendingLocationPermission = true,
                                pendingLocationToolCall = toolCall,
                                pendingLocationAgent = agent
                            )
                            return
                        }
                        handleLocationToolCall(agent, toolCall)
                    }

                    ToolSearchHandler.TOOL_NAME -> {
                        handleToolSearchCall(agent, toolCall)
                    }

                    SubtaskToolHandler.TOOL_NAME -> {
                        handleSubtaskToolCall(agent, toolCall)
                    }

                    AppControlToolHandler.TOOL_NAME -> {
                        handleAppControlToolCall(agent, toolCall)
                    }

                    in TodoToolHandler.ALL_TOOL_NAMES -> {
                        handleTodoToolCall(agent, toolCall)
                    }

                    ScheduledTaskToolHandler.TOOL_NAME -> {
                        handleScheduledTaskToolCall(agent, toolCall)
                    }

                    DelegationToolHandler.TOOL_NAME -> {
                        // Non-orchestrator agent called delegate_to_agent — block it
                        val toolMessage = Message(
                            role = MessageRole.TOOL,
                            content = "Only Cortex can delegate. Complete the task directly.",
                            toolResults = listOf(ToolResult(toolCallId = toolCall.id, name = DelegationToolHandler.TOOL_NAME, content = "Error: only the orchestrator agent can delegate tasks. Complete the task directly."))
                        )
                        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
                    }

                    else -> {
                        Log.w("WorkspaceDetailVM", "Unknown tool call: ${toolCall.function.name}")
                    }
                }
            }

            _uiState.value = _uiState.value.copy(executingCommand = null)
            updateAgentStatus(agent.name, "Pensando...")

            // Post tool usage to board for parallel agents
            if (_workingAgents.value.size > 1) {
                val toolSummary = toolCalls.joinToString(", ") { it.function.name }
                postToBoard(agent.name, "Usó: $toolSummary")
            }

            // Pequeño delay para asegurar que todos los mensajes TOOL se hayan guardado
            // en la BD y el Flow haya emitido el nuevo valor
            kotlinx.coroutines.delay(100)
            continueConversationAfterTools(agent, depth)
        } catch (e: Exception) {
            Log.e("WorkspaceDetailVM", "Error in handleToolCalls", e)
            _uiState.value = _uiState.value.copy(
                executingCommand = null,
                error = "Error al ejecutar herramienta: ${e.message}"
            )
        }
    }

    /**
     * Handles delegate_to_agent tool calls from Cortex.
     * Multiple calls in a single response are executed in parallel via IsolatedAgentExecutor.
     * Non-delegation tool calls from the same response are processed first.
     * After delegation completes, results are saved as TOOL messages and Cortex synthesizes.
     */
    private suspend fun handleDelegationToolCalls(
        orchestrator: Agent,
        delegationCalls: List<ToolCall>,
        allToolCalls: List<ToolCall>,
        depth: Int
    ) {
        val otherCalls = allToolCalls.filter { it.function.name != DelegationToolHandler.TOOL_NAME }

        // 1. Process non-delegation tools first (e.g. memory_save, todo_write)
        for (tc in otherCalls) {
            Log.d("WorkspaceDetailVM", "Pre-delegation tool: ${tc.function.name}")
            updateAgentStatus(orchestrator.name, getToolStatusLabel(tc.function.name))
            when (tc.function.name) {
                in MemoryToolHandler.ALL_TOOL_NAMES -> handleMemoryToolCall(orchestrator, tc)
                in TodoToolHandler.ALL_TOOL_NAMES -> handleTodoToolCall(orchestrator, tc)
                in FILE_TOOL_NAMES -> handleFileToolCall(orchestrator, tc)
                else -> {
                    // Save as no-op tool result so the LLM loop stays consistent
                    val toolMessage = Message(
                        role = MessageRole.TOOL,
                        content = "OK",
                        toolResults = listOf(ToolResult(tc.id, tc.function.name, "OK"))
                    )
                    repository.addMessage(workspaceId, _conversationId.value, toolMessage, orchestrator.id)
                }
            }
        }

        // 2. Parse delegation calls
        data class DelegationEntry(val toolCall: ToolCall, val agent: Agent, val task: String)

        val entries = mutableListOf<DelegationEntry>()
        for (tc in delegationCalls) {
            try {
                val args = JsonParser.parseString(tc.function.arguments).asJsonObject
                val agentName = args.get("agent_name")?.asString
                val task = args.get("task")?.asString
                if (agentName == null || task == null) {
                    val errorResult = ToolResult(tc.id, DelegationToolHandler.TOOL_NAME, "Error: agent_name and task are required")
                    repository.addMessage(workspaceId, _conversationId.value,
                        Message(role = MessageRole.TOOL, content = "Error: missing parameters", toolResults = listOf(errorResult)),
                        orchestrator.id)
                    continue
                }
                val resolvedAgent = findAgentFuzzy(agentName)
                if (resolvedAgent == null) {
                    Log.w("WorkspaceDetailVM", "Delegation target not found: $agentName")
                    val errorResult = ToolResult(tc.id, DelegationToolHandler.TOOL_NAME, "Error: agent '$agentName' not found. Available agents: ${repository.getAllAgentsOnce().filter { it.id != orchestrator.id }.joinToString { it.name }}")
                    repository.addMessage(workspaceId, _conversationId.value,
                        Message(role = MessageRole.TOOL, content = "Agent not found: $agentName", toolResults = listOf(errorResult)),
                        orchestrator.id)
                    continue
                }
                entries.add(DelegationEntry(tc, resolvedAgent, task))
            } catch (e: Exception) {
                Log.e("WorkspaceDetailVM", "Failed to parse delegation args", e)
                val errorResult = ToolResult(tc.id, DelegationToolHandler.TOOL_NAME, "Error parsing arguments: ${e.message}")
                repository.addMessage(workspaceId, _conversationId.value,
                    Message(role = MessageRole.TOOL, content = "Error: ${e.message}", toolResults = listOf(errorResult)),
                    orchestrator.id)
            }
        }

        if (entries.isEmpty()) {
            // All delegations failed to resolve — let Cortex handle the errors
            continueConversationAfterTools(orchestrator, depth + 1)
            return
        }

        // 3. Update UI with working agents
        _workingAgents.value = entries.mapIndexed { i, e ->
            if (entries.size > 1) "${e.agent.name} #${i + 1}" else e.agent.name
        }
        _uiState.value = _uiState.value.copy(isLoading = true)

        val isParallel = entries.size > 1
        Log.d("WorkspaceDetailVM", "Delegation: ${entries.size} agent(s) ${if (isParallel) "in parallel" else "single"}")

        // 4. Get the last user message for context
        val lastUserMsg = messages.value.lastOrNull { it.role == MessageRole.USER }
            ?: Message(role = MessageRole.USER, content = entries.first().task)

        // 5. Execute delegations (parallel if multiple, single if one)
        val delegationTimeout = 5 * 60 * 1000L // 5 minutes per agent

        val results: List<Pair<ToolCall, IsolatedExecutionResult>> = if (isParallel) {
            coroutineScope {
                entries.map { entry ->
                    async {
                        val agentWithCtx = buildAgentWithFileContext(entry.agent)
                        val enhancedAgent = agentWithCtx.copy(
                            systemPrompt = agentWithCtx.systemPrompt +
                                "\n\n## PARALLEL EXECUTION CONTEXT\nYou are one of ${entries.size} agents working simultaneously on different parts of the same project. Focus ONLY on YOUR assigned task. Other parts are being handled by other agents."
                        )
                        val result = withTimeoutOrNull(delegationTimeout) {
                            executeDelegatedAgentIsolated(enhancedAgent, lastUserMsg, subtaskOverride = entry.task)
                        } ?: IsolatedExecutionResult(
                            finalContent = "Error: delegation timed out after ${delegationTimeout / 1000}s",
                            subConversationId = -1L,
                            success = false,
                            agentName = entry.agent.name
                        )
                        entry.toolCall to result
                    }
                }.awaitAll()
            }
        } else {
            val entry = entries.first()
            val agentWithCtx = buildAgentWithFileContext(entry.agent)
            val result = withTimeoutOrNull(delegationTimeout) {
                executeDelegatedAgentIsolated(agentWithCtx, lastUserMsg, subtaskOverride = entry.task)
            } ?: IsolatedExecutionResult(
                finalContent = "Error: delegation timed out after ${delegationTimeout / 1000}s",
                subConversationId = -1L,
                success = false,
                agentName = entry.agent.name
            )
            listOf(entry.toolCall to result)
        }

        // 6. Save results as TOOL messages so Cortex sees them in the next turn
        for ((tc, result) in results) {
            val content = "[${result.agentName}]:\n${result.finalContent}"
            val toolResult = ToolResult(tc.id, DelegationToolHandler.TOOL_NAME, content)
            val toolMessage = Message(
                role = MessageRole.TOOL,
                content = content,
                toolResults = listOf(toolResult),
                subConversationId = if (result.subConversationId > 0) result.subConversationId else null
            )
            repository.addMessage(workspaceId, _conversationId.value, toolMessage, orchestrator.id)
        }

        // 7. Let Cortex synthesize the results naturally
        _workingAgents.value = listOf(orchestrator.name)
        updateAgentStatus(orchestrator.name, if (isParallel) "Sintetizando resultados..." else "Finalizando...")
        kotlinx.coroutines.delay(100) // Ensure TOOL messages are persisted
        continueConversationAfterTools(orchestrator, depth + 1)
    }

    private suspend fun handleFileToolCall(agent: Agent, toolCall: ToolCall) {
        val fileToolHandler = repository.getFileToolHandler()
        val result = fileToolHandler.executeTool(
            toolCallId = toolCall.id,
            toolName = toolCall.function.name,
            arguments = toolCall.function.arguments,
            workspaceId = workspaceId
        )

        val apiContent = if (result.success) result.content else "Error: ${result.content}"

        // Para imágenes: Message.content = descripción corta (UI + conversación)
        // ToolResult.content = data URI completa (enviada al modelo como vision block)
        val isImage = result.success && result.mimeType != null
        val displayContent = if (isImage) {
            val fileName = try {
                JsonParser.parseString(toolCall.function.arguments).asJsonObject
                    .get("file_name")?.asString ?: "imagen"
            } catch (_: Exception) { "imagen" }
            "Imagen leída: $fileName (${result.mimeType})\nDisponible para análisis visual."
        } else {
            apiContent
        }

        val toolResult = ToolResult(
            toolCallId = result.toolCallId,
            name = result.toolName,
            content = apiContent  // data URI completa — usada por el repositorio para vision
        )

        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = displayContent,  // descripción corta para UI
            toolResults = listOf(toolResult)
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)

        // Refresh file list after write operations
        if (toolCall.function.name == "write_file" && result.success) {
            scanWorkspaceFiles()
        }
    }

    private suspend fun handleAgentSelectionToolCall(agent: Agent, toolCall: ToolCall) {
        val agentSelectionHandler = repository.getAgentSelectionToolHandler()
        val result = agentSelectionHandler.executeTool(
            toolCallId = toolCall.id,
            arguments = toolCall.function.arguments
        )

        val formattedResult = agentSelectionHandler.formatResultForLLM(result)

        val toolResult = ToolResult(
            toolCallId = result.toolCallId,
            name = "select_agent",
            content = formattedResult
        )

        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = formattedResult,
            toolResults = listOf(toolResult)
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)

        // Delegate to the selected agent immediately
        if (result.success && result.agentName != "Cortex" && result.agentName != agent.name) {
            val targetAgent = findAgentFuzzy(result.agentName)
            if (targetAgent != null) {
                Log.d("WorkspaceDetailVM", "select_agent delegating to: ${targetAgent.name} (confidence: ${result.confidence})")
                _workingAgents.value = listOf(targetAgent.name)
                val targetAgentWithContext = buildAgentWithFileContext(targetAgent)
                // Find the last user message to delegate
                val lastUserMsg = messages.value.lastOrNull { it.role == MessageRole.USER }
                if (lastUserMsg != null) {
                    processWithNormalAgent(targetAgentWithContext, lastUserMsg)
                }
            }
        }
    }

    fun grantPermissionAndExecute(permissionLevel: PermissionLevel) {
        val terminalCmd = _uiState.value.terminalPendingCommand
        if (terminalCmd != null) {
            _uiState.value = _uiState.value.copy(
                pendingPermissionRequest = null,
                terminalPendingCommand = null,
                terminalIsExecuting = true
            )
            viewModelScope.launch {
                val workspaceDir = fileRepository.getWorkspaceFolderPath(workspaceId)
                val handler = repository.getToolHandler()
                val request = ToolExecutionRequest(
                    toolCallId = "terminal-${System.currentTimeMillis()}",
                    command = terminalCmd,
                    workingDirectory = workspaceDir
                )
                val result = handler.executeAfterPermissionGranted(request, permissionLevel, workspaceDir)
                val entry = TerminalEntry(
                    command = terminalCmd,
                    output = result.output.ifEmpty { "(sin salida)" },
                    isSuccess = result.success,
                    executionTimeMs = result.executionTimeMs
                )
                _uiState.value = _uiState.value.copy(
                    terminalIsExecuting = false,
                    terminalHistory = _uiState.value.terminalHistory + entry
                )
            }
            return
        }

        val request = _uiState.value.pendingToolExecution ?: return
        val rawAgent = _activeAgent.value ?: return

        viewModelScope.launch {
            try {
                val agent = buildAgentWithFileContext(rawAgent)
                _uiState.value = _uiState.value.copy(
                    pendingPermissionRequest = null,
                    pendingToolExecution = null,
                    executingCommand = request.command
                )

                val toolHandler = repository.getToolHandler()
                // Obtener el directorio del workspace como directorio de trabajo por defecto
                val workspaceDir = fileRepository.getWorkspaceFolderPath(workspaceId)
                val result = toolHandler.executeAfterPermissionGranted(request, permissionLevel, workspaceDir)

                val toolCall = ToolCall(
                    id = request.toolCallId,
                    type = "function",
                    function = com.aiagents.app.domain.model.ToolFunction(
                        name = "execute_command",
                        arguments = "{\"command\": \"${request.command}\"}"
                    )
                )

                saveToolResult(agent, toolCall, result)

                _uiState.value = _uiState.value.copy(executingCommand = null)

                // Pequeño delay para asegurar que el mensaje se haya guardado
                kotlinx.coroutines.delay(100)
                continueConversationAfterTools(agent)
            } catch (e: Exception) {
                Log.e("WorkspaceDetailVM", "Error in grantPermissionAndExecute", e)
                _uiState.value = _uiState.value.copy(
                    executingCommand = null,
                    error = "Error al ejecutar comando: ${e.message}"
                )
            }
        }
    }

    fun denyPermission() {
        _uiState.value = _uiState.value.copy(
            pendingPermissionRequest = null,
            pendingToolExecution = null
        )
    }

    private suspend fun saveToolResult(agent: Agent, toolCall: ToolCall, result: ToolExecutionResult) {
        Log.d("WorkspaceDetailVM", "Saving tool result for command: ${result.command}")
        
        val toolResult = ToolResult(
            toolCallId = result.toolCallId,
            name = "execute_command",
            content = toolHandler.formatResultForLLM(result)
        )
        
        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = toolResult.content,
            toolResults = listOf(toolResult)
        )
        
        val messageId = repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
        Log.d("WorkspaceDetailVM", "Tool result saved with messageId: $messageId")
    }

    private suspend fun continueConversationAfterTools(agent: Agent, depth: Int = 0) {
        if (depth >= 10) {
            Log.w("WorkspaceDetailVM", "Max depth reached in continueConversationAfterTools")
            _uiState.value = _uiState.value.copy(isLoading = false, currentReasoning = null)
            return
        }
        
        val fullKey = _selectedModel.value
        val workspacePath = fileRepository.getWorkspaceFolderPath(workspaceId)
        
        // Log el estado actual de los mensajes para debuggear
        val currentMessages = messages.value
        Log.d("WorkspaceDetailVM", "Continuing conversation at depth $depth with ${currentMessages.size} messages")
        currentMessages.lastOrNull()?.let { lastMsg ->
            Log.d("WorkspaceDetailVM", "Last message: role=${lastMsg.role}, content=${lastMsg.content.take(100)}...")
        }

        _uiState.value = _uiState.value.copy(isLoading = true)

        try {
            val result = repository.chatWithTools(
                agent = agent,
                messages = messages.value,
                overrideModel = extractModelId(fullKey),
                overrideProvider = extractProvider(fullKey),
                enableTerminal = true,
                workspaceFolderPath = workspacePath
            )
            
            result.onSuccess { response ->
                if (!response.reasoning.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(currentReasoning = response.reasoning)
                }
                if (response.toolCalls.isNullOrEmpty()) {
                    // Check if response was truncated — auto-continue if so
                    if (handleAutoContinue(agent, response)) {
                        return@onSuccess
                    }
                    val cleanContent = checkAndSetOptions(response.content ?: "")
                    val assistantMessage = Message(
                        role = MessageRole.ASSISTANT,
                        content = cleanContent,
                        reasoning = response.reasoning
                    )
                    repository.addMessage(workspaceId, _conversationId.value, assistantMessage, agent.id)
                    // Solo desactivar isLoading si no hay más tool calls
                    _uiState.value = _uiState.value.copy(isLoading = false, currentReasoning = null)
                    notifyTaskCompletedIfLong(cleanContent.take(200))
                } else {
                    val assistantMessage = Message(
                        role = MessageRole.ASSISTANT,
                        content = response.content ?: "",
                        toolCalls = response.toolCalls,
                        reasoning = response.reasoning
                    )
                    repository.addMessage(workspaceId, _conversationId.value, assistantMessage, agent.id)
                    // No desactivar isLoading aquí, handleToolCalls se encargará
                    handleToolCalls(agent, response.toolCalls, depth + 1)
                }
            }.onFailure { error ->
                Log.e("WorkspaceDetailVM", "Error continuing conversation", error)
                _uiState.value = _uiState.value.copy(
                    error = "Error: ${error.message}",
                    isLoading = false,
                    currentReasoning = null
                )
            }
        } catch (e: Exception) {
            Log.e("WorkspaceDetailVM", "Exception in continueConversationAfterTools", e)
            _uiState.value = _uiState.value.copy(
                error = "Error: ${e.message}",
                isLoading = false,
                currentReasoning = null
            )
        }
        
        // If there are pending sequential agents, process the next one
        if (agent.name != "Cortex" && _pendingSequentialAgents.value.isNotEmpty()) {
            val userMsg = _sequentialUserMessage.value
            if (userMsg != null) {
                Log.d("WorkspaceDetailVM", "Sequential: agent '${agent.name}' done, continuing pipeline")
                processNextSequentialAgent(userMsg)
                return
            }
        }

        // Si el agente que procesó no es el orquestador (fue una delegación), restaurarlo
        if (agent.role != "Agent Orchestrator") {
            Log.d("WorkspaceDetailVM", "Restoring orchestrator after delegation to ${agent.name}")
            _workingAgents.value = emptyList()
            val orchestrator = repository.getOrchestratorAgent()
            if (orchestrator != null) {
                _activeAgent.value = orchestrator
                repository.setActiveAgent(workspaceId, orchestrator.id)
            }
            // Trigger extraction from inactive conversations after delegation completes
            triggerInactiveConversationsExtraction()
        }

        // Check if context window needs compaction
        checkContextWindowUsage()
    }

    private val toolHandler = repository.getToolHandler()

    /**
     * Ensures a conversation exists for the current chat.
     * If no conversationId is set, creates a new conversation with a title based on the first message.
     * Returns the conversation ID.
     */
    private suspend fun ensureConversation(firstMessageText: String? = null): Long? {
        val existing = _conversationId.value
        if (existing != null && existing > 0) {
            // Verify the conversation still exists in DB (may have been deleted)
            val conv = repository.getConversationById(existing)
            if (conv != null) {
                repository.touchConversation(existing)
                return existing
            }
            // Conversation was deleted — fall through to create a new one
            _conversationId.value = null
        }
        if (workspaceId <= 0) return null
        val title = firstMessageText?.take(50)?.trim()?.ifBlank { "Nuevo chat" } ?: "Nuevo chat"
        val id = repository.createConversation(
            Conversation(workspaceId = workspaceId, title = title)
        )
        _conversationId.value = id
        return id
    }

    fun setConversationId(id: Long?) {
        _conversationId.value = id
        _uiState.value = _uiState.value.copy(inputText = "")
    }

    fun clearChat() {
        viewModelScope.launch {
            val convId = _conversationId.value
            if (convId != null && convId > 0) {
                repository.clearConversation(convId)
            } else {
                repository.clearMessages(workspaceId)
            }
        }
    }

    fun deleteFile(fileId: Long, fileName: String? = null) {
        viewModelScope.launch {
            try {
                // Si el archivo tiene ID 0, es un archivo escaneado (no en BD)
                // Eliminamos directamente del sistema de archivos
                if (fileId == 0L && fileName != null) {
                    fileRepository.deleteFile(workspaceId, fileName)
                    // Refrescar la lista después de eliminar
                    scanWorkspaceFiles()
                } else {
                    // Archivo registrado en BD
                    repository.deleteFile(fileId)
                }
            } catch (e: Exception) {
                Log.e("WorkspaceDetailVM", "Error deleting file", e)
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0) {
                size = cursor.getLong(sizeIndex)
            }
        }
        return size
    }

    private suspend fun handleCalendarToolCall(agent: Agent, toolCall: ToolCall) {
        val calendarToolHandler = repository.getCalendarToolHandler()
        val result = calendarToolHandler.executeTool(
            toolCallId = toolCall.id,
            toolName = toolCall.function.name,
            arguments = toolCall.function.arguments
        )

        val toolResult = ToolResult(
            toolCallId = result.toolCallId,
            name = result.toolName,
            content = if (result.success) result.content else "Error: ${result.content}"
        )

        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = toolResult.content,
            toolResults = listOf(toolResult)
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    /**
     * Called when user grants calendar permission
     */
    fun onCalendarPermissionGranted() {
        val toolCall = _uiState.value.pendingCalendarToolCall ?: return
        val agent = _uiState.value.pendingCalendarAgent ?: _activeAgent.value ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                pendingCalendarPermission = false,
                pendingCalendarToolCall = null,
                pendingCalendarAgent = null,
                isLoading = true
            )

            try {
                handleCalendarToolCall(agent, toolCall)
                // Pequeño delay para asegurar que el mensaje se haya guardado
                kotlinx.coroutines.delay(100)
                continueConversationAfterTools(agent)
            } catch (e: Exception) {
                Log.e("WorkspaceDetailVM", "Error handling calendar tool after permission", e)
                _uiState.value = _uiState.value.copy(
                    error = "Error al acceder al calendario: ${e.message}",
                    isLoading = false,
                    currentReasoning = null
                )
            }
        }
    }

    /**
     * Called when user denies calendar permission
     */
    fun onCalendarPermissionDenied() {
        val toolCall = _uiState.value.pendingCalendarToolCall ?: return
        val agent = _uiState.value.pendingCalendarAgent ?: _activeAgent.value ?: return

        viewModelScope.launch {
            val toolResult = ToolResult(
                toolCallId = toolCall.id,
                name = toolCall.function.name,
                content = "Permiso denegado: El usuario no ha concedido acceso al calendario."
            )

            val toolMessage = Message(
                role = MessageRole.TOOL,
                content = toolResult.content,
                toolResults = listOf(toolResult)
            )
            repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)

            _uiState.value = _uiState.value.copy(
                pendingCalendarPermission = false,
                pendingCalendarToolCall = null,
                pendingCalendarAgent = null
            )

            // Pequeño delay para asegurar que el mensaje se haya guardado
            kotlinx.coroutines.delay(100)
            continueConversationAfterTools(agent)
        }
    }

    /**
     * Check if calendar permission is needed and set pending state
     * Returns true if permission is needed, false otherwise
     */
    fun checkCalendarPermissionNeeded(toolCall: ToolCall): Boolean {
        // El permiso real se verificará en la Activity/Fragment
        // Aquí solo marcamos que necesitamos el permiso
        _uiState.value = _uiState.value.copy(
            pendingCalendarPermission = true,
            pendingCalendarToolCall = toolCall
        )
        return true
    }

    private suspend fun handleSystemAppIntentToolCall(agent: Agent, toolCall: ToolCall) {
        val systemAppToolHandler = repository.getSystemAppToolHandler()
        val result = systemAppToolHandler.executeTool(
            toolCallId = toolCall.id,
            toolName = toolCall.function.name,
            arguments = toolCall.function.arguments
        )

        val toolResult = ToolResult(
            toolCallId = result.toolCallId,
            name = result.toolName,
            content = if (result.success) result.content else "Error: ${result.content}"
        )

        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = toolResult.content,
            toolResults = listOf(toolResult)
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleSubtaskToolCall(agent: Agent, toolCall: ToolCall) {
        val result = repository.getSubtaskToolHandler().executeTool(
            toolCallId = toolCall.id,
            arguments = toolCall.function.arguments,
            selectedModelKey = _selectedModel.value
        )
        val toolResult = ToolResult(
            toolCallId = result.toolCallId,
            name = SubtaskToolHandler.TOOL_NAME,
            content = result.content
        )
        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = result.content,
            toolResults = listOf(toolResult)
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleDuckDuckGoSearchToolCall(agent: Agent, toolCall: ToolCall) {
        val result = repository.getDuckDuckGoSearchToolHandler().executeTool(
            toolCallId = toolCall.id,
            arguments = toolCall.function.arguments
        )
        val toolResult = ToolResult(
            toolCallId = result.toolCallId,
            name = DuckDuckGoSearchToolHandler.TOOL_NAME,
            content = result.content
        )
        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = result.content,
            toolResults = listOf(toolResult)
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleBraveSearchToolCall(agent: Agent, toolCall: ToolCall) {
        val apiKey = repository.getBraveApiKey()
        if (apiKey.isBlank()) {
            val toolResult = ToolResult(
                toolCallId = toolCall.id,
                name = BraveSearchToolHandler.TOOL_NAME,
                content = "Error: Brave Search no está configurado. Ve a la sección MCP para agregar tu API key."
            )
            val toolMessage = Message(
                role = MessageRole.TOOL,
                content = toolResult.content,
                toolResults = listOf(toolResult)
            )
            repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
            return
        }

        val result = repository.getBraveSearchToolHandler().executeTool(
            toolCallId = toolCall.id,
            arguments = toolCall.function.arguments,
            apiKey = apiKey
        )

        val toolResult = ToolResult(
            toolCallId = result.toolCallId,
            name = BraveSearchToolHandler.TOOL_NAME,
            content = result.content
        )
        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = result.content,
            toolResults = listOf(toolResult)
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleSerpAPIToolCall(agent: Agent, toolCall: ToolCall) {
        val apiKey = repository.getSerpApiKey()
        if (apiKey.isBlank()) {
            val toolResult = ToolResult(
                toolCallId = toolCall.id,
                name = SerpAPIToolHandler.TOOL_NAME,
                content = "Error: SerpAPI no está configurado. Ve a la sección MCP para agregar tu API key."
            )
            val toolMessage = Message(
                role = MessageRole.TOOL,
                content = toolResult.content,
                toolResults = listOf(toolResult)
            )
            repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
            return
        }

        val result = repository.getSerpAPIToolHandler().executeTool(
            toolCallId = toolCall.id,
            arguments = toolCall.function.arguments,
            apiKey = apiKey
        )

        val toolResult = ToolResult(
            toolCallId = result.toolCallId,
            name = SerpAPIToolHandler.TOOL_NAME,
            content = result.content
        )
        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = result.content,
            toolResults = listOf(toolResult)
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private val GOOGLE_MAPS_TOOL_NAMES = setOf(
        GoogleMapsToolHandler.TOOL_NAME_GEOCODE,
        GoogleMapsToolHandler.TOOL_NAME_PLACES,
        GoogleMapsToolHandler.TOOL_NAME_DIRECTIONS,
        GoogleMapsToolHandler.TOOL_NAME_DISTANCE,
        GoogleMapsToolHandler.TOOL_NAME_ELEVATION
    )

    private suspend fun handleGoogleMapsToolCall(agent: Agent, toolCall: ToolCall) {
        val apiKey = repository.getGoogleMapsApiKey()
        if (apiKey.isBlank()) {
            val toolResult = ToolResult(
                toolCallId = toolCall.id,
                name = toolCall.function.name,
                content = "Error: Google Maps no está configurado. Ve a la sección MCP para agregar tu API key."
            )
            val toolMessage = Message(
                role = MessageRole.TOOL,
                content = toolResult.content,
                toolResults = listOf(toolResult)
            )
            repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
            return
        }

        val result = repository.getGoogleMapsToolHandler().executeTool(
            toolCallId = toolCall.id,
            toolName = toolCall.function.name,
            arguments = toolCall.function.arguments,
            apiKey = apiKey
        )

        val toolResult = ToolResult(
            toolCallId = result.toolCallId,
            name = toolCall.function.name,
            content = result.content
        )
        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = result.content,
            toolResults = listOf(toolResult)
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleCanvaToolCall(agent: Agent, toolCall: ToolCall) {
        val token = repository.getCanvaAccessToken()
        if (token.isBlank()) {
            val toolResult = ToolResult(
                toolCallId = toolCall.id,
                name = toolCall.function.name,
                content = "Error: Canva no está configurado. Ve a la sección MCP para agregar tu Access Token."
            )
            val toolMessage = Message(
                role = MessageRole.TOOL,
                content = toolResult.content,
                toolResults = listOf(toolResult)
            )
            repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
            return
        }

        val result = repository.getCanvaToolHandler().executeTool(
            toolCallId = toolCall.id,
            toolName = toolCall.function.name,
            arguments = toolCall.function.arguments,
            apiKey = token
        )

        val toolResult = ToolResult(
            toolCallId = result.toolCallId,
            name = toolCall.function.name,
            content = result.content
        )
        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = result.content,
            toolResults = listOf(toolResult)
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private val PUBMED_TOOL_NAMES = setOf(
        PubMedToolHandler.TOOL_SEARCH,
        PubMedToolHandler.TOOL_FETCH_ARTICLE
    )

    private suspend fun handlePubMedToolCall(agent: Agent, toolCall: ToolCall) {
        val result = repository.getPubMedToolHandler().executeTool(
            toolCallId = toolCall.id,
            toolName = toolCall.function.name,
            arguments = toolCall.function.arguments
        )

        val toolResult = ToolResult(
            toolCallId = result.toolCallId,
            name = toolCall.function.name,
            content = result.content
        )
        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = result.content,
            toolResults = listOf(toolResult)
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleFinanceToolCall(agent: Agent, toolCall: ToolCall) {
        val result = repository.getFinanceToolHandler().executeTool(
            toolCallId = toolCall.id, toolName = toolCall.function.name, arguments = toolCall.function.arguments
        )
        val toolMessage = Message(role = MessageRole.TOOL, content = result.content,
            toolResults = listOf(ToolResult(result.toolCallId, toolCall.function.name, result.content)))
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleAgentCreatorToolCall(agent: Agent, toolCall: ToolCall) {
        val result = repository.getAgentCreatorToolHandler().executeTool(
            toolCallId = toolCall.id, toolName = toolCall.function.name, arguments = toolCall.function.arguments
        )
        val toolMessage = Message(role = MessageRole.TOOL, content = result.content,
            toolResults = listOf(ToolResult(result.toolCallId, toolCall.function.name, result.content)))
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleGitHubToolCall(agent: Agent, toolCall: ToolCall) {
        val result = repository.getGitHubToolHandler().executeTool(
            toolCallId = toolCall.id, toolName = toolCall.function.name, arguments = toolCall.function.arguments
        )
        val toolMessage = Message(role = MessageRole.TOOL, content = result.content,
            toolResults = listOf(ToolResult(result.toolCallId, toolCall.function.name, result.content)))
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleNotionToolCall(agent: Agent, toolCall: ToolCall) {
        val result = repository.getNotionToolHandler().executeTool(
            toolCallId = toolCall.id, toolName = toolCall.function.name, arguments = toolCall.function.arguments
        )
        val toolMessage = Message(role = MessageRole.TOOL, content = result.content,
            toolResults = listOf(ToolResult(result.toolCallId, toolCall.function.name, result.content)))
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleSlackToolCall(agent: Agent, toolCall: ToolCall) {
        val result = repository.getSlackToolHandler().executeTool(
            toolCallId = toolCall.id, toolName = toolCall.function.name, arguments = toolCall.function.arguments
        )
        val toolMessage = Message(role = MessageRole.TOOL, content = result.content,
            toolResults = listOf(ToolResult(result.toolCallId, toolCall.function.name, result.content)))
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleGoogleDriveToolCall(agent: Agent, toolCall: ToolCall) {
        val token = repository.getValidGoogleDriveToken()
        if (token.isBlank()) {
            val toolMessage = Message(role = MessageRole.TOOL,
                content = "Error: Google Drive no esta configurado. Ve a MCP para conectar tu cuenta.",
                toolResults = listOf(ToolResult(toolCall.id, toolCall.function.name,
                    "Error: Google Drive no esta configurado.")))
            repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
            return
        }
        val result = repository.getGoogleDriveToolHandler().executeTool(
            toolCallId = toolCall.id, toolName = toolCall.function.name,
            arguments = toolCall.function.arguments, accessToken = token
        )
        val toolMessage = Message(role = MessageRole.TOOL, content = result.content,
            toolResults = listOf(ToolResult(result.toolCallId, toolCall.function.name, result.content)))
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleGoogleWorkspaceToolCall(agent: Agent, toolCall: ToolCall) {
        val accessToken = repository.getGoogleWorkspaceOAuthManager().getValidAccessToken().getOrElse {
            // Try falling back to Google Drive token
            securePreferences.getGoogleDriveAccessToken() ?: run {
                val toolMessage = Message(role = MessageRole.TOOL,
                    content = "Error: Google Workspace not authenticated. Please connect your Google account in Settings > Providers.",
                    toolResults = listOf(ToolResult(toolCall.id, toolCall.function.name,
                        "Error: Google Workspace not authenticated.")))
                repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
                return
            }
        }
        val result = repository.getGoogleWorkspaceToolHandler().executeTool(
            toolCallId = toolCall.id, toolName = toolCall.function.name,
            arguments = toolCall.function.arguments, accessToken = accessToken
        )
        val toolMessage = Message(role = MessageRole.TOOL, content = result.content,
            toolResults = listOf(ToolResult(result.toolCallId, toolCall.function.name, result.content)))
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleReminderToolCall(agent: Agent, toolCall: ToolCall) {
        val result = repository.getReminderToolHandler().executeTool(
            toolCallId = toolCall.id, toolName = toolCall.function.name, arguments = toolCall.function.arguments
        )
        val toolMessage = Message(role = MessageRole.TOOL, content = result.content,
            toolResults = listOf(ToolResult(result.toolCallId, toolCall.function.name, result.content)))
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleMemoryToolCall(agent: Agent, toolCall: ToolCall) {
        val result = repository.getMemoryToolHandler().executeTool(
            toolCallId = toolCall.id, toolName = toolCall.function.name, arguments = toolCall.function.arguments
        )
        val toolMessage = Message(role = MessageRole.TOOL, content = result.content,
            toolResults = listOf(ToolResult(result.toolCallId, toolCall.function.name, result.content)))
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleCodeExecutionToolCall(agent: Agent, toolCall: ToolCall) {
        val workspaceDir = fileRepository.getWorkspaceFolderPath(workspaceId)
        val result = codeExecutionHandler.executeTool(
            toolCallId = toolCall.id,
            toolName = toolCall.function.name,
            arguments = toolCall.function.arguments,
            workspacePath = workspaceDir
        )

        // If preview_web, show the WebView dialog with inline HTML
        if (result.htmlPreview != null) {
            _uiState.value = _uiState.value.copy(
                webPreviewHtml = result.htmlPreview,
                webPreviewUrl = null,
                webPreviewTitle = result.previewTitle ?: "Preview"
            )
        }

        // If preview_project, show the WebView dialog with localhost URL
        if (result.projectPreviewUrl != null) {
            _uiState.value = _uiState.value.copy(
                webPreviewHtml = null,
                webPreviewUrl = result.projectPreviewUrl,
                webPreviewTitle = result.previewTitle ?: "Project Preview"
            )
        }

        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = result.content,
            toolResults = listOf(ToolResult(result.toolCallId, toolCall.function.name, result.content))
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)

        if (toolCall.function.name == CodeExecutionHandler.TOOL_RUN_CODE) {
            scanWorkspaceFiles()
        }
    }

    private suspend fun handlePresentationToolCall(agent: Agent, toolCall: ToolCall) {
        val result = repository.getPresentationToolHandler().executeTool(
            toolCallId = toolCall.id, toolName = toolCall.function.name, arguments = toolCall.function.arguments
        )
        val toolMessage = Message(role = MessageRole.TOOL, content = result.content,
            toolResults = listOf(ToolResult(result.toolCallId, toolCall.function.name, result.content)))
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    fun dismissWebPreview() {
        // Stop the local server if it was running for a project preview
        if (_uiState.value.webPreviewUrl != null) {
            codeExecutionHandler.stopServer()
        }
        _uiState.value = _uiState.value.copy(webPreviewHtml = null, webPreviewUrl = null)
    }

    private suspend fun handleToolSearchCall(agent: Agent, toolCall: ToolCall) {
        val handler = repository.getToolSearchHandler()
        val args = try {
            com.google.gson.JsonParser.parseString(toolCall.function.arguments).asJsonObject
        } catch (_: Exception) { com.google.gson.JsonObject() }

        val query = args.get("query")?.asString ?: ""
        val workspacePath = fileRepository.getWorkspaceFolderPath(workspaceId)
        val availableToolNames = repository.getAllAvailableToolNames(agent, true, workspacePath)

        val result = handler.search(query, availableToolNames)

        // Activate discovered tools so they're included in the next API call
        if (result.found && result.toolNames.isNotEmpty()) {
            repository.activateTools(result.toolNames)
        }

        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = result.message,
            toolResults = listOf(ToolResult(toolCall.id, ToolSearchHandler.TOOL_NAME, result.message))
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleObsidianToolCall(agent: Agent, toolCall: ToolCall) {
        val result = repository.getObsidianToolHandler().executeTool(
            toolCallId = toolCall.id,
            toolName = toolCall.function.name,
            arguments = toolCall.function.arguments
        )

        val toolResult = ToolResult(
            toolCallId = result.toolCallId,
            name = toolCall.function.name,
            content = result.content
        )
        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = result.content,
            toolResults = listOf(toolResult)
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleLocationToolCall(agent: Agent, toolCall: ToolCall) {
        val result = repository.getLocationToolHandler().executeTool(toolCall.id)

        val toolResult = ToolResult(
            toolCallId = result.toolCallId,
            name = toolCall.function.name,
            content = result.content
        )
        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = result.content,
            toolResults = listOf(toolResult)
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleAcademicSearchToolCall(agent: Agent, toolCall: ToolCall) {
        val handler = repository.getAcademicSearchToolHandler()
        val result = when (toolCall.function.name) {
            AcademicSearchToolHandler.TOOL_NAME_WIKIPEDIA -> handler.executeWikipediaSearch(toolCall.id, toolCall.function.arguments)
            AcademicSearchToolHandler.TOOL_NAME_ARXIV -> handler.executeArxivSearch(toolCall.id, toolCall.function.arguments)
            else -> return
        }

        val toolResult = ToolResult(
            toolCallId = result.toolCallId,
            name = toolCall.function.name,
            content = result.content
        )
        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = result.content,
            toolResults = listOf(toolResult)
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleWeatherToolCall(agent: Agent, toolCall: ToolCall) {
        val apiKey = repository.getOpenWeatherApiKey()
        if (apiKey.isBlank()) {
            val toolResult = ToolResult(
                toolCallId = toolCall.id,
                name = toolCall.function.name,
                content = "Error: OpenWeather API no está configurado. Ve a la sección MCP para agregar tu API key."
            )
            val toolMessage = Message(
                role = MessageRole.TOOL,
                content = toolResult.content,
                toolResults = listOf(toolResult)
            )
            repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
            return
        }

        val handler = repository.getWeatherToolHandler()
        val result = when (toolCall.function.name) {
            WeatherToolHandler.TOOL_NAME_CURRENT -> handler.executeCurrentWeather(toolCall.id, toolCall.function.arguments, apiKey)
            WeatherToolHandler.TOOL_NAME_FORECAST -> handler.executeForecast(toolCall.id, toolCall.function.arguments, apiKey)
            WeatherToolHandler.TOOL_NAME_AIR_QUALITY -> handler.executeAirQuality(toolCall.id, toolCall.function.arguments, apiKey)
            else -> return
        }

        val toolResult = ToolResult(
            toolCallId = result.toolCallId,
            name = toolCall.function.name,
            content = result.content
        )
        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = result.content,
            toolResults = listOf(toolResult)
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleImageGenerationToolCall(agent: Agent, toolCall: ToolCall, provider: String = "dalle") {
        val apiKey = when (provider) {
            "google" -> repository.getGoogleImagenApiKey()
            else -> repository.getOpenAIApiKey()
        }

        if (apiKey.isBlank()) {
            val providerName = if (provider == "google") "Google Imagen" else "OpenAI"
            val toolResult = ToolResult(
                toolCallId = toolCall.id,
                name = toolCall.function.name,
                content = "Error: $providerName API no está configurada. Ve a la sección MCP para agregar tu API key."
            )
            val toolMessage = Message(
                role = MessageRole.TOOL,
                content = toolResult.content,
                toolResults = listOf(toolResult)
            )
            repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
            return
        }

        val handler = repository.getImageGenerationToolHandler()
        val result = when (toolCall.function.name) {
            ImageGenerationToolHandler.TOOL_NAME_DALLE -> handler.executeGenerateImage(toolCall.id, toolCall.function.arguments, apiKey)
            ImageGenerationToolHandler.TOOL_NAME_GOOGLE_IMAGEN -> handler.executeGenerateImageGoogle(toolCall.id, toolCall.function.arguments, apiKey)
            ImageGenerationToolHandler.TOOL_NAME_EDIT_IMAGE -> handler.executeEditImage(toolCall.id, toolCall.function.arguments, apiKey)
            ImageGenerationToolHandler.TOOL_NAME_VARIATION -> handler.executeImageVariation(toolCall.id, toolCall.function.arguments, apiKey)
            else -> return
        }

        val toolResult = ToolResult(
            toolCallId = result.toolCallId,
            name = toolCall.function.name,
            content = result.content
        )
        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = result.content,
            toolResults = listOf(toolResult)
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleTodoToolCall(agent: Agent, toolCall: ToolCall) {
        val handler = repository.getTodoToolHandler()
        val convId = _conversationId.value ?: 0L
        val result = handler.executeTool(toolCall.id, toolCall.function.name, toolCall.function.arguments, convId)
        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = result.content,
            toolResults = listOf(ToolResult(result.toolCallId, toolCall.function.name, result.content))
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleScheduledTaskToolCall(agent: Agent, toolCall: ToolCall) {
        val handler = repository.getScheduledTaskToolHandler()
        val result = handler.executeTool(toolCall.id, toolCall.function.arguments, workspaceId)
        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = result.content,
            toolResults = listOf(ToolResult(result.toolCallId, toolCall.function.name, result.content))
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    private suspend fun handleAppControlToolCall(agent: Agent, toolCall: ToolCall) {
        val handler = repository.getAppControlToolHandler()
        val result = handler.executeTool(
            toolCallId = toolCall.id,
            arguments = toolCall.function.arguments,
            currentWorkspaceId = workspaceId
        )
        // If model was changed, refresh local state
        if (result.success && result.content.startsWith("Model changed to:")) {
            val newModel = result.content.removePrefix("Model changed to: ").trim()
            _selectedModel.value = newModel
            val available = buildAvailableModelsList()
            _uiState.value = _uiState.value.copy(availableModels = available)
        }
        val toolMessage = Message(
            role = MessageRole.TOOL,
            content = result.content,
            toolResults = listOf(ToolResult(result.toolCallId, toolCall.function.name, result.content))
        )
        repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)
    }

    fun onLocationPermissionGranted() {
        val toolCall = _uiState.value.pendingLocationToolCall ?: return
        val agent = _uiState.value.pendingLocationAgent ?: _activeAgent.value ?: return

        _uiState.value = _uiState.value.copy(
            pendingLocationPermission = false,
            pendingLocationToolCall = null,
            pendingLocationAgent = null
        )

        viewModelScope.launch {
            handleLocationToolCall(agent, toolCall)
            kotlinx.coroutines.delay(100)
            continueConversationAfterTools(agent)
        }
    }

    fun onLocationPermissionDenied() {
        val toolCall = _uiState.value.pendingLocationToolCall ?: return
        val agent = _uiState.value.pendingLocationAgent ?: _activeAgent.value ?: return

        viewModelScope.launch {
            val toolResult = ToolResult(
                toolCallId = toolCall.id,
                name = toolCall.function.name,
                content = "Permiso denegado: El usuario no ha concedido acceso a la ubicacion. Informa al usuario que necesita otorgar el permiso de ubicacion para esta funcionalidad."
            )
            val toolMessage = Message(
                role = MessageRole.TOOL,
                content = toolResult.content,
                toolResults = listOf(toolResult)
            )
            repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)

            _uiState.value = _uiState.value.copy(
                pendingLocationPermission = false,
                pendingLocationToolCall = null,
                pendingLocationAgent = null
            )

            continueConversationAfterTools(agent)
        }
    }

    fun onCameraPermissionGranted() {
        _uiState.value = _uiState.value.copy(
            pendingCameraPermission = false,
            pendingCameraCapture = true
        )
    }

    fun onCameraPermissionDenied() {
        val toolCall = _uiState.value.pendingCameraToolCall ?: return
        val agent = _uiState.value.pendingCameraAgent ?: _activeAgent.value ?: return

        viewModelScope.launch {
            val toolResult = ToolResult(
                toolCallId = toolCall.id,
                name = toolCall.function.name,
                content = "Permiso denegado: El usuario no ha concedido acceso a la cámara."
            )
            val toolMessage = Message(
                role = MessageRole.TOOL,
                content = toolResult.content,
                toolResults = listOf(toolResult)
            )
            repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)

            _uiState.value = _uiState.value.copy(
                pendingCameraPermission = false,
                pendingCameraToolCall = null,
                pendingCameraAgent = null
            )

            continueConversationAfterTools(agent)
        }
    }

    fun onPhotoCaptured(savedPath: String?) {
        val toolCall = _uiState.value.pendingCameraToolCall ?: return
        val agent = _uiState.value.pendingCameraAgent ?: _activeAgent.value ?: return

        viewModelScope.launch {
            val content = if (savedPath != null) {
                "Foto tomada y guardada en: $savedPath\nPuedes usar read_image_file con esta ruta para analizar la imagen."
            } else {
                "El usuario canceló la captura de foto."
            }
            val toolResult = ToolResult(
                toolCallId = toolCall.id,
                name = toolCall.function.name,
                content = content
            )
            val toolMessage = Message(
                role = MessageRole.TOOL,
                content = content,
                toolResults = listOf(toolResult)
            )
            repository.addMessage(workspaceId, _conversationId.value, toolMessage, agent.id)

            _uiState.value = _uiState.value.copy(
                pendingCameraCapture = false,
                pendingCameraToolCall = null,
                pendingCameraAgent = null
            )

            if (savedPath != null) scanWorkspaceFiles()
            
            // Pequeño delay para asegurar que el mensaje se haya guardado en la BD
            // y el Flow haya emitido el nuevo valor
            kotlinx.coroutines.delay(100)
            continueConversationAfterTools(agent)
        }
    }

    suspend fun getWorkspacePath(): String = fileRepository.getWorkspaceFolderPath(workspaceId)

    /**
     * EVENT: User is actively chatting or conversation state changed.
     * Triggers memory extraction from INACTIVE conversations only.
     * The active conversation is excluded to avoid interfering with current chat.
     */
    private fun triggerInactiveConversationsExtraction() {
        val fullKey = _selectedModel.value
        val modelId = extractModelId(fullKey).ifBlank { return }
        val provider = extractProvider(fullKey) ?: return
        val activeConversationId = _conversationId.value

        memoryExtractor.triggerExtraction(
            excludeConversationId = activeConversationId,
            modelId = modelId,
            provider = provider
        )
    }

    /**
     * EVENT: User just switched to/resumed a specific conversation.
     * Checks if this conversation needs re-extraction due to new messages
     * that were added while the conversation was inactive.
     */
    fun onConversationResumed(conversationId: Long) {
        // Update our local tracking
        _conversationId.value = conversationId
        
        val fullKey = _selectedModel.value
        val modelId = extractModelId(fullKey).ifBlank { return }
        val provider = extractProvider(fullKey) ?: return

        viewModelScope.launch(Dispatchers.IO) {
            // Check if this conversation needs re-extraction
            val didExtract = memoryExtractor.checkConversationOnResume(
                conversationId = conversationId,
                modelId = modelId,
                provider = provider
            )
            
            if (didExtract) {
                Log.i("WorkspaceDetailVM", "Re-extracted memories from resumed conversation $conversationId")
            }
            
            // Also trigger extraction from other inactive conversations
            triggerInactiveConversationsExtraction()
        }
    }

    /**
     * EVENT: User started a new conversation.
     * Triggers extraction from all inactive conversations.
     */
    fun onNewConversationStarted() {
        _conversationId.value = null
        triggerInactiveConversationsExtraction()
    }

    /**
     * Fetches relevant memories and appends them to an agent's system prompt
     * before delegation. This gives delegated agents personal context about the user.
     */
    private suspend fun buildAgentWithMemoryContext(agent: Agent, userMessage: String): Agent {
        try {
            val memorySummary = repository.getImportantMemoriesCompact() ?: return agent
            val memorySection = "\n\n## User context\n$memorySummary\n\nUse this to personalize. Don't mention memory."
            return agent.copy(systemPrompt = agent.systemPrompt + memorySection)
        } catch (e: Exception) {
            Log.w("WorkspaceDetailVM", "Memory sharing failed (non-critical)", e)
            return agent
        }
    }
}
