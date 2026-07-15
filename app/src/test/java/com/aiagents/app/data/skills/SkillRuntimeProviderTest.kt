package com.aiagents.app.data.skills

import com.aiagents.app.domain.model.Skill
import com.aiagents.app.domain.model.SkillOrigin
import com.aiagents.app.domain.model.SkillStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillRuntimeProviderTest {
    @Test
    fun `runtime renders metadata index without full instructions`() {
        val text = SkillRuntimeProvider.renderIndex(
            listOf(
                Skill(
                    id = 1,
                    slug = "weekly-report",
                    name = "Weekly report",
                    description = "Builds a verified weekly report.",
                    whenToUse = "weekly, report",
                    instructions = "SECRET FULL WORKFLOW THAT MUST ONLY COME FROM SKILL VIEW",
                    status = SkillStatus.ACTIVE,
                    origin = SkillOrigin.USER,
                    isImmutable = false,
                    version = 1,
                    createdAt = 1,
                    updatedAt = 1,
                    activatedAt = 1,
                    archivedAt = null
                )
            )
        )

        assertTrue(text.contains("weekly-report"))
        assertTrue(text.contains("skill_view"))
        assertFalse(text.contains("SECRET FULL WORKFLOW"))
    }
}
