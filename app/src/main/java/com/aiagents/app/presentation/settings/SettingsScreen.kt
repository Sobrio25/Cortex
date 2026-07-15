package com.aiagents.app.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiagents.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAgents: () -> Unit,
    onNavigateToProviders: () -> Unit,
    onNavigateToLocalModels: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToScheduledTasks: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToMCP: () -> Unit,
    onNavigateToGoogleWorkspace: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                SettingsSection(
                    title = stringResource(R.string.settings_section_automation),
                    entries = listOf(
                        SettingsEntry(
                            icon = Icons.Default.RecordVoiceOver,
                            title = stringResource(R.string.settings_assistant_title),
                            subtitle = stringResource(R.string.settings_assistant_subtitle),
                            onClick = onNavigateToAssistant
                        ),
                        SettingsEntry(
                            icon = Icons.Default.Alarm,
                            title = stringResource(R.string.settings_scheduled_tasks_title),
                            subtitle = stringResource(R.string.settings_scheduled_tasks_subtitle),
                            onClick = onNavigateToScheduledTasks
                        )
                    )
                )
            }

            item {
                SettingsSection(
                    title = stringResource(R.string.settings_section_ai),
                    entries = listOf(
                        SettingsEntry(
                            icon = Icons.Default.SmartToy,
                            title = stringResource(R.string.settings_agents_title),
                            subtitle = stringResource(R.string.settings_agents_subtitle),
                            onClick = onNavigateToAgents
                        ),
                        SettingsEntry(
                            icon = Icons.Default.Cloud,
                            title = stringResource(R.string.settings_providers_title),
                            subtitle = stringResource(R.string.settings_providers_subtitle),
                            onClick = onNavigateToProviders
                        ),
                        SettingsEntry(
                            icon = Icons.Default.DeveloperBoard,
                            title = stringResource(R.string.settings_local_models_title),
                            subtitle = stringResource(R.string.settings_local_models_subtitle),
                            onClick = onNavigateToLocalModels
                        )
                    )
                )
            }

            item {
                SettingsSection(
                    title = stringResource(R.string.settings_section_personalization),
                    entries = listOf(
                        SettingsEntry(
                            icon = Icons.Default.Psychology,
                            title = stringResource(R.string.settings_memory_title),
                            subtitle = stringResource(R.string.settings_memory_subtitle),
                            onClick = onNavigateToMemory
                        ),
                        SettingsEntry(
                            icon = Icons.Default.AutoAwesome,
                            title = stringResource(R.string.settings_skills_title),
                            subtitle = stringResource(R.string.settings_skills_subtitle),
                            onClick = onNavigateToSkills
                        )
                    )
                )
            }

            item {
                SettingsSection(
                    title = stringResource(R.string.settings_section_integrations),
                    entries = listOf(
                        SettingsEntry(
                            icon = Icons.Default.Extension,
                            title = stringResource(R.string.settings_mcp_title),
                            subtitle = stringResource(R.string.settings_mcp_subtitle),
                            onClick = onNavigateToMCP
                        ),
                        SettingsEntry(
                            icon = Icons.Default.CloudSync,
                            title = stringResource(R.string.settings_google_workspace_title),
                            subtitle = stringResource(R.string.settings_google_workspace_subtitle),
                            onClick = onNavigateToGoogleWorkspace
                        )
                    )
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    entries: List<SettingsEntry>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
            )
        ) {
            entries.forEachIndexed { index, entry ->
                SettingsItem(entry)
                if (index != entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsItem(entry: SettingsEntry) {
    ListItem(
        headlineContent = { Text(entry.title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(entry.subtitle) },
        leadingContent = {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(9.dp)
                )
            }
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = entry.onClick)
    )
}

private data class SettingsEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)
