package com.aiagents.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRouteTest {
    @Test
    fun `static destinations have unique stable routes`() {
        val routes = listOf(
            Screen.Onboarding.route,
            Screen.Chat.route,
            Screen.Workspaces.route,
            Screen.Settings.route,
            Screen.Subscription.route,
            Screen.Agents.route,
            Screen.Providers.route,
            Screen.DefaultChatModel.route,
            Screen.LocalModels.route,
            Screen.MCP.route,
            Screen.Memory.route,
            Screen.Skills.route,
            Screen.ScheduledTasks.route,
            Screen.Assistant.route,
            Screen.Voice.route,
            Screen.Capabilities.route,
            Screen.GoogleWorkspace.route
        )

        assertEquals(routes.size, routes.toSet().size)
        assertTrue(routes.none(String::isBlank))
        assertTrue(routes.none { it.startsWith('/') || it.endsWith('/') })
    }

    @Test
    fun `conversation and workspace route builders satisfy their nav patterns`() {
        assertEquals("chat/{conversationId}", Screen.ChatWithConversation.route)
        assertEquals("chat/42", Screen.ChatWithConversation.createRoute(42))
        assertEquals("workspace/{workspaceId}", Screen.WorkspaceDetail.route)
        assertEquals("workspace/99", Screen.WorkspaceDetail.createRoute(99))

        assertFalse(Screen.ChatWithConversation.createRoute(42).contains('{'))
        assertFalse(Screen.WorkspaceDetail.createRoute(99).contains('{'))
    }
}
