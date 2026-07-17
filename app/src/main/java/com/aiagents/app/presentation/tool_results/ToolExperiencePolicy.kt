package com.aiagents.app.presentation.tool_results

import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolResult
import com.google.gson.JsonParser
import java.net.URI
import java.util.Locale

data class ToolActionReceipt(
    val id: String,
    val toolName: String,
    val title: String,
    val summary: String,
    val status: Status,
    val permission: Permission = Permission.NOT_APPLICABLE,
    val undo: UndoAction? = null
) {
    enum class Status { RUNNING, COMPLETED, NEEDS_USER, FAILED }
    enum class Permission { NOT_APPLICABLE, USER_ACTION_REQUIRED, DENIED }
}

/**
 * An inverse operation that is safe to expose only when the original result contains the
 * identifier required by an already-supported tool. UI callers decide whether they have an
 * execution path; cards never pretend that an undo is available without one.
 */
data class UndoAction(
    val toolName: String,
    val arguments: String,
    val confirmation: String
)

data class ResearchSource(
    val title: String,
    val url: String,
    val domain: String,
    val snippet: String = ""
)

data class ArtifactPresentation(
    val toolCallId: String,
    val name: String,
    val kind: Kind,
    val detail: String,
    val path: String? = null,
    val url: String? = null
) {
    enum class Kind { CODE, DATA, DOCUMENT, PRESENTATION, IMAGE, REMOTE, FILE }
}

data class ToolExperience(
    val receipts: List<ToolActionReceipt>,
    val sources: List<ResearchSource>,
    val artifacts: List<ArtifactPresentation>
)

/** Pure conversion from persisted tool history to stable, user-facing product concepts. */
object ToolExperiencePolicy {
    private const val CHECKPOINT_PREFIX = "[CORTEX_CONTEXT_CHECKPOINT_V1]"
    private val researchTools = setOf(
        "web_search", "web_fetch", "duckduckgo_search", "brave_web_search",
        "serpapi_search", "wikipedia_search", "arxiv_search"
    )

    private val artifactTools = setOf(
        "write_file", "pptx_save", "finance_export_csv", "gdrive_create_doc",
        "gws_docs_create", "gws_sheets_create", "gws_slides_create",
        "gws_drive_upload", "notion_create_page", "github_create_update_file",
        "canva_create_design", "canva_export_design"
    )

    private val failurePattern = Regex(
        """(?i)^(error|failed|failure|fallo|timeout|par[aá]metro .+ requerido|herramienta .+ desconocida)|""" +
            """\b(no autorizado|unauthorized|forbidden|access denied|archivo no encontrado|not found)\b"""
    )
    private val deniedPattern = Regex("""(?i)\b(permiso denegado|permission denied|no autorizado|forbidden)\b""")
    private val userActionPattern = Regex(
        """(?i)\b(debe confirmar|usuario debe|user must|requiere permiso|se necesita permiso|""" +
            """pantalla de permisos|di[aá]logo .+ abierto|selector de android abierto)\b"""
    )
    private val urlPattern = Regex("""https?://[^\s<>\])}]+""", RegexOption.IGNORE_CASE)
    private val markdownLinkPattern = Regex("""\[([^]]+)]\((https?://[^\s)]+)\)""", RegexOption.IGNORE_CASE)
    private val titledLinePattern = Regex("""^\s*\d+[.)]\s*\*\*(.+?)\*\*\s*$""")

    /**
     * Produces one product row for a complete tool turn. Provider protocol messages remain
     * persisted, but calls and results are attached to the final assistant response so the UI
     * can show a single timeline instead of separate, duplicated debug bubbles.
     */
    fun prepareVisible(messages: List<Message>, locale: Locale): List<Message> {
        val turns = mutableListOf<List<Message>>()
        var current = mutableListOf<Message>()
        messages.forEach { message ->
            if (message.role == MessageRole.USER && current.isNotEmpty()) {
                turns += current
                current = mutableListOf()
            }
            current += message
        }
        if (current.isNotEmpty()) turns += current

        val merged = turns.flatMap(::mergeTurn).filterNot { message ->
            message.role == MessageRole.SYSTEM && message.content.startsWith(CHECKPOINT_PREFIX)
        }
        return DirectToolResultPolicy.prepareVisible(merged, locale)
    }

    private fun mergeTurn(turn: List<Message>): List<Message> {
        val calls = turn.flatMap { it.toolCalls }.distinctBy { it.id }
        val results = turn.flatMap { it.toolResults }.distinctBy { it.toolCallId }
        val visible = turn.filterNot { message ->
            message.role == MessageRole.TOOL ||
                (message.role == MessageRole.ASSISTANT && message.toolCalls.isNotEmpty())
        }.toMutableList()
        if (calls.isEmpty() && results.isEmpty()) return visible

        val targetIndex = visible.indexOfLast { it.role == MessageRole.ASSISTANT }
        if (targetIndex >= 0) {
            val target = visible[targetIndex]
            visible[targetIndex] = target.copy(
                toolCalls = (target.toolCalls + calls).distinctBy { it.id },
                toolResults = (target.toolResults + results).distinctBy { it.toolCallId }
            )
        } else {
            val protocolAnchor = turn.lastOrNull { it.role == MessageRole.ASSISTANT && it.toolCalls.isNotEmpty() }
                ?: turn.lastOrNull { it.role == MessageRole.TOOL }
            if (protocolAnchor != null) {
                visible += protocolAnchor.copy(
                    role = MessageRole.ASSISTANT,
                    content = "",
                    toolCalls = calls,
                    toolResults = results
                )
            }
        }
        return visible
    }

    fun from(message: Message, locale: Locale): ToolExperience {
        val callsById = message.toolCalls.associateBy { it.id }
        val resultsById = message.toolResults.associateBy { it.toolCallId }
        val orderedIds = buildList {
            message.toolCalls.forEach { if (it.id !in this) add(it.id) }
            message.toolResults.forEach { if (it.toolCallId !in this) add(it.toolCallId) }
        }

        return ToolExperience(
            receipts = orderedIds.mapNotNull { id ->
                val call = callsById[id]
                val result = resultsById[id]
                val toolName = call?.function?.name ?: result?.name ?: return@mapNotNull null
                receipt(id, toolName, result, locale)
            },
            sources = message.toolResults
                .filter { it.name in researchTools }
                .flatMap { result -> sources(result, callsById[result.toolCallId]) }
                .distinctBy { it.url }
                .take(8),
            artifacts = message.toolResults.mapNotNull { result ->
                if (result.name !in artifactTools) return@mapNotNull null
                artifact(result, callsById[result.toolCallId], locale)
            }.distinctBy { it.url ?: it.path ?: it.name }
        )
    }

    private fun receipt(
        id: String,
        toolName: String,
        result: ToolResult?,
        locale: Locale
    ): ToolActionReceipt {
        val content = result?.content.orEmpty().trim()
        val permission = when {
            deniedPattern.containsMatchIn(content) -> ToolActionReceipt.Permission.DENIED
            userActionPattern.containsMatchIn(content) -> ToolActionReceipt.Permission.USER_ACTION_REQUIRED
            else -> ToolActionReceipt.Permission.NOT_APPLICABLE
        }
        val status = when {
            result == null -> ToolActionReceipt.Status.RUNNING
            permission == ToolActionReceipt.Permission.DENIED -> ToolActionReceipt.Status.FAILED
            failurePattern.containsMatchIn(content) -> ToolActionReceipt.Status.FAILED
            permission == ToolActionReceipt.Permission.USER_ACTION_REQUIRED -> ToolActionReceipt.Status.NEEDS_USER
            else -> ToolActionReceipt.Status.COMPLETED
        }
        return ToolActionReceipt(
            id = id,
            toolName = toolName,
            title = toolLabel(toolName, locale),
            summary = content.userFacingSummary(locale),
            status = status,
            permission = permission,
            undo = if (status == ToolActionReceipt.Status.COMPLETED) undoFor(toolName, content, locale) else null
        )
    }

    private fun sources(result: ToolResult, call: ToolCall?): List<ResearchSource> {
        val lines = result.content.lines()
        val collected = mutableListOf<ResearchSource>()

        markdownLinkPattern.findAll(result.content).forEach { match ->
            addSource(collected, match.groupValues[1].cleanTitle(), match.groupValues[2], "")
        }

        lines.forEachIndexed { index, line ->
            urlPattern.findAll(line).forEach { match ->
                val url = match.value.trimUrlPunctuation()
                val title = when {
                    line.contains("URL:", ignoreCase = true) -> lines
                        .subList((index - 2).coerceAtLeast(0), index)
                        .asReversed()
                        .firstNotNullOfOrNull { titledLinePattern.find(it)?.groupValues?.get(1) }
                    else -> line.substringBefore(url).substringAfterLast(':').cleanTitle().takeIf { it.length > 2 }
                } ?: domainOf(url)
                val snippet = lines.drop(index + 1)
                    .firstOrNull { candidate ->
                        val trimmed = candidate.trim()
                        trimmed.isNotBlank() &&
                            !trimmed.startsWith("URL:", ignoreCase = true) &&
                            !titledLinePattern.matches(trimmed) &&
                            !urlPattern.containsMatchIn(trimmed)
                    }
                    ?.cleanSnippet()
                    .orEmpty()
                addSource(collected, title, url, snippet)
            }
        }

        if (collected.isEmpty() && result.name == "web_fetch") {
            argument(call, "url")?.let { url ->
                val title = lines.firstOrNull { it.isNotBlank() }?.cleanTitle().orEmpty().ifBlank { domainOf(url) }
                addSource(collected, title, url, lines.drop(1).firstOrNull().orEmpty().cleanSnippet())
            }
        }
        return collected
    }

    private fun addSource(target: MutableList<ResearchSource>, title: String, rawUrl: String, snippet: String) {
        val url = rawUrl.trimUrlPunctuation()
        if (!isSafeWebUrl(url) || target.any { it.url == url }) return
        target += ResearchSource(
            title = title.ifBlank { domainOf(url) }.take(120),
            url = url,
            domain = domainOf(url),
            snippet = snippet.take(220)
        )
    }

    private fun artifact(result: ToolResult, call: ToolCall?, locale: Locale): ArtifactPresentation? {
        val content = result.content
        if (failurePattern.containsMatchIn(content.trim())) return null

        val path = extractPath(content)
        val url = urlPattern.find(content)?.value?.trimUrlPunctuation()?.takeIf(::isSafeWebUrl)
        val argumentName = argument(call, "file_name")
            ?: argument(call, "filename")?.let { name ->
                if (result.name == "pptx_save" && !name.endsWith(".pptx", ignoreCase = true)) "$name.pptx" else name
            }
        val outputName = path?.substringAfterLast('/')
            ?: Regex("""(?i)(?:archivo|file):?\*{0,2}\s*['\"]?([^'\"\n]+?\.[a-z0-9]{1,8})['\"]?(?:\s|$)""")
                .find(content)?.groupValues?.get(1)?.trim()
            ?: argumentName
            ?: argument(call, "title")
            ?: url?.let(::domainOf)
            ?: return null
        val kind = artifactKind(outputName, result.name, url)
        val detail = when (kind) {
            ArtifactPresentation.Kind.CODE -> localized(locale, "Archivo de código", "Code file")
            ArtifactPresentation.Kind.DATA -> localized(locale, "Datos estructurados", "Structured data")
            ArtifactPresentation.Kind.DOCUMENT -> localized(locale, "Documento", "Document")
            ArtifactPresentation.Kind.PRESENTATION -> localized(locale, "Presentación", "Presentation")
            ArtifactPresentation.Kind.IMAGE -> localized(locale, "Imagen", "Image")
            ArtifactPresentation.Kind.REMOTE -> localized(locale, "Artefacto en la nube", "Cloud artifact")
            ArtifactPresentation.Kind.FILE -> localized(locale, "Archivo generado", "Generated file")
        }
        return ArtifactPresentation(result.toolCallId, outputName.cleanTitle(), kind, detail, path, url)
    }

    private fun extractPath(content: String): String? {
        val labeled = Regex("""(?im)(?:ruta|path):?\*{0,2}\s*([^\n]+)""")
            .find(content)?.groupValues?.get(1)?.trim()?.removeSurrounding("`")
        if (!labeled.isNullOrBlank() && labeled.startsWith('/')) return labeled
        return Regex("""(?:^|\s)(/[^\n]+?\.[a-zA-Z0-9]{1,8})(?=\s|$|\))""")
            .find(content)?.groupValues?.get(1)?.trim()
    }

    private fun artifactKind(name: String, toolName: String, url: String?): ArtifactPresentation.Kind {
        val extension = name.substringAfterLast('.', "").lowercase()
        return when {
            extension in setOf("kt", "java", "py", "js", "ts", "tsx", "jsx", "go", "rs", "swift", "sh", "html", "css", "sql") -> ArtifactPresentation.Kind.CODE
            extension in setOf("csv", "json", "xml", "yaml", "yml", "xlsx", "xls") -> ArtifactPresentation.Kind.DATA
            extension in setOf("pptx", "ppt", "key") || "slides" in toolName -> ArtifactPresentation.Kind.PRESENTATION
            extension in setOf("png", "jpg", "jpeg", "gif", "webp", "svg") -> ArtifactPresentation.Kind.IMAGE
            extension in setOf("pdf", "doc", "docx", "md", "txt", "rtf") ||
                "docs" in toolName || toolName.endsWith("_doc") -> ArtifactPresentation.Kind.DOCUMENT
            url != null -> ArtifactPresentation.Kind.REMOTE
            else -> ArtifactPresentation.Kind.FILE
        }
    }

    private fun undoFor(toolName: String, content: String, locale: Locale): UndoAction? {
        if (toolName != "set_reminder") return null
        val reminderId = Regex("""(?i)\bID:\s*(\d+)""").find(content)?.groupValues?.get(1) ?: return null
        return UndoAction(
            toolName = "cancel_reminder",
            arguments = "{\"reminder_id\":$reminderId}",
            confirmation = localized(locale, "Cancelar este recordatorio", "Cancel this reminder")
        )
    }

    private fun toolLabel(name: String, locale: Locale): String {
        val pair = when {
            name in researchTools -> "Investigación" to "Research"
            name == "write_file" -> "Guardar archivo" to "Save file"
            name.startsWith("read_") || name.contains("_read") -> "Leer información" to "Read information"
            name.startsWith("pptx_") -> "Crear presentación" to "Build presentation"
            name.startsWith("gws_") || name.startsWith("gdrive_") -> "Google Workspace" to "Google Workspace"
            name.startsWith("github_") -> "GitHub" to "GitHub"
            name.startsWith("notion_") -> "Notion" to "Notion"
            name.startsWith("slack_") -> "Slack" to "Slack"
            name.startsWith("finance_") -> "Finanzas" to "Finance"
            name.contains("calendar") -> "Calendario" to "Calendar"
            name.contains("reminder") || name == "set_alarm" -> "Recordatorio" to "Reminder"
            name == "execute_command" -> "Terminal" to "Terminal"
            else -> name.replace('_', ' ').replaceFirstChar { it.titlecase(locale) } to
                name.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ENGLISH) }
        }
        return localized(locale, pair.first, pair.second)
    }

    private fun argument(call: ToolCall?, name: String): String? = try {
        call?.function?.arguments?.let(JsonParser::parseString)?.asJsonObject?.get(name)
            ?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun String.userFacingSummary(locale: Locale): String {
        if (isBlank()) return localized(locale, "Esperando resultado", "Waiting for result")
        val first = lineSequence().map(String::trim).firstOrNull(String::isNotBlank).orEmpty()
            .replace("**", "").replace(Regex("\\s+"), " ")
        return if (first.length <= 140) first else first.take(137).trimEnd() + "…"
    }

    private fun String.cleanTitle(): String = trim()
        .removePrefix("**").removeSuffix("**")
        .removePrefix("-").trim()
        .replace(Regex("^\\d+[.)]\\s*"), "")
        .replace(Regex("\\s+"), " ")

    private fun String.cleanSnippet(): String = trim().removePrefix("-").trim()
        .replace(Regex("^(Fuente|Fecha|Canal|Duración|Proveedor):\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ")

    private fun String.trimUrlPunctuation(): String = trim().trimEnd('.', ',', ';', ':', '!', '?', '\'', '"')

    private fun domainOf(url: String): String = try {
        URI(url).host?.removePrefix("www.").orEmpty().ifBlank { url }
    } catch (_: Exception) {
        url
    }

    private fun isSafeWebUrl(url: String): Boolean = try {
        URI(url).scheme?.lowercase() in setOf("http", "https") && !URI(url).host.isNullOrBlank()
    } catch (_: Exception) {
        false
    }

    private fun localized(locale: Locale, spanish: String, english: String): String =
        if (locale.language.equals("es", ignoreCase = true)) spanish else english
}
