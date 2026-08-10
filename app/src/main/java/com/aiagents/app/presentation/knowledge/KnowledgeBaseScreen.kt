package com.aiagents.app.presentation.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiagents.app.data.model.KnowledgeDocumentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeBaseScreen(
    onBack: () -> Unit,
    viewModel: KnowledgeBaseViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<KnowledgeDocumentEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Base de conocimiento (RAG)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(horizontal = 16.dp)
        ) {
            state.message?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            ModelCard(
                modelReady = state.modelReady,
                isDownloading = state.isDownloading,
                progress = state.downloadProgress,
                onDownload = viewModel::downloadModel
            )

            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Documentos (${state.documents.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { showAddDialog = true }, enabled = state.modelReady && !state.isBusy) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Añadir documento")
                }
            }

            Spacer(Modifier.height(8.dp))
            if (state.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }

            if (state.documents.isEmpty()) {
                Text(
                    text = if (state.modelReady) {
                        "Tu base de conocimiento está vacía. Añade documentos y luego podrás preguntarle a Cortex sobre ellos en el chat."
                    } else {
                        "Descarga el modelo de embeddings para poder añadir documentos."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.documents, key = { it.id }) { document ->
                        DocumentRow(
                            document = document,
                            onDelete = { pendingDelete = document }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddDocumentDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, text ->
                viewModel.addDocument(title, text)
                showAddDialog = false
            }
        )
    }

    pendingDelete?.let { document ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar documento") },
            text = { Text("¿Eliminar \"${document.title}\" y sus ${document.chunkCount} fragmentos indexados?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteDocument(document.id)
                    pendingDelete = null
                }) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun ModelCard(
    modelReady: Boolean,
    isDownloading: Boolean,
    progress: Float?,
    onDownload: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Modelo de embeddings", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (modelReady) {
                    "Universal Sentence Encoder (multilingüe) — listo ✓"
                } else if (isDownloading) {
                    "Descargando modelo (~27 MB)…"
                } else {
                    "No descargado — necesario para indexar y buscar."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isDownloading) {
                Spacer(Modifier.height(8.dp))
                progress?.let { p ->
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier.fillMaxWidth()
                    )
                } ?: CircularProgressIndicator(modifier = Modifier.height(24.dp))
            } else if (!modelReady) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onDownload, enabled = !isDownloading) {
                    Text("Descargar modelo")
                }
            }
        }
    }
}

@Composable
private fun DocumentRow(
    document: KnowledgeDocumentEntity,
    onDelete: () -> Unit
) {
    val date = remember(document.updatedAt) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(document.updatedAt))
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(document.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${document.chunkCount} fragmentos · $date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar ${document.title}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AddDocumentDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, text: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir documento") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Contenido (se dividirá y indexará automáticamente)") },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, text) },
                enabled = text.isNotBlank()
            ) {
                Text("Indexar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
