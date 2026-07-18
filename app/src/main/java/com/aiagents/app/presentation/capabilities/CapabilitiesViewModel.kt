package com.aiagents.app.presentation.capabilities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.capabilities.CapabilityCatalog
import com.aiagents.app.data.local.MCPDao
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.repository.SkillRepository
import com.aiagents.app.domain.model.CapabilityCategory
import com.aiagents.app.domain.model.Skill
import com.aiagents.app.domain.model.SkillStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ToolCapabilityUi(
    val name: String,
    val category: CapabilityCategory,
    val activeSkillNames: List<String>,
    val requiredMcpId: String?,
    val mcpEnabled: Boolean,
    val mcpConfigured: Boolean
) {
    val enabledBySkill: Boolean get() = activeSkillNames.isNotEmpty()
    val isAvailable: Boolean get() = enabledBySkill &&
        (requiredMcpId == null || (mcpEnabled && mcpConfigured))
}

data class McpCapabilityUi(
    val id: String,
    val name: String,
    val description: String,
    val enabled: Boolean,
    val configured: Boolean,
    val toolCount: Int
)

@HiltViewModel
class CapabilitiesViewModel @Inject constructor(
    private val skillRepository: SkillRepository,
    private val mcpDao: MCPDao,
    private val securePreferences: SecurePreferences
) : ViewModel() {
    val skills = skillRepository.observeSkills().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val toolCapabilities = combine(
        skillRepository.observeSkills(),
        mcpDao.getAllServers()
    ) { skills, servers ->
        val activeSkills = skills.filter { it.status == SkillStatus.ACTIVE }
        val enabledMcpIds = servers.filter { it.isEnabled }.mapTo(hashSetOf()) { it.id }
        CapabilityCatalog.knownToolNames.sorted().map { toolName ->
            val mcpId = CapabilityCatalog.mcpIdForTool(toolName)
            ToolCapabilityUi(
                name = toolName,
                category = CapabilityCatalog.categoryForTool(toolName),
                activeSkillNames = activeSkills.filter { toolName in it.requiredTools }.map(Skill::name),
                requiredMcpId = mcpId,
                mcpEnabled = mcpId == null || mcpId in enabledMcpIds,
                mcpConfigured = mcpId == null || isConfigured(mcpId)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mcpCapabilities = mcpDao.getAllServers().combine(skills) { servers, _ ->
        val byId = servers.associateBy { it.id }
        CapabilityCatalog.mcpCapabilities.map { capability ->
            val row = byId[capability.id]
            McpCapabilityUi(
                id = capability.id,
                name = capability.name,
                description = capability.description,
                enabled = row?.isEnabled == true,
                configured = isConfigured(capability.id),
                toolCount = capability.toolNames.size
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSkillEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { skillRepository.setEnabled(id, enabled) }
    }

    fun setMcpEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            mcpDao.setServerEnabled(id, enabled)
            when (id) {
                "pubmed" -> securePreferences.setPubMedEnabled(enabled)
                "finance" -> securePreferences.setFinanceEnabled(enabled)
            }
        }
    }

    private fun isConfigured(id: String): Boolean = when (id) {
        "brave_search" -> securePreferences.hasBraveApiKey()
        "serpapi" -> securePreferences.hasSerpApiKey()
        "google_maps" -> securePreferences.hasGoogleMapsApiKey()
        "canva" -> securePreferences.hasCanvaAccessToken()
        "obsidian" -> securePreferences.hasObsidianVaultPath()
        "github" -> securePreferences.hasGitHubToken()
        "notion" -> securePreferences.hasNotionToken()
        "slack" -> securePreferences.hasSlackToken()
        "google_drive" -> securePreferences.hasGoogleWorkspaceConfig()
        "pubmed", "finance" -> true
        else -> false
    }
}
