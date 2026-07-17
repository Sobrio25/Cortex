package com.aiagents.app.presentation.workspace_detail

import com.aiagents.app.domain.model.ProviderType

data class ModelInfo(
    val provider: ProviderType,
    val modelId: String,
    val fullKey: String = "${provider.name}|$modelId"
)

/** Product labels for the compact selector; internal routing IDs do not belong in the UI. */
internal fun ModelInfo.displayModelName(): String = when {
    provider == ProviderType.MANAGED && modelId.equals("auto", ignoreCase = true) -> "Free"
    else -> modelId
}

internal fun ModelInfo.displayProviderName(): String = when (provider) {
    ProviderType.MANAGED -> "Cortex Gateway"
    else -> provider.name
}

/** Parsing rules for persisted model keys, kept independent from ViewModel lifecycle state. */
internal object ModelSelectionPolicy {
    fun modelId(fullKey: String): String =
        if ('|' in fullKey) fullKey.substringAfter('|') else fullKey

    fun provider(fullKey: String): ProviderType? =
        if ('|' in fullKey) {
            runCatching { ProviderType.valueOf(fullKey.substringBefore('|')) }.getOrNull()
        } else {
            null
        }

    fun modelInfo(fullKey: String): ModelInfo? {
        val provider = provider(fullKey) ?: return null
        return ModelInfo(provider, modelId(fullKey), fullKey)
    }
}

internal fun extractModelId(fullKey: String): String = ModelSelectionPolicy.modelId(fullKey)

internal fun extractProvider(fullKey: String): ProviderType? = ModelSelectionPolicy.provider(fullKey)
