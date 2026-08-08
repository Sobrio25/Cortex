package com.aiagents.app.data.runtime

import com.aiagents.app.data.memory.CortexMemorySnapshot
import java.time.ZonedDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeContextProviderTest {
    @Test
    fun runtimeTimestampUsesMinutePrecisionForStableToolRounds() {
        val timestamp = RuntimeContextProvider.promptTimestamp(
            ZonedDateTime.parse("2026-07-13T12:30:47.123-06:00")
        )

        assertTrue(timestamp.contains("12:30"))
        assertFalse(timestamp.contains("12:30:47"))
    }

    @Test
    fun configuredAssistantNameIsAuthoritative() {
        val identity = RuntimeContextProvider.renderIdentity("Clawdy")

        assertTrue(identity.contains(RuntimeContextProvider.IDENTITY_MARKER))
        assertTrue(identity.contains("configured name for you is: Clawdy"))
        assertTrue(identity.contains("authoritative"))
        assertFalse(identity.contains("Cortex"))
    }

    @Test
    fun voiceIdentityContainsOnlyConfiguredNamesAndNoMemoryBlocks() {
        val identity = RuntimeContextProvider.renderVoiceIdentity(
            agentName = "Clawdy",
            userName = "Gabriel",
            preferredUserName = "Gabo"
        )

        assertTrue(identity.contains("Clawdy"))
        assertTrue(identity.contains("Gabo"))
        assertTrue(identity.contains(RuntimeContextProvider.VOICE_USER_MARKER))
        assertFalse(identity.contains(RuntimeContextProvider.MEMORY_MARKER))
        assertFalse(identity.contains(RuntimeContextProvider.USER_MARKER))
    }

    @Test
    fun localModelPromptKeepsOnlyIdentityDateAndWebTools() {
        val prompt = RuntimeContextProvider.renderLocalModelPrompt(
            agentName = "Atlas",
            userName = "Gabo",
            date = "2026-07-31"
        )

        assertTrue(prompt.contains("Agent: Atlas"))
        assertTrue(prompt.contains("User: Gabo"))
        assertTrue(prompt.contains("Date: 2026-07-31"))
        assertTrue(prompt.contains("web_search"))
        assertTrue(prompt.contains("web_fetch"))
        assertFalse(prompt.contains("MEMORY"))
        assertFalse(prompt.contains("SOUL"))
        assertFalse(prompt.contains("Android"))
    }

    @Test
    fun voiceSoulUsesDedicatedAssistantDocument() {
        val voiceSoul = RuntimeContextProvider.renderVoiceSoul(
            snapshot("# ASSISTANT_SOUL.md\n\nSé cálido y conciso.", maxChars = 20_000),
            fallbackAgentName = "Clawdy"
        )

        assertTrue(voiceSoul.contains(RuntimeContextProvider.VOICE_SOUL_MARKER))
        assertTrue(voiceSoul.contains("Sé cálido y conciso"))
        assertFalse(voiceSoul.contains("MEMORY.md"))
        assertFalse(voiceSoul.contains("USER.md"))
    }

    @Test
    fun renderIncludesTimeAndroidAndCompactToolCountsWithoutUserIdentity() {
        val text = RuntimeContextProvider.render(
            RuntimeSnapshot(
                isoDateTime = "2026-07-13T12:30:00-06:00",
                timeZone = "America/Mexico_City",
                localeTag = "es-MX",
                agentName = "Atlas",
                agentRole = "Agent Orchestrator",
                androidRelease = "15",
                androidApi = 35,
                deviceManufacturer = "Google",
                deviceModel = "Pixel",
                exposedTools = listOf("search_tools", "web_search"),
                discoverableTools = listOf("device_control", "search_tools", "web_search")
            )
        )

        assertTrue(text.contains("2026-07-13"))
        assertTrue(text.contains("America/Mexico_City"))
        assertFalse(text.contains("Gabriel"))
        assertFalse(text.contains("Gabo"))
        assertTrue(text.contains("Android 15 / API 35"))
        assertTrue(text.contains("2 exposed; 3 discoverable"))
        assertFalse(text.contains("device_control"))
        assertFalse(text.contains("DuckDuckGo"))
    }

    @Test
    fun renderExplicitlyAnnouncesFinanceOnlyWhenItIsEnabled() {
        val enabled = RuntimeContextProvider.render(runtimeSnapshot(
            exposedTools = listOf("finance_add_transaction", "finance_update_transaction"),
            discoverableTools = listOf("finance_add_transaction", "finance_update_transaction")
        ))
        val disabled = RuntimeContextProvider.render(runtimeSnapshot(
            exposedTools = listOf("search_tools"),
            discoverableTools = listOf("search_tools")
        ))

        assertTrue(enabled.contains("Personal finance is enabled"))
        assertTrue(enabled.contains("search_tools"))
        assertTrue(enabled.contains("'finanzas'"))
        assertFalse(disabled.contains("Personal finance is enabled"))
    }

    @Test
    fun renderMemoryIncludesUsageAndCurrentMarkdownContent() {
        val text = RuntimeContextProvider.renderMemory(
            CortexMemorySnapshot(
                content = "# Perfil\n§\nPrefiere respuestas concisas",
                entries = listOf("# Perfil", "Prefiere respuestas concisas"),
                usedChars = 42,
                maxChars = 2_200,
                usagePercent = 2,
                revision = "revision-1",
                storageError = null
            )
        )

        assertTrue(text.contains(RuntimeContextProvider.MEMORY_MARKER))
        assertTrue(text.contains("42/2200 chars"))
        assertTrue(text.contains("# Perfil"))
        assertTrue(text.contains("Prefiere respuestas concisas"))
    }

    @Test
    fun renderMemoryBlocksStoredContentWhenStorageHasAnError() {
        val hiddenContent = "contenido que no debe inyectarse"
        val text = RuntimeContextProvider.renderMemory(
            CortexMemorySnapshot(
                content = hiddenContent,
                entries = listOf(hiddenContent),
                usedChars = hiddenContent.length,
                maxChars = 2_200,
                usagePercent = 2,
                revision = "revision-2",
                storageError = "disk unavailable"
            )
        )

        assertTrue(text.contains("MEMORY BLOCKED"))
        assertTrue(text.contains("disk unavailable"))
        assertFalse(text.contains(hiddenContent))
    }

    @Test
    fun soulAndUserFilesRenderWithDistinctAuthority() {
        val soul = RuntimeContextProvider.renderSoul(
            snapshot("You are Atlas.", maxChars = 20_000),
            fallbackAgentName = "Atlas"
        )
        val user = RuntimeContextProvider.renderUser(
            snapshot("- Name: Gabriel\n- Preferred name: Gabo", maxChars = 1_375)
        )

        assertTrue(soul.contains(RuntimeContextProvider.SOUL_MARKER))
        assertTrue(soul.contains("primary identity"))
        assertFalse(soul.contains("Configured agent name (authoritative)"))
        assertTrue(user.contains(RuntimeContextProvider.USER_MARKER))
        assertTrue(user.contains("remembered user data"))
        assertTrue(user.contains("Preferred name: Gabo"))
    }

    @Test
    fun legacyHardcodedCortexIdentityIsRemovedButOperationalPromptIsKept() {
        val legacy = "You are Atlas, the central AI agent orchestration system. You coordinate specialized agents to execute complex tasks.\n\n## LANGUAGE RULE\nMatch the user."

        val stripped = RuntimeContextProvider.stripLegacyOrchestratorIdentity(legacy)

        assertFalse(stripped.contains("You are Atlas"))
        assertTrue(stripped.startsWith("## LANGUAGE RULE"))
    }

    private fun snapshot(content: String, maxChars: Int) = CortexMemorySnapshot(
        content = content,
        entries = listOf(content),
        usedChars = content.length,
        maxChars = maxChars,
        usagePercent = 1,
        revision = "revision",
        storageError = null
    )

    private fun runtimeSnapshot(
        exposedTools: List<String>,
        discoverableTools: List<String>
    ) = RuntimeSnapshot(
        isoDateTime = "2026-07-14T19:55:00-06:00",
        timeZone = "America/Mexico_City",
        localeTag = "es-MX",
        agentName = "Clawdy",
        agentRole = "Agent Orchestrator",
        androidRelease = "16",
        androidApi = 36,
        deviceManufacturer = "Google",
        deviceModel = "Pixel 6a",
        exposedTools = exposedTools,
        discoverableTools = discoverableTools
    )
}
