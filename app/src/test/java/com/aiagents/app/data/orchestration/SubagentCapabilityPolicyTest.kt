package com.aiagents.app.data.orchestration

import com.aiagents.app.data.terminal.DelegationToolHandler
import com.aiagents.app.data.terminal.GoogleWorkspaceToolHandler
import com.aiagents.app.data.terminal.ToolSearchHandler
import com.aiagents.app.domain.model.SubagentRole
import com.aiagents.app.domain.model.SubagentToolPermission
import com.aiagents.app.domain.model.SubagentWorkspacePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentCapabilityPolicyTest {
    @Test
    fun readOnlyLeafCannotWriteSpawnOrPerformBackgroundSideEffects() {
        val tools = SubagentCapabilityPolicy.allowedTools(
            SubagentWorkspacePolicy.READ_ONLY_SHARED,
            SubagentRole.LEAF,
            depth = 1,
            maxDepth = 3
        )

        assertTrue("read_text_file" in tools)
        assertTrue("skill_list" in tools)
        assertTrue("skill_view" in tools)
        assertFalse("write_file" in tools)
        assertFalse("execute_command" in tools)
        assertFalse(DelegationToolHandler.TOOL_NAME in tools)
        assertFalse("control_app" in tools)
        assertFalse("schedule_task" in tools)
    }

    @Test
    fun orchestratorCanSpawnOnlyBelowDepthLimit() {
        val belowLimit = SubagentCapabilityPolicy.allowedTools(
            SubagentWorkspacePolicy.WRITE_EXCLUSIVE,
            SubagentRole.ORCHESTRATOR,
            depth = 2,
            maxDepth = 3
        )
        val atLimit = SubagentCapabilityPolicy.allowedTools(
            SubagentWorkspacePolicy.WRITE_EXCLUSIVE,
            SubagentRole.ORCHESTRATOR,
            depth = 3,
            maxDepth = 3
        )

        assertTrue(DelegationToolHandler.TOOL_NAME in belowLimit)
        assertFalse(DelegationToolHandler.TOOL_NAME in atLimit)
        assertEquals(
            SubagentToolPermission.ASK,
            SubagentCapabilityPolicy.permissions(belowLimit)["execute_command"]
        )
    }

    @Test
    fun googleDocsCapabilityGrantsOnlyNarrowWorkspaceTools() {
        val tools = SubagentCapabilityPolicy.allowedTools(
            SubagentWorkspacePolicy.READ_ONLY_SHARED,
            SubagentRole.LEAF,
            depth = 1,
            maxDepth = 3,
            requestedCapabilities = setOf(SubagentCapabilityPolicy.GOOGLE_DOCS)
        )

        assertTrue(GoogleWorkspaceToolHandler.TOOL_GWS_DOCS_CREATE in tools)
        assertTrue(GoogleWorkspaceToolHandler.TOOL_GWS_DOCS_READ in tools)
        assertFalse(GoogleWorkspaceToolHandler.TOOL_GWS_GMAIL_SEND in tools)
        assertFalse(GoogleWorkspaceToolHandler.TOOL_GWS_EXECUTE in tools)
    }

    @Test
    fun integrationActionsAreDetectedButHowToQuestionsStayInParent() {
        assertTrue(
            SubagentCapabilityPolicy.shouldAutoDelegateIntegration(
                "Crea un documento en Google Docs con este contenido"
            )
        )
        assertTrue(
            SubagentCapabilityPolicy.shouldAutoDelegateIntegration(
                text = "Sí, hazlo",
                context = "La tarea pendiente es crear un archivo en Google Docs"
            )
        )
        assertFalse(
            SubagentCapabilityPolicy.shouldAutoDelegateIntegration(
                "¿Cómo puedo crear un documento en Google Docs?"
            )
        )
    }

    @Test
    fun cortexCoreToolSetDoesNotExposeGoogleWorkspaceSchemas() {
        assertTrue(
            ToolSearchHandler.CORE_TOOL_NAMES.none { toolName ->
                toolName in GoogleWorkspaceToolHandler.ALL_TOOL_NAMES
            }
        )
    }
}
