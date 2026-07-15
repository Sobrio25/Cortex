package com.aiagents.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionTest {
    @Test
    fun freeStarterAndPlusOnlyExposeAutoSelection() {
        listOf(SubscriptionPlan.FREE, SubscriptionPlan.STARTER, SubscriptionPlan.PLUS).forEach { plan ->
            assertEquals(listOf(ManagedModelCatalog.AUTO), ManagedModelCatalog.availableFor(plan).map { it.id })
        }
    }

    @Test
    fun proAndHigherCatalogsAreCumulative() {
        val pro = ManagedModelCatalog.availableFor(SubscriptionPlan.PRO).map { it.id }.toSet()
        val max = ManagedModelCatalog.availableFor(SubscriptionPlan.MAX).map { it.id }.toSet()
        val ultra = ManagedModelCatalog.availableFor(SubscriptionPlan.ULTRA).map { it.id }.toSet()
        assertTrue(max.containsAll(pro))
        assertTrue(ultra.containsAll(max))
        assertFalse(pro.contains("gpt-5.6-terra"))
        assertTrue(ultra.contains("claude-fable-5"))
    }

    @Test
    fun usagePercentageUsesTokensOrBudget() {
        assertEquals(50, UsageSnapshot(freeTokensUsed = 1_000_000).remainingPercentage)
        assertEquals(
            75,
            UsageSnapshot(
                plan = SubscriptionPlan.PRO,
                spentMicros = 2_500_000,
                budgetMicros = 10_000_000
            ).remainingPercentage
        )
    }
}
