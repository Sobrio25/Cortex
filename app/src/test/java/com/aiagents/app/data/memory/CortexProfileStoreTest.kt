package com.aiagents.app.data.memory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CortexProfileStoreTest {
    @Test
    fun onboardingTemplatesKeepUserIdentityOnlyInUserFile() {
        val soul = CortexProfileStore.defaultSoul(agentName = "Atlas")
        val user = CortexProfileStore.defaultUser(
            userName = "Gabriel",
            preferredName = "Gabo"
        )

        assertTrue(soul.contains("You are Atlas"))
        assertFalse(soul.contains("Gabriel"))
        assertFalse(soul.contains("Gabo"))
        assertFalse(soul.contains("You are Cortex"))
        assertTrue(user.contains("- Name: Gabriel"))
        assertTrue(user.contains("- Preferred name: Gabo"))
        assertTrue(user.contains("- Address the user as: Gabo"))
        assertTrue(
            CortexMemoryPolicy.countCharacters(user) <= CortexProfileStore.HERMES_USER_MAX_CHARS
        )
    }

    @Test
    fun genericAssistantRemainsTheDefaultWhenNoAgentNameWasProvided() {
        val soul = CortexProfileStore.defaultSoul("")

        assertTrue(soul.contains("You are Assistant"))
        assertFalse(soul.contains("Cortex"))
        assertFalse(soul.contains("central agent orchestrator"))
    }

    @Test
    fun assistantSoulIsDedicatedAndContainsNoUserMemoryTemplate() {
        val soul = CortexProfileStore.defaultAssistantSoul()

        assertTrue(soul.contains("ASSISTANT_SOUL.md"))
        assertTrue(soul.contains("voice assistant"))
        assertFalse(soul.contains("USER.md"))
        assertFalse(soul.contains("MEMORY.md"))
        assertFalse(soul.contains("central agent orchestrator"))
    }

    @Test
    fun renamePreservesCustomSoulPersonality() {
        val soul = """
            # SOUL.md

            You are Clawdy, the ghost of a sarcastic turtle.

            ## Tone
            - Dry wit
        """.trimIndent()

        val renamed = CortexProfileStore.replaceIdentityName(soul, "Nova")

        assertTrue(renamed.contains("You are Nova, the ghost of a sarcastic turtle."))
        assertTrue(renamed.contains("## Tone\n- Dry wit"))
        assertFalse(renamed.contains("You are Clawdy"))
    }
}
