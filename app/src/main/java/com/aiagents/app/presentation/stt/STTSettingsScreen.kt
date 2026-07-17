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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiagents.app.data.model.CloudSTTProvider
import com.aiagents.app.data.model.LocalModelType
import com.aiagents.app.data.model.LocalSTTEngine
import com.aiagents.app.data.model.STTMode
import com.aiagents.app.data.speech.Qwen3TtsVoiceSkill
import com.aiagents.app.data.speech.RemoteTtsAudioMode
import com.aiagents.app.data.speech.RemoteTtsApiFlavor
import com.aiagents.app.data.speech.RemoteTtsConfig
import com.aiagents.app.data.speech.VoiceFeatureInstallState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun STTSettingsDialog(
    currentSettings: STTSettingsUiState,
    onSave: (STTSettingsUiState) -> Unit,
    onDismiss: () -> Unit,
    onInstallOfflineEngine: () -> Unit,
    onRequestVoicePackInstallPermission: () -> Unit,
    onDownloadModel: () -> Unit,
    voiceFeatureState: VoiceFeatureInstallState = VoiceFeatureInstallState(installed = false),
    isOfflineModelReady: Boolean = false,
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f
) {
    var selectedMode by remember { mutableStateOf(currentSettings.mode) }
    var selectedProvider by remember { mutableStateOf(currentSettings.cloudProvider) }
    var apiKey by remember { mutableStateOf(currentSettings.apiKey) }
    var selectedLocalModel by remember { mutableStateOf(currentSettings.localModelType) }
    var selectedLocalEngine by remember { mutableStateOf(currentSettings.localEngine) }
    var language by remember { mutableStateOf(currentSettings.language) }
    var remoteSttEndpoint by remember { mutableStateOf(currentSettings.remoteSttEndpoint) }
    var remoteSttModel by remember { mutableStateOf(currentSettings.remoteSttModel) }
    var remoteSttApiKey by remember { mutableStateOf(currentSettings.remoteSttApiKey) }
    var useRemoteTts by remember { mutableStateOf(currentSettings.useRemoteTts) }
    var remoteTtsEndpoint by remember { mutableStateOf(currentSettings.remoteTtsEndpoint) }
    var remoteTtsModel by remember { mutableStateOf(currentSettings.remoteTtsModel) }
    var remoteTtsVoice by remember { mutableStateOf(currentSettings.remoteTtsVoice) }
    var remoteTtsApiKey by remember { mutableStateOf(currentSettings.remoteTtsApiKey) }
    var remoteTtsApiFlavor by remember { mutableStateOf(currentSettings.remoteTtsApiFlavor) }
    var remoteTtsLanguage by remember { mutableStateOf(currentSettings.remoteTtsLanguage) }
    var remoteTtsVoiceDescription by remember {
        mutableStateOf(currentSettings.remoteTtsVoiceDescription)
    }
    var remoteTtsAdaptiveStyle by remember {
        mutableStateOf(currentSettings.remoteTtsAdaptiveStyle)
    }
    var remoteTtsAudioMode by remember { mutableStateOf(currentSettings.remoteTtsAudioMode) }
    var remoteTtsPcmSampleRate by remember {
        mutableStateOf(currentSettings.remoteTtsPcmSampleRate.toString())
    }

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

                // Local voice settings: Android built-in recognizer plus optional offline engine.
                AnimatedVisibility(
                    visible = selectedMode == STTMode.LOCAL,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    LocalSTTSettings(
                        voiceFeatureState = voiceFeatureState,
                        isOfflineModelReady = isOfflineModelReady,
                        isDownloading = isDownloading,
                        downloadProgress = downloadProgress,
                        onInstallOfflineEngine = onInstallOfflineEngine,
                        onRequestVoicePackInstallPermission = onRequestVoicePackInstallPermission,
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
                        remoteEndpoint = remoteSttEndpoint,
                        remoteModel = remoteSttModel,
                        remoteApiKey = remoteSttApiKey,
                        onProviderSelected = { selectedProvider = it },
                        onApiKeyChange = { apiKey = it },
                        onRemoteEndpointChange = { remoteSttEndpoint = it },
                        onRemoteModelChange = { remoteSttModel = it },
                        onRemoteApiKeyChange = { remoteSttApiKey = it }
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

                HorizontalDivider()
                RemoteChatTtsSettings(
                    enabled = useRemoteTts,
                    endpoint = remoteTtsEndpoint,
                    model = remoteTtsModel,
                    voice = remoteTtsVoice,
                    apiKey = remoteTtsApiKey,
                    apiFlavor = remoteTtsApiFlavor,
                    language = remoteTtsLanguage,
                    voiceDescription = remoteTtsVoiceDescription,
                    adaptiveStyle = remoteTtsAdaptiveStyle,
                    audioMode = remoteTtsAudioMode,
                    pcmSampleRate = remoteTtsPcmSampleRate,
                    onEnabledChange = { useRemoteTts = it },
                    onEndpointChange = { remoteTtsEndpoint = it },
                    onModelChange = { remoteTtsModel = it },
                    onVoiceChange = { remoteTtsVoice = it },
                    onApiKeyChange = { remoteTtsApiKey = it },
                    onApiFlavorChange = { remoteTtsApiFlavor = it },
                    onLanguageChange = { remoteTtsLanguage = it },
                    onVoiceDescriptionChange = { remoteTtsVoiceDescription = it },
                    onAdaptiveStyleChange = { remoteTtsAdaptiveStyle = it },
                    onAudioModeChange = { remoteTtsAudioMode = it },
                    onPcmSampleRateChange = { remoteTtsPcmSampleRate = it }
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
                            language = language,
                            remoteSttEndpoint = remoteSttEndpoint,
                            remoteSttModel = remoteSttModel,
                            remoteSttApiKey = remoteSttApiKey,
                            useRemoteTts = useRemoteTts,
                            remoteTtsEndpoint = remoteTtsEndpoint,
                            remoteTtsModel = remoteTtsModel,
                            remoteTtsVoice = remoteTtsVoice,
                            remoteTtsApiKey = remoteTtsApiKey,
                            remoteTtsApiFlavor = remoteTtsApiFlavor,
                            remoteTtsLanguage = remoteTtsLanguage,
                            remoteTtsVoiceDescription = remoteTtsVoiceDescription,
                            remoteTtsAdaptiveStyle = remoteTtsAdaptiveStyle,
                            remoteTtsAudioMode = remoteTtsAudioMode,
                            remoteTtsPcmSampleRate = remoteTtsPcmSampleRate.toIntOrNull() ?: 24_000
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
    remoteEndpoint: String,
    remoteModel: String,
    remoteApiKey: String,
    onProviderSelected: (CloudSTTProvider) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onRemoteEndpointChange: (String) -> Unit,
    onRemoteModelChange: (String) -> Unit,
    onRemoteApiKeyChange: (String) -> Unit
) {
    val requiresApiKey = selectedProvider != CloudSTTProvider.ANDROID_SPEECH_RECOGNIZER &&
        selectedProvider != CloudSTTProvider.SELF_HOSTED

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
            Triple(CloudSTTProvider.SELF_HOSTED, "Whisper en servidor propio", "LM Studio, Speaches u otro endpoint compatible con OpenAI"),
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

        AnimatedVisibility(
            visible = selectedProvider == CloudSTTProvider.SELF_HOSTED,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = remoteEndpoint,
                    onValueChange = onRemoteEndpointChange,
                    label = { Text("Endpoint de transcripcion") },
                    placeholder = { Text("http://192.168.1.20:8000/v1/audio/transcriptions") },
                    supportingText = { Text("Usa la IP LAN de la computadora, no localhost") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remoteModel,
                    onValueChange = onRemoteModelChange,
                    label = { Text("Modelo Whisper") },
                    placeholder = { Text("Systran/faster-whisper-large-v3") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remoteApiKey,
                    onValueChange = onRemoteApiKeyChange,
                    label = { Text("Token Bearer (opcional)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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

        if (selectedProvider == CloudSTTProvider.ANDROID_SPEECH_RECOGNIZER) {
            Text(
                "Usa el reconocimiento de voz integrado de Android. No requiere configuracion.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun RemoteChatTtsSettings(
    enabled: Boolean,
    endpoint: String,
    model: String,
    voice: String,
    apiKey: String,
    apiFlavor: RemoteTtsApiFlavor,
    language: String,
    voiceDescription: String,
    adaptiveStyle: Boolean,
    audioMode: RemoteTtsAudioMode,
    pcmSampleRate: String,
    onEnabledChange: (Boolean) -> Unit,
    onEndpointChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onVoiceChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onApiFlavorChange: (RemoteTtsApiFlavor) -> Unit,
    onLanguageChange: (String) -> Unit,
    onVoiceDescriptionChange: (String) -> Unit,
    onAdaptiveStyleChange: (Boolean) -> Unit,
    onAudioModeChange: (RemoteTtsAudioMode) -> Unit,
    onPcmSampleRateChange: (String) -> Unit
) {
    val draftConfig = RemoteTtsConfig(
        endpointUrl = endpoint,
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
    val qwenSkillActive = Qwen3TtsVoiceSkill.isActive(draftConfig)
    val requiresVoice = Qwen3TtsVoiceSkill.requiresVoice(draftConfig)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Lectura de respuestas (TTS)", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Muestra un boton de voz en las respuestas del chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }

        AnimatedVisibility(
            visible = enabled,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = onEndpointChange,
                    label = { Text("Endpoint TTS") },
                    placeholder = { Text("http://192.168.1.20:8000/v1/audio/speech") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = onModelChange,
                    label = { Text("Modelo TTS") },
                    placeholder = { Text("speaches-ai/Kokoro-82M-v1.0-ONNX") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ChatTtsApiFlavorSelector(apiFlavor, onApiFlavorChange)
                ChatTtsAudioModeSelector(audioMode, onAudioModeChange)
                if (audioMode == RemoteTtsAudioMode.STREAMING_PCM) {
                    OutlinedTextField(
                        value = pcmSampleRate,
                        onValueChange = {
                            onPcmSampleRateChange(it.filter(Char::isDigit).take(5))
                        },
                        label = { Text("Frecuencia PCM") },
                        supportingText = { Text("PCM16 mono; normalmente 24000 Hz") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (requiresVoice) {
                    OutlinedTextField(
                        value = voice,
                        onValueChange = onVoiceChange,
                        label = { Text(if (qwenSkillActive) "Speaker de Qwen" else "Voz") },
                        placeholder = { Text(if (qwenSkillActive) "Ryan" else "ef_dora") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (qwenSkillActive) {
                    Text(
                        "Skill expresiva Qwen3-TTS activa para las respuestas del chat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = language,
                        onValueChange = onLanguageChange,
                        label = { Text("Idioma para Qwen") },
                        placeholder = { Text("Spanish o Auto") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = voiceDescription,
                        onValueChange = onVoiceDescriptionChange,
                        label = { Text("Descripcion de voz") },
                        supportingText = { Text("Timbre, edad aparente, acento y personalidad") },
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
                            Text("Adaptar tono automaticamente")
                            Text(
                                "Ajusta la interpretacion segun el contenido de cada respuesta.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = adaptiveStyle, onCheckedChange = onAdaptiveStyleChange)
                    }
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("Token Bearer (opcional)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTtsAudioModeSelector(
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
private fun ChatTtsApiFlavorSelector(
    selected: RemoteTtsApiFlavor,
    onSelected: (RemoteTtsApiFlavor) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf(
        RemoteTtsApiFlavor.AUTO to "Automatico por modelo",
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
fun LocalSTTSettings(
    voiceFeatureState: VoiceFeatureInstallState,
    isOfflineModelReady: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    onInstallOfflineEngine: () -> Unit,
    onRequestVoicePackInstallPermission: () -> Unit,
    onDownloadModel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Reconocimiento local",
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
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Motor integrado de Android",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Disponible sin descargar librerías en Cortex",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Whisper offline (opcional)", style = MaterialTheme.typography.bodyMedium)
                Text(
                    when {
                        isOfflineModelReady -> "Motor y modelo listos para transcribir sin conexión."
                        voiceFeatureState.installed -> "Cortex Voice Pack listo. Descarga Whisper Tiny (~111 MB) para usarlo."
                        else -> "Cortex Voice Pack se descarga sólo si lo activas; no aumenta el APK base."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    voiceFeatureState.requiresInstallPermission -> FilledTonalButton(
                        onClick = onRequestVoicePackInstallPermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Permitir Cortex Voice Pack")
                    }
                    voiceFeatureState.installing -> {
                        LinearProgressIndicator(
                            progress = { voiceFeatureState.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Preparando Cortex Voice Pack… ${(voiceFeatureState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    !voiceFeatureState.installed -> FilledTonalButton(
                        onClick = onInstallOfflineEngine,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Descargar Cortex Voice Pack")
                    }
                    !isOfflineModelReady && isDownloading -> {
                        LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "Descargando modelo… ${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    !isOfflineModelReady -> FilledTonalButton(
                        onClick = onDownloadModel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Descargar Whisper Tiny")
                    }
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
    val language: String = "es",
    val remoteSttEndpoint: String = "",
    val remoteSttModel: String = "whisper-1",
    val remoteSttApiKey: String = "",
    val useRemoteTts: Boolean = false,
    val remoteTtsEndpoint: String = "",
    val remoteTtsModel: String = "tts-1",
    val remoteTtsVoice: String = "alloy",
    val remoteTtsApiKey: String = "",
    val remoteTtsApiFlavor: RemoteTtsApiFlavor = RemoteTtsApiFlavor.AUTO,
    val remoteTtsLanguage: String = "Auto",
    val remoteTtsVoiceDescription: String = Qwen3TtsVoiceSkill.DEFAULT_VOICE_DESCRIPTION,
    val remoteTtsAdaptiveStyle: Boolean = true,
    val remoteTtsAudioMode: RemoteTtsAudioMode = RemoteTtsAudioMode.BUFFERED_WAV,
    val remoteTtsPcmSampleRate: Int = 24_000
)
