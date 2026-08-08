package com.aiagents.app.presentation.local_models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiagents.app.data.model.CustomLocalModelEntity
import com.aiagents.app.domain.model.LocalModel
import com.aiagents.app.domain.model.ModelDownloadHelp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: LocalModelsViewModel = hiltViewModel()
) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val successMessage by viewModel.successMessage.collectAsStateWithLifecycle()
    val huggingFaceToken by viewModel.huggingFaceToken.collectAsStateWithLifecycle()

    val customModels by viewModel.customModels.collectAsStateWithLifecycle()

    var showHelpDialog by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var showBrowseSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modelos Locales") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showTokenDialog = true }) {
                        Icon(
                            imageVector = if (huggingFaceToken.isNotBlank())
                                Icons.Default.Key
                            else
                                Icons.Default.VpnKey,
                            contentDescription = "Token HuggingFace",
                            tint = if (huggingFaceToken.isNotBlank())
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Help,
                            contentDescription = "Ayuda"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Banner de token de HuggingFace
            item {
                if (huggingFaceToken.isBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        onClick = { showTokenDialog = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Token de HuggingFace requerido",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Algunos modelos Gemma anteriores requieren autenticación. Toca aquí para configurar tu token.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        onClick = { showTokenDialog = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Token HuggingFace configurado ✓",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "hf_****${huggingFaceToken.takeLast(4)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar token",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Botón de ayuda
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    onClick = { showHelpDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "¿Cómo obtener modelos?",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Instrucciones paso a paso para descargar e importar modelos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Modelos disponibles",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            items(models) { model ->
                val isCustom = customModels.any { it.id == model.id }
                ModelCard(
                    model = model,
                    isDownloading = isDownloading[model.id] == true,
                    downloadProgress = downloadProgress[model.id] ?: 0f,
                    hasToken = huggingFaceToken.isNotBlank(),
                    onDownload = { viewModel.downloadModel(model) },
                    onCancelDownload = { viewModel.cancelDownload(model.id) },
                    onDelete = { viewModel.deleteModel(model) },
                    onShowTokenDialog = { showTokenDialog = true },
                    onShowHelp = { showHelpDialog = true },
                    formatBytes = viewModel::formatBytes,
                    isCustomModel = isCustom,
                    onRemoveCustom = if (isCustom) {
                        { viewModel.removeCustomModel(model.id) }
                    } else null
                )
            }

            // "Buscar en HuggingFace" button
            item {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    onClick = { showBrowseSheet = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Buscar cualquier modelo",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Busca en todo Hugging Face y filtra bundles para Android",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Error dialog
        errorMessage?.let { error ->
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Error de descarga")
                    }
                },
                text = {
                    Column {
                        Text(error)
                        if (error.contains("403") || error.contains("401") || error.contains("token", ignoreCase = true)) {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    viewModel.clearError()
                                    showTokenDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Configurar token de HuggingFace")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("OK")
                    }
                }
            )
        }

        // Success snackbar
        successMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { viewModel.clearSuccess() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Listo")
                    }
                },
                text = { Text(msg) },
                confirmButton = {
                    Button(onClick = { viewModel.clearSuccess() }) {
                        Text("OK")
                    }
                }
            )
        }

        // Token dialog
        if (showTokenDialog) {
            HuggingFaceTokenDialog(
                currentToken = huggingFaceToken,
                onSave = { token ->
                    viewModel.saveHuggingFaceToken(token)
                    showTokenDialog = false
                },
                onDismiss = { showTokenDialog = false }
            )
        }

        // HuggingFace browse sheet
        if (showBrowseSheet) {
            HuggingFaceBrowseSheet(
                onDismiss = { showBrowseSheet = false },
                onModelAdded = {
                    viewModel.loadModels()
                }
            )
        }

        // Help dialog
        if (showHelpDialog) {
            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                title = { Text(ModelDownloadHelp.TITLE) },
                text = {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp)
                    ) {
                        item {
                            Text(
                                ModelDownloadHelp.INSTRUCTIONS,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showHelpDialog = false }) {
                        Text("Entendido")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showHelpDialog = false
                            showTokenDialog = true
                        }
                    ) {
                        Text("Configurar token")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuggingFaceTokenDialog(
    currentToken: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var token by remember { mutableStateOf(currentToken) }
    var showToken by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Token de HuggingFace")
            }
        },
        text = {
            Column {
                Text(
                    "Para descargar modelos Gemma necesitas un token de HuggingFace con permisos de lectura.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "Pasos:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "1. Ve a huggingface.co/settings/tokens\n" +
                            "2. Crea un token tipo 'Read'\n" +
                            "3. Acepta la licencia en huggingface.co/google/gemma-2b-it\n" +
                            "4. Pega el token aquí",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token (hf_...)") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showToken)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    singleLine = true,
                    placeholder = { Text("hf_xxxxxxxxxxxxxxxxxxxx") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(token) }
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
fun ModelCard(
    model: LocalModel,
    isDownloading: Boolean,
    downloadProgress: Float,
    hasToken: Boolean,
    onDownload: () -> Unit,
    onCancelDownload: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onShowTokenDialog: () -> Unit,
    onShowHelp: () -> Unit,
    formatBytes: (Long) -> String,
    isCustomModel: Boolean = false,
    onRemoveCustom: (() -> Unit)? = null
) {
    val isImportModel = model.huggingFaceUrl.isBlank()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = if (model.isDownloaded)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(top = 4.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (model.sizeBytes > 0) {
                        Text(
                            text = "${formatBytes(model.sizeBytes)} • ${model.contextLength} tokens contexto",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (model.requiresHFToken) {
                        Spacer(Modifier.height(4.dp))
                        AssistChip(
                            onClick = onShowHelp,
                            label = { Text("Requiere licencia + token HF", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Botones de acción
                when {
                    isDownloading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(52.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.size(44.dp),
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    text = "${(downloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            if (onCancelDownload != null) {
                                IconButton(onClick = onCancelDownload) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancelar descarga",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    model.isDownloaded -> {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Eliminar",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    isImportModel -> {
                        // Modelo sin URL: solo importación manual
                        FilledTonalButton(
                            onClick = onShowHelp,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Importar",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Importar", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    model.requiresHFToken && !hasToken -> {
                        // Modelo que requiere token pero no está configurado
                        OutlinedButton(
                            onClick = onShowTokenDialog,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Token", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    else -> {
                        Button(
                            onClick = onDownload,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Descargar",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Descargar", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            if (model.isDownloaded) {
                Spacer(Modifier.height(8.dp))
                AssistChip(
                    onClick = { },
                    label = { Text("Descargado y listo para usar") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }

            // Remove custom model option
            if (isCustomModel && onRemoveCustom != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onRemoveCustom,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        Icons.Default.RemoveCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Quitar de la lista",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (isDownloading && model.sizeBytes > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${formatBytes((model.sizeBytes * downloadProgress).toLong())} / ${formatBytes(model.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
