package com.aiagents.app.presentation.assistant

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aiagents.app.R
import com.aiagents.app.presentation.voice.VoiceAssetUiState
import com.aiagents.app.data.speech.AssistantSttMode
import com.aiagents.app.data.speech.AssistantTtsMode
import com.aiagents.app.data.speech.GoogleTtsVoice
import com.aiagents.app.data.speech.Qwen3TtsVoiceSkill
import com.aiagents.app.data.speech.RemoteSttConfig
import com.aiagents.app.data.speech.RemoteTtsAudioMode
import com.aiagents.app.data.speech.RemoteTtsApiFlavor
import com.aiagents.app.data.speech.RemoteTtsConfig
import com.aiagents.app.data.speech.SelfHostedVoiceApi
import com.aiagents.app.data.speech.VoiceCatalog
import com.aiagents.app.data.speech.VoiceFeatureInstallState
import com.aiagents.app.ui.theme.CortexColors

private val AssistantPurple = CortexColors.Violet
private val AssistantCyan = CortexColors.Blue
private val AssistantMint = CortexColors.Mint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantSettingsScreen(
    onBack: () -> Unit,
    viewModel: AssistantSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val speakResponses by viewModel.speakResponses.collectAsState()
    val assistantModel by viewModel.assistantModel.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val ttsMode by viewModel.ttsMode.collectAsState()
    val error by viewModel.error.collectAsState()
    val assistantName by viewModel.assistantName.collectAsState()
    val assistantSoul by viewModel.assistantSoul.collectAsState()
    val isSavingName by viewModel.isSavingName.collectAsState()

    var roleHeld by remember { mutableStateOf(context.isAssistantRoleHeld()) }
    var assistantNameDraft by remember(assistantName) { mutableStateOf(assistantName) }
    var assistantSoulDraft by remember(assistantSoul.revision) {
        mutableStateOf(assistantSoul.content)
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                roleHeld = context.isAssistantRoleHeld()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestAssistantRole() {
        val destination = listOf(
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        ).first { intent -> intent.resolveActivity(context.packageManager) != null }

        context.startActivity(destination)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.assistant_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AssistantHero(roleHeld = roleHeld, assistantName = assistantName)
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.assistant_identity_title),
                    subtitle = stringResource(R.string.assistant_identity_subtitle)
                ) {
                    OutlinedTextField(
                        value = assistantNameDraft,
                        onValueChange = { assistantNameDraft = it },
                        label = { Text(stringResource(R.string.assistant_identity_field)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.saveAssistantName(assistantNameDraft) },
                        enabled = assistantNameDraft.isNotBlank() &&
                            assistantNameDraft != assistantName &&
                            !isSavingName,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.assistant_identity_save))
                    }
                }
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.assistant_soul_title),
                    subtitle = stringResource(R.string.assistant_soul_subtitle)
                ) {
                    OutlinedTextField(
                        value = assistantSoulDraft,
                        onValueChange = { assistantSoulDraft = it },
                        label = { Text("ASSISTANT_SOUL.md") },
                        minLines = 6,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            viewModel.saveAssistantSoul(
                                assistantSoulDraft,
                                assistantSoul.revision
                            )
                        },
                        enabled = assistantSoulDraft.isNotBlank() &&
                            assistantSoulDraft != assistantSoul.content,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.assistant_soul_save))
                    }
                }
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.assistant_system_role_title),
                    subtitle = stringResource(R.string.assistant_system_role_subtitle)
                ) {
                    AssistantStatusRow(
                        icon = if (roleHeld) Icons.Default.CheckCircle else Icons.Default.RecordVoiceOver,
                        title = if (roleHeld) {
                            stringResource(R.string.assistant_role_active, assistantName)
                        } else {
                            stringResource(R.string.assistant_role_inactive, assistantName)
                        },
                        detail = if (roleHeld) {
                            stringResource(R.string.assistant_role_active_detail)
                        } else {
                            stringResource(R.string.assistant_role_inactive_detail, assistantName)
                        },
                        ready = roleHeld
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = ::requestAssistantRole,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (roleHeld) stringResource(R.string.assistant_change_role)
                                else stringResource(R.string.assistant_enable_role, assistantName)
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(context, CortexAssistantActivity::class.java)
                                )
                            }
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text(stringResource(R.string.assistant_try_now))
                        }
                    }
                }
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.assistant_model_title),
                    subtitle = stringResource(R.string.assistant_model_subtitle)
                ) {
                    AssistantModelSelector(
                        selectedModel = assistantModel,
                        availableModels = availableModels,
                        onModelSelected = viewModel::setAssistantModel
                    )
                    Spacer(Modifier.height(14.dp))
                    AssistantStatusRow(
                        icon = Icons.Default.CheckCircle,
                        title = stringResource(R.string.assistant_phone_actions_title),
                        detail = stringResource(R.string.assistant_phone_actions_detail),
                        ready = true
                    )
                }
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.assistant_behavior_title),
                    subtitle = stringResource(R.string.assistant_behavior_subtitle)
                ) {
                    PreferenceSwitch(
                        title = "Leer respuestas iniciadas por texto",
                        subtitle = if (ttsMode == AssistantTtsMode.NONE) {
                            "Elige primero una opcion de salida de voz"
                        } else {
                            stringResource(R.string.assistant_speak_subtitle)
                        },
                        checked = speakResponses,
                        onCheckedChange = viewModel::setSpeakResponses,
                        enabled = ttsMode != AssistantTtsMode.NONE
                    )
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(R.string.assistant_privacy_title),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(R.string.assistant_privacy_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (error != null) {
                item {
                    Card(
                        onClick = viewModel::dismissError,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            error.orEmpty(),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun VoiceSectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(AssistantPurple.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = AssistantPurple)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
internal fun VoiceChoiceCard(
    title: String,
    description: String,
    badge: String,
    selected: Boolean,
    available: Boolean,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        enabled = available,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(selected = selected, onClick = onSelect, enabled = available)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                badge,
                style = MaterialTheme.typography.labelMedium,
                color = if (available) AssistantMint else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
internal fun DownloadableVoiceCard(
    title: String,
    description: String,
    badge: String,
    selected: Boolean,
    state: VoiceAssetUiState,
    moduleState: VoiceFeatureInstallState,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onPreview: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    val moduleInstalling = state.downloading && moduleState.installing && !moduleState.installed
    val busy = state.downloading
    val progress = if (moduleInstalling) moduleState.progress else state.progress
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadioButton(
                    selected = selected,
                    onClick = onSelect,
                    enabled = state.installed
                )
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Medium)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (state.installed) "Instalado" else badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.installed) AssistantMint else AssistantCyan
                )
            }

            if (busy) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (moduleInstalling) {
                        "Preparando Cortex Voice Pack... ${(progress * 100).toInt()}%"
                    } else {
                        "Descargando... ${(progress * 100).toInt()}%"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (state.installed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onPreview != null) {
                        TextButton(onClick = onPreview) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("Probar")
                        }
                    }
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text("Eliminar")
                    }
                }
            } else {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Text("Descargar · $badge")
                }
            }
        }
    }
}

@Composable
internal fun RemoteSttServerCard(
    selected: Boolean,
    config: RemoteSttConfig,
    onSelect: () -> Unit,
    onSave: (RemoteSttConfig) -> Unit
) {
    var endpointUrl by remember(config.endpointUrl) { mutableStateOf(config.endpointUrl) }
    var model by remember(config.model) { mutableStateOf(config.model) }
    var apiKey by remember(config.apiKey) { mutableStateOf(config.apiKey) }
    val configured = SelfHostedVoiceApi.isConfigured(config)

    VoiceServerCard(
        title = "Whisper en servidor propio",
        description = "Compatible con POST multipart de OpenAI para audio/transcriptions",
        selected = selected,
        configured = configured,
        onSelect = onSelect
    ) {
        OutlinedTextField(
            value = endpointUrl,
            onValueChange = { endpointUrl = it },
            label = { Text("URL del endpoint STT") },
            placeholder = { Text("http://192.168.1.20:8000/v1/audio/transcriptions") },
            supportingText = { Text("En un telefono usa la IP LAN del servidor, no localhost") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("Modelo Whisper") },
            placeholder = { Text("whisper-1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        VoiceServerApiKeyField(apiKey = apiKey, onApiKeyChange = { apiKey = it })
        HttpEndpointNotice(endpointUrl)
        Button(
            onClick = { onSave(RemoteSttConfig(endpointUrl, model, apiKey)) },
            enabled = endpointUrl.isNotBlank() && model.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar y usar servidor STT")
        }
    }
}

@Composable
internal fun RemoteTtsServerCard(
    selected: Boolean,
    config: RemoteTtsConfig,
    onSelect: () -> Unit,
    onSave: (RemoteTtsConfig) -> Unit,
    onPreview: (RemoteTtsConfig) -> Unit
) {
    var endpointUrl by remember(config.endpointUrl) { mutableStateOf(config.endpointUrl) }
    var model by remember(config.model) { mutableStateOf(config.model) }
    var voice by remember(config.voice) { mutableStateOf(config.voice) }
    var apiKey by remember(config.apiKey) { mutableStateOf(config.apiKey) }
    var apiFlavor by remember(config.apiFlavor) { mutableStateOf(config.apiFlavor) }
    var language by remember(config.language) { mutableStateOf(config.language) }
    var voiceDescription by remember(config.voiceDescription) {
        mutableStateOf(config.voiceDescription)
    }
    var adaptiveStyle by remember(config.adaptiveStyle) { mutableStateOf(config.adaptiveStyle) }
    var audioMode by remember(config.audioMode) { mutableStateOf(config.audioMode) }
    var pcmSampleRate by remember(config.pcmSampleRate) {
        mutableStateOf(config.pcmSampleRate.toString())
    }
    val configured = SelfHostedVoiceApi.isConfigured(config)
    val draft = {
        RemoteTtsConfig(
            endpointUrl = endpointUrl,
            model = model,
            voice = voice,
            apiKey = apiKey,
            apiFlavor = apiFlavor,
            language = language,
            voiceDescription = voiceDescription,
            adaptiveStyle = adaptiveStyle,
            audioMode = audioMode,
            pcmSampleRate = pcmSampleRate.toIntOrNull() ?: 24_000
        )
    }
    val qwenSkillActive = Qwen3TtsVoiceSkill.isActive(draft())
    val requiresVoice = Qwen3TtsVoiceSkill.requiresVoice(draft())
    val draftComplete = endpointUrl.isNotBlank() && model.isNotBlank() &&
        (!requiresVoice || voice.isNotBlank())

    VoiceServerCard(
        title = "TTS en servidor propio",
        description = "Compatible con audio/speech de OpenAI: WAV completo o PCM por streaming",
        selected = selected,
        configured = configured,
        onSelect = onSelect
    ) {
        OutlinedTextField(
            value = endpointUrl,
            onValueChange = { endpointUrl = it },
            label = { Text("URL del endpoint TTS") },
            placeholder = { Text("http://192.168.1.20:8000/v1/audio/speech") },
            supportingText = {
                Text(
                    if (Qwen3TtsVoiceSkill.resolveApiFlavor(draft()) ==
                        RemoteTtsApiFlavor.QWEN3_VOICE_DESIGN
                    ) {
                        "Usa el endpoint Voice Design del servidor, por ejemplo /v1/audio/speech/design"
                    } else {
                        if (audioMode == RemoteTtsAudioMode.STREAMING_PCM) {
                            "El servidor debe devolver PCM16 mono sin cabecera"
                        } else {
                            "El servidor puede devolver WAV o MP3"
                        }
                    }
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("Modelo TTS") },
            placeholder = { Text("tts-1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        TtsApiFlavorSelector(selected = apiFlavor, onSelected = { apiFlavor = it })
        TtsAudioModeSelector(selected = audioMode, onSelected = { audioMode = it })
        if (audioMode == RemoteTtsAudioMode.STREAMING_PCM) {
            OutlinedTextField(
                value = pcmSampleRate,
                onValueChange = { value -> pcmSampleRate = value.filter(Char::isDigit).take(5) },
                label = { Text("Frecuencia PCM") },
                supportingText = {
                    Text("PCM16 mono sin cabecera; Speaches y OpenAI usan normalmente 24000 Hz")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (requiresVoice) {
            OutlinedTextField(
                value = voice,
                onValueChange = { voice = it },
                label = { Text(if (qwenSkillActive) "Speaker de Qwen" else "Voz") },
                placeholder = { Text(if (qwenSkillActive) "Ryan" else "alloy") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (qwenSkillActive) {
            Text(
                "Skill expresiva Qwen3-TTS activa: estos datos se agregan a cada solicitud de voz.",
                style = MaterialTheme.typography.bodySmall,
                color = AssistantMint
            )
            OutlinedTextField(
                value = language,
                onValueChange = { language = it },
                label = { Text("Idioma para Qwen") },
                placeholder = { Text("Spanish o Auto") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = voiceDescription,
                onValueChange = { voiceDescription = it },
                label = { Text("Descripcion de voz") },
                supportingText = {
                    Text("Identidad estable: timbre, edad aparente, acento y personalidad")
                },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Adaptar tono automaticamente", fontWeight = FontWeight.Medium)
                    Text(
                        "Cortex agrega un tono calmado, empatico, entusiasta o didactico segun la respuesta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = adaptiveStyle, onCheckedChange = { adaptiveStyle = it })
            }
        }
        VoiceServerApiKeyField(apiKey = apiKey, onApiKeyChange = { apiKey = it })
        HttpEndpointNotice(endpointUrl)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onSave(draft()) },
                enabled = draftComplete,
                modifier = Modifier.weight(1f)
            ) {
                Text("Guardar y usar")
            }
            OutlinedButton(
                onClick = { onPreview(draft()) },
                enabled = draftComplete
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text("Probar")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtsAudioModeSelector(
    selected: RemoteTtsAudioMode,
    onSelected: (RemoteTtsAudioMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf(
        RemoteTtsAudioMode.BUFFERED_WAV to "WAV completo",
        RemoteTtsAudioMode.STREAMING_PCM to "PCM streaming · baja latencia"
    )
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = labels.getValue(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text("Entrega de audio") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RemoteTtsAudioMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(labels.getValue(mode)) },
                    onClick = {
                        onSelected(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtsApiFlavorSelector(
    selected: RemoteTtsApiFlavor,
    onSelected: (RemoteTtsApiFlavor) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf(
        RemoteTtsApiFlavor.AUTO to "Automatico por nombre del modelo",
        RemoteTtsApiFlavor.OPENAI to "OpenAI compatible",
        RemoteTtsApiFlavor.QWEN3_CUSTOM_VOICE to "Qwen3-TTS · CustomVoice",
        RemoteTtsApiFlavor.QWEN3_VOICE_DESIGN to "Qwen3-TTS · VoiceDesign"
    )
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = labels.getValue(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text("Tipo de API TTS") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RemoteTtsApiFlavor.entries.forEach { flavor ->
                DropdownMenuItem(
                    text = { Text(labels.getValue(flavor)) },
                    onClick = {
                        onSelected(flavor)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun VoiceServerCard(
    title: String,
    description: String,
    selected: Boolean,
    configured: Boolean,
    onSelect: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadioButton(
                    selected = selected,
                    onClick = onSelect,
                    enabled = configured
                )
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Medium)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (configured) "Configurado" else "Servidor",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (configured) AssistantMint else AssistantCyan
                )
            }
            content()
        }
    }
}

@Composable
private fun VoiceServerApiKeyField(
    apiKey: String,
    onApiKeyChange: (String) -> Unit
) {
    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        label = { Text("Token Bearer (opcional)") },
        supportingText = { Text("Se guarda cifrado en el dispositivo") },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun HttpEndpointNotice(endpointUrl: String) {
    if (endpointUrl.trim().startsWith("http://", ignoreCase = true)) {
        Text(
            "HTTP sin cifrar solo se recomienda dentro de una red local de confianza. Para acceso remoto usa HTTPS.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoogleTtsCard(
    selected: Boolean,
    voices: List<GoogleTtsVoice>,
    selectedVoiceId: String,
    onSelect: () -> Unit,
    onVoiceSelected: (String) -> Unit,
    onPreview: (String) -> Unit,
    onManageVoices: () -> Unit
) {
    val installedVoices = voices.filter { it.installed }
    val selectedVoice = installedVoices.firstOrNull { it.id == selectedVoiceId }
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadioButton(
                    selected = selected,
                    onClick = onSelect,
                    enabled = installedVoices.isNotEmpty()
                )
                Column(Modifier.weight(1f)) {
                    Text("Google TTS", fontWeight = FontWeight.Medium)
                    Text(
                        "Voces administradas por Android; no aumentan el APK de Cortex",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Sistema",
                    style = MaterialTheme.typography.labelMedium,
                    color = AssistantCyan
                )
            }

            if (installedVoices.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedVoice?.displayName ?: "Elige una voz instalada",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Voz de Google") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        installedVoices.forEach { voice ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(voice.displayName)
                                        Text(
                                            voice.languageTag,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                },
                                onClick = {
                                    onVoiceSelected(voice.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            } else {
                Text(
                    "No hay una voz local instalada para este idioma.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onManageVoices,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Text("Administrar voces")
                }
                if (selectedVoice != null) {
                    OutlinedButton(onClick = { onPreview(selectedVoice.id) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("Probar")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantModelSelector(
    selectedModel: String,
    availableModels: List<String>,
    onModelSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val chatDefault = stringResource(R.string.assistant_model_chat_default)
    val selectedLabel = selectedModel.takeIf(String::isNotBlank)?.toAssistantModelLabel()
        ?: chatDefault

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.assistant_model_field)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(chatDefault) },
                onClick = {
                    onModelSelected("")
                    expanded = false
                }
            )
            availableModels.forEach { modelKey ->
                DropdownMenuItem(
                    text = { Text(modelKey.toAssistantModelLabel()) },
                    onClick = {
                        onModelSelected(modelKey)
                        expanded = false
                    }
                )
            }
        }
    }
    if (availableModels.isEmpty()) {
        Text(
            stringResource(R.string.assistant_model_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

private fun String.toAssistantModelLabel(): String {
    val provider = substringBefore('|', missingDelimiterValue = "")
        .lowercase()
        .replaceFirstChar { it.titlecase() }
    val model = substringAfter('|', missingDelimiterValue = this)
    return if (provider.isBlank()) model else "$provider · $model"
}

@Composable
private fun AssistantHero(roleHeld: Boolean, assistantName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF211A3A), Color(0xFF102D3C), Color(0xFF182027))
                ),
                RoundedCornerShape(28.dp)
            )
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            CortexAssistantPreview(Modifier.size(80.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.assistant_hero_title, assistantName),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.assistant_hero_subtitle),
                    color = Color.White.copy(alpha = 0.67f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (roleHeld) stringResource(R.string.assistant_ready_badge)
                    else stringResource(R.string.assistant_setup_badge),
                    color = if (roleHeld) AssistantMint else AssistantCyan,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
internal fun SettingsCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun AssistantStatusRow(
    icon: ImageVector,
    title: String,
    detail: String,
    ready: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    if (ready) AssistantMint.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surface,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (ready) AssistantMint else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PreferenceSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

private fun Context.isAssistantRoleHeld(): Boolean {
    return VoiceInteractionService.isActiveService(
        this,
        android.content.ComponentName(this, CortexVoiceInteractionService::class.java)
    )
}
