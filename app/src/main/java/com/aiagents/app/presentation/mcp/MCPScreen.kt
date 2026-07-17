package com.aiagents.app.presentation.mcp

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.aiagents.app.R
import com.aiagents.app.domain.model.WebSearchProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPScreen(
    onBack: (() -> Unit)? = null,
    viewModel: MCPViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_mcp_title)) },
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
            // Sección de información
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = toolServiceCardColors(),
                    border = toolServiceCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "¿Qué es MCP?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Model Context Protocol (MCP) permite a los agentes acceder a herramientas externas como búsqueda web, bases de datos y más.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                WebSearchProviderCard(
                    selectedProvider = uiState.webSearchProvider,
                    braveIsConfigured = uiState.braveIsConfigured,
                    serpApiIsConfigured = uiState.serpApiIsConfigured,
                    onProviderSelected = viewModel::selectWebSearchProvider
                )
            }

            // Brave Search Card
            item {
                BraveSearchCard(
                    isConfigured = uiState.braveIsConfigured,
                    isSelected = uiState.webSearchProvider == WebSearchProvider.BRAVE,
                    isLoading = uiState.isLoading,
                    onConfigure = { viewModel.showBraveConfigDialog() }
                )
            }

            // Google Maps Card
            item {
                GoogleMapsCard(
                    isConfigured = uiState.googleMapsIsConfigured,
                    isLoading = uiState.isLoading,
                    onConfigure = { viewModel.showGoogleMapsConfigDialog() }
                )
            }

            // SerpAPI Card
            item {
                SerpApiCard(
                    isConfigured = uiState.serpApiIsConfigured,
                    isSelected = uiState.webSearchProvider == WebSearchProvider.SERPAPI,
                    isLoading = uiState.isLoading,
                    onConfigure = { viewModel.showSerpApiConfigDialog() }
                )
            }

            // Canva Card
            item {
                CanvaCard(
                    isConfigured = uiState.canvaIsConfigured,
                    onConfigure = { viewModel.showCanvaConfigDialog() }
                )
            }

            // PubMed Card
            item {
                PubMedCard(
                    isEnabled = uiState.pubMedIsEnabled,
                    onToggle = { viewModel.togglePubMed() }
                )
            }

            // Finance Card
            item {
                FinanceCard(
                    isEnabled = uiState.financeIsEnabled,
                    onToggle = { viewModel.toggleFinance() }
                )
            }

            // Obsidian Card
            item {
                ObsidianCard(
                    vaultPath = uiState.obsidianVaultPath,
                    isConfigured = uiState.obsidianIsConfigured,
                    onConfigure = { viewModel.showObsidianConfigDialog() }
                )
            }

            // GitHub Card
            item {
                GitHubCard(
                    isConfigured = uiState.gitHubIsConfigured,
                    onConfigure = { viewModel.showGitHubConfigDialog() }
                )
            }

            // Notion Card
            item {
                NotionCard(
                    isConfigured = uiState.notionIsConfigured,
                    onConfigure = { viewModel.showNotionConfigDialog() }
                )
            }

            // Slack Card
            item {
                SlackCard(
                    isConfigured = uiState.slackIsConfigured,
                    onConfigure = { viewModel.showSlackConfigDialog() }
                )
            }

            // Weather Card
            item {
                WeatherCard()
            }

            // Google Imagen (Nano Banana 2) Card
            item {
                ImageGenCard(
                    title = "Google Imagen",
                    subtitle = "Gemini 3.1 Flash / Nano Banana 2",
                    description = "Generacion de imagenes con el modelo de Google. Rapido y de alta calidad.",
                    iconColor = Color(0xFF4285F4),
                    icon = Icons.Default.Palette,
                    isConfigured = uiState.googleImagenIsConfigured,
                    onConfigure = { viewModel.showGoogleImagenConfigDialog() }
                )
            }

            // DALL-E Card
            item {
                ImageGenCard(
                    title = "DALL-E",
                    subtitle = "OpenAI Image Generation",
                    description = "Generacion, edicion y variaciones de imagenes con DALL-E 3.",
                    iconColor = Color(0xFF10A37F),
                    icon = Icons.Default.Image,
                    isConfigured = uiState.dalleIsConfigured,
                    onConfigure = { viewModel.showDalleConfigDialog() }
                )
            }

        }
    }

    // Slack config dialog
    if (uiState.showSlackConfigDialog) {
        TokenConfigDialog(
            title = "Configurar Slack",
            tokenLabel = "Bot User OAuth Token",
            currentToken = uiState.slackToken,
            instructions = "1. Go to api.slack.com/apps and create a new app\n2. Go to OAuth & Permissions\n3. Add scopes: channels:read, channels:history, chat:write, users:read, search:read, reactions:write, files:read\n4. Install the app to your workspace\n5. Copy the Bot User OAuth Token (xoxb-...)",
            onDismiss = { viewModel.hideSlackConfigDialog() },
            onSave = { viewModel.saveSlackToken(it) }
        )
    }

    // Google Imagen config dialog
    if (uiState.showGoogleImagenConfigDialog) {
        TokenConfigDialog(
            title = "Configurar Google Imagen",
            tokenLabel = "Google AI API Key",
            currentToken = uiState.googleImagenApiKey,
            instructions = "1. Ve a aistudio.google.com\n2. Obtén tu API Key\n3. Pegala aqui\n4. Se usa para generar imagenes con Gemini 3.1 Flash (Nano Banana 2)",
            onDismiss = { viewModel.hideGoogleImagenConfigDialog() },
            onSave = { viewModel.saveGoogleImagenApiKey(it) }
        )
    }

    // DALL-E config dialog
    if (uiState.showDalleConfigDialog) {
        TokenConfigDialog(
            title = "Configurar DALL-E",
            tokenLabel = "OpenAI API Key",
            currentToken = uiState.dalleApiKey,
            instructions = "1. Ve a platform.openai.com/api-keys\n2. Crea una nueva API key\n3. Pegala aqui\n4. Se usa para DALL-E 3 (generacion, edicion y variaciones)",
            onDismiss = { viewModel.hideDalleConfigDialog() },
            onSave = { viewModel.saveDalleApiKey(it) }
        )
    }

    // Diálogo de configuración de Brave Search
    if (uiState.showBraveConfigDialog) {
        BraveSearchConfigDialog(
            apiKey = uiState.braveApiKey,
            onDismiss = { viewModel.hideBraveConfigDialog() },
            onSave = { apiKey ->
                viewModel.saveBraveApiKey(apiKey)
            }
        )
    }

    // Diálogo de configuración de Google Maps
    if (uiState.showGoogleMapsConfigDialog) {
        GoogleMapsConfigDialog(
            apiKey = uiState.googleMapsApiKey,
            onDismiss = { viewModel.hideGoogleMapsConfigDialog() },
            onSave = { apiKey ->
                viewModel.saveGoogleMapsApiKey(apiKey)
            }
        )
    }

    // Diálogo de configuración de SerpAPI
    if (uiState.showSerpApiConfigDialog) {
        SerpApiConfigDialog(
            apiKey = uiState.serpApiKey,
            onDismiss = { viewModel.hideSerpApiConfigDialog() },
            onSave = { apiKey ->
                viewModel.saveSerpApiKey(apiKey)
            }
        )
    }

    // Diálogo de configuración de Canva
    if (uiState.showCanvaConfigDialog) {
        CanvaConfigDialog(
            currentToken = uiState.canvaAccessToken,
            onDismiss = { viewModel.hideCanvaConfigDialog() },
            onSave = { viewModel.saveCanvaAccessToken(it) }
        )
    }

    // Diálogo de configuración de Obsidian
    if (uiState.showObsidianConfigDialog) {
        ObsidianConfigDialog(
            currentPath = uiState.obsidianVaultPath,
            onDismiss = { viewModel.hideObsidianConfigDialog() },
            onSave = { path -> viewModel.saveObsidianVaultPath(path) }
        )
    }

    // Diálogo de configuración de GitHub
    if (uiState.showGitHubConfigDialog) {
        TokenConfigDialog(
            title = "Configurar GitHub",
            tokenLabel = "Personal Access Token",
            currentToken = uiState.gitHubToken,
            instructions = "1. Ve a github.com/settings/tokens\n2. Genera un token (classic)\n3. Selecciona scopes: repo, read:user\n4. Copia el token y pegalo aqui",
            onDismiss = { viewModel.hideGitHubConfigDialog() },
            onSave = { viewModel.saveGitHubToken(it) }
        )
    }

    // Diálogo de configuración de Notion
    if (uiState.showNotionConfigDialog) {
        TokenConfigDialog(
            title = "Configurar Notion",
            tokenLabel = "Integration Token",
            currentToken = uiState.notionToken,
            instructions = "1. Ve a notion.so/my-integrations\n2. Crea una nueva integracion\n3. Copia el Internal Integration Token\n4. En Notion, comparte las paginas con tu integracion",
            onDismiss = { viewModel.hideNotionConfigDialog() },
            onSave = { viewModel.saveNotionToken(it) }
        )
    }

}

/**
 * Tool cards intentionally use the surface family instead of primaryContainer.
 * Dynamic dark themes can expose a very light primaryContainer, which makes
 * onSurface/onSurfaceVariant content illegible when a tool becomes active.
 */
@Composable
private fun toolServiceCardColors(isActive: Boolean = false): CardColors {
    val colors = MaterialTheme.colorScheme
    val container = if (isActive) colors.surfaceContainerHigh else colors.surfaceContainerLow
    return CardDefaults.cardColors(
        containerColor = container,
        contentColor = colors.onSurface,
        disabledContainerColor = container.copy(alpha = 0.6f),
        disabledContentColor = colors.onSurface.copy(alpha = 0.38f)
    )
}

@Composable
private fun toolServiceCardBorder(isActive: Boolean = false): BorderStroke {
    val colors = MaterialTheme.colorScheme
    return BorderStroke(
        width = 1.dp,
        color = if (isActive) colors.primary.copy(alpha = 0.9f)
        else colors.outlineVariant.copy(alpha = 0.65f)
    )
}

@Composable
private fun toolServiceActionButtonColors(): ButtonColors {
    val colors = MaterialTheme.colorScheme
    return ButtonDefaults.buttonColors(
        containerColor = colors.surfaceContainerHighest,
        contentColor = colors.onSurface,
        disabledContainerColor = colors.surfaceContainerHighest.copy(alpha = 0.45f),
        disabledContentColor = colors.onSurface.copy(alpha = 0.38f)
    )
}

@Composable
private fun toolServiceSwitchColors(): SwitchColors {
    val colors = MaterialTheme.colorScheme
    return SwitchDefaults.colors(
        checkedThumbColor = colors.primary,
        checkedTrackColor = colors.surfaceContainerHighest,
        checkedBorderColor = colors.primary,
        uncheckedThumbColor = colors.onSurfaceVariant,
        uncheckedTrackColor = colors.surfaceContainerLow,
        uncheckedBorderColor = colors.outline
    )
}

@Composable
private fun WebSearchProviderCard(
    selectedProvider: WebSearchProvider,
    braveIsConfigured: Boolean,
    serpApiIsConfigured: Boolean,
    onProviderSelected: (WebSearchProvider) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = toolServiceCardColors(isActive = true),
        border = toolServiceCardBorder(isActive = true)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Motor de búsqueda web",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "La opción nativa no utiliza API keys",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            WebSearchProviderOption(
                title = "Nativo (predeterminado)",
                description = "DuckDuckGo HTML y extracción local con Jsoup",
                selected = selectedProvider == WebSearchProvider.NATIVE,
                enabled = true,
                onClick = { onProviderSelected(WebSearchProvider.NATIVE) }
            )
            WebSearchProviderOption(
                title = "Brave Search",
                description = if (braveIsConfigured) "Configurado · uso opcional" else "Requiere configurar una API key",
                selected = selectedProvider == WebSearchProvider.BRAVE,
                enabled = braveIsConfigured,
                onClick = { onProviderSelected(WebSearchProvider.BRAVE) }
            )
            WebSearchProviderOption(
                title = "SerpAPI",
                description = if (serpApiIsConfigured) "Configurado · uso opcional" else "Requiere configurar una API key",
                selected = selectedProvider == WebSearchProvider.SERPAPI,
                enabled = serpApiIsConfigured,
                onClick = { onProviderSelected(WebSearchProvider.SERPAPI) }
            )
        }
    }
}

@Composable
private fun WebSearchProviderOption(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.55f)
            )
        }
    }
}

@Composable
private fun BraveSearchCard(
    isConfigured: Boolean,
    isSelected: Boolean,
    isLoading: Boolean,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = toolServiceCardColors(isActive = isSelected),
        border = toolServiceCardBorder(isActive = isSelected)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isConfigured)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_brave),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Brave Search",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        when {
                            isSelected -> "Seleccionado para búsquedas"
                            isConfigured -> "Configurado · uso opcional"
                            else -> "No configurado"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isConfigured)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(
                        if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Settings,
                        contentDescription = null,
                        tint = if (isConfigured)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Búsqueda web privada y gratuita (2,000 consultas/mes). " +
                "Permite a los agentes buscar información actualizada en internet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onConfigure,
                modifier = Modifier.fillMaxWidth(),
                colors = toolServiceActionButtonColors()
            ) {
                Icon(
                    if (isConfigured) Icons.Default.Edit else Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isConfigured) "Editar configuración" else "Configurar")
            }
        }
    }
}

@Composable
private fun GoogleMapsCard(
    isConfigured: Boolean,
    isLoading: Boolean,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = toolServiceCardColors(isActive = isConfigured),
        border = toolServiceCardBorder(isActive = isConfigured)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isConfigured)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_google_maps),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Google Maps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (isConfigured) "Configurado y activo" else "No configurado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isConfigured)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(
                        if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Settings,
                        contentDescription = null,
                        tint = if (isConfigured)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Acceso a geocodificación, búsqueda de lugares, rutas, distancias y elevación. " +
                "Permite a los agentes trabajar con información geoespacial y ubicaciones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            // Lista de herramientas disponibles
            if (isConfigured) {
                Column {
                    val tools = listOf(
                        "📍 Geocodificación de direcciones",
                        "🔍 Búsqueda de lugares",
                        "🗺️ Direcciones y rutas",
                        "📏 Cálculo de distancias",
                        "⛰️ Elevación del terreno"
                    )
                    tools.forEach { tool ->
                        Text(
                            tool,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = onConfigure,
                modifier = Modifier.fillMaxWidth(),
                colors = toolServiceActionButtonColors()
            ) {
                Icon(
                    if (isConfigured) Icons.Default.Edit else Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isConfigured) "Editar configuración" else "Configurar")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BraveSearchConfigDialog(
    apiKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var currentApiKey by remember { mutableStateOf(apiKey) }
    var showApiKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar Brave Search") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = currentApiKey,
                    onValueChange = { currentApiKey = it },
                    label = { Text("API Key de Brave Search") },
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

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "¿Cómo obtener tu API Key?",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "1. Visita api.search.brave.com\n" +
                            "2. Crea una cuenta gratuita\n" +
                            "3. Genera tu API Key en el dashboard\n" +
                            "4. Copia y pega la clave aquí",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (apiKey.isNotBlank()) {
                    OutlinedButton(
                        onClick = { currentApiKey = "" },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Eliminar configuración")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(currentApiKey.trim()) },
                enabled = currentApiKey.isNotBlank()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoogleMapsConfigDialog(
    apiKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var currentApiKey by remember { mutableStateOf(apiKey) }
    var showApiKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar Google Maps") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = currentApiKey,
                    onValueChange = { currentApiKey = it },
                    label = { Text("API Key de Google Maps") },
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

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "¿Cómo obtener tu API Key?",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "1. Visita console.cloud.google.com\n" +
                            "2. Crea un nuevo proyecto o selecciona uno existente\n" +
                            "3. Ve a 'APIs y servicios' > 'Credenciales'\n" +
                            "4. Crea una API Key\n" +
                            "5. Habilita las APIs: Geocoding, Places, Directions, Distance Matrix, Elevation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (apiKey.isNotBlank()) {
                    OutlinedButton(
                        onClick = { currentApiKey = "" },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Eliminar configuración")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(currentApiKey.trim()) },
                enabled = currentApiKey.isNotBlank()
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
private fun SerpApiCard(
    isConfigured: Boolean,
    isSelected: Boolean,
    isLoading: Boolean,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = toolServiceCardColors(isActive = isSelected),
        border = toolServiceCardBorder(isActive = isSelected)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isConfigured)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_serpapi),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "SerpAPI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        when {
                            isSelected -> "Seleccionado para búsquedas"
                            isConfigured -> "Configurado · uso opcional"
                            else -> "No configurado"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isConfigured)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(
                        if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Settings,
                        contentDescription = null,
                        tint = if (isConfigured)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Búsquedas en Google, Google Maps, Google Flights, Shopping, News y más. " +
                "100 búsquedas gratuitas al mes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            // Lista de herramientas disponibles
            if (isConfigured) {
                Column {
                    val tools = listOf(
                        "🔍 Búsqueda Google",
                        "🗺️ Google Maps",
                        "✈️ Google Flights",
                        "🛒 Google Shopping",
                        "📰 Google News",
                        "📍 Google Local",
                        "🎥 YouTube Search"
                    )
                    tools.forEach { tool ->
                        Text(
                            tool,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = onConfigure,
                modifier = Modifier.fillMaxWidth(),
                colors = toolServiceActionButtonColors()
            ) {
                Icon(
                    if (isConfigured) Icons.Default.Edit else Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isConfigured) "Editar configuración" else "Configurar")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SerpApiConfigDialog(
    apiKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var currentApiKey by remember { mutableStateOf(apiKey) }
    var showApiKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar SerpAPI") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = currentApiKey,
                    onValueChange = { currentApiKey = it },
                    label = { Text("API Key de SerpAPI") },
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

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "¿Cómo obtener tu API Key?",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "1. Visita serpapi.com\n" +
                            "2. Crea una cuenta gratuita\n" +
                            "3. Ve a tu dashboard\n" +
                            "4. Copia tu API Key\n" +
                            "5. Pégala aquí",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Card(
                    colors = toolServiceCardColors(),
                    border = toolServiceCardBorder()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Servicios disponibles:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "• Google Search, Maps, Flights\n" +
                            "• Google Shopping, News, Images\n" +
                            "• YouTube, Local Services\n" +
                            "• 100 búsquedas/mes gratuitas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (apiKey.isNotBlank()) {
                    OutlinedButton(
                        onClick = { currentApiKey = "" },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Eliminar configuración")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(currentApiKey.trim()) },
                enabled = currentApiKey.isNotBlank()
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
private fun CanvaCard(
    isConfigured: Boolean,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = toolServiceCardColors(isActive = isConfigured),
        border = toolServiceCardBorder(isActive = isConfigured)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(painter = painterResource(id = R.drawable.ic_canva), contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Canva", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (isConfigured) "Conectado" else "No configurado", style = MaterialTheme.typography.bodyMedium, color = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                }
                Icon(if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Settings, contentDescription = null, tint = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(12.dp))
            Text("Crea diseños profesionales: posters, presentaciones, contenido para redes sociales.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onConfigure,
                modifier = Modifier.fillMaxWidth(),
                colors = toolServiceActionButtonColors()
            ) {
                Icon(if (isConfigured) Icons.Default.Edit else Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isConfigured) "Editar configuración" else "Configurar")
            }
        }
    }
}

@Composable
private fun CanvaConfigDialog(
    currentToken: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var token by remember { mutableStateOf(currentToken) }
    var showToken by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar Canva") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Ingresa tu Access Token de Canva. Puedes obtenerlo desde la página de desarrolladores de Canva.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Access Token") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showToken)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                if (showToken) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(token.trim()) }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
            if (currentToken.isNotBlank()) {
                TextButton(
                    onClick = { onSave("") },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Desconectar") }
            }
        }
    )
}

@Composable
private fun PubMedCard(
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = toolServiceCardColors(isActive = isEnabled),
        border = toolServiceCardBorder(isActive = isEnabled)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isEnabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_pubmed),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "PubMed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (isEnabled) "Activo" else "Desactivado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = toolServiceSwitchColors()
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Base de datos de la Biblioteca Nacional de Medicina (NIH). " +
                "Acceso gratuito a millones de artículos científicos y médicos. " +
                "Ideal para el agente Health Advisor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isEnabled) {
                Spacer(Modifier.height(8.dp))
                Column {
                    val tools = listOf(
                        "Buscar artículos científicos",
                        "Obtener detalles de un artículo (abstract, autores, DOI)"
                    )
                    tools.forEach { tool ->
                        Text(
                            "- $tool",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Gratuito - No requiere API Key",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Solo activa el interruptor para que los agentes puedan buscar evidencia médica.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FinanceCard(
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = toolServiceCardColors(isActive = isEnabled),
        border = toolServiceCardBorder(isActive = isEnabled)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isEnabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.AccountBalance,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = if (isEnabled)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Finanzas Personales",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (isEnabled) "Activo" else "Desactivado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = toolServiceSwitchColors()
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Registro local de movimientos financieros: gastos, ingresos e inversiones. " +
                "Los datos se almacenan solo en tu dispositivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isEnabled) {
                Spacer(Modifier.height(8.dp))
                Column {
                    val tools = listOf(
                        "Registrar gastos, ingresos e inversiones",
                        "Listar y buscar transacciones",
                        "Resumen financiero por periodo",
                        "Balance neto (ingresos - gastos)"
                    )
                    tools.forEach { tool ->
                        Text(
                            "- $tool",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Gratuito - Almacenamiento local",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Solo activa el interruptor para que los agentes puedan registrar y consultar tus finanzas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ObsidianCard(
    vaultPath: String,
    isConfigured: Boolean,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = toolServiceCardColors(isActive = isConfigured),
        border = toolServiceCardBorder(isActive = isConfigured)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isConfigured)
                        Color(0xFF7C3AED)
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = if (isConfigured) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Obsidian",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (isConfigured) "Conectado" else "No configurado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isConfigured)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Acceso directo al vault de Obsidian en tu dispositivo. " +
                "Los agentes pueden leer, escribir, buscar y organizar tus notas. " +
                "No requiere que Obsidian este abierto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isConfigured) {
                Spacer(Modifier.height(8.dp))

                Text(
                    "Vault: $vaultPath",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))

                Column {
                    val tools = listOf(
                        "Leer notas del vault",
                        "Crear y editar notas",
                        "Agregar contenido a notas existentes",
                        "Buscar notas por nombre o contenido",
                        "Listar carpetas y archivos"
                    )
                    tools.forEach { tool ->
                        Text(
                            "- $tool",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Gratuito - Acceso local a archivos",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Indica la ruta de tu vault de Obsidian en el almacenamiento del dispositivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onConfigure,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(if (isConfigured) Icons.Default.Edit else Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isConfigured) "Cambiar vault" else "Configurar vault")
            }
        }
    }
}

@Composable
private fun ObsidianConfigDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var pathText by remember { mutableStateOf(currentPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar Obsidian Vault") },
        text = {
            Column {
                Text(
                    "Introduce la ruta completa de tu vault de Obsidian en el dispositivo.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = pathText,
                    onValueChange = { pathText = it },
                    label = { Text("Ruta del vault") },
                    placeholder = { Text("/storage/emulated/0/Documents/Obsidian/MiVault") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "Rutas comunes:\n" +
                    "- /storage/emulated/0/Documents/Obsidian/NombreVault\n" +
                    "- /storage/emulated/0/Obsidian/NombreVault",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (currentPath.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = { onSave("") },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Desconectar vault")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(pathText.trim()) },
                enabled = pathText.trim().isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun GitHubCard(
    isConfigured: Boolean,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = toolServiceCardColors(isActive = isConfigured),
        border = toolServiceCardBorder(isActive = isConfigured)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isConfigured) Color(0xFF24292E) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(28.dp),
                            tint = if (isConfigured) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("GitHub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (isConfigured) "Conectado" else "No configurado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                }
                Icon(if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Settings,
                    contentDescription = null,
                    tint = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(12.dp))
            Text("Buscar repositorios, leer codigo, crear y listar issues, ver pull requests.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isConfigured) {
                Spacer(Modifier.height(8.dp))
                Column {
                    listOf("Buscar repositorios", "Leer archivos de un repo", "Crear issues", "Listar issues y PRs").forEach {
                        Text("- $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onConfigure, modifier = Modifier.fillMaxWidth()) {
                Icon(if (isConfigured) Icons.Default.Edit else Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isConfigured) "Editar token" else "Configurar")
            }
        }
    }
}

@Composable
private fun NotionCard(
    isConfigured: Boolean,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = toolServiceCardColors(isActive = isConfigured),
        border = toolServiceCardBorder(isActive = isConfigured)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isConfigured) Color(0xFF000000) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(28.dp),
                            tint = if (isConfigured) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Notion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (isConfigured) "Conectado" else "No configurado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                }
                Icon(if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Settings,
                    contentDescription = null,
                    tint = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(12.dp))
            Text("Buscar paginas, leer contenido, crear paginas y listar databases en Notion.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isConfigured) {
                Spacer(Modifier.height(8.dp))
                Column {
                    listOf("Buscar paginas y databases", "Leer contenido de paginas", "Crear nuevas paginas", "Agregar bloques a paginas").forEach {
                        Text("- $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onConfigure, modifier = Modifier.fillMaxWidth()) {
                Icon(if (isConfigured) Icons.Default.Edit else Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isConfigured) "Editar token" else "Configurar")
            }
        }
    }
}

@Composable
private fun SlackCard(
    isConfigured: Boolean,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = toolServiceCardColors(isActive = isConfigured),
        border = toolServiceCardBorder(isActive = isConfigured)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isConfigured) Color(0xFF4A154B) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(28.dp),
                            tint = if (isConfigured) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Slack", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (isConfigured) "Conectado" else "No configurado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                }
                Icon(if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Settings,
                    contentDescription = null,
                    tint = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(12.dp))
            Text("Send messages, read channels, search, list users and files in Slack.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isConfigured) {
                Spacer(Modifier.height(8.dp))
                Column {
                    listOf("Read & send messages", "Search messages", "List channels & users", "React to messages", "Manage channel topics").forEach {
                        Text("- $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onConfigure, modifier = Modifier.fillMaxWidth()) {
                Icon(if (isConfigured) Icons.Default.Edit else Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isConfigured) "Editar token" else "Configurar")
            }
        }
    }
}

@Composable
private fun WeatherCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = toolServiceCardColors(isActive = true),
        border = toolServiceCardBorder(isActive = true)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0288D1),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(28.dp),
                            tint = Color.White)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Open-Meteo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Incluido · sin API key",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
                Icon(Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            Text("Clima actual, pronóstico y calidad del aire para cualquier ubicación, usando la ubicación del dispositivo cuando no se indica una ciudad.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Column {
                listOf("Clima actual", "Pronóstico de 5 días", "Calidad del aire Open-Meteo/CAMS", "Widgets visuales automáticos").forEach {
                    Text("- $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun ImageGenCard(
    title: String,
    subtitle: String,
    description: String,
    iconColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isConfigured: Boolean,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = toolServiceCardColors(isActive = isConfigured),
        border = toolServiceCardBorder(isActive = isConfigured)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isConfigured) iconColor else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp),
                            tint = if (isConfigured) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (isConfigured) "Configurado" else "No configurado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                }
                Icon(if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Settings,
                    contentDescription = null,
                    tint = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(12.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onConfigure, modifier = Modifier.fillMaxWidth()) {
                Icon(if (isConfigured) Icons.Default.Edit else Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isConfigured) "Editar API Key" else "Configurar")
            }
        }
    }
}

@Composable
private fun TokenConfigDialog(
    title: String,
    tokenLabel: String,
    currentToken: String,
    instructions: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var token by remember { mutableStateOf(currentToken) }
    var showToken by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(tokenLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                        }
                    },
                    singleLine = true
                )
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Como obtener tu token:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Text(instructions, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (currentToken.isNotBlank()) {
                    OutlinedButton(
                        onClick = { token = "" },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Eliminar configuracion")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(token.trim()) }, enabled = token.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
