package com.aiagents.app.data.terminal

import android.util.Log
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolSearchHandler @Inject constructor() {

    companion object {
        private const val TAG = "ToolSearchHandler"
        const val TOOL_NAME = "search_tools"

        /** Minimum number of total tools before deferred mode activates */
        const val DEFERRED_THRESHOLD = 15

        /** Core tools always included in deferred mode (not deferred) */
        val CORE_TOOL_NAMES = setOf(
            // File operations
            "read_text_file", "read_image_file", "read_pdf_file", "write_file", "list_files",
            // App control — always core so Cortex can manage the app
            "app_control",
            // Task planning — always core so agents can show progress
            "todo_write", "todo_read",
            // Scheduling — always core so agents can manage cron jobs
            "schedule_task",
            // Memory
            "memory_search", "memory_save", "memory_list", "memory_delete", "memory_update", "memory_link",
            // Agent selection & delegation (Cortex routing)
            "select_agent", "delegate_to_agent",
            // Terminal
            "execute_command",
            // Code execution
            "run_code", "preview_web", "preview_project",
            // Web search — always available so agents don't resort to Python scripts
            "duckduckgo_search", "brave_web_search", "serpapi_search",
            // Google Workspace — core shortcuts always available
            "gws_gmail_send", "gws_gmail_list", "gws_gmail_read", "gws_gmail_search",
            "gws_drive_list", "gws_drive_search",
            "gws_calendar_list", "gws_calendar_create",
            "gws_sheets_read", "gws_sheets_write", "gws_sheets_create",
            "gws_docs_read", "gws_docs_create",
            "gws_slides_create",
            "gws_execute", "gws_list_services", "gws_list_methods"
        )

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_NAME,
                    "description" to "Search for available tools by keyword. IMPORTANT: You MUST use this tool before using any capability not already in your tool list. Do NOT attempt to call tools you haven't discovered yet. Use for: Google Workspace (gmail, drive, calendar, sheets, docs, slides), presentations/slides/pptx, web search, maps, reminders, github, obsidian, notion, canva, slack, device control, and more. Returns matching tools that become available for use.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "query" to mapOf(
                                "type" to "string",
                                "description" to "Keywords for the capability needed (e.g. 'calendar events', 'web search', 'github issues')"
                            )
                        ),
                        "required" to listOf("query")
                    )
                )
            )
        )
    }

    data class ToolEntry(
        val name: String,
        val category: String,
        val description: String,
        val keywords: Set<String>
    )

    private val registry = listOf(
        // App Control
        ToolEntry("app_control", "app_settings", "Control app settings: change model, toggle services, configure agents, display options", setOf("model", "change", "switch", "provider", "settings", "config", "configure", "service", "toggle", "enable", "disable", "display", "reasoning", "agent", "temperature", "personality", "modelo", "cambiar", "configurar", "servicio", "habilitar", "deshabilitar", "activar", "desactivar")),

        // Task Planning
        ToolEntry("todo_write", "planning", "Create or update a task plan with progress tracking for complex multi-step tasks", setOf("todo", "plan", "task", "progress", "step", "checklist", "track", "tarea", "plan", "progreso", "paso", "lista")),
        ToolEntry("todo_read", "planning", "Read the current task plan to check progress", setOf("todo", "plan", "task", "progress", "check", "status", "tarea", "progreso", "estado")),

        // Scheduling
        ToolEntry("schedule_task", "scheduling", "Create, list, delete, toggle scheduled agent tasks (cron jobs). Execute prompts at specific times: once, daily, weekly, interval", setOf("schedule", "cron", "timer", "alarm", "recurring", "daily", "weekly", "interval", "automatic", "programar", "programado", "automatico", "diario", "semanal", "repetir", "horario", "tarea")),

        // Agent Creator
        ToolEntry("create_agent", "agents", "Create a new AI agent with custom prompt and personality", setOf("agent", "create", "new", "design", "build", "agente", "crear", "nuevo", "diseñar")),
        ToolEntry("delete_agent", "agents", "Delete an existing agent by name (requires user confirmation)", setOf("agent", "delete", "remove", "agente", "eliminar", "borrar", "quitar")),

        // Calendar
        ToolEntry("read_calendar_events", "calendar", "Read calendar events in a date range", setOf("calendar", "events", "schedule", "agenda", "meetings", "appointments", "evento", "calendario")),
        ToolEntry("add_calendar_event", "calendar", "Add event to calendar", setOf("calendar", "event", "schedule", "create", "add", "meeting", "appointment", "evento", "crear", "agendar")),

        // Reminders & Alarms
        ToolEntry("set_reminder", "reminders", "Create a notification reminder", setOf("reminder", "notify", "alert", "remember", "notification", "recordatorio", "recordar")),
        ToolEntry("set_alarm", "reminders", "Set system alarm via clock app", setOf("alarm", "wake", "clock", "timer", "alarma", "despertador")),
        ToolEntry("list_reminders", "reminders", "List active reminders", setOf("reminder", "list", "pending", "active", "recordatorios")),
        ToolEntry("cancel_reminder", "reminders", "Cancel a reminder by ID", setOf("reminder", "cancel", "delete", "remove", "cancelar", "eliminar")),

        // Location
        ToolEntry("get_user_location", "location", "Get user's current GPS location", setOf("location", "gps", "where", "coordinates", "address", "position", "ubicacion", "donde", "estoy")),

        // Device control
        ToolEntry("device_control", "device", "Control device: open/list/inspect/uninstall apps, camera, volume, brightness, flashlight, Spotify. Also get_app_info (detailed app capabilities) and query_capable_apps (find apps by intent/mime type)", setOf("app", "open", "list", "uninstall", "remove", "camera", "photo", "volume", "brightness", "flashlight", "spotify", "music", "settings", "device", "abrir", "aplicacion", "desinstalar", "eliminar", "lista", "foto", "volumen", "linterna", "musica", "info", "capabilities", "intents", "inspect", "details", "capacidades", "detalles", "query", "pdf", "share", "compartir")),

        // Brave Search
        ToolEntry("duckduckgo_search", "web_search", "Search the web via DuckDuckGo (free, no API key)", setOf("search", "web", "internet", "google", "find", "lookup", "news", "information", "buscar", "precio", "noticias")),
        ToolEntry("brave_web_search", "web_search", "Search the web via Brave Search", setOf("search", "web", "internet", "google", "find", "lookup", "news", "information", "buscar", "precio", "noticias")),

        // SerpAPI
        ToolEntry("serpapi_search", "web_search", "Multi-engine search: Google, Maps, Flights, Hotels, YouTube, News", setOf("search", "google", "flights", "hotels", "youtube", "news", "shopping", "images", "vuelos", "hoteles", "noticias")),

        // Google Maps
        ToolEntry("google_maps_geocode", "maps", "Convert address to coordinates", setOf("maps", "geocode", "address", "coordinates", "location", "direccion", "coordenadas")),
        ToolEntry("google_maps_places", "maps", "Search nearby places and businesses", setOf("maps", "places", "nearby", "restaurant", "store", "business", "restaurante", "tienda", "cerca", "cercano")),
        ToolEntry("google_maps_directions", "maps", "Get route directions between locations", setOf("maps", "directions", "route", "navigate", "driving", "walking", "transit", "ruta", "como llegar", "navegacion")),
        ToolEntry("google_maps_distance", "maps", "Calculate distance and travel time", setOf("maps", "distance", "time", "travel", "matrix", "distancia", "tiempo")),
        ToolEntry("google_maps_elevation", "maps", "Get elevation data for coordinates", setOf("maps", "elevation", "altitude", "height", "elevacion", "altitud")),

        // Canva
        ToolEntry("canva_create_design", "canva", "Create a professional design in Canva", setOf("canva", "design", "graphic", "poster", "presentation", "image", "create", "diseño", "grafico", "presentacion")),
        ToolEntry("canva_export_design", "canva", "Export Canva design as PNG/PDF/JPG", setOf("canva", "export", "download", "png", "pdf", "exportar", "descargar")),
        ToolEntry("canva_list_designs", "canva", "List user's Canva designs", setOf("canva", "designs", "list", "my", "diseños", "listar")),
        ToolEntry("canva_get_design", "canva", "Get details of a specific Canva design", setOf("canva", "design", "details", "info", "get", "detalles", "diseño")),
        ToolEntry("canva_upload_asset", "canva", "Upload image/video to Canva for use in designs", setOf("canva", "upload", "asset", "image", "video", "subir", "imagen")),
        ToolEntry("canva_list_brand_templates", "canva", "List brand templates available for autofill", setOf("canva", "brand", "template", "templates", "plantilla", "plantillas", "marca")),
        ToolEntry("canva_get_brand_template", "canva", "Get brand template details and editable fields", setOf("canva", "brand", "template", "dataset", "fields", "campos", "plantilla")),
        ToolEntry("canva_autofill_template", "canva", "Create design by autofilling a brand template with custom data", setOf("canva", "autofill", "template", "fill", "edit", "brand", "plantilla", "rellenar", "editar", "contenido")),
        ToolEntry("canva_create_folder", "canva", "Create a folder in Canva", setOf("canva", "folder", "create", "carpeta", "crear", "organizar")),
        ToolEntry("canva_get_folder", "canva", "Get folder details", setOf("canva", "folder", "get", "details", "carpeta", "detalles")),
        ToolEntry("canva_list_folder_items", "canva", "List items inside a Canva folder", setOf("canva", "folder", "list", "items", "contents", "carpeta", "contenido", "listar")),
        ToolEntry("canva_move_to_folder", "canva", "Move a design or item to a folder", setOf("canva", "folder", "move", "organize", "mover", "carpeta", "organizar")),
        ToolEntry("canva_delete_folder", "canva", "Delete an empty Canva folder", setOf("canva", "folder", "delete", "remove", "eliminar", "borrar", "carpeta")),
        ToolEntry("canva_add_comment", "canva", "Add a comment to a Canva design", setOf("canva", "comment", "add", "write", "comentario", "agregar", "comentar")),
        ToolEntry("canva_list_comments", "canva", "List comments on a Canva design", setOf("canva", "comment", "comments", "list", "comentarios", "listar")),
        ToolEntry("canva_reply_comment", "canva", "Reply to a comment on a Canva design", setOf("canva", "comment", "reply", "respond", "responder", "respuesta")),
        ToolEntry("canva_get_user_profile", "canva", "Get current Canva user profile", setOf("canva", "user", "profile", "me", "account", "perfil", "usuario", "cuenta")),
        ToolEntry("canva_import_design", "canva", "Import external file (PDF, PPTX) as Canva design", setOf("canva", "import", "upload", "pdf", "pptx", "powerpoint", "file", "importar", "archivo")),

        // PubMed
        ToolEntry("pubmed_search", "medical", "Search PubMed medical/scientific articles", setOf("pubmed", "medical", "health", "research", "article", "study", "clinical", "science", "medico", "salud", "estudio", "cientifico")),
        ToolEntry("pubmed_fetch_article", "medical", "Get full PubMed article details by PMID", setOf("pubmed", "article", "paper", "fetch", "pmid", "details", "articulo")),

        // Obsidian
        ToolEntry("obsidian_read_note", "obsidian", "Read an Obsidian vault note", setOf("obsidian", "note", "read", "vault", "markdown", "nota", "leer")),
        ToolEntry("obsidian_write_note", "obsidian", "Create or overwrite Obsidian note", setOf("obsidian", "note", "write", "create", "vault", "nota", "escribir", "crear")),
        ToolEntry("obsidian_append_note", "obsidian", "Append content to Obsidian note", setOf("obsidian", "note", "append", "add", "agregar", "añadir")),
        ToolEntry("obsidian_search_notes", "obsidian", "Search notes in Obsidian vault", setOf("obsidian", "search", "find", "note", "vault", "buscar", "encontrar")),
        ToolEntry("obsidian_list_folder", "obsidian", "List contents of Obsidian folder", setOf("obsidian", "folder", "list", "directory", "vault", "carpeta", "listar")),

        // GitHub
        ToolEntry("github_search_repos", "github", "Search GitHub repositories", setOf("github", "repo", "repository", "search", "code", "repositorio")),
        ToolEntry("github_get_repo", "github", "Get GitHub repo details", setOf("github", "repo", "details", "info", "repositorio")),
        ToolEntry("github_list_issues", "github", "List GitHub issues", setOf("github", "issues", "bugs", "list", "problemas")),
        ToolEntry("github_create_issue", "github", "Create a GitHub issue", setOf("github", "issue", "create", "bug", "report", "crear")),
        ToolEntry("github_read_file", "github", "Read file from GitHub repo", setOf("github", "file", "read", "code", "source", "archivo", "leer")),
        ToolEntry("github_list_pulls", "github", "List GitHub pull requests", setOf("github", "pull", "pr", "merge", "review")),
        ToolEntry("github_create_update_file", "github", "Create or edit a file in a GitHub repo", setOf("github", "file", "create", "edit", "update", "write", "commit", "push", "editar", "crear", "archivo")),
        ToolEntry("github_delete_file", "github", "Delete a file from a GitHub repo", setOf("github", "file", "delete", "remove", "eliminar", "borrar")),
        ToolEntry("github_create_branch", "github", "Create a new branch in a repo", setOf("github", "branch", "create", "rama", "crear")),
        ToolEntry("github_create_pull", "github", "Create a pull request", setOf("github", "pull", "pr", "create", "merge", "request", "crear")),
        ToolEntry("github_comment_issue", "github", "Comment on an issue or PR", setOf("github", "comment", "issue", "pr", "comentar", "comentario")),
        ToolEntry("github_list_branches", "github", "List repo branches", setOf("github", "branch", "branches", "list", "ramas")),
        ToolEntry("github_get_issue", "github", "Get details of a specific GitHub issue", setOf("github", "issue", "get", "details", "detalles")),
        ToolEntry("github_update_issue", "github", "Update a GitHub issue (title, body, state, labels, assignees)", setOf("github", "issue", "update", "edit", "close", "actualizar", "editar", "cerrar")),
        ToolEntry("github_list_issue_comments", "github", "List comments on a GitHub issue or PR", setOf("github", "issue", "comments", "list", "comentarios")),
        ToolEntry("github_get_pull", "github", "Get details of a specific pull request", setOf("github", "pull", "pr", "get", "details", "detalles")),
        ToolEntry("github_merge_pull", "github", "Merge a GitHub pull request", setOf("github", "pull", "pr", "merge", "squash", "rebase", "mergear")),
        ToolEntry("github_update_pull", "github", "Update a pull request (title, body, state)", setOf("github", "pull", "pr", "update", "edit", "actualizar")),
        ToolEntry("github_list_pr_files", "github", "List files changed in a pull request", setOf("github", "pull", "pr", "files", "changes", "diff", "archivos", "cambios")),
        ToolEntry("github_request_review", "github", "Request review on a pull request", setOf("github", "pull", "pr", "review", "request", "reviewer", "revisar")),
        ToolEntry("github_list_commits", "github", "List commits on a branch", setOf("github", "commit", "commits", "list", "history", "historial")),
        ToolEntry("github_get_commit", "github", "Get details of a specific commit", setOf("github", "commit", "get", "details", "sha", "detalles")),
        ToolEntry("github_list_releases", "github", "List releases of a GitHub repo", setOf("github", "release", "releases", "list", "version", "versiones")),
        ToolEntry("github_create_release", "github", "Create a new release in a GitHub repo", setOf("github", "release", "create", "tag", "version", "crear")),
        ToolEntry("github_get_latest_release", "github", "Get the latest release of a repo", setOf("github", "release", "latest", "last", "ultimo", "version")),
        ToolEntry("github_list_repo_contents", "github", "List directory contents of a GitHub repo", setOf("github", "directory", "contents", "list", "files", "ls", "directorio", "contenido")),
        ToolEntry("github_list_tags", "github", "List tags of a GitHub repo", setOf("github", "tag", "tags", "list", "etiquetas")),
        ToolEntry("github_list_contributors", "github", "List contributors of a GitHub repo", setOf("github", "contributors", "list", "contribuidores", "autores")),
        ToolEntry("github_create_repo", "github", "Create a new GitHub repository", setOf("github", "repo", "create", "new", "repository", "crear", "nuevo")),
        ToolEntry("github_fork_repo", "github", "Fork a GitHub repository", setOf("github", "fork", "repo", "copy", "clonar")),
        ToolEntry("github_get_user", "github", "Get a GitHub user's profile", setOf("github", "user", "profile", "usuario", "perfil")),
        ToolEntry("github_get_authenticated_user", "github", "Get your own GitHub profile", setOf("github", "user", "me", "profile", "my", "mi", "perfil")),
        ToolEntry("github_list_user_repos", "github", "List repositories of a GitHub user", setOf("github", "user", "repos", "repositories", "list", "repositorios")),
        ToolEntry("github_list_gists", "github", "List your GitHub gists", setOf("github", "gist", "gists", "list", "snippets")),
        ToolEntry("github_create_gist", "github", "Create a new GitHub gist", setOf("github", "gist", "create", "snippet", "code", "crear")),
        ToolEntry("github_list_workflows", "github", "List GitHub Actions workflows", setOf("github", "actions", "workflow", "workflows", "ci", "cd", "pipeline")),
        ToolEntry("github_trigger_workflow", "github", "Trigger a GitHub Actions workflow", setOf("github", "actions", "workflow", "trigger", "dispatch", "run", "ejecutar", "disparar")),
        ToolEntry("github_list_workflow_runs", "github", "List GitHub Actions workflow runs", setOf("github", "actions", "workflow", "runs", "executions", "ejecuciones", "builds")),
        ToolEntry("github_star_repo", "github", "Star or unstar a GitHub repo", setOf("github", "star", "unstar", "favorite", "estrella")),
        ToolEntry("github_list_starred", "github", "List your starred GitHub repos", setOf("github", "starred", "stars", "favorites", "favoritos", "estrellas")),

        // Notion
        ToolEntry("notion_search", "notion", "Search Notion pages and databases", setOf("notion", "search", "page", "database", "buscar")),
        ToolEntry("notion_read_page", "notion", "Read a Notion page", setOf("notion", "read", "page", "content", "leer", "pagina")),
        ToolEntry("notion_create_page", "notion", "Create a Notion page", setOf("notion", "create", "page", "new", "crear", "pagina")),
        ToolEntry("notion_append_blocks", "notion", "Append content to Notion page", setOf("notion", "append", "add", "block", "content", "agregar")),
        ToolEntry("notion_list_databases", "notion", "List Notion databases", setOf("notion", "database", "list", "db", "base de datos")),

        // Slack
        ToolEntry("slack_list_channels", "slack", "List Slack channels", setOf("slack", "channels", "list", "canales")),
        ToolEntry("slack_get_channel_info", "slack", "Get Slack channel info", setOf("slack", "channel", "info", "details", "canal")),
        ToolEntry("slack_read_channel", "slack", "Read messages from a Slack channel", setOf("slack", "read", "messages", "channel", "history", "leer", "mensajes")),
        ToolEntry("slack_send_message", "slack", "Send a message to Slack", setOf("slack", "send", "message", "post", "write", "enviar", "mensaje")),
        ToolEntry("slack_reply_thread", "slack", "Reply to a Slack thread", setOf("slack", "reply", "thread", "respond", "responder", "hilo")),
        ToolEntry("slack_list_users", "slack", "List Slack workspace members", setOf("slack", "users", "members", "list", "team", "usuarios", "miembros")),
        ToolEntry("slack_get_user", "slack", "Get Slack user profile", setOf("slack", "user", "profile", "info", "usuario", "perfil")),
        ToolEntry("slack_search_messages", "slack", "Search Slack messages", setOf("slack", "search", "find", "messages", "buscar", "mensajes")),
        ToolEntry("slack_add_reaction", "slack", "Add emoji reaction to Slack message", setOf("slack", "reaction", "emoji", "react", "reaccion")),
        ToolEntry("slack_set_channel_topic", "slack", "Set Slack channel topic", setOf("slack", "topic", "channel", "set", "tema", "canal")),
        ToolEntry("slack_list_files", "slack", "List files shared in Slack", setOf("slack", "files", "list", "shared", "archivos")),
        ToolEntry("slack_delete_message", "slack", "Delete a Slack message", setOf("slack", "delete", "message", "remove", "borrar", "eliminar", "mensaje")),

        // Google Drive
        ToolEntry("gdrive_list_files", "google_drive", "List recent Google Drive files", setOf("drive", "google", "files", "list", "recent", "archivos")),
        ToolEntry("gdrive_search_files", "google_drive", "Search Google Drive files", setOf("drive", "google", "search", "find", "file", "buscar", "archivo")),
        ToolEntry("gdrive_read_doc", "google_drive", "Read a Google Doc", setOf("drive", "google", "doc", "read", "document", "leer", "documento")),
        ToolEntry("gdrive_create_doc", "google_drive", "Create a new Google Doc", setOf("drive", "google", "doc", "create", "new", "document", "crear", "documento")),

        // Finance
        ToolEntry("finance_add_transaction", "finance", "Record a financial transaction (expense, income, investment)", setOf("finance", "expense", "income", "investment", "money", "spend", "earn", "gasto", "ingreso", "inversion", "dinero", "registrar", "transaccion")),
        ToolEntry("finance_list_transactions", "finance", "List recent financial transactions", setOf("finance", "transactions", "list", "recent", "history", "transacciones", "listar", "historial", "movimientos")),
        ToolEntry("finance_get_summary", "finance", "Get financial summary with totals by type", setOf("finance", "summary", "totals", "report", "overview", "resumen", "reporte", "totales")),
        ToolEntry("finance_search_transactions", "finance", "Search transactions by keyword", setOf("finance", "search", "find", "query", "buscar", "encontrar", "transaccion")),
        ToolEntry("finance_delete_transaction", "finance", "Delete a transaction by ID", setOf("finance", "delete", "remove", "eliminar", "borrar", "transaccion")),
        ToolEntry("finance_get_balance", "finance", "Get net balance (income - expenses) for a period", setOf("finance", "balance", "net", "income", "expenses", "saldo", "neto", "ingresos", "gastos")),
        ToolEntry("finance_export_csv", "finance", "Export financial transactions to a CSV file", setOf("finance", "export", "csv", "download", "file", "spreadsheet", "excel", "exportar", "archivo", "descargar")),

        // Presentations (PPTX)
        ToolEntry("pptx_create", "presentations", "Create a new PPTX presentation", setOf("pptx", "presentation", "powerpoint", "slides", "crear", "presentacion", "diapositivas")),
        ToolEntry("pptx_add_slide", "presentations", "Add a slide to a presentation", setOf("pptx", "slide", "add", "layout", "agregar", "diapositiva")),
        ToolEntry("pptx_add_text", "presentations", "Add text box to a slide", setOf("pptx", "text", "add", "font", "title", "texto", "titulo", "agregar")),
        ToolEntry("pptx_add_image", "presentations", "Add image to a slide from URL", setOf("pptx", "image", "picture", "photo", "add", "imagen", "foto", "agregar")),
        ToolEntry("pptx_add_shape", "presentations", "Add shape to a slide (rectangle, ellipse, etc)", setOf("pptx", "shape", "rectangle", "circle", "form", "forma", "rectangulo", "circulo")),
        ToolEntry("pptx_set_background", "presentations", "Set slide background color or image", setOf("pptx", "background", "color", "fondo")),
        ToolEntry("pptx_save", "presentations", "Save presentation as PPTX file", setOf("pptx", "save", "export", "file", "guardar", "exportar")),
        ToolEntry("pptx_list", "presentations", "List active presentations and saved files", setOf("pptx", "list", "presentations", "files", "listar", "presentaciones")),

        // Academic Search (Wikipedia, ArXiv)
        ToolEntry("wikipedia_search", "academic", "Search Wikipedia for general knowledge and encyclopedia articles", setOf("wikipedia", "wiki", "encyclopedia", "knowledge", "general", "article", "reference", "enciclopedia", "conocimiento")),
        ToolEntry("arxiv_search", "academic", "Search arXiv for academic papers and research articles", setOf("arxiv", "paper", "research", "academic", "science", "physics", "math", "computer science", "preprint", "paper", "cientifico", "investigacion")),

        // Weather
        ToolEntry("weather_current", "weather", "Get current weather conditions for a location", setOf("weather", "current", "temperature", "humidity", "wind", "clima", "tiempo", "temperatura", "humedad")),
        ToolEntry("weather_forecast", "weather", "Get weather forecast for the next days", setOf("weather", "forecast", "prediction", "future", "prono", "pronostico", "pronostico", "prevision")),
        ToolEntry("weather_air_quality", "weather", "Get air quality index and pollutant levels", setOf("weather", "air", "quality", "pollution", "aqi", "pm2.5", "ozone", "calidad aire", "contaminacion")),

        // Image Generation (DALL-E)
        ToolEntry("generate_image_dalle", "image_generation", "Generate images using DALL-E AI", setOf("image", "generate", "create", "picture", "art", "dalle", "ai art", "illustration", "imagen", "generar", "crear", "ilustracion", "dibujo")),
        ToolEntry("generate_image_google", "image_generation", "Generate images using Gemini 3.1 Flash Image Preview (Nano Banana 2)", setOf("image", "generate", "create", "picture", "art", "google", "gemini", "nano", "banana", "ai art", "illustration", "alternative", "3.1", "flash"))
    )

    /**
     * Search for tools matching a query. Returns matching entries grouped by category.
     * Also includes all tools from matched categories for completeness.
     *
     * @param query The search keywords
     * @param availableToolNames Set of tool names actually available (configured + enabled)
     */
    fun search(query: String, availableToolNames: Set<String>): ToolSearchResult {
        val queryWords = query.lowercase()
            .replace(Regex("[^a-záéíóúñü0-9\\s]"), " ")
            .split("\\s+".toRegex())
            .filter { it.length > 1 }
            .toSet()

        if (queryWords.isEmpty()) {
            return listAllCategories(availableToolNames)
        }

        val scored = registry
            .filter { it.name in availableToolNames }
            .map { entry ->
                val score = queryWords.count { word ->
                    word in entry.keywords ||
                        entry.category.contains(word) ||
                        entry.name.contains(word) ||
                        entry.description.lowercase().contains(word)
                }
                entry to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

        if (scored.isEmpty()) {
            return listAllCategories(availableToolNames)
        }

        // Include all tools from matched categories
        val matchedCategories = scored.map { it.first.category }.toSet()
        val allMatchedTools = registry
            .filter { it.category in matchedCategories && it.name in availableToolNames }

        val toolNames = allMatchedTools.map { it.name }.toSet()

        val message = buildString {
            appendLine("Found ${allMatchedTools.size} tools:")
            appendLine()
            matchedCategories.forEach { cat ->
                val catTools = allMatchedTools.filter { it.category == cat }
                appendLine("[$cat]")
                catTools.forEach { tool ->
                    appendLine("  - ${tool.name}: ${tool.description}")
                }
            }
            appendLine()
            appendLine("These tools are now available. Call them directly.")
        }

        Log.d(TAG, "Search '$query' found ${toolNames.size} tools in categories: $matchedCategories")

        return ToolSearchResult(
            found = true,
            message = message,
            toolNames = toolNames
        )
    }

    private fun listAllCategories(availableToolNames: Set<String>): ToolSearchResult {
        val available = registry.filter { it.name in availableToolNames }
        val categories = available.map { it.category }.toSet()

        val message = buildString {
            appendLine("No specific match. Available tool categories:")
            categories.forEach { cat ->
                val count = available.count { it.category == cat }
                appendLine("  - $cat ($count tools)")
            }
            appendLine()
            appendLine("Try searching with more specific keywords.")
        }

        return ToolSearchResult(found = false, message = message, toolNames = emptySet())
    }

    /**
     * Get all tool names in the registry (for computing available set).
     */
    fun getAllRegisteredToolNames(): Set<String> = registry.map { it.name }.toSet()

    data class ToolSearchResult(
        val found: Boolean,
        val message: String,
        val toolNames: Set<String>
    )
}
