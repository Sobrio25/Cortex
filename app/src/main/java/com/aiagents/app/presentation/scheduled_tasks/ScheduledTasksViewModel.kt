package com.aiagents.app.presentation.scheduled_tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.diagnostics.AppErrorReporter
import com.aiagents.app.data.diagnostics.ErrorReportContext
import com.aiagents.app.data.local.ScheduledTaskDao
import com.aiagents.app.data.model.ScheduledTaskEntity
import com.aiagents.app.data.repository.AgentRepository
import com.aiagents.app.data.scheduling.TaskSchedulerManager
import com.aiagents.app.domain.model.Agent
import com.aiagents.app.domain.model.Conversation
import com.aiagents.app.domain.model.Workspace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScheduledTaskFormState(
    val label: String = "",
    val prompt: String = "",
    val scheduleType: String = "daily",
    val scheduleValue: String = "07:00",
    val workspaceId: Long? = null,
    val agentName: String? = null
)

data class ScheduledTasksUiState(
    val showEditor: Boolean = false,
    val editingTaskId: Long? = null,
    val form: ScheduledTaskFormState = ScheduledTaskFormState(),
    val taskToDelete: ScheduledTaskEntity? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ScheduledTasksViewModel @Inject constructor(
    private val scheduledTaskDao: ScheduledTaskDao,
    private val repository: AgentRepository,
    private val schedulerManager: TaskSchedulerManager,
    private val errorReporter: AppErrorReporter
) : ViewModel() {

    val tasks: StateFlow<List<ScheduledTaskEntity>> = scheduledTaskDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val workspaces: StateFlow<List<Workspace>> = repository.getAllWorkspaces()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val agents: StateFlow<List<Agent>> = repository.getAllAgents()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _uiState = MutableStateFlow(ScheduledTasksUiState())
    val uiState: StateFlow<ScheduledTasksUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            workspaces.collect { availableWorkspaces ->
                val state = _uiState.value
                if (
                    state.showEditor &&
                    state.editingTaskId == null &&
                    state.form.workspaceId == null
                ) {
                    val defaultWorkspace = availableWorkspaces
                        .firstOrNull { it.name != GLOBAL_WORKSPACE_NAME }
                        ?: availableWorkspaces.firstOrNull()
                    if (defaultWorkspace != null) {
                        _uiState.value = state.copy(
                            form = state.form.copy(workspaceId = defaultWorkspace.id)
                        )
                    }
                }
            }
        }
    }

    fun showCreateEditor() {
        val defaultWorkspace = workspaces.value.firstOrNull { it.name != GLOBAL_WORKSPACE_NAME }
            ?: workspaces.value.firstOrNull()
        _uiState.value = ScheduledTasksUiState(
            showEditor = true,
            form = ScheduledTaskFormState(workspaceId = defaultWorkspace?.id)
        )
    }

    fun showEditEditor(task: ScheduledTaskEntity) {
        _uiState.value = ScheduledTasksUiState(
            showEditor = true,
            editingTaskId = task.id,
            form = ScheduledTaskFormState(
                label = task.label,
                prompt = task.prompt,
                scheduleType = task.scheduleType,
                scheduleValue = task.scheduleValue,
                workspaceId = task.workspaceId,
                agentName = task.agentName
            )
        )
    }

    fun dismissEditor() {
        _uiState.value = ScheduledTasksUiState()
    }

    fun updateForm(form: ScheduledTaskFormState) {
        _uiState.value = _uiState.value.copy(form = form, errorMessage = null)
    }

    fun saveTask() {
        if (_uiState.value.isSaving) return
        val state = _uiState.value
        val form = state.form
        val label = form.label.trim()
        val prompt = form.prompt.trim()
        val workspaceId = form.workspaceId
        val scheduleType = form.scheduleType.trim().lowercase()
        val scheduleValue = normalizeScheduleValue(scheduleType, form.scheduleValue)

        val validationError = when {
            label.isBlank() -> "Escribe un nombre para la tarea."
            prompt.isBlank() -> "Escribe qué debe hacer el agente."
            workspaceId == null || workspaceId <= 0 -> "Selecciona un workspace."
            scheduleType !in SUPPORTED_SCHEDULES -> "Selecciona un tipo de programación válido."
            scheduleValue.isBlank() -> "Completa el horario de la tarea."
            else -> null
        }
        if (validationError != null) {
            _uiState.value = state.copy(errorMessage = validationError)
            return
        }
        val selectedWorkspaceId = checkNotNull(workspaceId)

        val nextRun = runCatching {
            schedulerManager.computeNextRun(scheduleType, scheduleValue)
        }.getOrNull()
        if (nextRun == null) {
            _uiState.value = state.copy(errorMessage = scheduleFormatError(scheduleType))
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            runCatching {
                val editingId = state.editingTaskId
                if (editingId == null) {
                    createTask(
                        label = label,
                        prompt = prompt,
                        workspaceId = selectedWorkspaceId,
                        agentName = form.agentName,
                        scheduleType = scheduleType,
                        scheduleValue = scheduleValue,
                        nextRun = nextRun
                    )
                } else {
                    updateTask(
                        id = editingId,
                        label = label,
                        prompt = prompt,
                        workspaceId = selectedWorkspaceId,
                        agentName = form.agentName,
                        scheduleType = scheduleType,
                        scheduleValue = scheduleValue,
                        nextRun = nextRun
                    )
                }
            }.onSuccess {
                _uiState.value = ScheduledTasksUiState()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = taskError(error, "scheduled_task_save")
                )
            }
        }
    }

    fun toggleTask(task: ScheduledTaskEntity, enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (!enabled) {
                    scheduledTaskDao.update(task.copy(enabled = false))
                    schedulerManager.cancelAlarm(task.id)
                    return@runCatching
                }

                val nextRun = schedulerManager.computeNextRun(
                    task.scheduleType,
                    task.scheduleValue
                ) ?: error("El horario guardado ya no es válido.")
                val updated = task.copy(enabled = true, nextRunAt = nextRun)
                scheduledTaskDao.update(updated)
                schedulerManager.scheduleAlarm(updated)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = taskError(error, "scheduled_task_toggle")
                )
            }
        }
    }

    fun openConversation(
        task: ScheduledTaskEntity,
        onReady: (workspaceId: Long, conversationId: Long) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                val existing = task.conversationId
                    ?.let { repository.getConversationById(it) }
                    ?.takeIf { it.workspaceId == task.workspaceId }
                val conversationId = existing?.id
                    ?: createCronConversation(task.workspaceId, task.label).also {
                        scheduledTaskDao.setConversationId(task.id, it)
                    }
                onReady(task.workspaceId, conversationId)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = taskError(error, "scheduled_task_conversation")
                )
            }
        }
    }

    fun requestDelete(task: ScheduledTaskEntity) {
        _uiState.value = _uiState.value.copy(taskToDelete = task, errorMessage = null)
    }

    fun dismissDelete() {
        _uiState.value = _uiState.value.copy(taskToDelete = null)
    }

    fun confirmDelete() {
        val task = _uiState.value.taskToDelete ?: return
        viewModelScope.launch {
            runCatching {
                schedulerManager.cancelAlarm(task.id)
                scheduledTaskDao.delete(task.id)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(taskToDelete = null)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    taskToDelete = null,
                    errorMessage = taskError(error, "scheduled_task_delete")
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun taskError(error: Throwable, operation: String): String =
        errorReporter.present(
            error,
            ErrorReportContext(component = "scheduled_tasks", operation = operation)
        ).displayMessage

    private suspend fun createTask(
        label: String,
        prompt: String,
        workspaceId: Long,
        agentName: String?,
        scheduleType: String,
        scheduleValue: String,
        nextRun: Long
    ) {
        check(repository.getWorkspaceById(workspaceId) != null) { "El workspace ya no existe." }
        val conversationId = createCronConversation(workspaceId, label)
        val task = ScheduledTaskEntity(
            workspaceId = workspaceId,
            conversationId = conversationId,
            agentName = agentName?.takeIf(String::isNotBlank),
            prompt = prompt,
            scheduleType = scheduleType,
            scheduleValue = scheduleValue,
            label = label,
            nextRunAt = nextRun
        )
        val id = try {
            scheduledTaskDao.insert(task)
        } catch (error: Throwable) {
            repository.deleteConversation(conversationId)
            throw error
        }
        schedulerManager.scheduleAlarm(task.copy(id = id))
    }

    private suspend fun updateTask(
        id: Long,
        label: String,
        prompt: String,
        workspaceId: Long,
        agentName: String?,
        scheduleType: String,
        scheduleValue: String,
        nextRun: Long
    ) {
        val current = scheduledTaskDao.getById(id) ?: error("La tarea ya no existe.")
        check(repository.getWorkspaceById(workspaceId) != null) { "El workspace ya no existe." }

        val linkedConversation = current.conversationId
            ?.let { repository.getConversationById(it) }
            ?.takeIf { it.workspaceId == workspaceId }
        val conversationId = linkedConversation?.id ?: createCronConversation(workspaceId, label)

        if (linkedConversation?.title?.startsWith(CRON_TITLE_PREFIX) == true) {
            repository.updateConversationTitle(conversationId, cronConversationTitle(label))
        }

        val updated = current.copy(
            workspaceId = workspaceId,
            conversationId = conversationId,
            agentName = agentName?.takeIf(String::isNotBlank),
            prompt = prompt,
            scheduleType = scheduleType,
            scheduleValue = scheduleValue,
            label = label,
            nextRunAt = nextRun
        )
        schedulerManager.cancelAlarm(id)
        scheduledTaskDao.update(updated)
        if (updated.enabled) schedulerManager.scheduleAlarm(updated)
    }

    private suspend fun createCronConversation(workspaceId: Long, label: String): Long =
        repository.createConversation(
            Conversation(
                workspaceId = workspaceId,
                title = cronConversationTitle(label)
            )
        )

    private fun cronConversationTitle(label: String): String =
        "$CRON_TITLE_PREFIX${label.ifBlank { "Tarea programada" }}".take(80)

    companion object {
        const val SCHEDULE_ONCE = "once"
        const val SCHEDULE_DAILY = "daily"
        const val SCHEDULE_WEEKLY = "weekly"
        const val SCHEDULE_INTERVAL = "interval"
        val SUPPORTED_SCHEDULES = setOf(
            SCHEDULE_ONCE,
            SCHEDULE_DAILY,
            SCHEDULE_WEEKLY,
            SCHEDULE_INTERVAL
        )

        private const val GLOBAL_WORKSPACE_NAME = "__global__"
        private const val CRON_TITLE_PREFIX = "Cron: "

        fun normalizeScheduleValue(type: String, value: String): String = when (type) {
            SCHEDULE_WEEKLY -> value.trim().uppercase()
            SCHEDULE_INTERVAL -> value.trim().lowercase()
            else -> value.trim()
        }

        fun scheduleFormatError(type: String): String = when (type) {
            SCHEDULE_ONCE -> "Usa fecha y hora: 2026-07-15T07:00. Debe estar en el futuro."
            SCHEDULE_DAILY -> "Usa una hora válida en formato HH:mm, por ejemplo 07:00."
            SCHEDULE_WEEKLY -> "Usa días y hora, por ejemplo MON,WED,FRI 07:00."
            SCHEDULE_INTERVAL -> "Usa un intervalo como 30m, 2h o 1d."
            else -> "El horario no es válido."
        }
    }
}
