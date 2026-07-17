package com.aiagents.app.presentation.agents

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiagents.app.domain.model.Agent
import com.aiagents.app.domain.model.isOrchestrator
import com.aiagents.app.ui.theme.CortexBubbleMark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: AgentsViewModel = hiltViewModel()
) {
    val agents by viewModel.agents.collectAsState()
    val formState by viewModel.formState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val autoCreateState by viewModel.autoCreateState.collectAsState()
    val configuredMcpTools = viewModel.configuredMcpTools

    var showModeSelection by remember { mutableStateOf(false) }
    var showManualCreateDialog by remember { mutableStateOf(false) }
    var showAutoCreateDialog by remember { mutableStateOf(false) }
    var agentToEdit by remember { mutableStateOf<Agent?>(null) }
    var showCortexCustomization by remember { mutableStateOf(false) }

    // Separar Cortex de los demás agentes
    val cortex = agents.find { it.isOrchestrator }
    val otherAgents = agents.filterNot { it.isOrchestrator }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agentes") },
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showModeSelection = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo agente")
            }
        }
    ) { padding ->
        if (agents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CortexBubbleMark(modifier = Modifier.size(72.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No tienes agentes creados",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Toca + para crear tu primer agente",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Sección de Cortex (Agente Orquestador) - Personalizable
                cortex?.let {
                    item {
                        CortexCard(
                            cortex = it,
                            onCustomize = { showCortexCustomization = true }
                        )
                    }
                    item {
                        Text(
                            text = "Agentes Especializados",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                
                // Lista de otros agentes
                items(otherAgents, key = { it.id }) { agent ->
                    AgentCard(
                        agent = agent,
                        onDelete = { viewModel.deleteAgent(agent.id) },
                        onEdit = { agentToEdit = agent }
                    )
                }
            }
        }

        // Diálogo de selección de modo
        if (showModeSelection) {
            AlertDialog(
                onDismissRequest = { showModeSelection = false },
                title = { Text("Nuevo Agente") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Elige cómo crear tu agente:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Opción Automática
                        OutlinedCard(
                            onClick = {
                                showModeSelection = false
                                showAutoCreateDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(44.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Automática",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Describe lo que necesitas y la IA diseña el agente por ti",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Opción Manual
                        OutlinedCard(
                            onClick = {
                                showModeSelection = false
                                showManualCreateDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(44.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Tune,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Manual",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Configura nombre, rol, prompt y personalidad a tu gusto",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showModeSelection = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Diálogo de creación automática
        if (showAutoCreateDialog) {
            var autoDescription by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = {
                    if (!autoCreateState.isCreating) {
                        showAutoCreateDialog = false
                        viewModel.clearAutoCreateState()
                    }
                },
                title = { Text("Creación Automática") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        if (autoCreateState.isCreating) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Diseñando agente...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (autoCreateState.result != null) {
                            Text(
                                autoCreateState.result!!,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else if (autoCreateState.error != null) {
                            Text(
                                autoCreateState.error!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                "Describe brevemente qué tipo de agente necesitas. La IA se encargará de diseñar su nombre, rol, personalidad e instrucciones.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = autoDescription,
                                onValueChange = { autoDescription = it },
                                label = { Text("Descripción") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 5,
                                placeholder = { Text("Ej: Un abogado que me ayude con contratos y temas legales") }
                            )
                        }
                    }
                },
                confirmButton = {
                    if (autoCreateState.result != null || autoCreateState.error != null) {
                        Button(onClick = {
                            showAutoCreateDialog = false
                            viewModel.clearAutoCreateState()
                        }) {
                            Text("Listo")
                        }
                    } else if (!autoCreateState.isCreating) {
                        Button(
                            onClick = { viewModel.autoCreateAgent(autoDescription) },
                            enabled = autoDescription.isNotBlank()
                        ) {
                            Text("Crear")
                        }
                    }
                },
                dismissButton = {
                    if (!autoCreateState.isCreating && autoCreateState.result == null) {
                        TextButton(onClick = {
                            showAutoCreateDialog = false
                            viewModel.clearAutoCreateState()
                        }) {
                            Text("Cancelar")
                        }
                    }
                }
            )
        }

        // Diálogo de crear agente (manual)
        if (showManualCreateDialog) {
            AlertDialog(
                onDismissRequest = { showManualCreateDialog = false },
                title = { Text("Nuevo Agente") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = formState.name,
                            onValueChange = { viewModel.updateFormState(formState.copy(name = it)) },
                            label = { Text("Nombre") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = formState.role,
                            onValueChange = { viewModel.updateFormState(formState.copy(role = it)) },
                            label = { Text("Rol / Especialidad") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Ej: Contador, Abogado, Programador...") }
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = formState.systemPrompt,
                            onValueChange = { viewModel.updateFormState(formState.copy(systemPrompt = it)) },
                            label = { Text("Instrucciones del sistema (opcional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = formState.whenToUse,
                            onValueChange = { viewModel.updateFormState(formState.copy(whenToUse = it)) },
                            label = { Text("Cuándo usar este agente") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3,
                            placeholder = { Text("Ej: Para preguntas sobre seguridad, auditoría de código...") }
                        )
                        if (configuredMcpTools.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            McpToolSelector(
                                configuredTools = configuredMcpTools,
                                enabledToolsCsv = formState.enabledTools,
                                onEnabledToolsChange = { viewModel.updateFormState(formState.copy(enabledTools = it)) }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.createAgent { showManualCreateDialog = false }
                        },
                        enabled = formState.name.isNotBlank()
                    ) {
                        Text("Crear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualCreateDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Diálogo de editar agente
        agentToEdit?.let { agent ->
            var editName by remember(agent.id) { mutableStateOf(agent.name) }
            var editRole by remember(agent.id) { mutableStateOf(agent.role) }
            var editSystemPrompt by remember(agent.id) { mutableStateOf(agent.systemPrompt) }
            var editWhenToUse by remember(agent.id) { mutableStateOf(agent.whenToUse) }
            var editEnabledTools by remember(agent.id) { mutableStateOf(agent.enabledTools) }

            AlertDialog(
                onDismissRequest = { agentToEdit = null },
                title = { Text(if (agent.isOrchestrator) "Editar orquestador" else "Editar Agente") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Nombre") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !agent.isOrchestrator
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editRole,
                            onValueChange = { editRole = it },
                            label = { Text("Rol / Especialidad") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Ej: Contador, Abogado, Programador...") }
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editSystemPrompt,
                            onValueChange = { editSystemPrompt = it },
                            label = { Text("Instrucciones del sistema") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editWhenToUse,
                            onValueChange = { editWhenToUse = it },
                            label = { Text("Cuándo usar este agente") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3,
                            placeholder = { Text("Ej: Para preguntas sobre seguridad, auditoría de código...") }
                        )
                        if (configuredMcpTools.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            McpToolSelector(
                                configuredTools = configuredMcpTools,
                                enabledToolsCsv = editEnabledTools,
                                onEnabledToolsChange = { editEnabledTools = it }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val updatedAgent = agent.copy(
                                name = editName,
                                role = editRole,
                                systemPrompt = editSystemPrompt,
                                whenToUse = editWhenToUse,
                                enableTerminal = true,
                                enabledTools = editEnabledTools
                            )
                            viewModel.updateAgent(updatedAgent) {
                                agentToEdit = null
                            }
                        },
                        enabled = editName.isNotBlank()
                    ) {
                        Text("Guardar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { agentToEdit = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Diálogo de personalización de Cortex
        cortex?.let {
            if (showCortexCustomization) {
                CortexPersonalityDialog(
                    cortex = it,
                    onDismiss = { showCortexCustomization = false },
                    onSave = { updatedCortex ->
                        viewModel.updateAgent(updatedCortex) {
                            showCortexCustomization = false
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CortexCard(
    cortex: Agent,
    onCustomize: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CortexBubbleMark(modifier = Modifier.size(56.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cortex.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Toca ⚙️ para personalizar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            
            IconButton(onClick = onCustomize) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Personalizar ${cortex.name}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CortexPersonalityDialog(
    cortex: Agent,
    onDismiss: () -> Unit,
    onSave: (Agent) -> Unit
) {
    var sarcasm by remember { mutableIntStateOf(cortex.sarcasmLevel) }
    var creativity by remember { mutableIntStateOf(cortex.creativityLevel) }
    var formality by remember { mutableIntStateOf(cortex.formalityLevel) }
    var empathy by remember { mutableIntStateOf(cortex.empathyLevel) }
    var technical by remember { mutableIntStateOf(cortex.technicalPrecision) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "🎨 Personalizar ${cortex.name}",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    "Ajusta la personalidad de ${cortex.name} según tus preferencias:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Sarcasm Slider
                PersonalitySlider(
                    icon = "🎭",
                    label = "Sarcasmo",
                    value = sarcasm,
                    onValueChange = { sarcasm = it },
                    lowLabel = "Serio",
                    highLabel = "Mordaz"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Creativity Slider
                PersonalitySlider(
                    icon = "🎨",
                    label = "Creatividad",
                    value = creativity,
                    onValueChange = { creativity = it },
                    lowLabel = "Convencional",
                    highLabel = "Innovador"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Formality Slider
                PersonalitySlider(
                    icon = "👔",
                    label = "Formalidad",
                    value = formality,
                    onValueChange = { formality = it },
                    lowLabel = "Casual",
                    highLabel = "Formal"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Empathy Slider
                PersonalitySlider(
                    icon = "💝",
                    label = "Empatía",
                    value = empathy,
                    onValueChange = { empathy = it },
                    lowLabel = "Objetivo",
                    highLabel = "Empático"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Technical Precision Slider
                PersonalitySlider(
                    icon = "🎯",
                    label = "Precisión Técnica",
                    value = technical,
                    onValueChange = { technical = it },
                    lowLabel = "Conceptual",
                    highLabel = "Preciso"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        cortex.copy(
                            sarcasmLevel = sarcasm,
                            creativityLevel = creativity,
                            formalityLevel = formality,
                            empathyLevel = empathy,
                            technicalPrecision = technical,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun PersonalitySlider(
    icon: String,
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    lowLabel: String,
    highLabel: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$icon $label",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = "$value",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f,
            steps = 99,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = lowLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = highLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentCard(
    agent: Agent,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CortexBubbleMark(modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    agent.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    agent.role,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Botón de editar
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Editar",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            // Botón de eliminar
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar agente") },
            text = { Text("¿Estás seguro de eliminar a ${agent.name}?") },
            confirmButton = {
                Button(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun McpToolSelector(
    configuredTools: List<Pair<String, String>>,
    enabledToolsCsv: String,
    onEnabledToolsChange: (String) -> Unit
) {
    val enabledSet = if (enabledToolsCsv.isBlank()) null
                     else enabledToolsCsv.split(",").map { it.trim() }.toSet()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Herramientas MCP",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = if (enabledSet == null) "Todas habilitadas" else "${enabledSet.size} seleccionadas",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            configuredTools.forEach { (toolId, toolLabel) ->
                val isEnabled = enabledSet == null || toolId in enabledSet
                FilterChip(
                    selected = isEnabled,
                    onClick = {
                        val currentSet = enabledSet ?: configuredTools.map { it.first }.toSet()
                        val newSet = if (toolId in currentSet) {
                            currentSet - toolId
                        } else {
                            currentSet + toolId
                        }
                        val allConfiguredIds = configuredTools.map { it.first }.toSet()
                        onEnabledToolsChange(
                            if (newSet.containsAll(allConfiguredIds)) "" else newSet.joinToString(",")
                        )
                    },
                    label = { Text(toolLabel) },
                    leadingIcon = if (isEnabled) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
    }
}
