package com.aiagents.app.presentation.workspace_detail

import android.Manifest
import com.aiagents.app.data.terminal.SystemAppToolHandler
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantActionPermissionPolicyTest {
    @Test
    fun `primitive params do not crash permission precheck`() {
        val permissions = AssistantActionPermissionPolicy.requiredPermissions(
            toolName = SystemAppToolHandler.TOOL_NAME,
            arguments = """{"action":"set_volume","params":"volume_level=50"}"""
        )

        assertEquals(emptyList<String>(), permissions)
    }

    @Test
    fun `malformed call params do not request permissions`() {
        val permissions = AssistantActionPermissionPolicy.requiredPermissions(
            toolName = SystemAppToolHandler.TOOL_NAME,
            arguments = """{"action":"call_phone","params":"5551234"}"""
        )

        assertEquals(emptyList<String>(), permissions)
    }

    @Test
    fun `structured phone number cannot crash or request permissions`() {
        val permissions = AssistantActionPermissionPolicy.requiredPermissions(
            toolName = SystemAppToolHandler.TOOL_NAME,
            arguments = """{"action":"call_phone","params":{"phone_number":{"raw":"5551234"}}}"""
        )

        assertEquals(emptyList<String>(), permissions)
    }

    @Test
    fun `direct phone call only requires phone permission`() {
        val permissions = AssistantActionPermissionPolicy.requiredPermissions(
            toolName = SystemAppToolHandler.TOOL_NAME,
            arguments = """{"action":"call_phone","params":{"phone_number":"5551234"}}"""
        )

        assertEquals(listOf(Manifest.permission.CALL_PHONE), permissions)
    }

    @Test
    fun `contact phone call requires phone and contacts permissions`() {
        val permissions = AssistantActionPermissionPolicy.requiredPermissions(
            toolName = SystemAppToolHandler.TOOL_NAME,
            arguments = """{"action":"call_phone","params":{"contact":"Ada"}}"""
        )

        assertEquals(
            listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS),
            permissions
        )
    }

    @Test
    fun `valid WhatsApp draft requires contacts permission`() {
        val permissions = AssistantActionPermissionPolicy.requiredPermissions(
            toolName = SystemAppToolHandler.TOOL_NAME,
            arguments = """{"action":"prepare_whatsapp_message","params":{"contact":"Ada","message":"Hola"}}"""
        )

        assertEquals(listOf(Manifest.permission.READ_CONTACTS), permissions)
    }
}
