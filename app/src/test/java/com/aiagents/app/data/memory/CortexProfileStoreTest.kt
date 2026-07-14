package com.aiagents.app.data.memory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CortexProfileStoreTest {
    @Test
    fun onboardingTemplatesUseCustomAgentAndBothUserNames() {
        val soul = CortexProfileStore.defaultSoul(
            agentName = "Atlas",
            userName = "Gabriel",
            preferredName = "Gabo"
        )
        val user = CortexProfileStore.defaultUser(
            userName = "Gabriel",
            preferredName = "Gabo"
        )

        assertTrue(soul.contains("You are Atlas"))
        assertTrue(soul.contains("speaking with Gabriel"))
        assertTrue(soul.contains("called Gabo"))
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
        val soul = CortexProfileStore.defaultSoul("", null, null)

        assertTrue(soul.contains("You are Cortex"))
        assertTrue(soul.contains("speaking with the user"))
    }
}
