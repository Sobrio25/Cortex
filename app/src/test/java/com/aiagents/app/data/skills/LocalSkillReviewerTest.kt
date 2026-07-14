package com.aiagents.app.data.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSkillReviewerTest {
    @Test
    fun `creates draft only for repeated intent`() {
        val candidate = LocalSkillReviewer.review(
            """
            USUARIO: Genera el reporte semanal de ventas por región
            ASISTENTE: Claro, prepararé el resumen
            USUARIO: Crea otro reporte semanal de ventas con las regiones
            USUARIO: Qué hora es en Tokio
            """.trimIndent()
        )

        assertNotNull(candidate)
        assertTrue(candidate!!.draft.name.contains("Reporte"))
        assertTrue(candidate.draft.whenToUse.contains("ventas"))
        assertTrue(candidate.draft.instructions.contains("No guardes credenciales"))
    }

    @Test
    fun `skips unrelated one-off requests`() {
        val candidate = LocalSkillReviewer.review(
            """
            USUARIO: Muéstrame el clima para mañana
            USUARIO: Escribe una función Kotlin para ordenar listas
            USUARIO: Resume este libro de historia
            """.trimIndent()
        )

        assertNull(candidate)
    }

    @Test
    fun `never promotes candidate beyond draft data`() {
        val candidate = LocalSkillReviewer.review(
            """
            USUARIO: Analiza facturas mensuales del negocio
            USUARIO: Revisa las facturas mensuales de mi negocio
            """.trimIndent()
        )

        assertNotNull(candidate)
        assertEquals(4, candidate!!.draft.let { listOf(it.name, it.description, it.whenToUse, it.instructions) }.size)
    }
}
