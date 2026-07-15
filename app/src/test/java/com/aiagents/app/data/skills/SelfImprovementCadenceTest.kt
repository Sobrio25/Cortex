package com.aiagents.app.data.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfImprovementCadenceTest {
    @Test
    fun `memory review fires every ten completed turns`() {
        var state = SelfImprovementCadenceState()
        repeat(9) {
            val result = SelfImprovementCadence.advance(state, completedTurnIterations = 1, interval = 10)
            assertFalse(result.reviewMemory)
            state = result.state
        }

        val result = SelfImprovementCadence.advance(state, completedTurnIterations = 1, interval = 10)

        assertTrue(result.reviewMemory)
        assertTrue(result.reviewSkills)
        assertEquals(SelfImprovementCadenceState(), result.state)
    }

    @Test
    fun `skill review counts model iterations independently from user turns`() {
        var state = SelfImprovementCadenceState()
        val first = SelfImprovementCadence.advance(state, completedTurnIterations = 6, interval = 10)
        state = first.state

        val second = SelfImprovementCadence.advance(state, completedTurnIterations = 4, interval = 10)

        assertTrue(second.reviewSkills)
        assertFalse(second.reviewMemory)
        assertEquals(2, second.state.memoryTurns)
        assertEquals(0, second.state.skillIterations)
    }
}
