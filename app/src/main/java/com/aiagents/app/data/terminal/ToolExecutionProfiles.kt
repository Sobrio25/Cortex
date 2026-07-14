package com.aiagents.app.data.terminal

/** Tool definitions must never advertise capabilities the current dispatcher cannot execute. */
object ToolExecutionProfiles {
    private val FILES = setOf(
        "read_text_file", "read_image_file", "read_pdf_file", "write_file", "list_files"
    )
    private val LEGACY_WEB = setOf("duckduckgo_search", "brave_web_search", "serpapi_search")

    /** Legacy fallback. New subagents receive a task-specific capability set. */
    val SUBAGENT: Set<String> = FILES.filterNot { it == "write_file" }.toSet() + LEGACY_WEB +
        UnifiedWebToolHandler.ALL_TOOL_NAMES + setOf("skill_list") + MemoryToolHandler.READ_TOOL_NAMES

    val BACKGROUND: Set<String> = FILES + LEGACY_WEB +
        UnifiedWebToolHandler.ALL_TOOL_NAMES +
        setOf("execute_command", "skill_list") +
        MemoryToolHandler.READ_TOOL_NAMES +
        GitHubToolHandler.ALL_TOOL_NAMES +
        NotionToolHandler.ALL_TOOL_NAMES +
        SlackToolHandler.ALL_TOOL_NAMES +
        setOf(AppControlToolHandler.TOOL_NAME)
}
