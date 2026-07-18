package com.aiagents.app.presentation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aiagents.app.presentation.agents.AgentsScreen
import com.aiagents.app.presentation.assistant.AssistantSettingsScreen
import com.aiagents.app.presentation.capabilities.CapabilitiesScreen
import com.aiagents.app.presentation.chat.ChatScreen
import com.aiagents.app.presentation.drawer.AppDrawerContent
import com.aiagents.app.presentation.drawer.DrawerViewModel
import com.aiagents.app.presentation.local_models.LocalModelsScreen
import com.aiagents.app.presentation.mcp.MCPScreen
import com.aiagents.app.presentation.memory.MemoryScreen
import com.aiagents.app.presentation.onboarding.OnboardingScreen
import com.aiagents.app.presentation.onboarding.OnboardingViewModel
import com.aiagents.app.presentation.providers.ProvidersScreen
import com.aiagents.app.presentation.scheduled_tasks.ScheduledTasksScreen
import com.aiagents.app.presentation.settings.GoogleWorkspaceSettingsScreen
import com.aiagents.app.presentation.settings.DiagnosticsScreen
import com.aiagents.app.presentation.settings.DefaultChatModelScreen
import com.aiagents.app.presentation.settings.SettingsScreen
import com.aiagents.app.presentation.subscription.SubscriptionScreen
import com.aiagents.app.presentation.skills.SkillsScreen
import com.aiagents.app.presentation.workspaces.WorkspacesScreen
import com.aiagents.app.presentation.workspace_detail.WorkspaceDetailScreen
import com.aiagents.app.presentation.voice.VoiceSettingsScreen
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Chat : Screen("chat")
    data object ChatWithConversation : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: Long) = "chat/$conversationId"
    }
    data object Workspaces : Screen("workspaces")
    data object Settings : Screen("settings")
    data object Subscription : Screen("settings/subscription")
    data object Agents : Screen("settings/agents")
    data object Providers : Screen("settings/providers")
    data object DefaultChatModel : Screen("settings/default_chat_model")
    data object LocalModels : Screen("settings/local_models")
    data object MCP : Screen("settings/mcp")
    data object Memory : Screen("settings/memory")
    data object Skills : Screen("settings/skills")
    data object ScheduledTasks : Screen("settings/scheduled_tasks")
    data object Assistant : Screen("settings/assistant")
    data object Voice : Screen("settings/voice")
    data object Capabilities : Screen("settings/capabilities")
    data object GoogleWorkspace : Screen("settings/google_workspace")
    data object Diagnostics : Screen("settings/diagnostics")
    data object WorkspaceDetail : Screen("workspace/{workspaceId}") {
        fun createRoute(workspaceId: Long) = "workspace/$workspaceId"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    scheduledTaskWorkspaceId: Long? = null,
    scheduledTaskConversationId: Long? = null,
    onScheduledTaskDestinationHandled: () -> Unit = {}
) {
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val onboardingCompleted by onboardingViewModel.onboardingCompleted.collectAsState()

    if (!onboardingCompleted) {
        OnboardingFlowContent(onboardingViewModel)
    } else {
        MainAppContent(
            scheduledTaskWorkspaceId = scheduledTaskWorkspaceId,
            scheduledTaskConversationId = scheduledTaskConversationId,
            onScheduledTaskDestinationHandled = onScheduledTaskDestinationHandled
        )
    }
}

@Composable
private fun OnboardingFlowContent(onboardingViewModel: OnboardingViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Onboarding.route
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                viewModel = onboardingViewModel,
                onNavigateToProviders = { navController.navigate(Screen.Providers.route) },
                onNavigateToLocalModels = { navController.navigate(Screen.LocalModels.route) },
                onNavigateToMCP = { navController.navigate(Screen.MCP.route) },
                onNavigateToAgents = { navController.navigate(Screen.Agents.route) },
                onNavigateToWorkspaces = { navController.navigate(Screen.Workspaces.route) },
                onComplete = { onboardingViewModel.completeOnboarding() }
            )
        }
        composable(Screen.Providers.route) {
            ProvidersScreen(
                onNavigateToLocalModels = { navController.navigate(Screen.LocalModels.route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.LocalModels.route) {
            LocalModelsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.MCP.route) {
            MCPScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Agents.route) {
            AgentsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Workspaces.route) {
            WorkspacesScreen(
                onWorkspaceClick = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppContent(
    scheduledTaskWorkspaceId: Long?,
    scheduledTaskConversationId: Long?,
    onScheduledTaskDestinationHandled: () -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val drawerViewModel: DrawerViewModel = hiltViewModel()

    val activeWorkspace by drawerViewModel.activeWorkspace.collectAsState()
    val conversations by drawerViewModel.conversations.collectAsState()
    val activeConversationId by drawerViewModel.activeConversationId.collectAsState()
    val isGlobalMode by drawerViewModel.isGlobalMode.collectAsState()
    val assistantName by drawerViewModel.assistantName.collectAsState()

    LaunchedEffect(scheduledTaskWorkspaceId, scheduledTaskConversationId) {
        val workspaceId = scheduledTaskWorkspaceId ?: return@LaunchedEffect
        val conversationId = scheduledTaskConversationId ?: return@LaunchedEffect
        if (workspaceId <= 0 || conversationId <= 0) return@LaunchedEffect

        drawerViewModel.setActiveWorkspace(workspaceId)
        drawerViewModel.setActiveConversation(conversationId)
        navController.navigate(Screen.ChatWithConversation.createRoute(conversationId)) {
            popUpTo(Screen.Chat.route) { inclusive = true }
            launchSingleTop = true
        }
        onScheduledTaskDestinationHandled()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.48f),
        drawerContent = {
            AppDrawerContent(
                activeWorkspace = activeWorkspace,
                conversations = conversations,
                activeConversationId = activeConversationId,
                isGlobalMode = isGlobalMode,
                assistantName = assistantName,
                onNewChat = {
                    scope.launch { drawerState.close() }
                    drawerViewModel.setActiveConversation(null)
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(Screen.Chat.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onConversationClick = { conversationId ->
                    scope.launch { drawerState.close() }
                    drawerViewModel.setActiveConversation(conversationId)
                    navController.navigate(Screen.ChatWithConversation.createRoute(conversationId)) {
                        popUpTo(Screen.Chat.route) { inclusive = true }
                    }
                },
                onDeleteConversation = { id ->
                    val wasActive = activeConversationId == id
                    drawerViewModel.deleteConversation(id)
                    if (wasActive) {
                        scope.launch { drawerState.close() }
                        navController.navigate(Screen.Chat.route) {
                            popUpTo(Screen.Chat.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onRenameConversation = { id, title ->
                    drawerViewModel.renameConversation(id, title)
                },
                onGoToGlobal = {
                    scope.launch { drawerState.close() }
                    drawerViewModel.goToGlobalMode()
                    drawerViewModel.setActiveConversation(null)
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(Screen.Chat.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onWorkspacesClick = {
                    scope.launch { drawerState.close() }
                    navController.navigate(Screen.Workspaces.route) {
                        launchSingleTop = true
                    }
                },
                onSettingsClick = {
                    scope.launch { drawerState.close() }
                    navController.navigate(Screen.Settings.route) {
                        launchSingleTop = true
                    }
                }
            )
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Chat.route,
            enterTransition = {
                fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                    slideInVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) { it / 4 }
            },
            exitTransition = {
                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow))
            },
            popEnterTransition = {
                fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow))
            },
            popExitTransition = {
                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                    slideOutVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) { it / 4 }
            }
        ) {
            composable(Screen.Chat.route) {
                ChatScreen(
                    conversationId = null,
                    onOpenDrawer = { keyboardController?.hide(); scope.launch { drawerState.open() } },
                    onNewChat = {
                        drawerViewModel.setActiveConversation(null)
                        navController.navigate(Screen.Chat.route) {
                            popUpTo(Screen.Chat.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    drawerViewModel = drawerViewModel
                )
            }
            composable(
                route = Screen.ChatWithConversation.route,
                arguments = listOf(navArgument("conversationId") { type = NavType.LongType })
            ) { backStackEntry ->
                val conversationId = backStackEntry.arguments?.getLong("conversationId")
                ChatScreen(
                    conversationId = conversationId,
                    onOpenDrawer = { keyboardController?.hide(); scope.launch { drawerState.open() } },
                    onNewChat = {
                        drawerViewModel.setActiveConversation(null)
                        navController.navigate(Screen.Chat.route) {
                            popUpTo(Screen.Chat.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    drawerViewModel = drawerViewModel
                )
            }
            composable(Screen.Workspaces.route) {
                WorkspacesScreen(
                    onWorkspaceClick = { workspace ->
                        drawerViewModel.setActiveWorkspace(workspace.id)
                        drawerViewModel.setActiveConversation(null)
                        navController.navigate(Screen.Chat.route) {
                            popUpTo(Screen.Chat.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToSubscription = { navController.navigate(Screen.Subscription.route) },
                    onNavigateToAgents = { navController.navigate(Screen.Agents.route) },
                    onNavigateToProviders = { navController.navigate(Screen.Providers.route) },
                    onNavigateToDefaultChatModel = {
                        navController.navigate(Screen.DefaultChatModel.route)
                    },
                    onNavigateToLocalModels = { navController.navigate(Screen.LocalModels.route) },
                    onNavigateToMemory = { navController.navigate(Screen.Memory.route) },
                    onNavigateToVoice = { navController.navigate(Screen.Voice.route) },
                    onNavigateToCapabilities = {
                        navController.navigate(Screen.Capabilities.route)
                    },
                    onNavigateToScheduledTasks = {
                        navController.navigate(Screen.ScheduledTasks.route)
                    },
                    onNavigateToAssistant = {
                        navController.navigate(Screen.Assistant.route)
                    },
                    onNavigateToGoogleWorkspace = {
                        navController.navigate(Screen.GoogleWorkspace.route)
                    },
                    onNavigateToDiagnostics = {
                        navController.navigate(Screen.Diagnostics.route)
                    }
                )
            }
            composable(Screen.Subscription.route) {
                SubscriptionScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Agents.route) {
                AgentsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Providers.route) {
                ProvidersScreen(
                    onNavigateToLocalModels = { navController.navigate(Screen.LocalModels.route) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.DefaultChatModel.route) {
                DefaultChatModelScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.LocalModels.route) {
                LocalModelsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.MCP.route) {
                MCPScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Memory.route) {
                MemoryScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Skills.route) {
                SkillsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.ScheduledTasks.route) {
                ScheduledTasksScreen(
                    onBack = { navController.popBackStack() },
                    onOpenConversation = { workspaceId, conversationId ->
                        drawerViewModel.setActiveWorkspace(workspaceId)
                        drawerViewModel.setActiveConversation(conversationId)
                        navController.navigate(
                            Screen.ChatWithConversation.createRoute(conversationId)
                        ) {
                            popUpTo(Screen.Chat.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Assistant.route) {
                AssistantSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Voice.route) {
                VoiceSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Capabilities.route) {
                CapabilitiesScreen(
                    onBack = { navController.popBackStack() },
                    onConfigureMcp = { navController.navigate(Screen.MCP.route) }
                )
            }
            composable(Screen.GoogleWorkspace.route) {
                GoogleWorkspaceSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Diagnostics.route) {
                DiagnosticsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.WorkspaceDetail.route,
                arguments = listOf(navArgument("workspaceId") { type = NavType.LongType })
            ) { backStackEntry ->
                val wsId = backStackEntry.arguments?.getLong("workspaceId") ?: 0L
                LaunchedEffect(wsId) {
                    drawerViewModel.setActiveWorkspace(wsId)
                }
                WorkspaceDetailScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
