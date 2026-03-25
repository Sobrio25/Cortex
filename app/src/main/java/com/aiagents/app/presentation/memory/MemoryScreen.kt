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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryEntity?>(null) }
    val context = LocalContext.current
    val exportResult by viewModel.exportResult.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportMemories(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importMemories(it) } }

    LaunchedEffect(exportResult) {
        exportResult?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearExportResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Memoria de Cortex")
                        Text(
                            "$totalCount memorias",
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
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Importar")
                    }
                    IconButton(onClick = { exportLauncher.launch("cortex_memories.json") }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Exportar")
                    }
                    IconButton(onClick = { viewModel.runCleanupNow() }) {
                        Icon(Icons.Default.CleaningServices, contentDescription = "Limpiar")
                    }
                    IconButton(onClick = { showDeleteAllDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Borrar todo")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Buscar memorias...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                        }
                    }
                }
            )

            // Category filter dropdown
            val categories = listOf(null, "fact", "preference", "habit", "interaction", "relationship")
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
            var categoryExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
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
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    label = { Text("Categoria") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
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
                                viewModel.setCategory(category)
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (memories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Sin memorias",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
    }

    // Delete all confirmation
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Borrar todas las memorias") },
            text = { Text("Se eliminaran permanentemente las $totalCount memorias. Esta accion no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllMemories()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Borrar todo") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancelar") }
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
