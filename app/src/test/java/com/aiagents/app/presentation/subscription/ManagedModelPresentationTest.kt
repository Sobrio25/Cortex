package com.aiagents.app.presentation.subscription

import com.aiagents.app.domain.model.ManagedCapabilitySupport
import com.aiagents.app.domain.model.ManagedInferenceUsage
import com.aiagents.app.domain.model.ManagedModel
import com.aiagents.app.domain.model.ManagedModelPricing
import com.aiagents.app.domain.model.SubscriptionPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedModelPresentationTest {
    @Test
    fun capabilityAndUnknownPriceAreExplicit() {
        val model = ManagedModel(
            id = "auto",
            displayName = "Auto",
            minimumPlan = SubscriptionPlan.FREE
        )

        assertEquals("Desconocido", ManagedCapabilitySupport.UNKNOWN.displayLabel())
        assertEquals("Precio desconocido", model.priceLabel())
    }

    @Test
    fun configuredMicrosPerTokenAreShownAsUsdPerMillionTokens() {
        val model = ManagedModel(
            id = "known",
            displayName = "Known",
            minimumPlan = SubscriptionPlan.PRO,
            pricing = ManagedModelPricing(0.14, 0.28)
        )

        assertTrue(model.priceLabel().contains("$0.140"))
        assertTrue(model.priceLabel().contains("$0.280"))
    }

    @Test
    fun usageCostUsesMicrosAndFreeUsageIsNotCharged() {
        assertEquals("$0.000300", ManagedInferenceUsage(costMicros = 300).costLabel())
        assertEquals("Sin coste facturado", ManagedInferenceUsage(costMicros = 300, free = true).costLabel())
    }
}
