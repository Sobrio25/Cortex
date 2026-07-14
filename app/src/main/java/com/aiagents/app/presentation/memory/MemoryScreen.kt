package com.aiagents.app.presentation.memory

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiagents.app.data.model.MemoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    onBack: () -> Unit,
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val memories by viewModel.memories.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val contextFileEditors by viewModel.contextFileEditors.collectAsState()
    val selectedContextFile by viewModel.selectedContextFile.collectAsState()
    val contextFileEditor = contextFileEditors.getValue(selectedContextFile)

    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryEntity?>(null) }
    val context = LocalContext.current
    val exportResult by viewModel.exportResult.collectAsState()

    val contextFileExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri -> uri?.let { viewModel.exportContextFile(selectedContextFile, it) } }

    val contextFileImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importContextFile(selectedContextFile, it) } }

    val semanticExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportMemories(it) } }

    val semanticImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importMemories(it) } }

    LaunchedEffect(exportResult) {
        exportResult?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearExportResult()
        }
    }

    LaunchedEffect(selectedContextFile, contextFileEditor.result) {
        contextFileEditor.result?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearContextFileResult(selectedContextFile)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Identidad y memoria")
                        Text(
                            "SOUL.md · USER.md · MEMORY.md",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ContextFileKind.entries.forEach { kind ->
                        FilterChip(
                            selected = selectedContextFile == kind,
                            onClick = { viewModel.selectContextFile(kind) },
                            label = { Text(kind.fileName) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                MarkdownMemoryCard(
                    fileKind = selectedContextFile,
                    markdown = contextFileEditor.draft,
                    maxChars = contextFileEditor.snapshot.maxChars,
                    entryCount = contextFileEditor.snapshot.entries.size,
                    dirty = contextFileEditor.dirty,
                    conflict = contextFileEditor.conflict,
                    storageError = contextFileEditor.snapshot.storageError,
                    characterCount = viewModel.contextFileCharacterCount(
                        selectedContextFile,
                        contextFileEditor.draft
                    ),
                    onMarkdownChange = {
                        viewModel.setContextFileDraft(selectedContextFile, it)
                    },
                    onSave = { viewModel.saveContextFile(selectedContextFile) },
                    onForceSave = { viewModel.forceSaveContextFile(selectedContextFile) },
                    onRevert = { viewModel.revertContextFileDraft(selectedContextFile) },
                    onImport = {
                        contextFileImportLauncher.launch(
                            arrayOf("text/markdown", "text/plain", "application/octet-stream")
                        )
                    },
                    onExport = { contextFileExportLauncher.launch(selectedContextFile.fileName) }
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Archivo historico recuperable",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$totalCount recuerdos semanticos en la base local. Cortex los busca cuando los necesita; no se inyectan siempre en el contexto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { semanticImportLauncher.launch(arrayOf("application/json")) }
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = "Importar archivo historico JSON")
                        }
                        IconButton(onClick = { semanticExportLauncher.launch("cortex_memory_archive.json") }) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Exportar archivo historico JSON")
                        }
                        IconButton(onClick = viewModel::runCleanupNow) {
                            Icon(Icons.Default.CleaningServices, contentDescription = "Depurar archivo historico")
                        }
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Borrar archivo historico",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar en el archivo historico...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar busqueda")
                            }
                        }
                    }
                )
            }

            item {
                SemanticCategoryFilter(
                    selectedCategory = selectedCategory,
                    onCategorySelected = viewModel::setCategory
                )
            }

            if (memories.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (searchQuery.isBlank() && selectedCategory == null) {
                                    "El archivo historico esta vacio"
                                } else {
                                    "No hay resultados"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(memories, key = { it.id }) { memory ->
                    MemoryCard(
                        memory = memory,
                        onEdit = { editingMemory = it },
                        onDelete = { viewModel.deleteMemory(it.id) }
                    )
                }
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Borrar el archivo historico") },
            text = {
                Text(
                    "Se eliminaran permanentemente los $totalCount recuerdos semanticos. " +
                        "SOUL.md, USER.md y MEMORY.md no se modificaran. Esta accion no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllMemories()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Borrar archivo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Edit dialog
    editingMemory?.let { memory ->
        EditMemoryDialog(
            memory = memory,
            onDismiss = { editingMemory = null },
            onSave = { content, importance ->
                viewModel.updateMemory(memory.id, content, importance)
                editingMemory = null
            }
        )
    }
}

@Composable
private fun MarkdownMemoryCard(
    fileKind: ContextFileKind,
    markdown: String,
    maxChars: Int,
    entryCount: Int,
    dirty: Boolean,
    conflict: Boolean,
    storageError: String?,
    characterCount: Int,
    onMarkdownChange: (String) -> Unit,
    onSave: () -> Unit,
    onForceSave: () -> Unit,
    onRevert: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit
) {
    val safeMaxChars = maxChars.coerceAtLeast(1)
    val usage = characterCount.toFloat() / safeMaxChars.toFloat()
    val overCapacity = characterCount > maxChars
    val nearCapacity = !overCapacity && characterCount * 100 >= safeMaxChars * 80
    val usageColor = when {
        overCapacity -> MaterialTheme.colorScheme.error
        nearCapacity -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Column {
                        Text(
                            fileKind.fileName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when (fileKind) {
                                ContextFileKind.SOUL -> "Identidad principal"
                                ContextFileKind.USER -> "$entryCount bloques de perfil"
                                ContextFileKind.MEMORY -> "$entryCount entradas curadas"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (dirty) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Sin guardar") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            Text(
                when (fileKind) {
                    ContextFileKind.SOUL ->
                        "Define la identidad, valores y estilo estable del agente. El nombre configurado en onboarding vive aqui; Cortex es solo el valor inicial."
                    ContextFileKind.USER ->
                        "Perfil breve de la persona: nombre, como prefiere que la llamen y preferencias duraderas. El agente puede curarlo con la tool de memoria."
                    ContextFileKind.MEMORY ->
                        "Memoria breve siempre disponible. Guarda correcciones y decisiones duraderas que seria costoso redescubrir."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LinearProgressIndicator(
                progress = { usage.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = usageColor
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "$characterCount/$maxChars caracteres",
                    style = MaterialTheme.typography.labelMedium,
                    color = usageColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${(usage * 100).toInt().coerceAtLeast(0)}% usado",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (nearCapacity || overCapacity) {
                Surface(
                    color = if (overCapacity) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            if (overCapacity) {
                                "Supera el limite por ${characterCount - maxChars} caracteres. No se guardara ni se truncara: resume o elimina contenido."
                            } else {
                                "${fileKind.fileName} supera el 80%. Conviene consolidar antes de agregar mas."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (!storageError.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "Error de almacenamiento: $storageError",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            OutlinedTextField(
                value = markdown,
                onValueChange = onMarkdownChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Contenido Markdown") },
                placeholder = {
                    Text(
                        when (fileKind) {
                            ContextFileKind.SOUL -> "You are…"
                            ContextFileKind.USER -> "- Preferred name: …"
                            ContextFileKind.MEMORY -> "- Preferencia o decision duradera"
                        }
                    )
                },
                minLines = 8,
                maxLines = 16,
                isError = overCapacity,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                supportingText = {
                    Text("El contador usa caracteres Unicode exactos; nunca se recorta automaticamente.")
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onImport,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Importar .md")
                }
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Exportar .md")
                }
            }

            if (conflict) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "${fileKind.fileName} cambio mientras editabas. Recarga la version nueva o sobrescribela conscientemente con tu borrador.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onRevert) {
                                Text("Recargar")
                            }
                            TextButton(
                                onClick = onForceSave,
                                enabled = dirty && !overCapacity
                            ) {
                                Text("Sobrescribir")
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onRevert, enabled = dirty || conflict) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Revertir")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onSave,
                    enabled = dirty && !overCapacity && !conflict
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Guardar")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SemanticCategoryFilter(
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit
) {
    val categories: List<String?> =
        listOf(null, "fact", "preference", "habit", "interaction", "relationship")
    val categoryLabels = mapOf(
        null to "Todas",
        "fact" to "Hechos",
        "preference" to "Preferencias",
        "habit" to "Habitos",
        "interaction" to "Interacciones",
        "relationship" to "Relaciones"
    )
    val categoryIcons = mapOf(
        null to Icons.Default.SelectAll,
        "fact" to Icons.Default.Info,
        "preference" to Icons.Default.Favorite,
        "habit" to Icons.Default.Repeat,
        "interaction" to Icons.AutoMirrored.Filled.Chat,
        "relationship" to Icons.Default.People
    )
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = categoryLabels[selectedCategory] ?: "Todas",
            onValueChange = {},
            readOnly = true,
            leadingIcon = {
                Icon(
                    categoryIcons[selectedCategory] ?: Icons.Default.SelectAll,
                    contentDescription = null
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            label = { Text("Categoria del archivo") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(categoryLabels[category] ?: "") },
                    leadingIcon = {
                        Icon(
                            categoryIcons[category] ?: Icons.Default.SelectAll,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onCategorySelected(category)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MemoryCard(
    memory: MemoryEntity,
    onEdit: (MemoryEntity) -> Unit,
    onDelete: (MemoryEntity) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (memory.confidence < 0.5f)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category badge
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            "${memory.category}${if (memory.subcategory.isNotBlank()) "/${memory.subcategory}" else ""}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.height(24.dp)
                )

                // Importance indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "imp:${memory.importance}",
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            memory.importance >= 8 -> MaterialTheme.colorScheme.error
                            memory.importance >= 5 -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.Bold
                    )
                    if (memory.confidence < 1.0f) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "conf:${"%.1f".format(memory.confidence)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                memory.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ID:${memory.id} | ${dateFormat.format(Date(memory.updatedAt))} | accesos:${memory.accessCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row {
                    IconButton(onClick = { onEdit(memory) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete, contentDescription = "Eliminar",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar memoria") },
            text = { Text("\"${memory.content}\"") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(memory)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun EditMemoryDialog(
    memory: MemoryEntity,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    var content by remember { mutableStateOf(memory.content) }
    var importance by remember { mutableFloatStateOf(memory.importance.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar memoria [ID:${memory.id}]") },
        text = {
            Column {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Contenido") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Importancia: ${importance.toInt()}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = importance,
                    onValueChange = { importance = it },
                    valueRange = 1f..10f,
                    steps = 8
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(content, importance.toInt()) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
