package com.aiagents.app.presentation.assistant

import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CortexAssistantResponsePolicyTest {
    @Test
    fun `weather tool is the answer and redundant model prose is removed`() {
        val user = Message(role = MessageRole.USER, content = "¿Qué clima habrá mañana?", timestamp = 1)
        val weather = Message(
            role = MessageRole.TOOL,
            content = "raw",
            timestamp = 2,
            toolResults = listOf(
                ToolResult(
                    toolCallId = "weather-1",
                    name = "weather_forecast",
                    content = """
                        🌤️ Pronóstico para **Ixtapaluca**
                        **Miércoles 15**
                        🌦️ Llovizna moderada
                        Mín. 13°C · máx. 22°C · lluvia 76%
                        Humedad promedio: 70%
                        <!--WEATHER_DATA:{"type":"forecast"}-->
                    """.trimIndent()
                )
            )
        )
        val redundant = Message(
            role = MessageRole.ASSISTANT,
            content = "Mañana habrá llovizna y quizá quieras llevar paraguas.",
            timestamp = 3
        )

        val visible = CortexAssistantResponsePolicy.prepareVisible(
            listOf(user, weather, redundant),
            Locale.forLanguageTag("es")
        )

        assertEquals(listOf(MessageRole.USER, MessageRole.TOOL), visible.map(Message::role))
        assertTrue(visible.last().content.contains("lluvia 76%"))
        assertFalse(visible.last().content.contains("Humedad"))
        assertTrue(visible.last().content.split(Regex("\\s+")).size <= 18)
    }

    @Test
    fun `reminder confirmation keeps only the scheduled time`() {
        val reminder = Message(
            role = MessageRole.TOOL,
            content = "raw",
            toolResults = listOf(
                ToolResult(
                    toolCallId = "reminder-1",
                    name = "set_reminder",
                    content = "Recordatorio creado (ID: 7)\nMensaje: Agua\nProgramado para: 15/07/2026 09:30"
                )
            )
        )

        val visible = CortexAssistantResponsePolicy.prepareVisible(
            listOf(reminder),
            Locale.forLanguageTag("es")
        )

        assertEquals("Recordatorio creado para 15/07/2026 09:30.", visible.single().content)
    }
}
