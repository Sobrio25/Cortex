package com.aiagents.app.presentation.providers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiagents.app.R
import com.aiagents.app.domain.model.MoonshotEndpointType
import com.aiagents.app.domain.model.ProviderType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    onNavigateToLocalModels: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    viewModel: ProvidersViewModel = hiltViewModel()
) {
    val providerStates by viewModel.providerStates.collectAsState()
    val selectedModels by viewModel.selectedModels.collectAsState()

    var showConfigDialog by remember { mutableStateOf<ProviderType?>(null) }

    // Refrescar estados cuando se vuelve a esta pantalla
    DisposableEffect(Unit) {
        viewModel.refreshProviderStates()
        onDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proveedores de IA") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
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
                .padding(padding)
        ) {
            items(ProviderType.entries.toList()) { type ->
                val state = providerStates[type] ?: return@items
                val selectedCount = selectedModels.count { it.startsWith("${type.name}|") }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    onClick = {
                        if (type == ProviderType.LOCAL) {
                            onNavigateToLocalModels()
                        } else {
                            showConfigDialog = type
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val providerLogo = when (type) {
                            ProviderType.OPENROUTER -> R.drawable.ic_openrouter
                            ProviderType.GOOGLE_AI -> R.drawable.ic_google_ai
                            ProviderType.OPENAI -> R.drawable.ic_openai
                            ProviderType.OLLAMA -> R.drawable.ic_ollama
                            ProviderType.MINIMAX -> R.drawable.ic_minimax
                            ProviderType.MOONSHOT -> R.drawable.ic_moonshot
                            ProviderType.ANTHROPIC -> R.drawable.ic_anthropic
                            ProviderType.DEEPSEEK -> R.drawable.ic_deepseek
                            ProviderType.GROK -> R.drawable.ic_grok
                            ProviderType.KILO -> R.drawable.ic_kilo
                            ProviderType.ALIBABA -> R.drawable.ic_alibaba
                            ProviderType.OPENCODE -> R.drawable.ic_opencode
                            ProviderType.ZAI -> R.drawable.ic_zai
                            ProviderType.LOCAL -> null
                        }
                        // No aplicamos tint para preservar los colores originales del logo
                        if (providerLogo != null) {
                            Icon(
                                painter = painterResource(id = providerLogo),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = androidx.compose.ui.graphics.Color.Unspecified
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                viewModel.getProviderDisplayName(type),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                when {
                                    !state.isConfigured -> "No configurado"
                                    selectedCount == 0 -> "Configurado · sin modelos seleccionados"
                                    selectedCount == 1 -> "1 modelo seleccionado"
                                    else -> "$selectedCount modelos seleccionados"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (state.isConfigured)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline
                            )
                        }
                        Icon(
                            if (state.isConfigured) Icons.Default.CheckCircle else Icons.Default.Add,
                            contentDescription = null,
                            tint = if (state.isConfigured) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Instrucciones",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "1. Obtén tu API Key del proveedor deseado\n" +
                            "2. Toca el proveedor para configurarlo\n" +
                            "3. Pega tu API Key y guarda\n" +
                            "4. Selecciona los modelos que quieres usar en el chat",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    showConfigDialog?.let { type ->
        if (type == ProviderType.LOCAL) {
            // Para LOCAL, mostrar diálogo especial que redirige a gestión de modelos
            AlertDialog(
                onDismissRequest = { showConfigDialog = null },
                title = { Text(viewModel.getProviderDisplayName(type)) },
                text = {
                    Column {
                        Text("Los modelos locales se ejecutan completamente en tu dispositivo sin necesidad de conexión a internet.")
                        Spacer(Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "Para usar IA local:",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "1. Descarga un modelo desde la sección de Modelos Locales\n" +
                                    "2. Selecciona el proveedor LOCAL como activo\n" +
                                    "3. Elige el modelo descargado en tu workspace",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfigDialog = null
                            onNavigateToLocalModels()
                        }
                    ) {
                        Text("Ir a Modelos Locales")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfigDialog = null }) {
                        Text("Cerrar")
                    }
                }
            )
        } else {
            ProviderConfigDialog(
                type = type,
                viewModel = viewModel,
                onDismiss = { showConfigDialog = null },
                onSave = { showConfigDialog = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderConfigDialog(
    type: ProviderType,
    viewModel: ProvidersViewModel,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val providerStates by viewModel.providerStates.collectAsState()
    val selectedModels by viewModel.selectedModels.collectAsState()
    val state = providerStates[type] ?: return

    var apiKey by remember { mutableStateOf(state.apiKey) }
    var baseUrl by remember { mutableStateOf(state.baseUrl) }
    var showApiKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(state.isConfigured) }

    // Estado específico para Moonshot
    var selectedMoonshotEndpoint by remember { mutableStateOf(state.moonshotEndpoint) }
    var moonshotEndpointExpanded by remember { mutableStateOf(false) }

    val needsBaseUrl = type == ProviderType.OLLAMA || type == ProviderType.MOONSHOT
    val needsApiKey = type != ProviderType.OLLAMA
    val isMoonshot = type == ProviderType.MOONSHOT

    // Cargar modelos si el proveedor ya estaba configurado al abrir
    LaunchedEffect(type) {
        viewModel.loadModelsForDialog(type)
    }

    // Actualizar apiKey cuando cambia el estado o el endpoint de Moonshot
    LaunchedEffect(state.apiKey, state.moonshotEndpoint) {
        apiKey = state.apiKey
        baseUrl = state.baseUrl
        selectedMoonshotEndpoint = state.moonshotEndpoint
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(viewModel.getProviderDisplayName(type)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Selector de endpoint para Moonshot ────────────────────
                if (isMoonshot) {
                    item {
                        ExposedDropdownMenuBox(
                            expanded = moonshotEndpointExpanded,
                            onExpandedChange = { moonshotEndpointExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedMoonshotEndpoint.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Región/Plan") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = moonshotEndpointExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = moonshotEndpointExpanded,
                                onDismissRequest = { moonshotEndpointExpanded = false }
                            ) {
                                MoonshotEndpointType.entries.forEach { endpoint ->
                                    DropdownMenuItem(
                                        text = { Text(endpoint.displayName) },
                                        onClick = {
                                            selectedMoonshotEndpoint = endpoint
                                            moonshotEndpointExpanded = false
                                            viewModel.setMoonshotEndpoint(endpoint)
                                            saved = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Sección de credenciales ──────────────────────────────
                if (needsApiKey) {
                    item {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = {
                                apiKey = it
                                saved = false
                                if (isMoonshot) {
                                    viewModel.updateMoonshotApiKey(selectedMoonshotEndpoint, it)
                                }
                            },
                            label = {
                                if (isMoonshot) {
                                    Text("API Key - ${selectedMoonshotEndpoint.displayName}")
                                } else {
                                    Text("API Key")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (showApiKey)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            singleLine = true
                        )
                    }
                }

                if (needsBaseUrl && !isMoonshot) {
                    item {
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = { Text("URL Base") },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(viewModel.getDefaultBaseUrl(type)) },
                            singleLine = true
                        )
                        Text(
                            "Dejar vacío para usar la URL por defecto",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                if (isMoonshot) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "Endpoint seleccionado:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    selectedMoonshotEndpoint.baseUrl,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Cada región/plan requiere su propia API key.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (type == ProviderType.OPENCODE) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "OpenCode Zen:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Pay-as-you-go con \$20 de saldo inicial.\n" +
                                    "Incluye modelos gratuitos (Big Pickle, GPT 5 Nano).\n" +
                                    "Modelos: opencode/claude-sonnet-4, opencode/gpt-5.3-codex, etc.\n" +
                                    "Plan GO: usa prefijo opencode/ en el nombre del modelo.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // Nota OAuth para Google AI
                if (type == ProviderType.GOOGLE_AI) {
                    val googleOAuthConfigured = state.isConfigured && apiKey.isBlank()
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "Autenticación con Google OAuth",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Si conectaste Google Drive en MCP → el token OAuth se usa automáticamente para Gemini. No necesitas API key.\n\n" +
                                    "Si prefieres API key, obtenla en aistudio.google.com/apikey",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                // Notas especiales por proveedor
                if (type == ProviderType.OLLAMA) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Para usar Ollama localmente:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text("1. Instala Ollama desde ollama.com\n2. Ejecuta: ollama serve\n3. URL: http://localhost:11434", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "Nota: El tool calling (ejecución de comandos de terminal) solo funciona con modelos específicos.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Modelos con soporte (Feb 2026): qwen3, qwen3.5, qwen3-coder, granite4, devstral-2, ministral-3, gpt-oss (cloud), functiongemma",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Botón Guardar inline ──────────────────────────────────
                if (!saved) {
                    item {
                        Button(
                            onClick = {
                                if (isMoonshot) {
                                    viewModel.saveMoonshotConfig(selectedMoonshotEndpoint) { saved = true }
                                } else {
                                    viewModel.updateApiKey(type, apiKey)
                                    // Para OpenAI, usar siempre la URL por defecto
                                    val baseUrlToSave = if (type == ProviderType.OPENAI) {
                                        viewModel.getDefaultBaseUrl(type)
                                    } else {
                                        baseUrl
                                    }
                                    viewModel.updateBaseUrl(type, baseUrlToSave)
                                    viewModel.saveProviderConfig(type) { saved = true }
                                }
                            },
                            enabled = when {
                                type == ProviderType.GOOGLE_AI -> true // puede usar OAuth sin API key
                                isMoonshot -> apiKey.isNotBlank() || state.moonshotApiKeys.values.any { it.isNotBlank() }
                                needsApiKey -> apiKey.isNotBlank()
                                else -> true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Guardar credenciales")
                        }
                    }
                }

                // ── Sección de modelos ────────────────────────────────────
                if (state.isConfigured || saved) {
                    item {
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Modelos disponibles",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val currentState = providerStates[type]
                            if (currentState?.isLoading == true) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(
                                    onClick = { viewModel.reloadModels(type) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Recargar modelos", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Text(
                            "Selecciona los modelos que quieres usar en el chat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val models = state.availableModels
                    if (models.isEmpty() && state.isLoading == false) {
                        item {
                            Text(
                                "No hay modelos cargados. Toca ↺ para cargar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    } else {
                        items(models) { modelId ->
                            val isSelected = selectedModels.contains("${type.name}|$modelId")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleModelSelection(type, modelId) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleModelSelection(type, modelId) }
                                )
                                Text(
                                    text = modelId,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
