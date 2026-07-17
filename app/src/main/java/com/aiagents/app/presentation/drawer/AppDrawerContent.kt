package com.aiagents.app.presentation.drawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.aiagents.app.R
import com.aiagents.app.domain.model.Conversation
import com.aiagents.app.domain.model.Workspace
import com.aiagents.app.ui.theme.CortexMark
import com.aiagents.app.ui.theme.CortexTheme
import com.aiagents.app.ui.theme.cortexGlass
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerContent(
    activeWorkspace: Workspace?,
    conversations: List<Conversation>,
    activeConversationId: Long?,
    isGlobalMode: Boolean,
    assistantName: String,
    onNewChat: () -> Unit,
    onConversationClick: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onRenameConversation: (Long, String) -> Unit,
    onWorkspacesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onGoToGlobal: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var conversationToDelete by remember { mutableStateOf<Long?>(null) }
    var conversationToRename by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showOptionsFor by remember { mutableStateOf<Conversation?>(null) }
    val haptic = LocalHapticFeedback.current

    val drawerShape = RoundedCornerShape(topEnd = 34.dp, bottomEnd = 34.dp)
    ModalDrawerSheet(
        modifier = modifier.cortexGlass(
            shape = drawerShape,
            tint = CortexTheme.colors.glassStrong
        ),
        drawerShape = drawerShape,
        drawerContainerColor = Color.Transparent
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CortexMark(Modifier.size(44.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isGlobalMode) assistantName else activeWorkspace?.name ?: assistantName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (!isGlobalMode && activeWorkspace?.description?.isNotBlank() == true) {
                        activeWorkspace.description
                    } else {
                        "Centro de conversaciones"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // New Chat button
        Button(
            onClick = onNewChat,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Nuevo chat")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Conversations list
        val grouped = groupConversationsByDate(conversations)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            grouped.forEach { (label, convs) ->
                item {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                items(convs, key = { it.id }) { conversation ->
                    val isActive = conversation.id == activeConversationId
                    val bgColor = if (isActive)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                    val contentColor = if (isActive)
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .combinedClickable(
                                onClick = { onConversationClick(conversation.id) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showOptionsFor = conversation
                                }
                            ),
                        color = bgColor,
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = contentColor
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = conversation.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = contentColor,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        // Bottom items
        if (!isGlobalMode) {
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Logout, contentDescription = null) },
                label = { Text("Salir del workspace") },
                selected = false,
                onClick = onGoToGlobal,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Folder, contentDescription = null) },
            label = { Text("Workspaces") },
            selected = false,
            onClick = onWorkspacesClick,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.settings_title_short)) },
            selected = false,
            onClick = onSettingsClick,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Long-press options menu
    showOptionsFor?.let { conversation ->
        AlertDialog(
            onDismissRequest = { showOptionsFor = null },
            title = {
                Text(
                    text = conversation.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showOptionsFor = null
                            conversationToRename = conversation.id to conversation.title
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("Renombrar", modifier = Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = {
                            showOptionsFor = null
                            conversationToDelete = conversation.id
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("Eliminar", modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showOptionsFor = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Delete confirmation dialog
    conversationToDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = { Text("Eliminar conversacion") },
            text = { Text("Se eliminara esta conversacion y todos sus mensajes.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteConversation(id)
                    conversationToDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    // Rename dialog
    conversationToRename?.let { (id, currentTitle) ->
        LaunchedEffect(id) { renameText = currentTitle }
        AlertDialog(
            onDismissRequest = { conversationToRename = null },
            title = { Text("Renombrar conversacion") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameConversation(id, renameText)
                    conversationToRename = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { conversationToRename = null }) { Text("Cancelar") }
            }
        )
    }
}

private fun groupConversationsByDate(conversations: List<Conversation>): List<Pair<String, List<Conversation>>> {
    if (conversations.isEmpty()) return emptyList()
    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val todayStart = cal.timeInMillis
    val sevenDaysAgo = todayStart - TimeUnit.DAYS.toMillis(7)

    val today = mutableListOf<Conversation>()
    val lastWeek = mutableListOf<Conversation>()
    val older = mutableListOf<Conversation>()

    conversations.forEach { c ->
        when {
            c.updatedAt >= todayStart -> today.add(c)
            c.updatedAt >= sevenDaysAgo -> lastWeek.add(c)
            else -> older.add(c)
        }
    }

    return buildList {
        if (today.isNotEmpty()) add("Hoy" to today)
        if (lastWeek.isNotEmpty()) add("Ultimos 7 dias" to lastWeek)
        if (older.isNotEmpty()) add("Anteriores" to older)
    }
}
