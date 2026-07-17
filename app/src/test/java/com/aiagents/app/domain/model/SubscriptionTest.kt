package com.aiagents.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionTest {
    @Test
    fun offlineCatalogOnlyExposesAutoBecauseServerIsAuthoritative() {
        SubscriptionPlan.entries.forEach { plan ->
            assertEquals(listOf(ManagedModelCatalog.AUTO), ManagedModelCatalog.availableFor(plan).map { it.id })
        }
    }

    @Test
    fun freeAutoWarnsThatToolsAreBestEffortAndPricesUnknown() {
        val auto = ManagedModelCatalog.defaults.single()
        assertEquals(ManagedCapabilitySupport.BEST_EFFORT, auto.capabilities.tools)
        assertTrue(auto.requiresFreeToolsWarning)
        assertEquals(null, auto.pricing.inputMicrosPerToken)
        assertFalse(auto.supportsVision)
    }

    @Test
    fun usagePercentageUsesTokensOrBudget() {
        assertEquals(50, UsageSnapshot(freeTokensUsed = 250_000).remainingPercentage)
        assertEquals(50, UsageSnapshot(freeTokensUsed = 250_000).freeUsedPercentage)
        assertEquals(100, UsageSnapshot(freeTokensUsed = 750_000).freeUsedPercentage)
        assertEquals(0, UsageSnapshot(freeTokensLimit = 0).freeUsedPercentage)
        assertEquals(
            75,
            UsageSnapshot(
                plan = SubscriptionPlan.PRO,
                spentMicros = 2_500_000,
                budgetMicros = 10_000_000
            ).remainingPercentage
        )
    }

    @Test
    fun freeDataConsentMustMatchTheCurrentDisclosureVersion() {
        assertFalse(UsageSnapshot(freeDataConsentVersion = 0).hasCurrentFreeDataConsent)
        assertTrue(
            UsageSnapshot(freeDataConsentVersion = FREE_DATA_CONSENT_VERSION)
                .hasCurrentFreeDataConsent
        )
    }
}
