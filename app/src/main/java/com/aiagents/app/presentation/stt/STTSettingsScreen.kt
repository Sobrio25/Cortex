package com.aiagents.app.presentation.stt

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiagents.app.data.model.CloudSTTProvider
import com.aiagents.app.data.model.LocalModelType
import com.aiagents.app.data.model.LocalSTTEngine
import com.aiagents.app.data.model.STTMode
import com.aiagents.app.data.speech.STTManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun STTSettingsDialog(
    currentSettings: STTSettingsUiState,
    onSave: (STTSettingsUiState) -> Unit,
    onDismiss: () -> Unit,
    onDownloadModel: () -> Unit,
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f,
    isVoskModelDownloaded: Boolean = false
) {
    var selectedMode by remember { mutableStateOf(currentSettings.mode) }
    var selectedProvider by remember { mutableStateOf(currentSettings.cloudProvider) }
    var apiKey by remember { mutableStateOf(currentSettings.apiKey) }
    var selectedLocalModel by remember { mutableStateOf(currentSettings.localModelType) }
    var selectedLocalEngine by remember { mutableStateOf(currentSettings.localEngine) }
    var language by remember { mutableStateOf(currentSettings.language) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Text("Configuracion de Voz")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Mode selector
                Text(
                    "Modo de transcripcion",
                    style = MaterialTheme.typography.titleSmall
                )
                STTModeSelector(
                    selectedMode = selectedMode,
                    onModeSelected = { selectedMode = it }
                )

                // LOCAL settings - download Vosk model
                AnimatedVisibility(
                    visible = selectedMode == STTMode.LOCAL,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    LocalSTTSettings(
                        isModelDownloaded = isVoskModelDownloaded,
                        isDownloading = isDownloading,
                        downloadProgress = downloadProgress,
                        onDownloadModel = onDownloadModel
                    )
                }

                // CLOUD settings
                AnimatedVisibility(
                    visible = selectedMode == STTMode.CLOUD,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    CloudSTTSettings(
                        selectedProvider = selectedProvider,
                        apiKey = apiKey,
                        onProviderSelected = { selectedProvider = it },
                        onApiKeyChange = { apiKey = it }
                    )
                }

                // Language selector
                Text(
                    "Idioma",
                    style = MaterialTheme.typography.titleSmall
                )
                LanguageSelector(
                    selectedLanguage = language,
                    onLanguageSelected = { language = it }
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onSave(
                        STTSettingsUiState(
                            mode = selectedMode,
                            cloudProvider = selectedProvider,
                            apiKey = apiKey,
                            localModelType = selectedLocalModel,
                            localEngine = selectedLocalEngine,
                            language = language
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
fun STTModeSelector(
    selectedMode: STTMode,
    onModeSelected: (STTMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        STTModeOption(
            title = "Procesamiento Local",
            description = "Procesamiento en el dispositivo. Sin internet, privado.",
            icon = Icons.Default.OfflineBolt,
            isSelected = selectedMode == STTMode.LOCAL,
            onSelected = { onModeSelected(STTMode.LOCAL) }
        )
        STTModeOption(
            title = "Procesamiento en la Nube",
            description = "Mayor precision. Requiere internet.",
            icon = Icons.Default.Cloud,
            isSelected = selectedMode == STTMode.CLOUD,
            onSelected = { onModeSelected(STTMode.CLOUD) }
        )
    }
}

@Composable
fun STTModeOption(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    Card(
        onClick = onSelected,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected)
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else
            null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun CloudSTTSettings(
    selectedProvider: CloudSTTProvider,
    apiKey: String,
    onProviderSelected: (CloudSTTProvider) -> Unit,
    onApiKeyChange: (String) -> Unit
) {
    val requiresApiKey = selectedProvider != CloudSTTProvider.ANDROID_SPEECH_RECOGNIZER

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Proveedor en la nube",
            style = MaterialTheme.typography.titleSmall
        )

        val providers = listOf(
            Triple(CloudSTTProvider.ANDROID_SPEECH_RECOGNIZER, "Google Integrado", "Gratis, sin API key, sin limites"),
            Triple(CloudSTTProvider.ASSEMBLY_AI, "AssemblyAI", "100 horas/mes gratis"),
            Triple(CloudSTTProvider.DEEPGRAM, "Deepgram", "$200 iniciales"),
            Triple(CloudSTTProvider.GOOGLE_FREE, "Google Cloud", "60 min/mes gratis"),
            Triple(CloudSTTProvider.WHISPER_API, "OpenAI Whisper", "$5 iniciales")
        )

        providers.forEach { (provider, name, description) ->
            Card(
                onClick = { onProviderSelected(provider) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedProvider == provider)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = selectedProvider == provider,
                        onClick = { onProviderSelected(provider) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Only show API key field when provider needs it
        AnimatedVisibility(
            visible = requiresApiKey,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API Key") },
                    placeholder = { Text("Ingresa tu API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedProvider == CloudSTTProvider.ASSEMBLY_AI) {
                    Text(
                        "Tip: AssemblyAI no requiere tarjeta de credito para el tier gratuito.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (!requiresApiKey) {
            Text(
                "Usa el reconocimiento de voz integrado de Android. No requiere configuracion.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun LocalSTTSettings(
    isModelDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    onDownloadModel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Modelo local (Vosk)",
            style = MaterialTheme.typography.titleSmall
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isModelDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                        contentDescription = null,
                        tint = if (isModelDownloaded)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "vosk-model-small-es",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (isModelDownloaded) "Descargado" else "~50 MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isModelDownloaded)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isModelDownloaded) {
                    if (isDownloading) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "Descargando... ${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FilledTonalButton(
                            onClick = onDownloadModel,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Descargar modelo")
                        }
                    }

                    Text(
                        "Requiere descargar el modelo para usar reconocimiento de voz offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelector(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    val languages = listOf(
        "auto" to "Deteccion automatica",
        "es" to "Espanol",
        "en" to "English",
        "pt" to "Portugues",
        "fr" to "Francais",
        "de" to "Deutsch",
        "it" to "Italiano"
    )

    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = languages.find { it.first == selectedLanguage }?.second ?: "Auto",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            languages.forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onLanguageSelected(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

data class STTSettingsUiState(
    val mode: STTMode = STTMode.LOCAL,
    val cloudProvider: CloudSTTProvider = CloudSTTProvider.ANDROID_SPEECH_RECOGNIZER,
    val apiKey: String = "",
    val localModelType: LocalModelType = LocalModelType.AUTO,
    val localEngine: LocalSTTEngine = LocalSTTEngine.AUTO,
    val language: String = "es"
)
