package com.aiagents.app.presentation.assistant

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aiagents.app.R
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
    val autoListen by viewModel.autoListen.collectAsState()
    val speakResponses by viewModel.speakResponses.collectAsState()
    val offlineVoiceAvailable by viewModel.offlineVoiceAvailable.collectAsState()
    val voskDownloaded by viewModel.voskModelDownloaded.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val error by viewModel.error.collectAsState()
    val onDeviceRecognition = viewModel.onDeviceRecognitionAvailable
    val systemRecognition = viewModel.systemRecognitionAvailable

    var roleHeld by remember { mutableStateOf(context.isAssistantRoleHeld()) }
    val assistantRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        roleHeld = context.isAssistantRoleHeld()
    }
    val installVoiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshOfflineVoice()
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                roleHeld = context.isAssistantRoleHeld()
                viewModel.refreshOfflineVoice()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestAssistantRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true) {
                assistantRoleLauncher.launch(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                )
                return
            }
        }
        context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
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
                AssistantHero(roleHeld = roleHeld)
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.assistant_system_role_title),
                    subtitle = stringResource(R.string.assistant_system_role_subtitle)
                ) {
                    AssistantStatusRow(
                        icon = if (roleHeld) Icons.Default.CheckCircle else Icons.Default.RecordVoiceOver,
                        title = if (roleHeld) {
                            stringResource(R.string.assistant_role_active)
                        } else {
                            stringResource(R.string.assistant_role_inactive)
                        },
                        detail = if (roleHeld) {
                            stringResource(R.string.assistant_role_active_detail)
                        } else {
                            stringResource(R.string.assistant_role_inactive_detail)
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
                                else stringResource(R.string.assistant_enable_role)
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
                    title = stringResource(R.string.assistant_voice_title),
                    subtitle = stringResource(R.string.assistant_voice_subtitle)
                ) {
                    AssistantStatusRow(
                        icon = Icons.Default.Mic,
                        title = stringResource(R.string.assistant_voice_input),
                        detail = when {
                            onDeviceRecognition -> stringResource(R.string.assistant_voice_android_local)
                            voskDownloaded -> stringResource(R.string.assistant_voice_vosk_ready)
                            systemRecognition -> stringResource(R.string.assistant_voice_android_system)
                            else -> stringResource(R.string.assistant_voice_vosk_needed)
                        },
                        ready = onDeviceRecognition || voskDownloaded || systemRecognition
                    )
                    if (!voskDownloaded) {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = viewModel::downloadSpanishVoiceModel,
                            enabled = !isDownloading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Text(stringResource(R.string.assistant_download_vosk))
                        }
                        if (isDownloading) {
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    AssistantStatusRow(
                        icon = Icons.Default.GraphicEq,
                        title = stringResource(R.string.assistant_voice_output),
                        detail = if (offlineVoiceAvailable) {
                            stringResource(R.string.assistant_tts_ready)
                        } else {
                            stringResource(R.string.assistant_tts_needed)
                        },
                        ready = offlineVoiceAvailable
                    )
                    if (!offlineVoiceAvailable) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                installVoiceLauncher.launch(viewModel.createInstallVoiceIntent())
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.assistant_install_tts))
                        }
                    }
                }
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.assistant_behavior_title),
                    subtitle = stringResource(R.string.assistant_behavior_subtitle)
                ) {
                    PreferenceSwitch(
                        title = stringResource(R.string.assistant_auto_listen_title),
                        subtitle = stringResource(R.string.assistant_auto_listen_subtitle),
                        checked = autoListen,
                        onCheckedChange = viewModel::setAutoListen
                    )
                    Spacer(Modifier.height(8.dp))
                    PreferenceSwitch(
                        title = stringResource(R.string.assistant_speak_title),
                        subtitle = stringResource(R.string.assistant_speak_subtitle),
                        checked = speakResponses,
                        onCheckedChange = viewModel::setSpeakResponses
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
private fun AssistantHero(roleHeld: Boolean) {
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
                    stringResource(R.string.assistant_hero_title),
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
private fun SettingsCard(
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
    onCheckedChange: (Boolean) -> Unit
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun Context.isAssistantRoleHeld(): Boolean {
    return VoiceInteractionService.isActiveService(
        this,
        android.content.ComponentName(this, CortexVoiceInteractionService::class.java)
    )
}

@Suppress("unused")
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
