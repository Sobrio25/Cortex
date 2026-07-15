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
import androidx.compose.ui.platform.LocalContext
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
import com.aiagents.app.domain.model.NvidiaProviderConfig
import com.aiagents.app.domain.model.OpenCodeVariantType
import com.aiagents.app.domain.model.ProviderType
import com.aiagents.app.domain.model.ZAIPlanType

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
                            ProviderType.NVIDIA -> R.drawable.ic_nvidia
                            ProviderType.OLLAMA -> R.drawable.ic_ollama
                            ProviderType.LM_STUDIO -> R.drawable.ic_lm_studio
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
    var showModelSearch by remember(type) { mutableStateOf(false) }
    var modelQuery by remember(type) { mutableStateOf("") }
    val filteredModels = remember(state.availableModels, modelQuery) {
        filterProviderModels(state.availableModels, modelQuery)
    }

    // Estado específico para Moonshot
    var selectedMoonshotEndpoint by remember { mutableStateOf(state.moonshotEndpoint) }
    var moonshotEndpointExpanded by remember { mutableStateOf(false) }

    // Estado específico para Z.AI
    var selectedZAIPlan by remember { mutableStateOf(state.zaiPlan) }
    var zaiPlanExpanded by remember { mutableStateOf(false) }

    // Estado específico para OpenCode
    var selectedOpenCodeVariant by remember { mutableStateOf(state.openCodeVariant) }
    var openCodeVariantExpanded by remember { mutableStateOf(false) }

    val isOpenAI = type == ProviderType.OPENAI
    val isLocalServer = type == ProviderType.OLLAMA || type == ProviderType.LM_STUDIO
    val needsBaseUrl = isLocalServer || type == ProviderType.MOONSHOT
    val showsApiKey = type != ProviderType.OLLAMA
    val requiresApiKey = !isLocalServer
    val isMoonshot = type == ProviderType.MOONSHOT
    val isZAI = type == ProviderType.ZAI
    val isOpenCode = type == ProviderType.OPENCODE
    val isAnthropic = type == ProviderType.ANTHROPIC
    val isNvidia = type == ProviderType.NVIDIA
    val oauthContext = LocalContext.current

    // Cargar modelos si el proveedor ya estaba configurado al abrir
    LaunchedEffect(type) {
        viewModel.loadModelsForDialog(type)
    }

    // Actualizar apiKey cuando cambia el estado o el endpoint de Moonshot
    LaunchedEffect(
        state.apiKey,
        state.baseUrl,
        state.moonshotEndpoint,
        state.zaiPlan,
        state.openCodeVariant
    ) {
        apiKey = state.apiKey
        baseUrl = state.baseUrl
        selectedMoonshotEndpoint = state.moonshotEndpoint
        selectedZAIPlan = state.zaiPlan
        selectedOpenCodeVariant = state.openCodeVariant
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

                // ── Selector de plan para Z.AI ────────────────────────────
                if (isZAI) {
                    item {
                        ExposedDropdownMenuBox(
                            expanded = zaiPlanExpanded,
                            onExpandedChange = { zaiPlanExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedZAIPlan.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Plan") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = zaiPlanExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = zaiPlanExpanded,
                                onDismissRequest = { zaiPlanExpanded = false }
                            ) {
                                ZAIPlanType.entries.forEach { plan ->
                                    DropdownMenuItem(
                                        text = { Text(plan.displayName) },
                                        onClick = {
                                            selectedZAIPlan = plan
                                            zaiPlanExpanded = false
                                            viewModel.setZAIPlan(plan)
                                            saved = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Selector de variante para OpenCode ─────────────────────
                if (isOpenCode) {
                    item {
                        ExposedDropdownMenuBox(
                            expanded = openCodeVariantExpanded,
                            onExpandedChange = { openCodeVariantExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedOpenCodeVariant.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Variante") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = openCodeVariantExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = openCodeVariantExpanded,
                                onDismissRequest = { openCodeVariantExpanded = false }
                            ) {
                                OpenCodeVariantType.entries.forEach { variant ->
                                    DropdownMenuItem(
                                        text = { Text(variant.displayName) },
                                        onClick = {
                                            selectedOpenCodeVariant = variant
                                            openCodeVariantExpanded = false
                                            viewModel.setOpenCodeVariant(variant)
                                            saved = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Sección de credenciales ──────────────────────────────
                if (showsApiKey) {
                    item {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = {
                                apiKey = it
                                saved = false
                                if (isMoonshot) {
                                    viewModel.updateMoonshotApiKey(selectedMoonshotEndpoint, it)
                                } else if (isZAI) {
                                    viewModel.updateZAIApiKey(selectedZAIPlan, it)
                                } else if (isOpenCode) {
                                    viewModel.updateOpenCodeApiKey(selectedOpenCodeVariant, it)
                                }
                            },
                            label = {
                                if (isMoonshot) {
                                    Text("API Key - ${selectedMoonshotEndpoint.displayName}")
                                } else if (isZAI) {
                                    Text("API Key - ${selectedZAIPlan.displayName}")
                                } else if (isOpenCode) {
                                    Text("API Key - ${selectedOpenCodeVariant.displayName}")
                                } else if (type == ProviderType.LM_STUDIO) {
                                    Text("Token API de LM Studio (opcional)")
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

                if (isOpenAI) {
                    item {
                        OutlinedButton(
                            onClick = {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://platform.openai.com/api-keys")
                                )
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                oauthContext.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Obtener API key de OpenAI")
                        }
                    }
                }

                if (isNvidia) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "API gratuita de NVIDIA Build",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Usa la key de NVIDIA Endpoints que empieza con nvapi-. No uses una key de NGC destinada a descargar contenedores.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(NvidiaProviderConfig.API_KEYS_URL)
                                )
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                oauthContext.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Obtener API key gratuita")
                        }
                    }
                }

                if (needsBaseUrl && !isMoonshot) {
                    item {
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = {
                                baseUrl = it
                                saved = false
                            },
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

                if (isZAI) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "Plan seleccionado:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    selectedZAIPlan.baseUrl,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Cada plan requiere su propia API key.",
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
                                    "La lista de modelos se consulta directamente a OpenCode.\n" +
                                    "La app usa el ID directo del modelo, sin el prefijo opencode/.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "OpenCode Go:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "La lista depende de tu suscripción y se actualiza desde OpenCode.\n" +
                                    "La app usa el ID directo, sin el prefijo opencode-go/.",
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

                // Anthropic: open console to get API key
                if (isAnthropic) {
                    item {
                        OutlinedButton(
                            onClick = {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://console.anthropic.com/settings/keys")
                                )
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                oauthContext.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Obtener API Key en Anthropic")
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Se abre la consola de Anthropic. Crea una API key, cópiala y pégala en el campo de arriba.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

                if (type == ProviderType.LM_STUDIO) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "Para usar LM Studio:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "1. Carga un modelo en LM Studio\n" +
                                        "2. En Developer, inicia el servidor local y habilita el acceso desde la red\n" +
                                        "3. Emulador: http://10.0.2.2:1234/v1/\n" +
                                        "4. Teléfono: usa http://IP-DE-TU-PC:1234/v1/\n" +
                                        "5. Si activas Require Authentication, pega arriba el token generado",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "El teléfono y la computadora deben estar en la misma red.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                                } else if (isZAI) {
                                    viewModel.saveZAIConfig(selectedZAIPlan) { saved = true }
                                } else if (isOpenCode) {
                                    viewModel.saveOpenCodeConfig(selectedOpenCodeVariant) { saved = true }
                                } else {
                                    viewModel.updateApiKey(type, apiKey)
                                    viewModel.updateBaseUrl(type, baseUrl)
                                    viewModel.saveProviderConfig(type) { saved = true }
                                }
                            },
                            enabled = when {
                                type == ProviderType.GOOGLE_AI -> true // puede usar OAuth sin API key
                                isMoonshot -> apiKey.isNotBlank() || state.moonshotApiKeys.values.any { it.isNotBlank() }
                                isZAI -> apiKey.isNotBlank() || state.zaiApiKeys.values.any { it.isNotBlank() }
                                isOpenCode -> apiKey.isNotBlank() || state.openCodeApiKeys.values.any { it.isNotBlank() }
                                requiresApiKey -> apiKey.isNotBlank()
                                else -> true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isLocalServer) "Guardar configuración" else "Guardar credenciales")
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (state.availableModels.size >= MODEL_SEARCH_THRESHOLD || modelQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            showModelSearch = !showModelSearch
                                            if (!showModelSearch) modelQuery = ""
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            if (showModelSearch) Icons.Default.Close else Icons.Default.Search,
                                            contentDescription = if (showModelSearch) {
                                                "Cerrar búsqueda"
                                            } else {
                                                "Buscar modelos"
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                if (state.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(horizontal = 7.dp)
                                            .size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    IconButton(
                                        onClick = { viewModel.reloadModels(type) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Actualizar catálogo",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            "Selecciona los modelos que quieres usar en el chat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        state.catalogSource?.let { source ->
                            Text(
                                buildString {
                                    append(source.displayName)
                                    append(" · ")
                                    append(state.availableModels.size)
                                    append(if (state.availableModels.size == 1) " modelo" else " modelos")
                                    if (state.isLoading) append(" · actualizando…")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    state.catalogError?.let { error ->
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (state.availableModels.isNotEmpty()) {
                                            "$error. Se conserva la última lista disponible."
                                        } else {
                                            error
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    if (showModelSearch && state.availableModels.isNotEmpty()) {
                        item {
                            OutlinedTextField(
                                value = modelQuery,
                                onValueChange = { modelQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Buscar modelo") },
                                placeholder = { Text("Nombre o familia") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (modelQuery.isNotEmpty()) {
                                        IconButton(onClick = { modelQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Limpiar búsqueda")
                                        }
                                    }
                                },
                                singleLine = true
                            )
                        }
                    }

                    val models = state.availableModels
                    if (models.isEmpty() && !state.isLoading) {
                        item {
                            Text(
                                if (state.catalogError == null) {
                                    "El proveedor no anunció modelos. Toca ↺ para volver a consultar."
                                } else {
                                    "No hay un catálogo guardado para este proveedor."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    } else if (modelQuery.isNotBlank() && filteredModels.isEmpty()) {
                        item {
                            Text(
                                "No hay modelos que coincidan con “${modelQuery.trim()}”.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    } else {
                        items(filteredModels, key = { it }) { modelId ->
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
