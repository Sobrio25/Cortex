package com.aiagents.app.presentation.voice

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiagents.app.data.speech.AssistantSttMode
import com.aiagents.app.data.speech.AssistantTtsMode
import com.aiagents.app.data.speech.VoiceCatalog
import com.aiagents.app.presentation.assistant.DownloadableVoiceCard
import com.aiagents.app.presentation.assistant.GoogleTtsCard
import com.aiagents.app.presentation.assistant.RemoteSttServerCard
import com.aiagents.app.presentation.assistant.RemoteTtsServerCard
import com.aiagents.app.presentation.assistant.SettingsCard
import com.aiagents.app.presentation.assistant.VoiceChoiceCard
import com.aiagents.app.presentation.assistant.VoiceSectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(
    onBack: () -> Unit,
    viewModel: VoiceSettingsViewModel = hiltViewModel()
) {
    val sttMode by viewModel.sttMode.collectAsState()
    val sttLanguage by viewModel.sttLanguage.collectAsState()
    val ttsMode by viewModel.ttsMode.collectAsState()
    val googleVoices by viewModel.googleVoices.collectAsState()
    val googleVoiceId by viewModel.selectedGoogleVoiceId.collectAsState()
    val remoteSttConfig by viewModel.remoteSttConfig.collectAsState()
    val remoteTtsConfig by viewModel.remoteTtsConfig.collectAsState()
    val voiceAssets by viewModel.voiceAssets.collectAsState()
    val voiceFeatureState by viewModel.voiceFeatureState.collectAsState()
    val error by viewModel.error.collectAsState()
    var remoteSttExpanded by rememberSaveable {
        mutableStateOf(sttMode == AssistantSttMode.REMOTE_SERVER)
    }

    LaunchedEffect(sttMode) {
        remoteSttExpanded = sttMode == AssistantSttMode.REMOTE_SERVER
    }

    val installVoiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshGoogleVoices() }
    val voiceConfirmationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { viewModel.refreshGoogleVoices() }
    val voicePackPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.resumeVoicePackInstall() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voz") },
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
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SettingsCard(
                    title = "Entrada de voz (STT)",
                    subtitle = "Una sola configuracion para todos los chats y el asistente"
                ) {
                    VoiceSectionHeader(
                        icon = Icons.Default.Mic,
                        title = "Motor de reconocimiento",
                        subtitle = "Cortex usa este motor en toda la app"
                    )
                    VoiceChoiceCard(
                        title = "Reconocimiento de Android",
                        description = when {
                            viewModel.onDeviceRecognitionAvailable ->
                                "Disponible directamente en el dispositivo"
                            viewModel.systemRecognitionAvailable ->
                                "Disponible mediante el servicio del sistema"
                            else -> "Android no informa un reconocedor compatible"
                        },
                        badge = "Sistema",
                        selected = sttMode == AssistantSttMode.ANDROID,
                        available = viewModel.onDeviceRecognitionAvailable ||
                            viewModel.systemRecognitionAvailable,
                        onSelect = {
                            remoteSttExpanded = false
                            viewModel.selectSttMode(AssistantSttMode.ANDROID)
                        }
                    )
                    DownloadableVoiceCard(
                        title = "Whisper Tiny",
                        description = "Privado, multilingue y completamente offline",
                        badge = "111 MB",
                        selected = sttMode == AssistantSttMode.WHISPER_TINY,
                        state = voiceAssets[VoiceCatalog.WHISPER_TINY_ID] ?: VoiceAssetUiState(),
                        moduleState = voiceFeatureState,
                        onSelect = {
                            remoteSttExpanded = false
                            viewModel.selectSttMode(AssistantSttMode.WHISPER_TINY)
                        },
                        onDownload = { viewModel.downloadAsset(VoiceCatalog.WHISPER_TINY_ID) },
                        onDelete = { viewModel.deleteAsset(VoiceCatalog.WHISPER_TINY_ID) }
                    )
                    RemoteSttServerCard(
                        selected = sttMode == AssistantSttMode.REMOTE_SERVER,
                        expanded = remoteSttExpanded,
                        config = remoteSttConfig,
                        onExpand = { remoteSttExpanded = true },
                        onSelect = { viewModel.selectSttMode(AssistantSttMode.REMOTE_SERVER) },
                        onSave = viewModel::saveRemoteSttConfig
                    )
                    Spacer(Modifier.height(8.dp))
                    SttLanguageSelector(sttLanguage, viewModel::setSttLanguage)
                }
            }

            item {
                SettingsCard(
                    title = "Proveedores STT avanzados",
                    subtitle = "Servicios externos con API key cifrada en el dispositivo"
                ) {
                    AdvancedSttProviderCard(
                        title = "OpenAI Whisper",
                        mode = AssistantSttMode.OPENAI_WHISPER,
                        selectedMode = sttMode,
                        initialApiKey = viewModel.getSttApiKey(AssistantSttMode.OPENAI_WHISPER),
                        onSave = viewModel::saveCloudSttProvider
                    )
                    AdvancedSttProviderCard(
                        title = "AssemblyAI",
                        mode = AssistantSttMode.ASSEMBLY_AI,
                        selectedMode = sttMode,
                        initialApiKey = viewModel.getSttApiKey(AssistantSttMode.ASSEMBLY_AI),
                        onSave = viewModel::saveCloudSttProvider
                    )
                    AdvancedSttProviderCard(
                        title = "Deepgram",
                        mode = AssistantSttMode.DEEPGRAM,
                        selectedMode = sttMode,
                        initialApiKey = viewModel.getSttApiKey(AssistantSttMode.DEEPGRAM),
                        onSave = viewModel::saveCloudSttProvider
                    )
                    AdvancedSttProviderCard(
                        title = "Google Cloud Speech",
                        mode = AssistantSttMode.GOOGLE_CLOUD,
                        selectedMode = sttMode,
                        initialApiKey = viewModel.getSttApiKey(AssistantSttMode.GOOGLE_CLOUD),
                        onSave = viewModel::saveCloudSttProvider
                    )
                }
            }

            item {
                SettingsCard(
                    title = "Salida de voz (TTS)",
                    subtitle = "La voz elegida se usa para leer respuestas en toda la app"
                ) {
                    VoiceSectionHeader(
                        icon = Icons.Default.GraphicEq,
                        title = "Motor de voz",
                        subtitle = "El chat la usa manualmente y el asistente segun su comportamiento"
                    )
                    VoiceChoiceCard(
                        title = "Sin voz",
                        description = "Las respuestas solo apareceran en pantalla",
                        badge = "0 MB",
                        selected = ttsMode == AssistantTtsMode.NONE,
                        available = true,
                        onSelect = { viewModel.selectTtsMode(AssistantTtsMode.NONE) }
                    )
                    GoogleTtsCard(
                        selected = ttsMode == AssistantTtsMode.GOOGLE,
                        voices = googleVoices,
                        selectedVoiceId = googleVoiceId,
                        onSelect = { viewModel.selectTtsMode(AssistantTtsMode.GOOGLE) },
                        onVoiceSelected = viewModel::selectGoogleVoice,
                        onPreview = viewModel::previewGoogleVoice,
                        onManageVoices = {
                            installVoiceLauncher.launch(viewModel.createInstallVoiceIntent())
                        }
                    )
                    RemoteTtsServerCard(
                        selected = ttsMode == AssistantTtsMode.REMOTE_SERVER,
                        config = remoteTtsConfig,
                        onSelect = { viewModel.selectTtsMode(AssistantTtsMode.REMOTE_SERVER) },
                        onSave = viewModel::saveRemoteTtsConfig,
                        onPreview = { viewModel.saveRemoteTtsConfig(it, preview = true) }
                    )
                    DownloadableVoiceCard(
                        title = "Piper · Ald",
                        description = "Espanol de Mexico, voz offline de calidad media",
                        badge = "20 MB",
                        selected = ttsMode == AssistantTtsMode.PIPER_ALD,
                        state = voiceAssets[VoiceCatalog.PIPER_ALD_ID] ?: VoiceAssetUiState(),
                        moduleState = voiceFeatureState,
                        onSelect = { viewModel.selectTtsMode(AssistantTtsMode.PIPER_ALD) },
                        onDownload = { viewModel.downloadAsset(VoiceCatalog.PIPER_ALD_ID) },
                        onPreview = { viewModel.previewPiperVoice(AssistantTtsMode.PIPER_ALD) },
                        onDelete = { viewModel.deleteAsset(VoiceCatalog.PIPER_ALD_ID) }
                    )
                    DownloadableVoiceCard(
                        title = "Piper · Claude",
                        description = "Espanol de Mexico, voz offline de calidad alta",
                        badge = "20 MB",
                        selected = ttsMode == AssistantTtsMode.PIPER_CLAUDE,
                        state = voiceAssets[VoiceCatalog.PIPER_CLAUDE_ID] ?: VoiceAssetUiState(),
                        moduleState = voiceFeatureState,
                        onSelect = { viewModel.selectTtsMode(AssistantTtsMode.PIPER_CLAUDE) },
                        onDownload = { viewModel.downloadAsset(VoiceCatalog.PIPER_CLAUDE_ID) },
                        onPreview = { viewModel.previewPiperVoice(AssistantTtsMode.PIPER_CLAUDE) },
                        onDelete = { viewModel.deleteAsset(VoiceCatalog.PIPER_CLAUDE_ID) }
                    )

                    if (voiceFeatureState.requiresConfirmation) {
                        Button(
                            onClick = {
                                viewModel.confirmVoiceFeatureInstall(voiceConfirmationLauncher)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Confirmar instalacion de Cortex Voice Pack") }
                    }
                    if (voiceFeatureState.requiresInstallPermission) {
                        Button(
                            onClick = {
                                viewModel.createVoicePackInstallPermissionIntent()?.let(
                                    voicePackPermissionLauncher::launch
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Permitir instalacion de Cortex Voice Pack") }
                    }
                }
            }

            error?.let { message ->
                item {
                    Card(
                        onClick = viewModel::dismissError,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SttLanguageSelector(selected: String, onSelected: (String) -> Unit) {
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
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = languages.firstOrNull { it.first == selected }?.second ?: selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Idioma de reconocimiento") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languages.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AdvancedSttProviderCard(
    title: String,
    mode: AssistantSttMode,
    selectedMode: AssistantSttMode,
    initialApiKey: String,
    onSave: (AssistantSttMode, String) -> Unit
) {
    var expanded by remember { mutableStateOf(selectedMode == mode) }
    var apiKey by remember(initialApiKey) { mutableStateOf(initialApiKey) }
    val selected = selectedMode == mode
    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = { expanded = true })
                Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                if (selected) Icon(Icons.Default.CheckCircle, contentDescription = null)
            }
            if (expanded) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onSave(mode, apiKey) },
                    enabled = apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Guardar y usar $title") }
            }
        }
    }
}
