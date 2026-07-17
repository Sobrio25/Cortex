package com.aiagents.app.presentation.assistant

import com.aiagents.app.domain.model.AndroidAppControlBuiltin
import com.aiagents.app.domain.model.WeatherWidgetsBuiltin
import org.junit.Assert.assertTrue
import org.junit.Test

class CortexAssistantPromptTest {
    @Test
    fun `assistant prompt enforces a brief final response`() {
        val prompt = CortexAssistantPrompt.SYSTEM_INSTRUCTIONS

        assertTrue(prompt.contains("at most 30 words"))
        assertTrue(prompt.contains("1–2 short sentences"))
        assertTrue(prompt.contains("ALWAYS-READY PHONE CAPABILITIES"))
        assertTrue(prompt.contains("plain, natural spoken sentences"))
        assertTrue(prompt.contains("Spell out units, percentages, labels, and abbreviations"))
    }

    @Test
    fun `assistant preloads phone action tool families`() {
        val tools = CortexAssistantPrompt.ALWAYS_ACTIVE_TOOL_NAMES

        assertTrue("weather_current" in tools)
        assertTrue("set_reminder" in tools)
        assertTrue("add_calendar_event" in tools)
        assertTrue("device_control" in tools)
        assertTrue(CortexAssistantPrompt.ALLOWED_TOOL_NAMES == tools)
        assertTrue("memory" !in CortexAssistantPrompt.ALLOWED_TOOL_NAMES)
    }

    @Test
    fun `assistant prompt fully preloads weather and phone action skills`() {
        assertTrue(CortexAssistantPrompt.SYSTEM_INSTRUCTIONS.contains(WeatherWidgetsBuiltin.instructions))
        assertTrue(CortexAssistantPrompt.SYSTEM_INSTRUCTIONS.contains(AndroidAppControlBuiltin.instructions))
        assertTrue(CortexAssistantPrompt.SYSTEM_INSTRUCTIONS.contains("without calling `skill_view`"))
        assertTrue(CortexAssistantPrompt.SYSTEM_INSTRUCTIONS.contains("prepare_whatsapp_message"))
        assertTrue(CortexAssistantPrompt.SYSTEM_INSTRUCTIONS.contains("call_phone"))
    }

    @Test
    fun `assistant instructions survive an orchestrator prompt rebuild without duplication`() {
        val rebuiltPrompt = "## OPERATING MODEL\nUse tools when helpful."
        val enriched = CortexAssistantPrompt.appendTo(rebuiltPrompt, "es")

        assertTrue(enriched.startsWith(rebuiltPrompt))
        assertTrue(enriched.contains("language tag `es`"))
        assertTrue(
            CortexAssistantPrompt.appendTo(enriched, "es").indexOf(CortexAssistantPrompt.MODE_MARKER) ==
                enriched.lastIndexOf(CortexAssistantPrompt.MODE_MARKER)
        )
    }

    @Test
    fun `configured Cortex language overrides the Android runtime language`() {
        val instructions = CortexAssistantPrompt.instructionsFor("es_MX")

        assertTrue(instructions.contains("language tag `es-MX`"))
        assertTrue(instructions.contains("regardless of the Android device language"))
        assertTrue(instructions.contains("mathematical operation"))
        assertTrue(instructions.contains("at most 12 words"))
        assertTrue(instructions.contains("Timer, alarm, reminder"))
        assertTrue(instructions.contains("derives natural speech from `WEATHER_DATA`"))
        assertTrue(instructions.contains("Never send visual shorthand"))
    }
}
