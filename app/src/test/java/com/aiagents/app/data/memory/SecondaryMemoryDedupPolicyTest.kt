package com.aiagents.app.data.memory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondaryMemoryDedupPolicyTest {
    @Test
    fun detectsExactContainedAndKeyValueDuplicatesAcrossMarkdown() {
        val active = listOf(
            "Prefiere respuestas breves y directas",
            "El usuario prefiere que lo llamen Gabo",
            "Ciudad principal: Querétaro"
        )

        assertTrue(
            SecondaryMemoryDedupPolicy.isRepresentedInActiveMemory(
                "Prefiere respuestas breves y directas",
                active
            )
        )
        assertTrue(
            SecondaryMemoryDedupPolicy.isRepresentedInActiveMemory(
                "preferred_name: Gabo",
                active
            )
        )
        assertTrue(
            SecondaryMemoryDedupPolicy.isRepresentedInActiveMemory(
                "city: Queretaro",
                active
            )
        )
    }

    @Test
    fun keepsDistinctLowerPriorityFacts() {
        val active = listOf("Prefiere respuestas breves", "Vive en Querétaro")

        assertFalse(
            SecondaryMemoryDedupPolicy.isRepresentedInActiveMemory(
                "minor_hobby: armar teclados mecánicos",
                active
            )
        )
    }
}
