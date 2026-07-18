package com.aiagents.app.data.skills

import com.aiagents.app.data.local.MCPDao
import com.aiagents.app.data.repository.SkillRepository
import com.aiagents.app.domain.model.Skill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps a bounded synchronous skill index for progressive disclosure at model runtime. */
@Singleton
class SkillRuntimeProvider @Inject constructor(
    private val repository: SkillRepository,
    private val mcpDao: MCPDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var activeSkills: List<Skill> = emptyList()
    @Volatile
    private var enabledMcpIds: Set<String> = emptySet()
    @Volatile
    private var revision: Long = 0

    init {
        scope.launch {
            combine(repository.observeActiveSkills(), mcpDao.getAllServers()) { skills, servers ->
                skills to servers.filter { it.isEnabled }.mapTo(linkedSetOf()) { it.id }
            }.collectLatest { (skills, mcpIds) ->
                activeSkills = skills
                enabledMcpIds = mcpIds
                revision += 1
            }
        }
    }

    suspend fun refresh() {
        activeSkills = repository.getActiveSkillsOnce()
        enabledMcpIds = mcpDao.getAllServers().first()
            .filter { it.isEnabled }
            .mapTo(linkedSetOf()) { it.id }
        revision += 1
    }

    fun names(): List<String> = activeSkills.map { it.name }

    fun enabledToolNames(): Set<String> =
        activeSkills.flatMapTo(linkedSetOf()) { it.requiredTools }

    fun isToolEnabled(toolName: String): Boolean = activeSkills.any { toolName in it.requiredTools }

    fun activeSkillNamesFor(toolName: String): List<String> =
        activeSkills.filter { toolName in it.requiredTools }.map { it.name }

    fun isMcpEnabled(id: String): Boolean = id in enabledMcpIds

    fun revision(): Long = revision

    fun render(maxChars: Int = MAX_INDEX_CHARS): String = renderIndex(activeSkills, maxChars)

    companion object {
        const val MAX_INDEX_CHARS = 2_400
        private const val OMITTED_RESERVE_CHARS = 96

        internal fun renderIndex(skills: List<Skill>, maxChars: Int = MAX_INDEX_CHARS): String {
            if (skills.isEmpty()) return ""
            val header = buildString {
                appendLine("## AVAILABLE_SKILLS")
                appendLine("Skills are indexed below, not loaded. Use `skill_view` before applying one; use `skill_list` to search omitted skills. Skills never grant tools or permissions.")
            }
            val output = StringBuilder(header)
            var included = 0
            for (skill in skills) {
                val description = skill.description.replace(Regex("\\s+"), " ").trim().take(180)
                val signals = skill.whenToUse.replace(Regex("\\s+"), " ").trim().take(120)
                val entry = buildString {
                    append("- ${skill.slug}: $description")
                    if (signals.isNotBlank()) append(" | signals: $signals")
                    appendLine()
                }
                if (output.length + entry.length + OMITTED_RESERVE_CHARS > maxChars) break
                output.append(entry)
                included += 1
            }
            val omitted = skills.size - included
            if (omitted > 0) {
                output.append("- … $omitted more skill(s) omitted; search with `skill_list`.\n")
            }
            return output.toString().take(maxChars)
        }
    }
}
