package com.aiagents.app.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aiagents.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAgents: () -> Unit,
    onNavigateToProviders: () -> Unit,
    onNavigateToLocalModels: () -> Unit,
    onNavigateToMCP: () -> Unit,
    onNavigateToMemory: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuracion") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SettingsItem(
                icon = Icons.Default.SmartToy,
                title = "Agentes",
                subtitle = "Gestionar agentes y personalidad",
                onClick = onNavigateToAgents
            )
            SettingsItem(
                icon = Icons.Default.Cloud,
                title = "Proveedores",
                subtitle = "API keys y modelos",
                onClick = onNavigateToProviders
            )
            SettingsItem(
                icon = Icons.Default.PhoneAndroid,
                title = "Modelos Locales",
                subtitle = "Modelos on-device con MediaPipe",
                onClick = onNavigateToLocalModels
            )
            SettingsItem(
                icon = Icons.Default.Extension,
                title = stringResource(R.string.settings_mcp_title),
                subtitle = stringResource(R.string.settings_mcp_subtitle),
                onClick = onNavigateToMCP
            )
            SettingsItem(
                icon = Icons.Default.Psychology,
                title = "Memoria",
                subtitle = "Memoria persistente de Cortex",
                onClick = onNavigateToMemory
            )
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
