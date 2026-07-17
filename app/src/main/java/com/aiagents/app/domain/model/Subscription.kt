package com.aiagents.app.domain.model

const val FREE_DATA_CONSENT_VERSION = 1

enum class SubscriptionPlan(
    val productId: String?,
    val displayName: String,
    val monthlyPriceUsd: Int,
    val inferenceBudgetMicros: Long
) {
    FREE(null, "Gratis", 0, 0),
    STARTER("cortex_starter", "Starter", 5, 2_500_000),
    PLUS("cortex_plus", "Plus", 10, 5_000_000),
    PRO("cortex_pro", "Pro", 20, 10_000_000),
    MAX("cortex_max", "Max", 50, 25_000_000),
    ULTRA("cortex_ultra", "Ultra", 100, 50_000_000);

    companion object {
        fun fromId(value: String?): SubscriptionPlan = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true) || it.productId == value
        } ?: FREE
    }
}

enum class RoutingMode { AUTO, MANUAL }

enum class ManagedCapabilitySupport {
    SUPPORTED,
    UNSUPPORTED,
    BEST_EFFORT,
    UNKNOWN
}

data class ManagedModelCapabilities(
    val tools: ManagedCapabilitySupport = ManagedCapabilitySupport.UNKNOWN,
    val vision: ManagedCapabilitySupport = ManagedCapabilitySupport.UNKNOWN,
    val streaming: ManagedCapabilitySupport = ManagedCapabilitySupport.UNKNOWN,
    val reasoning: ManagedCapabilitySupport = ManagedCapabilitySupport.UNKNOWN
)

data class ManagedModelPricing(
    val inputMicrosPerToken: Double? = null,
    val outputMicrosPerToken: Double? = null
)

data class ManagedModel(
    val id: String,
    val displayName: String,
    val minimumPlan: SubscriptionPlan,
    val contextWindow: Int? = null,
    val capabilities: ManagedModelCapabilities = ManagedModelCapabilities(),
    val pricing: ManagedModelPricing = ManagedModelPricing(),
    val available: Boolean = true,
    val selectable: Boolean = true
) {
    val supportsVision: Boolean
        get() = capabilities.vision == ManagedCapabilitySupport.SUPPORTED

    val requiresFreeToolsWarning: Boolean
        get() = minimumPlan == SubscriptionPlan.FREE &&
            capabilities.tools != ManagedCapabilitySupport.SUPPORTED
}

data class ManagedInferenceUsage(
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val totalTokens: Long = promptTokens + completionTokens,
    val estimated: Boolean = false,
    val costMicros: Long? = null,
    val free: Boolean = false,
    val requestedModel: String? = null,
    val modelUsed: String? = null,
    val provider: String? = null,
    val gateway: String? = null,
    val fallback: Boolean = false,
    val fallbackCategory: String? = null,
    val fallbackReason: String? = null
)

data class UsageSnapshot(
    val plan: SubscriptionPlan = SubscriptionPlan.FREE,
    val freeTokensUsed: Long = 0,
    val freeTokensLimit: Long = 500_000,
    val spentMicros: Long = 0,
    val budgetMicros: Long = 0,
    val periodEndEpochMillis: Long? = null,
    val freeDataConsentVersion: Int = 0,
    val freeDataConsentRequiredVersion: Int = FREE_DATA_CONSENT_VERSION
) {
    val hasCurrentFreeDataConsent: Boolean
        get() = freeDataConsentVersion >= freeDataConsentRequiredVersion

    val freeUsedPercentage: Int
        get() = if (freeTokensLimit <= 0) {
            0
        } else {
            ((freeTokensUsed.coerceIn(0, freeTokensLimit).toDouble() / freeTokensLimit) * 100)
                .toInt()
                .coerceIn(0, 100)
        }

    val remainingPercentage: Int
        get() = if (budgetMicros <= 0) {
            if (freeTokensLimit <= 0) 0 else
                (((freeTokensLimit - freeTokensUsed).coerceAtLeast(0) * 100) / freeTokensLimit).toInt()
        } else {
            (((budgetMicros - spentMicros).coerceAtLeast(0) * 100) / budgetMicros).toInt()
        }
}

object ManagedModelCatalog {
    const val AUTO = "auto"

    /**
     * Minimal offline fallback. The managed `/v1/models` endpoint is the sole source
     * of truth for named models, capabilities and prices so the app cannot drift.
     */
    val defaults = listOf(
        ManagedModel(
            id = AUTO,
            displayName = "Auto",
            minimumPlan = SubscriptionPlan.FREE,
            contextWindow = 128_000,
            capabilities = ManagedModelCapabilities(
                tools = ManagedCapabilitySupport.BEST_EFFORT,
                streaming = ManagedCapabilitySupport.UNSUPPORTED
            )
        )
    )

    fun availableFor(plan: SubscriptionPlan): List<ManagedModel> = defaults.filter {
        it.available && it.minimumPlan.ordinal <= plan.ordinal &&
            (plan.ordinal >= SubscriptionPlan.PRO.ordinal || it.id == AUTO)
    }
}
