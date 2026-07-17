package com.aiagents.app.presentation.chat

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiagents.app.presentation.drawer.DrawerViewModel
import com.aiagents.app.presentation.workspace_detail.WorkspaceDetailScreen

@Composable
fun ChatScreen(
    conversationId: Long?,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    drawerViewModel: DrawerViewModel,
    modifier: Modifier = Modifier
) {
    val activeWorkspaceId by drawerViewModel.activeWorkspaceId.collectAsStateWithLifecycle()

    if (activeWorkspaceId > 0) {
        WorkspaceDetailScreen(
            onBack = onOpenDrawer,
            isDrawerMode = true,
            onOpenDrawer = onOpenDrawer,
            onNewChat = onNewChat,
            conversationId = conversationId
        )
    }
}
