package com.aiagents.app.presentation.tool_results

import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DirectToolResultPolicyTest {
    private val spanish = Locale.forLanguageTag("es")

    @Test
    fun `calendar result is a typed card payload and replaces model prose`() {
        val result = directMessage(
            "add_calendar_event",
            "Evento agregado exitosamente\nTítulo: Dentista\nInicio: 19/07/2026 10:00"
        )
        val followUp = Message(role = MessageRole.ASSISTANT, content = "Listo, agregué el evento.")

        val visible = DirectToolResultPolicy.prepareVisible(listOf(result, followUp), spanish)
        val payload = DirectToolResultPolicy.primaryPayload(result, spanish)!!

        assertEquals(DirectToolResultPayload.Kind.CALENDAR, payload.kind)
        assertEquals("Evento «Dentista» agregado para 19/07/2026 10:00.", payload.summary)
        assertEquals(1, visible.size)
        assertFalse(visible.single().content.contains("Listo"))
    }

    @Test
    fun `all reminder calendar and device families use the shared typed policy`() {
        val names = listOf(
            "set_reminder", "list_reminders", "cancel_reminder", "set_alarm",
            "read_calendar_events", "add_calendar_event", "device_control"
        )

        names.forEach { name ->
            val payload = DirectToolResultPolicy.primaryPayload(directMessage(name, "Operación completada."), spanish)
            assertTrue("Missing typed payload for $name", payload != null)
        }
    }

    private fun directMessage(name: String, content: String) = Message(
        role = MessageRole.TOOL,
        content = content,
        toolResults = listOf(ToolResult("call-$name", name, content))
    )
}
