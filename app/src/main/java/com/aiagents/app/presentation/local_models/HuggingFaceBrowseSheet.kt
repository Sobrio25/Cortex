package com.aiagents.app.presentation.local_models

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val browseResults by viewModel.browseResults.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val hasSearched by viewModel.hasSearched.collectAsStateWithLifecycle()
    val loadingFileRepos by viewModel.loadingFileRepos.collectAsStateWithLifecycle()
    val fileErrors by viewModel.fileErrors.collectAsStateWithLifecycle()
    val addedFileKeys by viewModel.addedFileKeys.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val addedMessage by viewModel.addedMessage.collectAsStateWithLifecycle()

    var compatibleOnly by rememberSaveable { mutableStateOf(false) }
    val visibleResults = remember(browseResults, compatibleOnly) {
        if (compatibleOnly) browseResults.filter { it.isLocallyCompatible } else browseResults
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(addedMessage) {
        if (addedMessage != null) onModelAdded()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Modelos de Hugging Face",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Busca cualquier repositorio del Hub",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                label = { Text("Buscar modelos") },
                placeholder = { Text("Gemma, Qwen, organización/modelo o URL") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = viewModel::clearSearch) {
                                Icon(Icons.Default.Clear, contentDescription = "Borrar búsqueda")
                            }
                        }
                        IconButton(
                            onClick = viewModel::submitSearch,
                            enabled = searchQuery.isNotBlank() && !isLoading
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.submitSearch() })
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChip(
                    onClick = { viewModel.selectSuggestion("litertlm") },
                    label = { Text("LiteRT-LM") }
                )
                SuggestionChip(
                    onClick = { viewModel.selectSuggestion("mediapipe llm") },
                    label = { Text("MediaPipe") }
                )
                SuggestionChip(
                    onClick = { viewModel.selectSuggestion("Gemma") },
                    label = { Text("Gemma") }
                )
                SuggestionChip(
                    onClick = { viewModel.selectSuggestion("Qwen") },
                    label = { Text("Qwen") }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = compatibleOnly,
                    onClick = { compatibleOnly = !compatibleOnly },
                    label = { Text("Solo compatibles") },
                    leadingIcon = if (compatibleOnly) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null
                )
                Spacer(Modifier.weight(1f))
                if (hasSearched && !isLoading) {
                    Text(
                        text = if (compatibleOnly) {
                            "${visibleResults.size} de ${browseResults.size}"
                        } else {
                            "${browseResults.size} resultados"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }

            error?.let { errorMessage ->
                ErrorCard(
                    message = errorMessage,
                    onRetry = viewModel::submitSearch,
                    onDismiss = viewModel::clearError
                )
                Spacer(Modifier.height(8.dp))
            }

            addedMessage?.let { message ->
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
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::clearAddedMessage) {
                            Text("OK")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when {
                        !hasSearched && searchQuery.isBlank() -> {
                            item {
                                BrowseHintCard()
                            }
                        }

                        !isLoading && visibleResults.isEmpty() -> {
                            item {
                                EmptyResultsCard(
                                    compatibleOnly = compatibleOnly,
                                    hasUnfilteredResults = browseResults.isNotEmpty()
                                )
                            }
                        }
                    }

                    items(visibleResults, key = { it.repoId }) { model ->
                        BrowsableModelCard(
                            model = model,
                            isLoadingFiles = model.repoId in loadingFileRepos,
                            fileError = fileErrors[model.repoId],
                            addedFileKeys = addedFileKeys,
                            fileKey = { file -> viewModel.fileKey(model, file) },
                            onLoadFiles = { viewModel.loadModelFiles(model) },
                            onAddFile = { file -> viewModel.addModelToLibrary(model, file) }
                        )
                    }

                    if (hasMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                } else {
                                    OutlinedButton(onClick = viewModel::loadNextPage) {
                                        Text("Cargar más resultados")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseHintCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Busca en todo el Hub",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Mostraremos cualquier modelo, pero solo podrás agregar bundles detectados para LiteRT-LM o MediaPipe. La compatibilidad final también depende del dispositivo y del runtime.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyResultsCard(
    compatibleOnly: Boolean,
    hasUnfilteredResults: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (compatibleOnly && hasUnfilteredResults) {
                    "No hay bundles locales compatibles en estos resultados"
                } else {
                    "No encontramos modelos"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (compatibleOnly && hasUnfilteredResults) {
                    "Desactiva “Solo compatibles” para ver todos los repositorios."
                } else {
                    "Prueba con otro nombre o pega la URL completa del repositorio."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
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
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = "Reintentar")
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Ocultar error")
            }
        }
    }
}

@Composable
private fun BrowsableModelCard(
    model: HFBrowsableModel,
    isLoadingFiles: Boolean,
    fileError: String?,
    addedFileKeys: Set<String>,
    fileKey: (HFBrowsableFile) -> String,
    onLoadFiles: () -> Unit,
    onAddFile: (HFBrowsableFile) -> Unit
) {
    var expanded by rememberSaveable(model.repoId) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Card(
        onClick = {
            expanded = !expanded
            if (expanded && model.isLocallyCompatible) onLoadFiles()
        },
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = if (model.isLocallyCompatible) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.size(30.dp)
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
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Contraer" else "Ver detalles"
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (model.isLocallyCompatible) "Formato local detectado" else "No compatible localmente",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (model.isLocallyCompatible) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
                if (model.gated || model.isPrivate) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                if (model.isPrivate) "Privado" else "Requiere acceso",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }
                if (!model.libraryName.isNullOrBlank()) {
                    AssistChip(
                        onClick = {},
                        label = { Text(model.libraryName, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (model.downloads > 0) {
                    StatLabel(
                        icon = Icons.Default.Download,
                        text = formatDownloads(model.downloads)
                    )
                }
                if (model.likes > 0) {
                    StatLabel(
                        icon = Icons.Default.Favorite,
                        text = formatDownloads(model.likes.toLong())
                    )
                }
                if (model.files.isNotEmpty()) {
                    Text(
                        text = "${model.files.size} archivo${if (model.files.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                if (isLoadingFiles) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Consultando tamaños y revisión…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                fileError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (model.files.isEmpty()) {
                    Text(
                        text = "Este repositorio no publica un bundle LLM que esta app pueda cargar. Los pesos Transformers, GGUF, Safetensors y tareas de visión no son modelos locales válidos para este runtime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Bundles detectados",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    model.files.forEach { file ->
                        CompatibleFileRow(
                            file = file,
                            isAdded = fileKey(file) in addedFileKeys,
                            enabled = !isLoadingFiles,
                            onAdd = { onAddFile(file) }
                        )
                    }
                    Text(
                        text = "“Formato detectado” no garantiza que el modelo quepa en RAM ni que una variante específica de hardware funcione en este dispositivo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                TextButton(
                    onClick = { uriHandler.openUri("https://huggingface.co/${model.repoId}") },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Ver repositorio")
                }
            }
        }
    }
}

@Composable
private fun CompatibleFileRow(
    file: HFBrowsableFile,
    isAdded: Boolean,
    enabled: Boolean,
    onAdd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                Text(
                    text = buildString {
                        append(file.format.displayName)
                        append(" · ")
                        append(if (file.sizeBytes > 0) formatBrowseBytes(file.sizeBytes) else "tamaño desconocido")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = onAdd,
                enabled = enabled && !isAdded,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = if (isAdded) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (isAdded) "Agregado" else "Agregar",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        file.deviceHint?.let { hint ->
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(start = 26.dp, top = 2.dp)
            )
        }
    }
}

@Composable
private fun StatLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatBrowseBytes(bytes: Long): String {
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return when {
        gb >= 1 -> "%.2f GB".format(gb)
        mb >= 1 -> "%.0f MB".format(mb)
        else -> "$bytes B"
    }
}

private fun formatDownloads(downloads: Long): String = when {
    downloads >= 1_000_000 -> "%.1f M".format(downloads / 1_000_000f)
    downloads >= 1_000 -> "%.1f mil".format(downloads / 1_000f)
    else -> downloads.toString()
}
