package com.aiagents.app.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelMemoryPolicyTest {
    private val pixel6aTotalMemory = 5_723_472L * 1024L

    @Test
    fun rejectsMultiGigabyteModelOnSixGigabyteDevice() {
        val decision = LocalModelMemoryPolicy.evaluate(
            modelBytes = 2_580_000_000L,
            totalMemoryBytes = pixel6aTotalMemory,
            availableMemoryBytes = 2_500_000_000L
        )

        assertFalse(decision.allowed)
    }

    @Test
    fun acceptsCompactModelWithAdequateHeadroom() {
        val decision = LocalModelMemoryPolicy.evaluate(
            modelBytes = 555_000_000L,
            totalMemoryBytes = pixel6aTotalMemory,
            availableMemoryBytes = 2_500_000_000L
        )

        assertTrue(decision.allowed)
    }

    @Test
    fun rejectsEvenCompactModelWhenAvailableMemoryIsTooLow() {
        val decision = LocalModelMemoryPolicy.evaluate(
            modelBytes = 555_000_000L,
            totalMemoryBytes = pixel6aTotalMemory,
            availableMemoryBytes = 700_000_000L
        )

        assertFalse(decision.allowed)
    }
}
