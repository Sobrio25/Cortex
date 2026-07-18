package com.aiagents.app.presentation.capabilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiagents.app.domain.model.CapabilityCategory
import com.aiagents.app.presentation.skills.SkillsScreen

private data class CapabilityTab(val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapabilitiesScreen(
    onBack: () -> Unit,
    onConfigureMcp: () -> Unit,
    viewModel: CapabilitiesViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = remember {
        listOf(
            CapabilityTab("Skills", Icons.Default.Star),
            CapabilityTab("Tools", Icons.Default.Build),
            CapabilityTab("MCP", Icons.Default.Extension)
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Capabilities") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(tab.label) },
                            icon = { Icon(tab.icon, contentDescription = null) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> SkillsScreen(
                showTopBar = false,
                modifier = Modifier.padding(padding)
            )
            1 -> ToolsPane(
                modifier = Modifier.padding(padding),
                viewModel = viewModel
            )
            else -> McpPane(
                modifier = Modifier.padding(padding),
                viewModel = viewModel,
                onConfigureMcp = onConfigureMcp
            )
        }
    }
}

@Composable
private fun ToolsPane(modifier: Modifier, viewModel: CapabilitiesViewModel) {
    val tools by viewModel.toolCapabilities.collectAsStateWithLifecycle()
    val categories = CapabilityCategory.entries.filter { category ->
        tools.any { it.category == category }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ExplanationCard(
                "Las tools no tienen interruptor propio. Se activan automáticamente cuando al menos una skill activa las necesita. Las integraciones MCP también deben estar habilitadas."
            )
        }
        categories.forEach { category ->
            item {
                Text(
                    categoryLabel(category),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(tools.filter { it.category == category }, key = { it.name }) { tool ->
                ToolCapabilityCard(tool)
            }
        }
    }
}

@Composable
private fun ToolCapabilityCard(tool: ToolCapabilityUi) {
    val statusColor = when {
        tool.isAvailable -> Color(0xFF2E7D32)
        tool.enabledBySkill -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        ListItem(
            headlineContent = {
                Text(tool.name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
            },
            supportingContent = {
                Text(
                    when {
                        tool.isAvailable -> "Activa por: ${tool.activeSkillNames.joinToString()}"
                        tool.enabledBySkill && !tool.mcpConfigured ->
                            "La skill está activa; configura ${tool.requiredMcpId} en MCP"
                        tool.enabledBySkill -> "La skill está activa; habilita ${tool.requiredMcpId} en MCP"
                        else -> "Sin ninguna skill activa que la use"
                    }
                )
            },
            trailingContent = {
                Surface(
                    color = statusColor.copy(alpha = 0.14f),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text(
                        if (tool.isAvailable) "Activa" else "Inactiva",
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
        )
    }
}

@Composable
private fun McpPane(
    modifier: Modifier,
    viewModel: CapabilitiesViewModel,
    onConfigureMcp: () -> Unit
) {
    val capabilities by viewModel.mcpCapabilities.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ExplanationCard(
                "MCP conecta skills con servicios externos. Una integración necesita su switch activo, credenciales válidas y al menos una skill activa que use sus tools."
            )
        }
        items(capabilities, key = { it.id }) { capability ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                ListItem(
                    headlineContent = { Text(capability.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Column {
                            Text(capability.description)
                            Text(
                                if (capability.configured) {
                                    "Configurado · ${capability.toolCount} tools"
                                } else {
                                    "Requiere configuración · ${capability.toolCount} tools"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (capability.configured) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    },
                    trailingContent = {
                        Switch(
                            checked = capability.enabled,
                            onCheckedChange = { viewModel.setMcpEnabled(capability.id, it) }
                        )
                    }
                )
            }
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Button(onClick = onConfigureMcp, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Configurar credenciales y proveedores")
            }
        }
    }
}

@Composable
private fun ExplanationCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.Info, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun categoryLabel(category: CapabilityCategory): String = when (category) {
    CapabilityCategory.CORE -> "Núcleo"
    CapabilityCategory.PRODUCTIVITY -> "Productividad"
    CapabilityCategory.KNOWLEDGE -> "Conocimiento"
    CapabilityCategory.CREATION -> "Creación"
    CapabilityCategory.DEVICE -> "Dispositivo"
    CapabilityCategory.INTEGRATIONS -> "Integraciones"
    CapabilityCategory.CUSTOM -> "Personalizadas"
}
