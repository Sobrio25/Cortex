package com.aiagents.app.presentation.skills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiagents.app.data.skills.SkillReviewScheduler
import com.aiagents.app.domain.model.Skill
import com.aiagents.app.domain.model.SkillDraftInput
import com.aiagents.app.domain.model.SkillOrigin
import com.aiagents.app.domain.model.SkillReviewStatus
import com.aiagents.app.domain.model.SkillStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    onBack: (() -> Unit)? = null,
    showTopBar: Boolean = true,
    modifier: Modifier = Modifier,
    viewModel: SkillsViewModel = hiltViewModel()
) {
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedSkillId.collectAsStateWithLifecycle()
    val reviewSettings by viewModel.reviewSettings.collectAsStateWithLifecycle()
    val recentReviews by viewModel.recentReviews.collectAsStateWithLifecycle()
    val selectedSkill = skills.firstOrNull { it.id == selectedId }
    val snackbarHostState = remember { SnackbarHostState() }
    var filter by remember { mutableStateOf<SkillStatus?>(null) }
    var editingSkill by remember { mutableStateOf<Skill?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("Skills") },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingSkill = null
                    showEditor = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nueva skill") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                AutomaticReviewCard(
                    enabled = reviewSettings.enabled,
                    interval = reviewSettings.messageInterval,
                    lastResult = recentReviews.firstOrNull()?.status,
                    onEnabledChange = viewModel::setAutomaticReviewEnabled,
                    onIntervalChange = viewModel::setReviewInterval
                )
            }

            item {
                Text(
                    "Biblioteca",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SkillFilterChip("Todas", filter == null) { filter = null }
                    SkillFilterChip("Borradores", filter == SkillStatus.DRAFT) { filter = SkillStatus.DRAFT }
                    SkillFilterChip("Activas", filter == SkillStatus.ACTIVE) { filter = SkillStatus.ACTIVE }
                    SkillFilterChip("Inactivas", filter == SkillStatus.INACTIVE) { filter = SkillStatus.INACTIVE }
                    SkillFilterChip("Archivadas", filter == SkillStatus.ARCHIVED) { filter = SkillStatus.ARCHIVED }
                }
            }

            val filteredSkills = skills.filter { filter == null || it.status == filter }
            if (filteredSkills.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            if (skills.isEmpty()) "Todavía no hay skills." else "No hay skills con este estado.",
                            modifier = Modifier.padding(20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredSkills, key = { it.id }) { skill ->
                    SkillListCard(
                        skill = skill,
                        onClick = { viewModel.selectSkill(skill.id) },
                        onEnabledChange = { viewModel.setEnabled(skill.id, it) }
                    )
                }
            }
        }
    }

    if (selectedSkill != null && !showEditor) {
        SkillDetailDialog(
            skill = selectedSkill,
            onDismiss = { viewModel.selectSkill(null) },
            onEdit = {
                editingSkill = selectedSkill
                showEditor = true
            },
            onEnabledChange = { viewModel.setEnabled(selectedSkill.id, it) },
            onArchive = { viewModel.archive(selectedSkill.id) }
        )
    }

    if (showEditor) {
        SkillEditorDialog(
            skill = editingSkill,
            onDismiss = { showEditor = false },
            onSave = { input ->
                viewModel.saveSkill(editingSkill?.id, input) {
                    showEditor = false
                }
            }
        )
    }
}

@Composable
private fun AutomaticReviewCard(
    enabled: Boolean,
    interval: Int,
    lastResult: SkillReviewStatus?,
    onEnabledChange: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit
) {
    var intervalMenuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Aprendizaje automático",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Después de responder, el asistente puede guardar memoria y crear o mejorar skills activas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Umbral de revisión",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Box {
                        TextButton(onClick = { intervalMenuExpanded = true }) {
                            Text("$interval turnos/pasos")
                        }
                        DropdownMenu(
                            expanded = intervalMenuExpanded,
                            onDismissRequest = { intervalMenuExpanded = false }
                        ) {
                            SkillReviewScheduler.ALLOWED_INTERVALS.sorted().forEach { option ->
                                DropdownMenuItem(
                                    text = { Text("$option turnos/pasos") },
                                    onClick = {
                                        onIntervalChange(option)
                                        intervalMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (lastResult != null) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            when (lastResult) {
                                SkillReviewStatus.PENDING -> "Revisión pendiente"
                                SkillReviewStatus.DRAFT_CREATED -> "Borrador sugerido"
                                SkillReviewStatus.CHANGES_APPLIED -> "Aprendizaje guardado"
                                SkillReviewStatus.SKIPPED -> "Sin cambios duraderos"
                                SkillReviewStatus.FAILED -> "Revisión fallida"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun SkillListCard(
    skill: Skill,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        skill.name,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (skill.isImmutable) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Integrada",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Switch(
                        checked = skill.status == SkillStatus.ACTIVE,
                        onCheckedChange = onEnabledChange
                    )
                }
            },
            supportingContent = {
                Column {
                    Text(skill.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkillStatusBadge(skill.status)
                        Text(
                            "${categoryLabel(skill.category)} · ${originLabel(skill.origin)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (skill.usageCount > 0) {
                            Text(
                                "· ${skill.usageCount} ${if (skill.usageCount == 1) "uso" else "usos"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun SkillStatusBadge(status: SkillStatus) {
    val (label, color) = when (status) {
        SkillStatus.DRAFT -> "Borrador" to MaterialTheme.colorScheme.tertiary
        SkillStatus.ACTIVE -> "Activa" to Color(0xFF2E7D32)
        SkillStatus.INACTIVE -> "Inactiva" to MaterialTheme.colorScheme.error
        SkillStatus.ARCHIVED -> "Archivada" to MaterialTheme.colorScheme.outline
    }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(99.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun SkillDetailDialog(
    skill: Skill,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onArchive: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(skill.name, style = MaterialTheme.typography.headlineSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SkillStatusBadge(skill.status)
                            Text(
                                originLabel(skill.origin),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    DetailSection("Descripción", skill.description)
                    DetailSection("Cuándo usar", skill.whenToUse)
                    DetailSection("Categoría", categoryLabel(skill.category))
                    DetailSection(
                        "Tools",
                        skill.requiredTools.sorted().joinToString("\n").ifBlank { "No usa tools" },
                        monospace = true
                    )
                    DetailSection("Instrucciones", skill.instructions, monospace = true)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Activada", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Switch(
                        checked = skill.status == SkillStatus.ACTIVE,
                        onCheckedChange = onEnabledChange
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (skill.isImmutable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Contenido integrado; puedes desactivarla", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("Cerrar") }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Editar")
                        }
                        if (skill.status != SkillStatus.ARCHIVED) {
                            TextButton(onClick = onArchive) {
                                Icon(Icons.Default.Archive, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Archivar")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, body: String, monospace: Boolean = false) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
        )
    )
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun SkillEditorDialog(
    skill: Skill?,
    onDismiss: () -> Unit,
    onSave: (SkillDraftInput) -> Unit
) {
    var name by remember(skill?.id) { mutableStateOf(skill?.name.orEmpty()) }
    var description by remember(skill?.id) { mutableStateOf(skill?.description.orEmpty()) }
    var whenToUse by remember(skill?.id) { mutableStateOf(skill?.whenToUse.orEmpty()) }
    var instructions by remember(skill?.id) { mutableStateOf(skill?.instructions.orEmpty()) }
    val canSave = name.isNotBlank() && description.isNotBlank() &&
        whenToUse.isNotBlank() && instructions.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (skill == null) "Nueva skill" else "Editar skill",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Las skills nuevas se guardan como borradores hasta que decidas activarlas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.take(500) },
                        label = { Text("Descripción") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = whenToUse,
                        onValueChange = { whenToUse = it.take(500) },
                        label = { Text("Cuándo usar") },
                        supportingText = { Text("Señales, temas o solicitudes que deben seleccionarla") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = instructions,
                        onValueChange = { instructions = it.take(20_000) },
                        label = { Text("Instrucciones") },
                        minLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = canSave,
                        onClick = {
                            onSave(
                                SkillDraftInput(
                                    name = name,
                                    description = description,
                                    whenToUse = whenToUse,
                                    instructions = instructions
                                )
                            )
                        }
                    ) {
                        Text(if (skill == null) "Guardar borrador" else "Guardar cambios")
                    }
                }
            }
        }
    }
}

private fun originLabel(origin: SkillOrigin): String = when (origin) {
    SkillOrigin.USER -> "Usuario"
    SkillOrigin.AUTO -> "Sugerida"
    SkillOrigin.IMPORTED -> "Importada"
    SkillOrigin.BUILTIN -> "Integrada"
}

private fun categoryLabel(category: com.aiagents.app.domain.model.CapabilityCategory): String =
    when (category) {
        com.aiagents.app.domain.model.CapabilityCategory.CORE -> "Núcleo"
        com.aiagents.app.domain.model.CapabilityCategory.PRODUCTIVITY -> "Productividad"
        com.aiagents.app.domain.model.CapabilityCategory.KNOWLEDGE -> "Conocimiento"
        com.aiagents.app.domain.model.CapabilityCategory.CREATION -> "Creación"
        com.aiagents.app.domain.model.CapabilityCategory.DEVICE -> "Dispositivo"
        com.aiagents.app.domain.model.CapabilityCategory.INTEGRATIONS -> "Integraciones"
        com.aiagents.app.domain.model.CapabilityCategory.CUSTOM -> "Personalizada"
    }
