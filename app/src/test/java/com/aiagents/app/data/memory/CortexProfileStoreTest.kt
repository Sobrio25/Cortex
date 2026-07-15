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
    fun cortexRemainsTheDefaultWhenNoAgentNameWasProvided() {
        val soul = CortexProfileStore.defaultSoul("")

        assertTrue(soul.contains("You are Cortex"))
        assertFalse(soul.contains("central agent orchestrator"))
    }
}
