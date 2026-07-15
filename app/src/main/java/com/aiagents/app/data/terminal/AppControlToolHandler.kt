package com.aiagents.app.data.terminal

import android.util.Log
import com.aiagents.app.data.local.AgentDao
import com.aiagents.app.data.local.MCPDao
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.local.WorkspaceDao
import com.aiagents.app.data.model.AgentEntity
import com.aiagents.app.domain.model.ProviderType
import com.google.gson.Gson
import com.google.gson.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

data class AppControlResult(
    val toolCallId: String,
    val toolName: String = TOOL_NAME,
    val success: Boolean,
    val content: String
) {
    companion object {
        const val TOOL_NAME = "app_control"
    }
}

@Singleton
class AppControlToolHandler @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val workspaceDao: WorkspaceDao,
    private val agentDao: AgentDao,
    private val mcpDao: MCPDao
) {
    companion object {
        private const val TAG = "AppControlToolHandler"
        const val TOOL_NAME = "app_control"

        val ALL_TOOL_NAMES = setOf(TOOL_NAME)

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_NAME,
                    "description" to """Control the app's settings and configuration. Actions:

MODEL:
- "get_status": Current model, provider, workspace info, and configured services
- "list_models": List all available models (selected by user)
- "set_model": Change active model. Params: model (format "PROVIDER|modelId", e.g. "OPENROUTER|google/gemini-2.5-flash"), workspace_id (optional, defaults to current)
- "list_providers": List all providers and their configuration status

SERVICES:
- "list_services": List all MCP services and their enabled/configured status
- "toggle_service": Enable/disable a service. Params: service (brave_search|google_maps|serpapi|pubmed|finance|weather|image_generation), enabled (true/false)

AGENTS:
- "list_agents": List all agents with their config summary
- "get_agent": Get full agent details. Params: name or id
- "update_agent": Modify agent settings. Params: id (required), and any of: temperature, enableTerminal, enabledTools, sarcasmLevel, creativityLevel, formalityLevel, empathyLevel, technicalPrecision, systemPrompt, whenToUse

DISPLAY:
- "set_display": Toggle display options. Params: show_reasoning (true/false), show_commands (true/false)""",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "action" to mapOf(
                                "type" to "string",
                                "description" to "The action to perform"
                            ),
                            "params" to mapOf(
                                "type" to "object",
                                "description" to "Action-specific parameters"
                            )
                        ),
                        "required" to listOf("action")
                    )
                )
            )
        )
    }

    private val gson = Gson()

    suspend fun executeTool(
        toolCallId: String,
        arguments: String,
        currentWorkspaceId: Long
    ): AppControlResult {
        return try {
            val args = gson.fromJson(arguments, JsonObject::class.java) ?: JsonObject()
            val action = args.get("action")?.asString
                ?: return AppControlResult(toolCallId, success = false, content = "Missing 'action' parameter.")
            val params = args.getAsJsonObject("params") ?: JsonObject()

            when (action) {
                "get_status" -> getStatus(toolCallId, currentWorkspaceId)
                "list_models" -> listModels(toolCallId)
                "set_model" -> setModel(toolCallId, params, currentWorkspaceId)
                "list_providers" -> listProviders(toolCallId)
                "list_services" -> listServices(toolCallId)
                "toggle_service" -> toggleService(toolCallId, params)
                "list_agents" -> listAgents(toolCallId)
                "get_agent" -> getAgent(toolCallId, params)
                "update_agent" -> updateAgent(toolCallId, params)
                "set_display" -> setDisplay(toolCallId, params)
                else -> AppControlResult(toolCallId, success = false, content = "Unknown action: $action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing app_control", e)
            AppControlResult(toolCallId, success = false, content = "Error: ${e.message}")
        }
    }

    // ── get_status ───────────────────────────────────────────────────────────

    private suspend fun getStatus(toolCallId: String, workspaceId: Long): AppControlResult {
        val workspace = workspaceDao.getWorkspaceById(workspaceId)
        val activeProvider = securePreferences.getActiveProvider()
        val showReasoning = securePreferences.getShowReasoning()
        val showCommands = securePreferences.getShowCommands()

        val services = buildList {
            if (securePreferences.hasBraveApiKey()) add("Brave Search")
            if (securePreferences.hasSerpApiKey()) add("SerpAPI")
            if (securePreferences.hasGoogleMapsApiKey()) add("Google Maps")
            if (securePreferences.hasGitHubToken()) add("GitHub")
            if (securePreferences.hasNotionToken()) add("Notion")
            if (securePreferences.hasSlackToken()) add("Slack")
            if (securePreferences.hasGoogleWorkspaceConfig()) add("Google Workspace")
            if (securePreferences.hasObsidianVaultPath()) add("Obsidian")
            if (securePreferences.hasCanvaAccessToken()) add("Canva")
            if (securePreferences.isFinanceEnabled()) add("Finance")
            if (securePreferences.isPubMedEnabled()) add("PubMed")
            if (securePreferences.isWeatherEnabled()) add("Weather")
            if (securePreferences.isImageGenerationEnabled()) add("Image Gen")
        }

        val info = buildString {
            appendLine("Workspace: ${workspace?.name ?: "Unknown"} (id: $workspaceId)")
            appendLine("Model: ${workspace?.selectedModel ?: "none"}")
            appendLine("Provider: ${activeProvider?.name ?: "auto"}")
            appendLine("Display: reasoning=${showReasoning}, commands=${showCommands}")
            if (services.isNotEmpty()) {
                appendLine("Active services: ${services.joinToString(", ")}")
            } else {
                appendLine("Active services: none")
            }
        }
        return AppControlResult(toolCallId, success = true, content = info.trim())
    }

    // ── list_models ──────────────────────────────────────────────────────────

    private fun listModels(toolCallId: String): AppControlResult {
        val models = securePreferences.getSelectedModels()
        if (models.isEmpty()) {
            return AppControlResult(toolCallId, success = true, content = "No models selected. User needs to add models in Providers settings.")
        }
        val grouped = models.sorted().groupBy { it.substringBefore("|") }
        val info = buildString {
            appendLine("Available models (${models.size}):")
            grouped.forEach { (provider, modelList) ->
                appendLine("[$provider]")
                modelList.forEach { fullKey ->
                    appendLine("  - $fullKey")
                }
            }
            appendLine()
            appendLine("Use set_model with the full key (e.g. \"OPENROUTER|google/gemini-2.5-flash\")")
        }
        return AppControlResult(toolCallId, success = true, content = info.trim())
    }

    // ── set_model ────────────────────────────────────────────────────────────

    private suspend fun setModel(toolCallId: String, params: JsonObject, defaultWorkspaceId: Long): AppControlResult {
        val model = params.get("model")?.asString
            ?: return AppControlResult(toolCallId, success = false, content = "Missing 'model' param. Use format 'PROVIDER|modelId'.")

        if ("|" !in model) {
            return AppControlResult(toolCallId, success = false, content = "Invalid format. Use 'PROVIDER|modelId' (e.g. 'OPENROUTER|google/gemini-2.5-flash').")
        }

        val selectedModels = securePreferences.getSelectedModels()
        if (model !in selectedModels) {
            // Check case-insensitive
            val match = selectedModels.find { it.equals(model, ignoreCase = true) }
            if (match == null) {
                return AppControlResult(toolCallId, success = false, content = "Model '$model' is not in the selected models list. Available: ${selectedModels.sorted().joinToString(", ")}")
            }
            // Use the correctly-cased version
            val wsId = params.get("workspace_id")?.asLong ?: defaultWorkspaceId
            workspaceDao.setSelectedModel(wsId, match)
            return AppControlResult(toolCallId, success = true, content = "Model changed to: $match")
        }

        val wsId = params.get("workspace_id")?.asLong ?: defaultWorkspaceId
        workspaceDao.setSelectedModel(wsId, model)
        return AppControlResult(toolCallId, success = true, content = "Model changed to: $model")
    }

    // ── list_providers ───────────────────────────────────────────────────────

    private fun listProviders(toolCallId: String): AppControlResult {
        val active = securePreferences.getActiveProvider()
        val info = buildString {
            appendLine("Providers:")
            ProviderType.entries.forEach { provider ->
                val configured = when (provider) {
                    ProviderType.OLLAMA -> true // no key needed
                    ProviderType.LM_STUDIO -> true // no key needed
                    ProviderType.LOCAL -> true   // local models
                    else -> securePreferences.hasApiKey(provider)
                }
                val status = if (configured) "configured" else "not configured"
                val activeMarker = if (provider == active) " [ACTIVE]" else ""
                appendLine("  - ${provider.name}: $status$activeMarker")
            }
        }
        return AppControlResult(toolCallId, success = true, content = info.trim())
    }

    // ── list_services ────────────────────────────────────────────────────────

    private fun listServices(toolCallId: String): AppControlResult {
        val info = buildString {
            appendLine("MCP Services:")
            appendLine("  brave_search: ${if (securePreferences.hasBraveApiKey()) "configured" else "no API key"}")
            appendLine("  google_maps: ${if (securePreferences.hasGoogleMapsApiKey()) "configured" else "no API key"}")
            appendLine("  serpapi: ${if (securePreferences.hasSerpApiKey()) "configured" else "no API key"}")
            appendLine("  github: ${if (securePreferences.hasGitHubToken()) "configured" else "no token"}")
            appendLine("  notion: ${if (securePreferences.hasNotionToken()) "configured" else "no token"}")
            appendLine("  slack: ${if (securePreferences.hasSlackToken()) "configured" else "no token"}")
            appendLine("  google_workspace: ${if (securePreferences.hasGoogleWorkspaceConfig()) "configured" else "not configured"}")
            appendLine("  obsidian: ${if (securePreferences.hasObsidianVaultPath()) "configured" else "no vault path"}")
            appendLine("  canva: ${if (securePreferences.hasCanvaAccessToken()) "configured" else "no token"}")
            appendLine()
            appendLine("Toggleable features:")
            appendLine("  finance: ${if (securePreferences.isFinanceEnabled()) "ON" else "OFF"}")
            appendLine("  pubmed: ${if (securePreferences.isPubMedEnabled()) "ON" else "OFF"}")
            appendLine("  weather: ${if (securePreferences.isWeatherEnabled()) "ON" else "OFF"}")
            appendLine("  image_generation: ${if (securePreferences.isImageGenerationEnabled()) "ON" else "OFF"}")
        }
        return AppControlResult(toolCallId, success = true, content = info.trim())
    }

    // ── toggle_service ───────────────────────────────────────────────────────

    private fun toggleService(toolCallId: String, params: JsonObject): AppControlResult {
        val service = params.get("service")?.asString
            ?: return AppControlResult(toolCallId, success = false, content = "Missing 'service' param.")
        val enabled = params.get("enabled")?.asBoolean
            ?: return AppControlResult(toolCallId, success = false, content = "Missing 'enabled' param (true/false).")

        return when (service) {
            "finance" -> {
                securePreferences.setFinanceEnabled(enabled)
                AppControlResult(toolCallId, success = true, content = "Finance ${if (enabled) "enabled" else "disabled"}.")
            }
            "pubmed" -> {
                securePreferences.setPubMedEnabled(enabled)
                AppControlResult(toolCallId, success = true, content = "PubMed ${if (enabled) "enabled" else "disabled"}.")
            }
            "weather" -> {
                securePreferences.setWeatherEnabled(enabled)
                AppControlResult(toolCallId, success = true, content = "Weather ${if (enabled) "enabled" else "disabled"}.")
            }
            "image_generation" -> {
                securePreferences.setImageGenerationEnabled(enabled)
                AppControlResult(toolCallId, success = true, content = "Image generation ${if (enabled) "enabled" else "disabled"}.")
            }
            else -> AppControlResult(toolCallId, success = false, content = "Service '$service' is not toggleable. Only finance, pubmed, weather, image_generation can be toggled. Services like brave_search, github, etc. require API keys configured in Settings.")
        }
    }

    // ── list_agents ──────────────────────────────────────────────────────────

    private suspend fun listAgents(toolCallId: String): AppControlResult {
        val agents = agentDao.getAllAgentsOnce()
        val info = buildString {
            appendLine("Agents (${agents.size}):")
            agents.forEach { agent ->
                val tools = if (agent.enabledTools.isBlank()) "all" else agent.enabledTools
                val terminal = if (agent.enableTerminal) "shell" else "no-shell"
                val system = if (agent.isSystemAgent) " [SYSTEM]" else ""
                appendLine("  - ${agent.name} (id:${agent.id}) | ${agent.role} | temp:${agent.temperature} | $terminal | tools:$tools$system")
            }
        }
        return AppControlResult(toolCallId, success = true, content = info.trim())
    }

    // ── get_agent ────────────────────────────────────────────────────────────

    private suspend fun getAgent(toolCallId: String, params: JsonObject): AppControlResult {
        val agent = params.get("id")?.asLong?.let { agentDao.getAgentById(it) }
            ?: params.get("name")?.asString?.let { agentDao.getAgentByName(it) }
            ?: return AppControlResult(toolCallId, success = false, content = "Provide 'id' (number) or 'name' (string).")

        val info = buildString {
            appendLine("Agent: ${agent.name} (id: ${agent.id})")
            appendLine("Role: ${agent.role}")
            appendLine("Temperature: ${agent.temperature}")
            appendLine("Max tokens: ${agent.maxTokens}")
            appendLine("Terminal: ${agent.enableTerminal}")
            appendLine("Enabled tools: ${agent.enabledTools.ifBlank { "all" }}")
            appendLine("When to use: ${agent.whenToUse.ifBlank { "(not set)" }}")
            appendLine("Personality: sarcasm=${agent.sarcasmLevel} creativity=${agent.creativityLevel} formality=${agent.formalityLevel} empathy=${agent.empathyLevel} precision=${agent.technicalPrecision}")
            appendLine("System agent: ${agent.isSystemAgent}")
            appendLine("Local routing: ${agent.useLocalRouting}")
            appendLine("System prompt (first 200 chars): ${agent.systemPrompt.take(200)}...")
        }
        return AppControlResult(toolCallId, success = true, content = info.trim())
    }

    // ── update_agent ─────────────────────────────────────────────────────────

    private suspend fun updateAgent(toolCallId: String, params: JsonObject): AppControlResult {
        val agentId = params.get("id")?.asLong
            ?: return AppControlResult(toolCallId, success = false, content = "Missing 'id' param.")

        val agent = agentDao.getAgentById(agentId)
            ?: return AppControlResult(toolCallId, success = false, content = "Agent with id $agentId not found.")

        val changes = mutableListOf<String>()

        var updated = agent.copy()

        params.get("temperature")?.asFloat?.let {
            updated = updated.copy(temperature = it.coerceIn(0f, 2f))
            changes.add("temperature → $it")
        }
        params.get("enableTerminal")?.asBoolean?.let {
            updated = updated.copy(enableTerminal = it)
            changes.add("enableTerminal → $it")
        }
        params.get("enabledTools")?.asString?.let {
            updated = updated.copy(enabledTools = it)
            changes.add("enabledTools → ${it.ifBlank { "all" }}")
        }
        params.get("sarcasmLevel")?.asInt?.let {
            updated = updated.copy(sarcasmLevel = it.coerceIn(0, 100))
            changes.add("sarcasmLevel → $it")
        }
        params.get("creativityLevel")?.asInt?.let {
            updated = updated.copy(creativityLevel = it.coerceIn(0, 100))
            changes.add("creativityLevel → $it")
        }
        params.get("formalityLevel")?.asInt?.let {
            updated = updated.copy(formalityLevel = it.coerceIn(0, 100))
            changes.add("formalityLevel → $it")
        }
        params.get("empathyLevel")?.asInt?.let {
            updated = updated.copy(empathyLevel = it.coerceIn(0, 100))
            changes.add("empathyLevel → $it")
        }
        params.get("technicalPrecision")?.asInt?.let {
            updated = updated.copy(technicalPrecision = it.coerceIn(0, 100))
            changes.add("technicalPrecision → $it")
        }
        params.get("systemPrompt")?.asString?.let {
            updated = updated.copy(systemPrompt = it)
            changes.add("systemPrompt updated (${it.length} chars)")
        }
        params.get("whenToUse")?.asString?.let {
            updated = updated.copy(whenToUse = it)
            changes.add("whenToUse → $it")
        }
        params.get("maxTokens")?.asInt?.let {
            updated = updated.copy(maxTokens = it.coerceIn(256, 128000))
            changes.add("maxTokens → $it")
        }

        if (changes.isEmpty()) {
            return AppControlResult(toolCallId, success = false, content = "No changes specified. Provide at least one field to update.")
        }

        agentDao.updateAgent(updated)
        Log.i(TAG, "Updated agent '${agent.name}' (id: $agentId): $changes")

        return AppControlResult(toolCallId, success = true, content = "Updated agent '${agent.name}': ${changes.joinToString(", ")}")
    }

    // ── set_display ──────────────────────────────────────────────────────────

    private fun setDisplay(toolCallId: String, params: JsonObject): AppControlResult {
        val changes = mutableListOf<String>()

        params.get("show_reasoning")?.asBoolean?.let {
            securePreferences.setShowReasoning(it)
            changes.add("show_reasoning → $it")
        }
        params.get("show_commands")?.asBoolean?.let {
            securePreferences.setShowCommands(it)
            changes.add("show_commands → $it")
        }

        if (changes.isEmpty()) {
            return AppControlResult(toolCallId, success = false, content = "Provide show_reasoning and/or show_commands (true/false).")
        }

        return AppControlResult(toolCallId, success = true, content = "Display updated: ${changes.joinToString(", ")}")
    }
}
