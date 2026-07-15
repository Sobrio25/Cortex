package com.aiagents.app.presentation.scheduled_tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiagents.app.R
import com.aiagents.app.data.model.ScheduledTaskEntity
import com.aiagents.app.domain.model.Agent
import com.aiagents.app.domain.model.Workspace
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTasksScreen(
    onBack: () -> Unit,
    onOpenConversation: (workspaceId: Long, conversationId: Long) -> Unit,
    viewModel: ScheduledTasksViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsState()
    val workspaces by viewModel.workspaces.collectAsState()
    val agents by viewModel.agents.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage, uiState.showEditor) {
        val message = uiState.errorMessage
        if (message != null && !uiState.showEditor) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scheduled_tasks_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::showCreateEditor,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.scheduled_tasks_add)) }
            )
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            EmptyScheduledTasks(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onCreate = viewModel::showCreateEditor
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    ScheduledTaskCard(
                        task = task,
                        workspaceName = workspaces.workspaceName(task.workspaceId),
                        onToggle = { viewModel.toggleTask(task, it) },
                        onEdit = { viewModel.showEditEditor(task) },
                        onDelete = { viewModel.requestDelete(task) },
                        onOpenConversation = {
                            viewModel.openConversation(task) { workspaceId, conversationId ->
                                onOpenConversation(workspaceId, conversationId)
                            }
                        }
                    )
                }
            }
        }
    }

    if (uiState.showEditor) {
        ScheduledTaskEditorDialog(
            form = uiState.form,
            isEditing = uiState.editingTaskId != null,
            isSaving = uiState.isSaving,
            errorMessage = uiState.errorMessage,
            workspaces = workspaces,
            agents = agents,
            onFormChange = viewModel::updateForm,
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::saveTask
        )
    }

    uiState.taskToDelete?.let { task ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.scheduled_tasks_delete_title)) },
            text = {
                Text(stringResource(R.string.scheduled_tasks_delete_message, task.label))
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.scheduled_tasks_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) {
                    Text(stringResource(R.string.scheduled_tasks_cancel))
                }
            }
        )
    }
}

@Composable
private fun EmptyScheduledTasks(
    modifier: Modifier = Modifier,
    onCreate: () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.scheduled_tasks_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.scheduled_tasks_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.scheduled_tasks_add))
            }
        }
    }
}

@Composable
private fun ScheduledTaskCard(
    task: ScheduledTaskEntity,
    workspaceName: String,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenConversation: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (task.enabled) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.label.ifBlank {
                            stringResource(R.string.scheduled_tasks_unnamed)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = scheduleDescription(task),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Switch(checked = task.enabled, onCheckedChange = onToggle)
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.scheduled_tasks_edit)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.scheduled_tasks_delete_confirm)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = task.prompt,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.scheduled_tasks_workspace_value, workspaceName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.scheduled_tasks_agent_value,
                    task.agentName ?: stringResource(R.string.scheduled_tasks_default_agent)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.scheduled_tasks_next_run_value,
                    if (task.nextRunAt > 0) formatTaskTimestamp(task.nextRunAt) else "—"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            task.lastRunAt?.let {
                Text(
                    text = stringResource(
                        R.string.scheduled_tasks_last_run_value,
                        formatTaskTimestamp(it)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.scheduled_tasks_edit))
                }
                TextButton(
                    onClick = onOpenConversation
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.scheduled_tasks_open_chat))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduledTaskEditorDialog(
    form: ScheduledTaskFormState,
    isEditing: Boolean,
    isSaving: Boolean,
    errorMessage: String?,
    workspaces: List<Workspace>,
    agents: List<Agent>,
    onFormChange: (ScheduledTaskFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val globalWorkspaceName = stringResource(R.string.scheduled_tasks_global_workspace)
    val defaultAgentName = stringResource(R.string.scheduled_tasks_default_agent)
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                stringResource(
                    if (isEditing) R.string.scheduled_tasks_edit_title
                    else R.string.scheduled_tasks_create_title
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = form.label,
                    onValueChange = { onFormChange(form.copy(label = it)) },
                    label = { Text(stringResource(R.string.scheduled_tasks_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = form.prompt,
                    onValueChange = { onFormChange(form.copy(prompt = it)) },
                    label = { Text(stringResource(R.string.scheduled_tasks_prompt_label)) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                SelectionDropdown(
                    label = stringResource(R.string.scheduled_tasks_workspace_label),
                    selectedLabel = workspaces.workspaceName(form.workspaceId),
                    options = workspaces.map {
                        it.id to it.displayName(globalWorkspaceName)
                    },
                    onSelected = { onFormChange(form.copy(workspaceId = it)) }
                )

                val agentOptions = buildList<Pair<String?, String>> {
                    add(null to defaultAgentName)
                    agents.forEach { add(it.name to it.name) }
                }
                SelectionDropdown(
                    label = stringResource(R.string.scheduled_tasks_agent_label),
                    selectedLabel = agentOptions.firstOrNull { it.first == form.agentName }?.second
                        ?: form.agentName
                        ?: defaultAgentName,
                    options = agentOptions,
                    onSelected = { onFormChange(form.copy(agentName = it)) }
                )

                val scheduleOptions = listOf(
                    ScheduledTasksViewModel.SCHEDULE_ONCE to stringResource(R.string.scheduled_tasks_type_once),
                    ScheduledTasksViewModel.SCHEDULE_DAILY to stringResource(R.string.scheduled_tasks_type_daily),
                    ScheduledTasksViewModel.SCHEDULE_WEEKLY to stringResource(R.string.scheduled_tasks_type_weekly),
                    ScheduledTasksViewModel.SCHEDULE_INTERVAL to stringResource(R.string.scheduled_tasks_type_interval)
                )
                SelectionDropdown(
                    label = stringResource(R.string.scheduled_tasks_type_label),
                    selectedLabel = scheduleOptions
                        .firstOrNull { it.first == form.scheduleType }
                        ?.second
                        ?: form.scheduleType,
                    options = scheduleOptions,
                    onSelected = {
                        onFormChange(
                            form.copy(
                                scheduleType = it,
                                scheduleValue = defaultScheduleValue(it)
                            )
                        )
                    }
                )

                OutlinedTextField(
                    value = form.scheduleValue,
                    onValueChange = { onFormChange(form.copy(scheduleValue = it)) },
                    label = { Text(stringResource(R.string.scheduled_tasks_schedule_value_label)) },
                    supportingText = { Text(scheduleValueHint(form.scheduleType)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !isSaving && workspaces.isNotEmpty()) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .width(18.dp)
                            .height(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.scheduled_tasks_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.scheduled_tasks_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SelectionDropdown(
    label: String,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        expanded = false
                        onSelected(value)
                    }
                )
            }
        }
    }
}

@Composable
private fun scheduleDescription(task: ScheduledTaskEntity): String = when (task.scheduleType) {
    ScheduledTasksViewModel.SCHEDULE_ONCE ->
        stringResource(R.string.scheduled_tasks_schedule_once_value, task.scheduleValue)
    ScheduledTasksViewModel.SCHEDULE_DAILY ->
        stringResource(R.string.scheduled_tasks_schedule_daily_value, task.scheduleValue)
    ScheduledTasksViewModel.SCHEDULE_WEEKLY ->
        stringResource(R.string.scheduled_tasks_schedule_weekly_value, task.scheduleValue)
    ScheduledTasksViewModel.SCHEDULE_INTERVAL ->
        stringResource(R.string.scheduled_tasks_schedule_interval_value, task.scheduleValue)
    else -> task.scheduleValue
}

@Composable
private fun scheduleValueHint(type: String): String = when (type) {
    ScheduledTasksViewModel.SCHEDULE_ONCE ->
        stringResource(R.string.scheduled_tasks_hint_once)
    ScheduledTasksViewModel.SCHEDULE_DAILY ->
        stringResource(R.string.scheduled_tasks_hint_daily)
    ScheduledTasksViewModel.SCHEDULE_WEEKLY ->
        stringResource(R.string.scheduled_tasks_hint_weekly)
    ScheduledTasksViewModel.SCHEDULE_INTERVAL ->
        stringResource(R.string.scheduled_tasks_hint_interval)
    else -> ""
}

private fun defaultScheduleValue(type: String): String = when (type) {
    ScheduledTasksViewModel.SCHEDULE_ONCE -> ""
    ScheduledTasksViewModel.SCHEDULE_DAILY -> "07:00"
    ScheduledTasksViewModel.SCHEDULE_WEEKLY -> "MON,WED,FRI 07:00"
    ScheduledTasksViewModel.SCHEDULE_INTERVAL -> "1h"
    else -> ""
}

private fun formatTaskTimestamp(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))

@Composable
private fun List<Workspace>.workspaceName(workspaceId: Long?): String {
    val workspace = firstOrNull { it.id == workspaceId }
    val globalName = stringResource(R.string.scheduled_tasks_global_workspace)
    return workspace?.displayName(globalName)
        ?: stringResource(R.string.scheduled_tasks_unknown_workspace)
}

private fun Workspace.displayName(globalName: String): String =
    if (name == "__global__") globalName else name
