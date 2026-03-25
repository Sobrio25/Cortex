package com.aiagents.app.data.terminal

import com.aiagents.app.data.google.discovery.GoogleApiExecutor
import com.aiagents.app.data.google.discovery.GoogleDiscoveryService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

data class GoogleWorkspaceResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

@Singleton
class GoogleWorkspaceToolHandler @Inject constructor(
    private val discoveryService: GoogleDiscoveryService,
    private val apiExecutor: GoogleApiExecutor
) {
    private val gson = Gson()

    companion object {
        // Meta-tool names that the LLM uses
        const val TOOL_GWS_LIST_SERVICES = "gws_list_services"
        const val TOOL_GWS_LIST_METHODS = "gws_list_methods"
        const val TOOL_GWS_DESCRIBE_METHOD = "gws_describe_method"
        const val TOOL_GWS_EXECUTE = "gws_execute"
        const val TOOL_GWS_SCHEMA = "gws_schema"

        // Shortcut tools for configured services
        // Gmail
        const val TOOL_GWS_GMAIL_SEND = "gws_gmail_send"
        const val TOOL_GWS_GMAIL_LIST = "gws_gmail_list"
        const val TOOL_GWS_GMAIL_READ = "gws_gmail_read"
        const val TOOL_GWS_GMAIL_SEARCH = "gws_gmail_search"
        const val TOOL_GWS_GMAIL_DRAFT = "gws_gmail_draft"
        const val TOOL_GWS_GMAIL_REPLY = "gws_gmail_reply"
        // Drive
        const val TOOL_GWS_DRIVE_LIST = "gws_drive_list"
        const val TOOL_GWS_DRIVE_SEARCH = "gws_drive_search"
        const val TOOL_GWS_DRIVE_UPLOAD = "gws_drive_upload"
        const val TOOL_GWS_DRIVE_DOWNLOAD = "gws_drive_download"
        // Calendar
        const val TOOL_GWS_CALENDAR_LIST = "gws_calendar_list"
        const val TOOL_GWS_CALENDAR_CREATE = "gws_calendar_create"
        // Sheets
        const val TOOL_GWS_SHEETS_READ = "gws_sheets_read"
        const val TOOL_GWS_SHEETS_WRITE = "gws_sheets_write"
        const val TOOL_GWS_SHEETS_CREATE = "gws_sheets_create"
        // Docs
        const val TOOL_GWS_DOCS_READ = "gws_docs_read"
        const val TOOL_GWS_DOCS_CREATE = "gws_docs_create"
        // Slides
        const val TOOL_GWS_SLIDES_CREATE = "gws_slides_create"
        // Keep
        const val TOOL_GWS_KEEP_LIST = "gws_keep_list"

        val ALL_TOOL_NAMES = setOf(
            TOOL_GWS_LIST_SERVICES, TOOL_GWS_LIST_METHODS, TOOL_GWS_DESCRIBE_METHOD,
            TOOL_GWS_EXECUTE, TOOL_GWS_SCHEMA,
            // Gmail
            TOOL_GWS_GMAIL_SEND, TOOL_GWS_GMAIL_LIST, TOOL_GWS_GMAIL_READ,
            TOOL_GWS_GMAIL_SEARCH, TOOL_GWS_GMAIL_DRAFT, TOOL_GWS_GMAIL_REPLY,
            // Drive
            TOOL_GWS_DRIVE_LIST, TOOL_GWS_DRIVE_SEARCH, TOOL_GWS_DRIVE_UPLOAD, TOOL_GWS_DRIVE_DOWNLOAD,
            // Calendar
            TOOL_GWS_CALENDAR_LIST, TOOL_GWS_CALENDAR_CREATE,
            // Sheets
            TOOL_GWS_SHEETS_READ, TOOL_GWS_SHEETS_WRITE, TOOL_GWS_SHEETS_CREATE,
            // Docs
            TOOL_GWS_DOCS_READ, TOOL_GWS_DOCS_CREATE,
            // Slides
            TOOL_GWS_SLIDES_CREATE,
            // Keep
            TOOL_GWS_KEEP_LIST
        )

        fun getToolDefinitionsJson(): List<Map<String, Any>> {
            return listOf(
                // === META TOOLS (for discovering and using any Google API) ===
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_LIST_SERVICES,
                        "description" to "List all available Google Workspace services (Drive, Gmail, Sheets, Calendar, Docs, Slides, Tasks, People, Chat, Meet, Keep, Forms, Classroom, Admin Reports)",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to emptyMap<String, Any>()
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_LIST_METHODS,
                        "description" to "List all available API methods for a Google Workspace service. Returns resource hierarchy with method names, HTTP methods, and descriptions.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "service" to mapOf(
                                    "type" to "string",
                                    "description" to "Service name: gmail, drive, calendar, sheets, docs, slides, keep (also supports any Google API via discovery)"
                                )
                            ),
                            "required" to listOf("service")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_DESCRIBE_METHOD,
                        "description" to "Get detailed info about a specific API method including parameters, request/response schema, required scopes, and example usage.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "service" to mapOf("type" to "string", "description" to "Service name (e.g. 'drive', 'gmail')"),
                                "method" to mapOf("type" to "string", "description" to "Full method path (e.g. 'files.list', 'users.messages.get', 'spreadsheets.values.get')")
                            ),
                            "required" to listOf("service", "method")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_EXECUTE,
                        "description" to "Execute any Google Workspace API method dynamically. Use gws_list_methods and gws_describe_method first to discover available endpoints and their parameters.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "service" to mapOf("type" to "string", "description" to "Service name (e.g. 'drive', 'gmail', 'sheets')"),
                                "method" to mapOf("type" to "string", "description" to "Method path (e.g. 'files.list', 'users.messages.send')"),
                                "params" to mapOf("type" to "string", "description" to "JSON string of URL/path/query parameters (e.g. '{\"pageSize\": 10, \"q\": \"name contains report\"}')"),
                                "body" to mapOf("type" to "string", "description" to "JSON string of the request body (for POST/PUT/PATCH methods)"),
                                "page_all" to mapOf("type" to "boolean", "description" to "Auto-paginate through all results (default: false)"),
                                "page_limit" to mapOf("type" to "integer", "description" to "Max pages when paginating (default: 10)")
                            ),
                            "required" to listOf("service", "method")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_SCHEMA,
                        "description" to "Inspect the JSON schema for a request/response type in a Google API. Useful for understanding what fields are available.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "service" to mapOf("type" to "string", "description" to "Service name"),
                                "schema_name" to mapOf("type" to "string", "description" to "Schema/type name (e.g. 'File', 'Message', 'Event', 'Spreadsheet')")
                            ),
                            "required" to listOf("service", "schema_name")
                        )
                    )
                ),

                // === GMAIL SHORTCUTS ===
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_GMAIL_SEND,
                        "description" to "Send an email via Gmail",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "to" to mapOf("type" to "string", "description" to "Recipient email address(es), comma-separated"),
                                "subject" to mapOf("type" to "string", "description" to "Email subject"),
                                "body" to mapOf("type" to "string", "description" to "Email body (plain text or HTML)"),
                                "cc" to mapOf("type" to "string", "description" to "CC recipients, comma-separated"),
                                "bcc" to mapOf("type" to "string", "description" to "BCC recipients, comma-separated"),
                                "is_html" to mapOf("type" to "boolean", "description" to "Whether body is HTML (default: false)")
                            ),
                            "required" to listOf("to", "subject", "body")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_GMAIL_LIST,
                        "description" to "List recent emails from Gmail inbox",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "max_results" to mapOf("type" to "integer", "description" to "Max emails to return (default: 10)"),
                                "label" to mapOf("type" to "string", "description" to "Label to filter by (e.g. INBOX, SENT, DRAFT, STARRED, UNREAD)")
                            )
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_GMAIL_READ,
                        "description" to "Read a specific email by ID, returns full content with headers",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "message_id" to mapOf("type" to "string", "description" to "Gmail message ID")
                            ),
                            "required" to listOf("message_id")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_GMAIL_SEARCH,
                        "description" to "Search emails using Gmail search query syntax",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "query" to mapOf("type" to "string", "description" to "Gmail search query (e.g. 'from:user@example.com subject:invoice after:2024/01/01')"),
                                "max_results" to mapOf("type" to "integer", "description" to "Max results (default: 10)")
                            ),
                            "required" to listOf("query")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_GMAIL_DRAFT,
                        "description" to "Create a draft email in Gmail",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "to" to mapOf("type" to "string", "description" to "Recipient email"),
                                "subject" to mapOf("type" to "string", "description" to "Email subject"),
                                "body" to mapOf("type" to "string", "description" to "Email body"),
                                "is_html" to mapOf("type" to "boolean", "description" to "Whether body is HTML")
                            ),
                            "required" to listOf("to", "subject", "body")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_GMAIL_REPLY,
                        "description" to "Reply to an existing email thread",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "message_id" to mapOf("type" to "string", "description" to "Message ID to reply to"),
                                "thread_id" to mapOf("type" to "string", "description" to "Thread ID"),
                                "body" to mapOf("type" to "string", "description" to "Reply body text"),
                                "is_html" to mapOf("type" to "boolean", "description" to "Whether body is HTML")
                            ),
                            "required" to listOf("message_id", "thread_id", "body")
                        )
                    )
                ),

                // === DRIVE SHORTCUTS ===
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_DRIVE_LIST,
                        "description" to "List files in Google Drive",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "page_size" to mapOf("type" to "integer", "description" to "Number of files (default: 20)"),
                                "folder_id" to mapOf("type" to "string", "description" to "Folder ID to list (default: root)"),
                                "mime_type" to mapOf("type" to "string", "description" to "Filter by MIME type (e.g. 'application/vnd.google-apps.spreadsheet')"),
                                "order_by" to mapOf("type" to "string", "description" to "Sort order (e.g. 'modifiedTime desc')")
                            )
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_DRIVE_SEARCH,
                        "description" to "Search for files in Google Drive",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "query" to mapOf("type" to "string", "description" to "Search query (name, content, type) - uses Drive query syntax"),
                                "max_results" to mapOf("type" to "integer", "description" to "Max results (default: 20)")
                            ),
                            "required" to listOf("query")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_DRIVE_UPLOAD,
                        "description" to "Upload a file to Google Drive (from workspace files)",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "file_path" to mapOf("type" to "string", "description" to "Local file path to upload"),
                                "name" to mapOf("type" to "string", "description" to "Name for the file in Drive"),
                                "folder_id" to mapOf("type" to "string", "description" to "Target folder ID"),
                                "mime_type" to mapOf("type" to "string", "description" to "MIME type of the file")
                            ),
                            "required" to listOf("file_path", "name")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_DRIVE_DOWNLOAD,
                        "description" to "Download/export a file from Google Drive",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "file_id" to mapOf("type" to "string", "description" to "Drive file ID"),
                                "export_format" to mapOf("type" to "string", "description" to "Export format for Google Docs: 'text/plain', 'application/pdf', 'text/csv', etc.")
                            ),
                            "required" to listOf("file_id")
                        )
                    )
                ),

                // === CALENDAR SHORTCUTS ===
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_CALENDAR_LIST,
                        "description" to "List upcoming calendar events from Google Calendar",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "max_results" to mapOf("type" to "integer", "description" to "Max events (default: 10)"),
                                "calendar_id" to mapOf("type" to "string", "description" to "Calendar ID (default: 'primary')"),
                                "time_min" to mapOf("type" to "string", "description" to "Start of time range (RFC3339, e.g. '2024-01-01T00:00:00Z')"),
                                "time_max" to mapOf("type" to "string", "description" to "End of time range (RFC3339)"),
                                "query" to mapOf("type" to "string", "description" to "Free text search query")
                            )
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_CALENDAR_CREATE,
                        "description" to "Create a new calendar event",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "summary" to mapOf("type" to "string", "description" to "Event title"),
                                "description" to mapOf("type" to "string", "description" to "Event description"),
                                "start" to mapOf("type" to "string", "description" to "Start time (RFC3339 or date for all-day)"),
                                "end" to mapOf("type" to "string", "description" to "End time (RFC3339 or date for all-day)"),
                                "attendees" to mapOf("type" to "string", "description" to "Comma-separated attendee emails"),
                                "location" to mapOf("type" to "string", "description" to "Event location"),
                                "calendar_id" to mapOf("type" to "string", "description" to "Calendar ID (default: 'primary')"),
                                "timezone" to mapOf("type" to "string", "description" to "Timezone (e.g. 'America/New_York')")
                            ),
                            "required" to listOf("summary", "start", "end")
                        )
                    )
                ),

                // === SHEETS SHORTCUTS ===
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_SHEETS_READ,
                        "description" to "Read data from a Google Sheets spreadsheet",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "spreadsheet_id" to mapOf("type" to "string", "description" to "Spreadsheet ID"),
                                "range" to mapOf("type" to "string", "description" to "A1 notation range (e.g. 'Sheet1!A1:D10')")
                            ),
                            "required" to listOf("spreadsheet_id", "range")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_SHEETS_WRITE,
                        "description" to "Write data to a Google Sheets spreadsheet",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "spreadsheet_id" to mapOf("type" to "string", "description" to "Spreadsheet ID"),
                                "range" to mapOf("type" to "string", "description" to "A1 notation range (e.g. 'Sheet1!A1')"),
                                "values" to mapOf("type" to "string", "description" to "JSON array of arrays with the data (e.g. '[[\"Name\",\"Age\"],[\"Alice\",30]]')"),
                                "input_option" to mapOf("type" to "string", "description" to "How to interpret input: 'RAW' or 'USER_ENTERED' (default: 'USER_ENTERED')")
                            ),
                            "required" to listOf("spreadsheet_id", "range", "values")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_SHEETS_CREATE,
                        "description" to "Create a new Google Sheets spreadsheet",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "title" to mapOf("type" to "string", "description" to "Spreadsheet title")
                            ),
                            "required" to listOf("title")
                        )
                    )
                ),

                // === DOCS SHORTCUTS ===
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_DOCS_READ,
                        "description" to "Read the content of a Google Doc",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "document_id" to mapOf("type" to "string", "description" to "Google Doc document ID")
                            ),
                            "required" to listOf("document_id")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_DOCS_CREATE,
                        "description" to "Create a new Google Doc with optional content",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "title" to mapOf("type" to "string", "description" to "Document title"),
                                "content" to mapOf("type" to "string", "description" to "Initial text content")
                            ),
                            "required" to listOf("title")
                        )
                    )
                ),

                // === SLIDES SHORTCUT ===
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_SLIDES_CREATE,
                        "description" to "Create a new Google Slides presentation",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "title" to mapOf("type" to "string", "description" to "Presentation title")
                            ),
                            "required" to listOf("title")
                        )
                    )
                ),

                // === KEEP SHORTCUT ===
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_GWS_KEEP_LIST,
                        "description" to "List Google Keep notes",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "page_size" to mapOf("type" to "integer", "description" to "Max notes (default: 20)")
                            )
                        )
                    )
                )
            )
        }
    }

    suspend fun executeTool(
        toolCallId: String,
        toolName: String,
        arguments: String,
        accessToken: String
    ): GoogleWorkspaceResult {
        return try {
            val args: Map<String, Any> = try {
                gson.fromJson(arguments, object : TypeToken<Map<String, Any>>() {}.type) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }

            when (toolName) {
                // Meta tools
                TOOL_GWS_LIST_SERVICES -> listServices(toolCallId)
                TOOL_GWS_LIST_METHODS -> listMethods(toolCallId, args)
                TOOL_GWS_DESCRIBE_METHOD -> describeMethod(toolCallId, args)
                TOOL_GWS_EXECUTE -> executeGeneric(toolCallId, args, accessToken)
                TOOL_GWS_SCHEMA -> getSchema(toolCallId, args)

                // Gmail shortcuts
                TOOL_GWS_GMAIL_SEND -> gmailSend(toolCallId, args, accessToken)
                TOOL_GWS_GMAIL_LIST -> gmailList(toolCallId, args, accessToken)
                TOOL_GWS_GMAIL_READ -> gmailRead(toolCallId, args, accessToken)
                TOOL_GWS_GMAIL_SEARCH -> gmailSearch(toolCallId, args, accessToken)
                TOOL_GWS_GMAIL_DRAFT -> gmailDraft(toolCallId, args, accessToken)
                TOOL_GWS_GMAIL_REPLY -> gmailReply(toolCallId, args, accessToken)

                // Drive shortcuts
                TOOL_GWS_DRIVE_LIST -> driveList(toolCallId, args, accessToken)
                TOOL_GWS_DRIVE_SEARCH -> driveSearch(toolCallId, args, accessToken)
                TOOL_GWS_DRIVE_UPLOAD -> driveUpload(toolCallId, args, accessToken)
                TOOL_GWS_DRIVE_DOWNLOAD -> driveDownload(toolCallId, args, accessToken)

                // Calendar shortcuts
                TOOL_GWS_CALENDAR_LIST -> calendarList(toolCallId, args, accessToken)
                TOOL_GWS_CALENDAR_CREATE -> calendarCreate(toolCallId, args, accessToken)

                // Sheets shortcuts
                TOOL_GWS_SHEETS_READ -> sheetsRead(toolCallId, args, accessToken)
                TOOL_GWS_SHEETS_WRITE -> sheetsWrite(toolCallId, args, accessToken)
                TOOL_GWS_SHEETS_CREATE -> sheetsCreate(toolCallId, args, accessToken)

                // Docs shortcuts
                TOOL_GWS_DOCS_READ -> docsRead(toolCallId, args, accessToken)
                TOOL_GWS_DOCS_CREATE -> docsCreate(toolCallId, args, accessToken)

                // Slides shortcut
                TOOL_GWS_SLIDES_CREATE -> slidesCreate(toolCallId, args, accessToken)

                // Keep shortcut
                TOOL_GWS_KEEP_LIST -> keepList(toolCallId, args, accessToken)

                else -> GoogleWorkspaceResult(toolCallId, false, "Unknown tool: $toolName")
            }
        } catch (e: Exception) {
            GoogleWorkspaceResult(toolCallId, false, "Error executing $toolName: ${e.message}")
        }
    }

    // ==================== META TOOLS ====================

    private fun listServices(toolCallId: String): GoogleWorkspaceResult {
        val services = GoogleDiscoveryService.SERVICES.joinToString("\n") { entry ->
            "- ${entry.aliases.first()} (${entry.apiName} ${entry.version}) -- ${entry.description}"
        }
        return GoogleWorkspaceResult(toolCallId, true, "Available Google Workspace Services:\n$services\n\nUse gws_list_methods with a service name to see available operations.")
    }

    private suspend fun listMethods(toolCallId: String, args: Map<String, Any>): GoogleWorkspaceResult {
        val service = args["service"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'service' parameter")

        val doc = discoveryService.fetchForService(service).getOrElse {
            return GoogleWorkspaceResult(toolCallId, false, "Failed to fetch service '$service': ${it.message}")
        }

        val methods = discoveryService.flattenMethods(doc)
        val grouped = methods.entries.groupBy { it.value.second }

        val sb = StringBuilder()
        sb.appendLine("${doc.title ?: doc.name} (${doc.version}) -- ${doc.description ?: ""}")
        sb.appendLine("Methods: ${methods.size}")
        sb.appendLine()

        for ((resource, entries) in grouped.toSortedMap()) {
            sb.appendLine("[$resource]")
            for ((fullName, pair) in entries.sortedBy { it.key }) {
                val method = pair.first
                val methodShortName = fullName.substringAfterLast(".")
                val desc = method.description?.take(80) ?: ""
                sb.appendLine("  ${method.httpMethod.padEnd(6)} $methodShortName -- $desc")
            }
            sb.appendLine()
        }

        return GoogleWorkspaceResult(toolCallId, true, sb.toString())
    }

    private suspend fun describeMethod(toolCallId: String, args: Map<String, Any>): GoogleWorkspaceResult {
        val service = args["service"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'service' parameter")
        val methodPath = args["method"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'method' parameter")

        val doc = discoveryService.fetchForService(service).getOrElse {
            return GoogleWorkspaceResult(toolCallId, false, "Failed to fetch service '$service': ${it.message}")
        }

        val methods = discoveryService.flattenMethods(doc)
        val entry = methods[methodPath] ?: return GoogleWorkspaceResult(toolCallId, false,
            "Method '$methodPath' not found. Available: ${methods.keys.sorted().joinToString(", ")}")

        val method = entry.first
        val sb = StringBuilder()
        sb.appendLine("${method.id ?: methodPath}")
        sb.appendLine("${method.httpMethod} ${method.path}")
        sb.appendLine()
        method.description?.let { sb.appendLine(it); sb.appendLine() }

        if (method.parameters.isNotEmpty()) {
            sb.appendLine("Parameters:")
            for ((name, param) in method.parameters.toSortedMap()) {
                val req = if (param.required) " [REQUIRED]" else ""
                val loc = param.location?.let { " ($it)" } ?: ""
                sb.appendLine("  - $name: ${param.paramType ?: "string"}$loc$req")
                param.description?.let { sb.appendLine("    $it") }
                param.enumValues?.let { sb.appendLine("    Enum: ${it.joinToString(", ")}") }
            }
            sb.appendLine()
        }

        method.request?.ref_?.let { sb.appendLine("Request body: $it (use gws_schema to inspect)") }
        method.response?.ref_?.let { sb.appendLine("Response type: $it") }

        if (method.scopes.isNotEmpty()) {
            sb.appendLine("\nRequired scopes:")
            method.scopes.forEach { sb.appendLine("  - $it") }
        }

        sb.appendLine("\nSupports media upload: ${method.supportsMediaUpload}")
        sb.appendLine("Supports media download: ${method.supportsMediaDownload}")

        return GoogleWorkspaceResult(toolCallId, true, sb.toString())
    }

    private suspend fun executeGeneric(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        val service = args["service"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'service' parameter")
        val methodPath = args["method"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'method' parameter")
        val params = args["params"]?.toString()
        val body = args["body"]?.toString()
        val pageAll = (args["page_all"] as? Boolean) ?: false
        val pageLimit = (args["page_limit"] as? Number)?.toInt() ?: 10

        val doc = discoveryService.fetchForService(service).getOrElse {
            return GoogleWorkspaceResult(toolCallId, false, "Failed to fetch service: ${it.message}")
        }

        val methods = discoveryService.flattenMethods(doc)
        val entry = methods[methodPath] ?: return GoogleWorkspaceResult(toolCallId, false,
            "Method '$methodPath' not found in $service")

        val result = apiExecutor.execute(doc, entry.first, params, body, accessToken, pageAll, pageLimit)

        return GoogleWorkspaceResult(
            toolCallId,
            result.success,
            if (result.success) result.body
            else "API Error (${result.statusCode}): ${result.body}"
        )
    }

    private suspend fun getSchema(toolCallId: String, args: Map<String, Any>): GoogleWorkspaceResult {
        val service = args["service"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'service' parameter")
        val schemaName = args["schema_name"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'schema_name' parameter")

        val doc = discoveryService.fetchForService(service).getOrElse {
            return GoogleWorkspaceResult(toolCallId, false, "Failed to fetch service: ${it.message}")
        }

        val schema = doc.schemas[schemaName] ?: return GoogleWorkspaceResult(toolCallId, false,
            "Schema '$schemaName' not found. Available: ${doc.schemas.keys.sorted().joinToString(", ")}")

        val sb = StringBuilder()
        sb.appendLine("Schema: $schemaName")
        schema.description?.let { sb.appendLine(it) }
        sb.appendLine("Type: ${schema.type_ ?: "object"}")
        sb.appendLine()

        schema.properties?.let { props ->
            sb.appendLine("Properties:")
            for ((name, prop) in props.toSortedMap()) {
                val type = prop.ref_ ?: prop.type_ ?: "any"
                val req = if (prop.required) " [REQUIRED]" else ""
                sb.appendLine("  - $name: $type$req")
                prop.description?.take(100)?.let { sb.appendLine("    $it") }
            }
        }

        return GoogleWorkspaceResult(toolCallId, true, sb.toString())
    }

    // ==================== GMAIL SHORTCUTS ====================

    private suspend fun gmailSend(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        val to = args["to"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'to'")
        val subject = args["subject"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'subject'")
        val body = args["body"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'body'")
        val cc = args["cc"]?.toString()
        val bcc = args["bcc"]?.toString()
        val isHtml = args["is_html"] as? Boolean ?: false

        val contentType = if (isHtml) "text/html" else "text/plain"
        val rawMessage = buildRawEmail(to, subject, body, contentType, cc, bcc)
        val encodedMessage = android.util.Base64.encodeToString(
            rawMessage.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )

        val requestBody = """{"raw": "$encodedMessage"}"""

        val doc = discoveryService.fetchForService("gmail").getOrElse {
            return GoogleWorkspaceResult(toolCallId, false, "Failed to load Gmail API: ${it.message}")
        }
        val methods = discoveryService.flattenMethods(doc)
        val method = methods["users.messages.send"]?.first
            ?: return GoogleWorkspaceResult(toolCallId, false, "Gmail send method not found in discovery")

        val result = apiExecutor.execute(doc, method, """{"userId": "me"}""", requestBody, accessToken)

        return GoogleWorkspaceResult(toolCallId, result.success,
            if (result.success) "Email sent successfully to $to" else "Failed to send email: ${result.body}")
    }

    private suspend fun gmailList(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        val maxResults = (args["max_results"] as? Number)?.toInt() ?: 10
        val label = args["label"]?.toString() ?: "INBOX"

        return executeGmailMethod(toolCallId, "users.messages.list",
            """{"userId": "me", "maxResults": $maxResults, "labelIds": "$label"}""", null, accessToken)
    }

    private suspend fun gmailRead(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        val messageId = args["message_id"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'message_id'")

        return executeGmailMethod(toolCallId, "users.messages.get",
            """{"userId": "me", "id": "$messageId", "format": "full"}""", null, accessToken)
    }

    private suspend fun gmailSearch(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        val query = args["query"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'query'")
        val maxResults = (args["max_results"] as? Number)?.toInt() ?: 10

        return executeGmailMethod(toolCallId, "users.messages.list",
            """{"userId": "me", "q": ${gson.toJson(query)}, "maxResults": $maxResults}""", null, accessToken)
    }

    private suspend fun gmailDraft(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        val to = args["to"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'to'")
        val subject = args["subject"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'subject'")
        val body = args["body"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'body'")
        val isHtml = args["is_html"] as? Boolean ?: false

        val contentType = if (isHtml) "text/html" else "text/plain"
        val rawMessage = buildRawEmail(to, subject, body, contentType)
        val encodedMessage = android.util.Base64.encodeToString(
            rawMessage.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )

        val requestBody = """{"message": {"raw": "$encodedMessage"}}"""

        return executeGmailMethod(toolCallId, "users.drafts.create", """{"userId": "me"}""", requestBody, accessToken)
    }

    private suspend fun gmailReply(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        val messageId = args["message_id"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'message_id'")
        val threadId = args["thread_id"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'thread_id'")
        val body = args["body"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'body'")
        val isHtml = args["is_html"] as? Boolean ?: false

        // First get the original message to extract headers
        val originalResult = executeGmailMethod(toolCallId, "users.messages.get",
            """{"userId": "me", "id": "$messageId", "format": "metadata", "metadataHeaders": ["From", "Subject"]}""",
            null, accessToken)

        if (!originalResult.success) return originalResult

        // Parse original message to get reply-to and subject
        val originalJson = gson.fromJson(originalResult.content, com.google.gson.JsonObject::class.java)
        val headers = originalJson?.getAsJsonObject("payload")?.getAsJsonArray("headers")
        var replyTo = ""
        var subject = ""
        headers?.forEach { header ->
            val h = header.asJsonObject
            when (h.get("name")?.asString) {
                "From" -> replyTo = h.get("value")?.asString ?: ""
                "Subject" -> subject = h.get("value")?.asString ?: ""
            }
        }
        if (!subject.startsWith("Re:")) subject = "Re: $subject"

        val contentType = if (isHtml) "text/html" else "text/plain"
        val rawMessage = buildRawEmail(replyTo, subject, body, contentType, inReplyTo = messageId, references = messageId)
        val encodedMessage = android.util.Base64.encodeToString(
            rawMessage.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )

        val requestBody = """{"raw": "$encodedMessage", "threadId": "$threadId"}"""

        return executeGmailMethod(toolCallId, "users.messages.send", """{"userId": "me"}""", requestBody, accessToken)
    }

    private suspend fun executeGmailMethod(toolCallId: String, methodPath: String, params: String?, body: String?, accessToken: String): GoogleWorkspaceResult {
        val doc = discoveryService.fetchForService("gmail").getOrElse {
            return GoogleWorkspaceResult(toolCallId, false, "Failed to load Gmail API: ${it.message}")
        }
        val methods = discoveryService.flattenMethods(doc)
        val method = methods[methodPath]?.first
            ?: return GoogleWorkspaceResult(toolCallId, false, "Method '$methodPath' not found")

        val result = apiExecutor.execute(doc, method, params, body, accessToken)
        return GoogleWorkspaceResult(toolCallId, result.success,
            if (result.success) result.body else "Error (${result.statusCode}): ${result.body}")
    }

    // ==================== DRIVE SHORTCUTS ====================

    private suspend fun driveList(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        val pageSize = (args["page_size"] as? Number)?.toInt() ?: 20
        val folderId = args["folder_id"]?.toString()
        val mimeType = args["mime_type"]?.toString()
        val orderBy = args["order_by"]?.toString() ?: "modifiedTime desc"

        val queryParts = mutableListOf<String>()
        folderId?.let { queryParts.add("'$it' in parents") }
        mimeType?.let { queryParts.add("mimeType='$it'") }
        queryParts.add("trashed=false")
        val q = queryParts.joinToString(" and ")

        val params = """{"pageSize": $pageSize, "orderBy": "$orderBy", "q": ${gson.toJson(q)}, "fields": "files(id,name,mimeType,modifiedTime,size,webViewLink),nextPageToken"}"""

        return executeServiceMethod(toolCallId, "drive", "files.list", params, null, accessToken)
    }

    private suspend fun driveSearch(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        val query = args["query"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'query'")
        val maxResults = (args["max_results"] as? Number)?.toInt() ?: 20

        val q = "fullText contains ${gson.toJson(query)} and trashed=false"
        val params = """{"pageSize": $maxResults, "q": ${gson.toJson(q)}, "fields": "files(id,name,mimeType,modifiedTime,size,webViewLink),nextPageToken"}"""

        return executeServiceMethod(toolCallId, "drive", "files.list", params, null, accessToken)
    }

    private suspend fun driveUpload(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        // Note: Simple metadata-only upload for now, full multipart will need special handling
        val name = args["name"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'name'")
        val folderId = args["folder_id"]?.toString()

        val metadata = mutableMapOf<String, Any>("name" to name)
        folderId?.let { metadata["parents"] = listOf(it) }

        val body = gson.toJson(metadata)
        return executeServiceMethod(toolCallId, "drive", "files.create", null, body, accessToken)
    }

    private suspend fun driveDownload(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        val fileId = args["file_id"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'file_id'")
        val exportFormat = args["export_format"]?.toString()

        return if (exportFormat != null) {
            // Export Google Workspace document
            executeServiceMethod(toolCallId, "drive", "files.export",
                """{"fileId": "$fileId", "mimeType": "$exportFormat"}""", null, accessToken)
        } else {
            // Get file metadata
            executeServiceMethod(toolCallId, "drive", "files.get",
                """{"fileId": "$fileId", "fields": "id,name,mimeType,size,webViewLink,modifiedTime"}""", null, accessToken)
        }
    }

    // ==================== CALENDAR SHORTCUTS ====================

    private suspend fun calendarList(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        val maxResults = (args["max_results"] as? Number)?.toInt() ?: 10
        val calendarId = args["calendar_id"]?.toString() ?: "primary"
        val timeMin = args["time_min"]?.toString() ?: java.time.Instant.now().toString()
        val timeMax = args["time_max"]?.toString()
        val query = args["query"]?.toString()

        val paramMap = mutableMapOf<String, Any>(
            "calendarId" to calendarId,
            "maxResults" to maxResults,
            "timeMin" to timeMin,
            "singleEvents" to true,
            "orderBy" to "startTime"
        )
        timeMax?.let { paramMap["timeMax"] = it }
        query?.let { paramMap["q"] = it }

        return executeServiceMethod(toolCallId, "calendar", "events.list", gson.toJson(paramMap), null, accessToken)
    }

    private suspend fun calendarCreate(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        val summary = args["summary"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'summary'")
        val description = args["description"]?.toString()
        val start = args["start"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'start'")
        val end = args["end"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'end'")
        val attendees = args["attendees"]?.toString()
        val location = args["location"]?.toString()
        val calendarId = args["calendar_id"]?.toString() ?: "primary"
        val timezone = args["timezone"]?.toString()

        val isAllDay = !start.contains("T")

        val startObj = if (isAllDay) mapOf("date" to start)
            else mapOf("dateTime" to start).let { if (timezone != null) it + ("timeZone" to timezone) else it }
        val endObj = if (isAllDay) mapOf("date" to end)
            else mapOf("dateTime" to end).let { if (timezone != null) it + ("timeZone" to timezone) else it }

        val event = mutableMapOf<String, Any>(
            "summary" to summary,
            "start" to startObj,
            "end" to endObj
        )
        description?.let { event["description"] = it }
        location?.let { event["location"] = it }
        attendees?.let { emails ->
            event["attendees"] = emails.split(",").map { mapOf("email" to it.trim()) }
        }

        return executeServiceMethod(toolCallId, "calendar", "events.insert",
            """{"calendarId": "$calendarId"}""", gson.toJson(event), accessToken)
    }

    // ==================== SHEETS SHORTCUTS ====================

    private suspend fun sheetsRead(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        val spreadsheetId = args["spreadsheet_id"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'spreadsheet_id'")
        val range = args["range"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'range'")

        return executeServiceMethod(toolCallId, "sheets", "spreadsheets.values.get",
            """{"spreadsheetId": "$spreadsheetId", "range": "$range"}""", null, accessToken)
    }

    private suspend fun sheetsWrite(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        val spreadsheetId = args["spreadsheet_id"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'spreadsheet_id'")
        val range = args["range"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'range'")
        val values = args["values"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'values'")
        val inputOption = args["input_option"]?.toString() ?: "USER_ENTERED"

        val parsedValues: Any = gson.fromJson(values, Any::class.java)
        val body = gson.toJson(mapOf("values" to parsedValues))

        return executeServiceMethod(toolCallId, "sheets", "spreadsheets.values.update",
            """{"spreadsheetId": "$spreadsheetId", "range": "$range", "valueInputOption": "$inputOption"}""", body, accessToken)
    }

    private suspend fun sheetsCreate(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        val title = args["title"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'title'")

        val body = """{"properties": {"title": ${gson.toJson(title)}}}"""
        return executeServiceMethod(toolCallId, "sheets", "spreadsheets.create", null, body, accessToken)
    }

    // ==================== DOCS SHORTCUTS ====================

    private suspend fun docsRead(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        val documentId = args["document_id"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'document_id'")

        // Export as plain text via Drive API for readability
        return driveDownload(toolCallId, mapOf("file_id" to documentId, "export_format" to "text/plain"), accessToken)
    }

    private suspend fun docsCreate(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        val title = args["title"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'title'")
        val content = args["content"]?.toString()

        val body = """{"title": ${gson.toJson(title)}}"""
        val createResult = executeServiceMethod(toolCallId, "docs", "documents.create", null, body, accessToken)

        if (!createResult.success || content == null) return createResult

        // If content provided, insert text
        val docJson = gson.fromJson(createResult.content, com.google.gson.JsonObject::class.java)
        val docId = docJson?.get("documentId")?.asString ?: return createResult

        val insertBody = gson.toJson(mapOf(
            "requests" to listOf(
                mapOf("insertText" to mapOf(
                    "location" to mapOf("index" to 1),
                    "text" to content
                ))
            )
        ))

        executeServiceMethod(toolCallId, "docs", "documents.batchUpdate",
            """{"documentId": "$docId"}""", insertBody, accessToken)

        return GoogleWorkspaceResult(toolCallId, true, "Document created: $docId\n${createResult.content}")
    }

    // ==================== SLIDES SHORTCUT ====================

    private suspend fun slidesCreate(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        val title = args["title"]?.toString() ?: return GoogleWorkspaceResult(toolCallId, false, "Missing 'title'")
        return executeServiceMethod(toolCallId, "slides", "presentations.create", null,
            """{"title": ${gson.toJson(title)}}""", accessToken)
    }

    private suspend fun keepList(toolCallId: String, args: Map<String, Any>, accessToken: String): GoogleWorkspaceResult {
        val pageSize = (args["page_size"] as? Number)?.toInt() ?: 20
        return executeServiceMethod(toolCallId, "keep", "notes.list",
            """{"pageSize": $pageSize}""", null, accessToken)
    }

    // ==================== HELPER ====================

    private suspend fun executeServiceMethod(
        toolCallId: String, service: String, methodPath: String,
        params: String?, body: String?, accessToken: String
    ): GoogleWorkspaceResult {
        val doc = discoveryService.fetchForService(service).getOrElse {
            return GoogleWorkspaceResult(toolCallId, false, "Failed to load $service API: ${it.message}")
        }
        val methods = discoveryService.flattenMethods(doc)
        val method = methods[methodPath]?.first
            ?: return GoogleWorkspaceResult(toolCallId, false, "Method '$methodPath' not found in $service")

        val result = apiExecutor.execute(doc, method, params, body, accessToken)
        return GoogleWorkspaceResult(toolCallId, result.success,
            if (result.success) result.body else "Error (${result.statusCode}): ${result.body}")
    }

    private fun buildRawEmail(
        to: String, subject: String, body: String, contentType: String,
        cc: String? = null, bcc: String? = null,
        inReplyTo: String? = null, references: String? = null
    ): String {
        val sb = StringBuilder()
        sb.appendLine("To: $to")
        cc?.let { sb.appendLine("Cc: $it") }
        bcc?.let { sb.appendLine("Bcc: $it") }
        sb.appendLine("Subject: $subject")
        sb.appendLine("Content-Type: $contentType; charset=utf-8")
        inReplyTo?.let { sb.appendLine("In-Reply-To: <$it>") }
        references?.let { sb.appendLine("References: <$it>") }
        sb.appendLine("MIME-Version: 1.0")
        sb.appendLine()
        sb.appendLine(body)
        return sb.toString()
    }
}
