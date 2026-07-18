package com.aiagents.app.data.capabilities

import com.aiagents.app.domain.model.CapabilityCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityCatalogTest {
    @Test
    fun `every built in skill owns at least one known tool`() {
        assertTrue(CapabilityCatalog.builtInSkills.isNotEmpty())
        assertTrue(CapabilityCatalog.builtInSkills.all { it.requiredTools.isNotEmpty() })
        assertEquals(
            CapabilityCatalog.knownToolNames,
            CapabilityCatalog.builtInSkills.flatMapTo(linkedSetOf()) { it.requiredTools }
        )
    }

    @Test
    fun `custom skill dependencies are detected from exact tool names`() {
        val detected = CapabilityCatalog.detectRequiredTools(
            "Primero usa weather_current y después web_fetch. No uses weather_current_extra."
        )

        assertTrue("weather_current" in detected)
        assertTrue("web_fetch" in detected)
        assertFalse("weather_current_extra" in detected)
        assertEquals(CapabilityCategory.KNOWLEDGE, CapabilityCatalog.inferCategory(detected))
    }

    @Test
    fun `every MCP tool is also owned by a skill`() {
        val mcpTools = CapabilityCatalog.mcpCapabilities.flatMapTo(linkedSetOf()) { it.toolNames }
        assertTrue(mcpTools.isNotEmpty())
        assertTrue(CapabilityCatalog.knownToolNames.containsAll(mcpTools))
    }
}
