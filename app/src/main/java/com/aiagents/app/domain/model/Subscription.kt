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

data class ManagedModel(
    val id: String,
    val displayName: String,
    val minimumPlan: SubscriptionPlan,
    val contextWindow: Int,
    val supportsVision: Boolean = false,
    val available: Boolean = true
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

    val defaults = listOf(
        ManagedModel(AUTO, "Auto", SubscriptionPlan.FREE, 128_000),
        ManagedModel("deepseek-v4-flash", "DeepSeek V4 Flash", SubscriptionPlan.STARTER, 1_000_000),
        ManagedModel("mimo-v2.5", "MiMo 2.5", SubscriptionPlan.STARTER, 1_100_000, true),
        ManagedModel("deepseek-v4-pro", "DeepSeek V4 Pro", SubscriptionPlan.PLUS, 1_000_000),
        ManagedModel("mimo-v2.5-pro", "MiMo 2.5 Pro", SubscriptionPlan.PLUS, 1_100_000, true),
        ManagedModel("gpt-5.6-luna", "GPT-5.6 Luna", SubscriptionPlan.PRO, 1_000_000, true),
        ManagedModel("kimi-k2.7-code", "Kimi K2.7 Code", SubscriptionPlan.PRO, 262_144, true),
        ManagedModel("minimax-m3", "MiniMax M3", SubscriptionPlan.PRO, 1_000_000, true),
        ManagedModel("grok-4.5", "Grok 4.5", SubscriptionPlan.PRO, 500_000, true),
        ManagedModel("gpt-5.6-terra", "GPT-5.6 Terra", SubscriptionPlan.MAX, 1_000_000, true),
        ManagedModel("glm-5.2", "GLM 5.2", SubscriptionPlan.MAX, 1_000_000),
        ManagedModel("claude-sonnet-5", "Claude Sonnet 5", SubscriptionPlan.MAX, 1_000_000, true),
        ManagedModel("claude-opus-4.8", "Claude Opus 4.8", SubscriptionPlan.MAX, 1_000_000, true),
        ManagedModel("gpt-5.6-sol", "GPT-5.6 Sol", SubscriptionPlan.ULTRA, 1_000_000, true),
        ManagedModel("claude-fable-5", "Claude Fable 5", SubscriptionPlan.ULTRA, 1_000_000, true)
    )

    fun availableFor(plan: SubscriptionPlan): List<ManagedModel> = defaults.filter {
        it.available && it.minimumPlan.ordinal <= plan.ordinal &&
            (plan.ordinal >= SubscriptionPlan.PRO.ordinal || it.id == AUTO)
    }
}
