package com.aiagents.app.presentation.local_models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiagents.app.data.repository.HFBrowsableFile
import com.aiagents.app.data.repository.HFBrowsableModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuggingFaceBrowseSheet(
    onDismiss: () -> Unit,
    onModelAdded: () -> Unit,
    viewModel: HuggingFaceBrowseViewModel = hiltViewModel()
) {
    val browseResults by viewModel.browseResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedAuthor by viewModel.selectedAuthor.collectAsState()
    val customUrl by viewModel.customUrl.collectAsState()
    val addedMessage by viewModel.addedMessage.collectAsState()

    var isPasteUrlMode by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Notify parent when model is added
    LaunchedEffect(addedMessage) {
        if (addedMessage != null) {
            onModelAdded()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.85f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Title
            Text(
                text = "Buscar en HuggingFace",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Filter chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !isPasteUrlMode && selectedAuthor == "litert-community",
                    onClick = {
                        isPasteUrlMode = false
                        viewModel.selectAuthor("litert-community")
                    },
                    label = { Text("litert-community") },
                    leadingIcon = if (!isPasteUrlMode && selectedAuthor == "litert-community") {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = !isPasteUrlMode && selectedAuthor == "google",
                    onClick = {
                        isPasteUrlMode = false
                        viewModel.selectAuthor("google")
                    },
                    label = { Text("google") },
                    leadingIcon = if (!isPasteUrlMode && selectedAuthor == "google") {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = isPasteUrlMode,
                    onClick = {
                        isPasteUrlMode = true
                        viewModel.clearError()
                    },
                    label = { Text("Pegar URL") },
                    leadingIcon = if (isPasteUrlMode) {
                        { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }

            Spacer(Modifier.height(12.dp))

            // URL input (only visible in paste mode)
            if (isPasteUrlMode) {
                OutlinedTextField(
                    value = customUrl,
                    onValueChange = { viewModel.setCustomUrl(it) },
                    label = { Text("URL de HuggingFace") },
                    placeholder = { Text("huggingface.co/org/model") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = { viewModel.resolveUrl(customUrl) },
                            enabled = customUrl.isNotBlank() && !isLoading
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar")
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            // Loading indicator
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }

            // Error message
            error?.let { errorMsg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Added message
            addedMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearAddedMessage() }) {
                            Text("OK")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Results
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(browseResults) { model ->
                    BrowsableModelCard(
                        model = model,
                        onAddFile = { file ->
                            viewModel.addModelToLibrary(model, file)
                        },
                        formatBytes = ::formatBrowseBytes
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowsableModelCard(
    model: HFBrowsableModel,
    onAddFile: (HFBrowsableFile) -> Unit,
    formatBytes: (Long) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Model header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.repoName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = model.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (model.downloads > 0) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = formatDownloads(model.downloads),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }

            if (model.gated) {
                Spacer(Modifier.height(4.dp))
                AssistChip(
                    onClick = {},
                    label = { Text("Requiere token HF", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            }

            // Compatible files
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Archivos compatibles:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))

            model.files.forEach { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        if (file.sizeBytes > 0) {
                            Text(
                                text = formatBytes(file.sizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = { onAddFile(file) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Agregar",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Agregar", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private fun formatBrowseBytes(bytes: Long): String {
    val gb = bytes.toFloat() / (1024 * 1024 * 1024)
    val mb = bytes.toFloat() / (1024 * 1024)
    return when {
        gb >= 1 -> "%.1f GB".format(gb)
        mb >= 1 -> "%.0f MB".format(mb)
        else -> "$bytes B"
    }
}

private fun formatDownloads(downloads: Int): String {
    return when {
        downloads >= 1_000_000 -> "%.1fM".format(downloads / 1_000_000f)
        downloads >= 1_000 -> "%.1fK".format(downloads / 1_000f)
        else -> "$downloads"
    }
}
