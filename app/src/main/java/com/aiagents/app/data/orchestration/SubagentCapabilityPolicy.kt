package com.aiagents.app.data.orchestration

import com.aiagents.app.data.terminal.CodeExecutionHandler
import com.aiagents.app.data.terminal.DelegationToolHandler
import com.aiagents.app.data.terminal.GoogleDriveToolHandler
import com.aiagents.app.data.terminal.MemoryToolHandler
import com.aiagents.app.data.terminal.GoogleWorkspaceToolHandler
import com.aiagents.app.data.terminal.UnifiedWebToolHandler
import com.aiagents.app.domain.model.SubagentRole
import com.aiagents.app.domain.model.SubagentToolPermission
import com.aiagents.app.domain.model.SubagentWorkspacePolicy

object SubagentCapabilityPolicy {
    const val GOOGLE_DOCS = "google_docs"
    const val GOOGLE_DRIVE = "google_drive"
    const val GOOGLE_SHEETS = "google_sheets"
    const val GOOGLE_GMAIL = "google_gmail"
    const val GOOGLE_CALENDAR = "google_calendar"
    const val GOOGLE_SLIDES = "google_slides"
    const val GOOGLE_WORKSPACE = "google_workspace"

    val SUPPORTED_CAPABILITIES = setOf(
        GOOGLE_DOCS,
        GOOGLE_DRIVE,
        GOOGLE_SHEETS,
        GOOGLE_GMAIL,
        GOOGLE_CALENDAR,
        GOOGLE_SLIDES,
        GOOGLE_WORKSPACE
    )

    private val readFiles = setOf("read_text_file", "read_image_file", "read_pdf_file", "list_files")
    private val writeFiles = setOf("write_file")
    private val web = setOf("duckduckgo_search", "brave_web_search", "serpapi_search") +
        UnifiedWebToolHandler.ALL_TOOL_NAMES
    private val safeReads = readFiles + web + MemoryToolHandler.READ_TOOL_NAMES +
        setOf("skill_list", "skill_view")
    private val codeTools = writeFiles + setOf("execute_command") + CodeExecutionHandler.ALL_TOOL_NAMES

    fun allowedTools(
        workspacePolicy: SubagentWorkspacePolicy,
        role: SubagentRole,
        depth: Int,
        maxDepth: Int,
        requestedCapabilities: Set<String> = emptySet()
    ): Set<String> = buildSet {
        addAll(safeReads)
        if (workspacePolicy == SubagentWorkspacePolicy.WRITE_EXCLUSIVE) addAll(codeTools)
        if (role == SubagentRole.ORCHESTRATOR && depth < maxDepth) {
            add(DelegationToolHandler.TOOL_NAME)
        }
        requestedCapabilities.forEach { capability ->
            addAll(toolsForCapability(capability))
        }
    }

    fun inferCapabilities(text: String): Set<String> {
        val normalized = text.lowercase()
        return buildSet {
            when {
                containsAny(normalized, "google docs", "google doc", "documento de google", "documento en google") -> add(GOOGLE_DOCS)
                containsAny(normalized, "google sheets", "hoja de calculo de google", "hoja de cálculo de google") -> add(GOOGLE_SHEETS)
                containsAny(normalized, "gmail", "correo de google") -> add(GOOGLE_GMAIL)
                containsAny(normalized, "google calendar", "calendario de google") -> add(GOOGLE_CALENDAR)
                containsAny(normalized, "google slides", "presentacion de google", "presentación de google") -> add(GOOGLE_SLIDES)
                containsAny(normalized, "google drive", "mi drive") -> add(GOOGLE_DRIVE)
                containsAny(normalized, "google workspace") -> add(GOOGLE_WORKSPACE)
            }
        }
    }

    fun shouldAutoDelegateIntegration(text: String, context: String = text): Boolean {
        val normalized = text.lowercase().trim()
        if (inferCapabilities(context).isEmpty()) return false
        if (containsAny(
                normalized,
                "como puedo", "cómo puedo", "how do i", "what is", "que es", "qué es",
                "como funciona", "cómo funciona"
            )
        ) return false
        return ACTION_WORDS.any { action ->
            Regex("(?:^|[^a-záéíóúñ])${Regex.escape(action)}(?:$|[^a-záéíóúñ])")
                .containsMatchIn(normalized)
        }
    }

    fun isExternalTool(toolName: String): Boolean =
        toolName in GoogleWorkspaceToolHandler.ALL_TOOL_NAMES ||
            toolName in GoogleDriveToolHandler.ALL_TOOL_NAMES

    fun boundedResultChars(capabilities: Set<String>): Int =
        if (capabilities.any { it in SUPPORTED_CAPABILITIES }) 2_000 else 6_000

    fun receiptContract(capabilities: Set<String>): String =
        if (capabilities.any { it in SUPPORTED_CAPABILITIES }) {
            "Return a compact receipt under 1,200 characters: status, action performed, artifact title, stable ID/URL when available, and any blocker. Do not repeat raw API responses or intermediate tool output."
        } else {
            ""
        }

    private fun toolsForCapability(capability: String): Set<String> = when (capability) {
        GOOGLE_DOCS -> setOf(
            GoogleWorkspaceToolHandler.TOOL_GWS_DOCS_READ,
            GoogleWorkspaceToolHandler.TOOL_GWS_DOCS_CREATE
        )
        GOOGLE_DRIVE -> setOf(
            GoogleWorkspaceToolHandler.TOOL_GWS_DRIVE_LIST,
            GoogleWorkspaceToolHandler.TOOL_GWS_DRIVE_SEARCH,
            GoogleWorkspaceToolHandler.TOOL_GWS_DRIVE_UPLOAD,
            GoogleWorkspaceToolHandler.TOOL_GWS_DRIVE_DOWNLOAD
        )
        GOOGLE_SHEETS -> setOf(
            GoogleWorkspaceToolHandler.TOOL_GWS_SHEETS_READ,
            GoogleWorkspaceToolHandler.TOOL_GWS_SHEETS_WRITE,
            GoogleWorkspaceToolHandler.TOOL_GWS_SHEETS_CREATE
        )
        GOOGLE_GMAIL -> setOf(
            GoogleWorkspaceToolHandler.TOOL_GWS_GMAIL_SEND,
            GoogleWorkspaceToolHandler.TOOL_GWS_GMAIL_LIST,
            GoogleWorkspaceToolHandler.TOOL_GWS_GMAIL_READ,
            GoogleWorkspaceToolHandler.TOOL_GWS_GMAIL_SEARCH,
            GoogleWorkspaceToolHandler.TOOL_GWS_GMAIL_DRAFT,
            GoogleWorkspaceToolHandler.TOOL_GWS_GMAIL_REPLY
        )
        GOOGLE_CALENDAR -> setOf(
            GoogleWorkspaceToolHandler.TOOL_GWS_CALENDAR_LIST,
            GoogleWorkspaceToolHandler.TOOL_GWS_CALENDAR_CREATE
        )
        GOOGLE_SLIDES -> setOf(GoogleWorkspaceToolHandler.TOOL_GWS_SLIDES_CREATE)
        GOOGLE_WORKSPACE -> GoogleWorkspaceToolHandler.ALL_TOOL_NAMES
        else -> emptySet()
    }

    private fun containsAny(text: String, vararg needles: String): Boolean =
        needles.any(text::contains)

    private val ACTION_WORDS = setOf(
        "crea", "crear", "haz", "hazlo", "hazla", "hacer", "escribe", "escribir", "lee", "leer",
        "lista", "listar", "busca", "buscar", "envia", "envía", "enviar", "sube",
        "subir", "descarga", "descargar", "agrega", "agregar", "edita", "editar",
        "actualiza", "actualizar", "responde", "responder", "programa", "programar",
        "create", "make", "write", "read", "list", "search", "send", "upload",
        "download", "add", "edit", "update", "reply", "schedule"
    )

    fun permissions(allowedTools: Set<String>): Map<String, SubagentToolPermission> =
        allowedTools.associateWith { tool ->
            when (tool) {
                "execute_command" -> SubagentToolPermission.ASK
                else -> SubagentToolPermission.ALLOW
            }
        }

    fun permissionFor(taskPermissions: Map<String, SubagentToolPermission>, toolName: String): SubagentToolPermission =
        taskPermissions[toolName] ?: SubagentToolPermission.DENY
}
