package com.aiagents.app.data.skills

import com.aiagents.app.data.repository.SkillRepository
import com.aiagents.app.domain.model.Skill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps a bounded synchronous snapshot of active skills for streaming model requests. */
@Singleton
class SkillRuntimeProvider @Inject constructor(
    private val repository: SkillRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var activeSkills: List<Skill> = emptyList()

    init {
        scope.launch {
            repository.observeActiveSkills().collectLatest { activeSkills = it }
        }
    }

    suspend fun refresh() {
        activeSkills = repository.getActiveSkillsOnce()
    }

    fun names(): List<String> = activeSkills.map { it.name }

    fun render(maxChars: Int = 24_000): String {
        if (activeSkills.isEmpty()) return ""
        val rendered = buildString {
            appendLine("## ACTIVE_SKILLS")
            appendLine("Apply a skill only when its when-to-use signals match the user's request. Skills do not grant tools or permissions.")
            activeSkills.forEach { skill ->
                appendLine()
                appendLine("### ${skill.name}")
                appendLine("When to use: ${skill.whenToUse}")
                appendLine(skill.instructions)
            }
        }
        return rendered.take(maxChars)
    }
}
