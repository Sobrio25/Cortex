package com.aiagents.app.data.skills

import com.aiagents.app.data.memory.CortexMemoryAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundReviewParserTest {
    @Test
    fun `parses atomic memory operations and an automatic skill update`() {
        val outcome = BackgroundReviewParser.parse(
            """
            ```json
            {
              "memory_operations": [
                {"target":"user","action":"add","content":"Prefiere respuestas breves."},
                {"target":"memory","action":"replace","old_text":"Usa Java","content":"Usa Kotlin."}
              ],
              "skill_change": {
                "action":"update",
                "existing_skill_id":7,
                "name":"Reportes recurrentes",
                "description":"Genera reportes recurrentes con validación.",
                "when_to_use":"Cuando el usuario solicite un reporte periódico.",
                "instructions":"Reúne los datos, confirma el periodo, genera el reporte y valida los totales antes de terminar.",
                "reason":"El usuario corrigió el flujo."
              },
              "summary":"Se detectaron cambios duraderos."
            }
            ```
            """.trimIndent()
        )

        assertEquals(2, outcome.memoryOperations.size)
        assertEquals(BackgroundMemoryTarget.USER, outcome.memoryOperations.first().target)
        assertEquals(CortexMemoryAction.REPLACE, outcome.memoryOperations.last().operation.action)
        assertEquals(BackgroundSkillAction.UPDATE, outcome.skillChange?.action)
        assertEquals(7L, outcome.skillChange?.existingSkillId)
    }

    @Test
    fun `keeps valid memory when skill payload is underspecified`() {
        val outcome = BackgroundReviewParser.parse(
            """{
              "memory_operations":[{"target":"memory","action":"add","content":"Usa Kotlin."}],
              "skill_change":{"action":"create","name":"x"},
              "summary":"memory only"
            }"""
        )

        assertTrue(outcome.memoryOperations.isNotEmpty())
        assertNull(outcome.skillChange)
    }
}
