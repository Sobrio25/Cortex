package com.aiagents.app.presentation.tool_results

import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolFunction
import com.aiagents.app.domain.model.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ToolExperiencePolicyTest {
    private val spanish = Locale.forLanguageTag("es")

    @Test
    fun `protocol turn becomes one assistant product row`() {
        val call = toolCall("web-1", "web_search", """{"query":"Cortex"}""")
        val messages = listOf(
            Message(id = 1, role = MessageRole.USER, content = "Investiga Cortex"),
            Message(id = 2, role = MessageRole.ASSISTANT, content = "", toolCalls = listOf(call)),
            Message(
                id = 3,
                role = MessageRole.TOOL,
                content = "resultado interno",
                toolResults = listOf(ToolResult("web-1", "web_search", searchOutput()))
            ),
            Message(id = 4, role = MessageRole.ASSISTANT, content = "Aquí está el resumen.")
        )

        val visible = ToolExperiencePolicy.prepareVisible(messages, spanish)

        assertEquals(2, visible.size)
        assertEquals(MessageRole.USER, visible[0].role)
        assertEquals("Aquí está el resumen.", visible[1].content)
        assertEquals(listOf(call), visible[1].toolCalls)
        assertEquals("web_search", visible[1].toolResults.single().name)
        assertFalse(visible.any { it.role == MessageRole.TOOL })
    }

    @Test
    fun `web result exposes structured deduplicated sources`() {
        val message = messageWithResult(
            toolCall("web-1", "web_search", """{"query":"Kotlin"}"""),
            ToolResult("web-1", "web_search", searchOutput())
        )

        val sources = ToolExperiencePolicy.from(message, spanish).sources

        assertEquals(2, sources.size)
        assertEquals("Documentación de Kotlin", sources[0].title)
        assertEquals("kotlinlang.org", sources[0].domain)
        assertEquals("https://kotlinlang.org/docs/home.html", sources[0].url)
        assertTrue(sources[0].snippet.contains("Lenguaje"))
    }

    @Test
    fun `permission and failure states are explicit`() {
        val waiting = messageWithResult(
            toolCall("device-1", "device_control", "{}"),
            ToolResult(
                "device-1",
                "device_control",
                "Se necesita permiso para modificar ajustes del sistema. Se abrió la pantalla de permisos para que el usuario lo conceda."
            )
        )
        val denied = messageWithResult(
            toolCall("shell-1", "execute_command", "{}"),
            ToolResult("shell-1", "execute_command", "Permission denied: command blocked")
        )

        val waitingReceipt = ToolExperiencePolicy.from(waiting, spanish).receipts.single()
        val deniedReceipt = ToolExperiencePolicy.from(denied, spanish).receipts.single()

        assertEquals(ToolActionReceipt.Status.NEEDS_USER, waitingReceipt.status)
        assertEquals(ToolActionReceipt.Permission.USER_ACTION_REQUIRED, waitingReceipt.permission)
        assertEquals(ToolActionReceipt.Status.FAILED, deniedReceipt.status)
        assertEquals(ToolActionReceipt.Permission.DENIED, deniedReceipt.permission)
    }

    @Test
    fun `generated file becomes typed artifact`() {
        val call = toolCall("file-1", "write_file", """{"file_name":"src/Main.kt","content":"fun main() = Unit"}""")
        val experience = ToolExperiencePolicy.from(
            messageWithResult(
                call,
                ToolResult("file-1", "write_file", "Archivo 'src/Main.kt' creado/actualizado exitosamente (17 bytes)")
            ),
            spanish
        )

        val artifact = experience.artifacts.single()
        assertEquals("src/Main.kt", artifact.name)
        assertEquals(ArtifactPresentation.Kind.CODE, artifact.kind)
        assertNull(artifact.url)
    }

    @Test
    fun `cloud document becomes openable remote artifact`() {
        val call = toolCall("doc-1", "gdrive_create_doc", """{"title":"Plan 90 días"}""")
        val artifact = ToolExperiencePolicy.from(
            messageWithResult(
                call,
                ToolResult("doc-1", "gdrive_create_doc", "Documento creado: \"Plan 90 días\"\nID: abc\nURL: https://docs.google.com/document/d/abc")
            ),
            spanish
        ).artifacts.single()

        assertEquals(ArtifactPresentation.Kind.DOCUMENT, artifact.kind)
        assertEquals("https://docs.google.com/document/d/abc", artifact.url)
    }

    @Test
    fun `undo is offered only when supported inverse has required identifier`() {
        val reminder = ToolExperiencePolicy.from(
            messageWithResult(
                toolCall("reminder-1", "set_reminder", "{}"),
                ToolResult("reminder-1", "set_reminder", "Recordatorio creado (ID: 42)\nProgramado para: mañana")
            ),
            spanish
        ).receipts.single()
        val file = ToolExperiencePolicy.from(
            messageWithResult(
                toolCall("file-1", "write_file", "{}"),
                ToolResult("file-1", "write_file", "Archivo creado exitosamente")
            ),
            spanish
        ).receipts.single()

        assertNotNull(reminder.undo)
        assertEquals("cancel_reminder", reminder.undo?.toolName)
        assertEquals("{\"reminder_id\":42}", reminder.undo?.arguments)
        assertNull(file.undo)
    }

    private fun messageWithResult(call: ToolCall, result: ToolResult) = Message(
        role = MessageRole.ASSISTANT,
        content = "",
        toolCalls = listOf(call),
        toolResults = listOf(result)
    )

    private fun toolCall(id: String, name: String, arguments: String) = ToolCall(
        id = id,
        function = ToolFunction(name, arguments)
    )

    private fun searchOutput() = """
        Proveedor: Nativo (DuckDuckGo HTML)
        Resultados de búsqueda para: "Kotlin"

        1. **Documentación de Kotlin**
           URL: https://kotlinlang.org/docs/home.html
           Lenguaje moderno para JVM y multiplataforma.

        2. **Kotlin en GitHub**
           URL: https://github.com/JetBrains/kotlin
           Código fuente del compilador.

        Repetida: https://kotlinlang.org/docs/home.html
    """.trimIndent()
}
