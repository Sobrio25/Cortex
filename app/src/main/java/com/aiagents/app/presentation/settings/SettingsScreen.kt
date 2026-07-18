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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RecordVoiceOver
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aiagents.app.R
import com.aiagents.app.ui.theme.CortexMark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onNavigateToAgents: () -> Unit,
    onNavigateToProviders: () -> Unit,
    onNavigateToDefaultChatModel: () -> Unit,
    onNavigateToLocalModels: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToCapabilities: () -> Unit,
    onNavigateToScheduledTasks: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToGoogleWorkspace: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val globalUsageAnalyticsEnabled by viewModel.globalUsageAnalyticsEnabled
    val errorReportingEnabled by viewModel.errorReportingEnabled

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
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                SettingsSection(
                    title = stringResource(R.string.settings_section_cortex),
                    entries = listOf(
                        SettingsEntry.Navigation(
                            icon = null,
                            title = stringResource(R.string.settings_memory_title),
                            subtitle = stringResource(R.string.settings_memory_subtitle),
                            onClick = onNavigateToMemory
                        ),
                        SettingsEntry.Navigation(
                            icon = Icons.Default.GraphicEq,
                            title = stringResource(R.string.settings_voice_title),
                            subtitle = stringResource(R.string.settings_voice_subtitle),
                            onClick = onNavigateToVoice
                        ),
                        SettingsEntry.Navigation(
                            icon = Icons.Default.RecordVoiceOver,
                            title = stringResource(R.string.settings_assistant_title),
                            subtitle = stringResource(R.string.settings_assistant_subtitle),
                            onClick = onNavigateToAssistant
                        ),
                        SettingsEntry.Navigation(
                            icon = null,
                            title = stringResource(R.string.settings_agents_title),
                            subtitle = stringResource(R.string.settings_agents_subtitle),
                            onClick = onNavigateToAgents
                        )
                    )
                )
            }

            item {
                SettingsSection(
                    title = stringResource(R.string.settings_section_models_plan),
                    entries = listOf(
                        SettingsEntry.Navigation(
                            Icons.AutoMirrored.Filled.Chat,
                            stringResource(R.string.settings_default_chat_model_title),
                            stringResource(R.string.settings_default_chat_model_subtitle),
                            onNavigateToDefaultChatModel
                        ),
                        SettingsEntry.Navigation(
                            Icons.Default.Cloud,
                            stringResource(R.string.settings_providers_title),
                            stringResource(R.string.settings_providers_subtitle),
                            onNavigateToProviders
                        ),
                        SettingsEntry.Navigation(
                            Icons.Default.DeveloperBoard,
                            stringResource(R.string.settings_local_models_title),
                            stringResource(R.string.settings_local_models_subtitle),
                            onNavigateToLocalModels
                        ),
                        SettingsEntry.Navigation(
                            Icons.Default.CreditCard,
                            stringResource(R.string.settings_subscription_title),
                            stringResource(R.string.settings_subscription_subtitle),
                            onNavigateToSubscription
                        )
                    )
                )
            }

            item {
                SettingsSection(
                    title = stringResource(R.string.settings_section_capabilities),
                    entries = listOf(
                        SettingsEntry.Navigation(
                            Icons.Default.AutoAwesome,
                            stringResource(R.string.settings_capabilities_title),
                            stringResource(R.string.settings_capabilities_subtitle),
                            onNavigateToCapabilities
                        ),
                        SettingsEntry.Navigation(
                            Icons.Default.Alarm,
                            stringResource(R.string.settings_scheduled_tasks_title),
                            stringResource(R.string.settings_scheduled_tasks_subtitle),
                            onNavigateToScheduledTasks
                        )
                    )
                )
            }

            item {
                SettingsSection(
                    title = stringResource(R.string.settings_section_integrations),
                    entries = listOf(
                        SettingsEntry.Navigation(
                            Icons.Default.CloudSync,
                            stringResource(R.string.settings_google_workspace_title),
                            stringResource(R.string.settings_google_workspace_subtitle),
                            onNavigateToGoogleWorkspace
                        )
                    )
                )
            }

            item {
                SettingsSection(
                    title = stringResource(R.string.settings_section_privacy_support),
                    entries = listOf(
                        SettingsEntry.Toggle(
                            Icons.Default.BugReport,
                            stringResource(R.string.settings_error_reporting_title),
                            stringResource(R.string.settings_error_reporting_subtitle),
                            errorReportingEnabled,
                            viewModel::setErrorReportingEnabled
                        ),
                        SettingsEntry.Toggle(
                            Icons.Default.CloudSync,
                            stringResource(R.string.settings_global_usage_title),
                            stringResource(R.string.settings_global_usage_subtitle),
                            globalUsageAnalyticsEnabled,
                            viewModel::setGlobalUsageAnalyticsEnabled
                        ),
                        SettingsEntry.Navigation(
                            Icons.Default.BugReport,
                            stringResource(R.string.settings_diagnostics_title),
                            stringResource(R.string.settings_diagnostics_subtitle),
                            onNavigateToDiagnostics
                        )
                    )
                )
            }
        }
    }
}

private sealed interface SettingsEntry {
    val icon: ImageVector?
    val title: String
    val subtitle: String

    data class Navigation(
        override val icon: ImageVector?,
        override val title: String,
        override val subtitle: String,
        val onClick: () -> Unit
    ) : SettingsEntry

    data class Toggle(
        override val icon: ImageVector,
        override val title: String,
        override val subtitle: String,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit
    ) : SettingsEntry
}

@Composable
private fun SettingsSection(title: String, entries: List<SettingsEntry>) {
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
            val icon = entry.icon
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                if (icon == null) {
                    CortexMark(Modifier.padding(5.dp).fillMaxSize())
                } else {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(9.dp)
                    )
                }
            }
        },
        trailingContent = {
            when (entry) {
                is SettingsEntry.Navigation -> Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                is SettingsEntry.Toggle -> Switch(
                    checked = entry.checked,
                    onCheckedChange = entry.onCheckedChange
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable {
            when (entry) {
                is SettingsEntry.Navigation -> entry.onClick()
                is SettingsEntry.Toggle -> entry.onCheckedChange(!entry.checked)
            }
        }
    )
}
