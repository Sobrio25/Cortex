package com.aiagents.app.presentation.providers

import com.aiagents.app.domain.model.MoonshotEndpointType
import com.aiagents.app.domain.model.ProviderType
import java.util.Locale

const val MODEL_SEARCH_THRESHOLD = 12

enum class ModelCatalogSource(val displayName: String) {
    PROVIDER_API("Catálogo remoto"),
    CACHE("Caché local")
}

data class ProviderCatalogKey(
    val provider: ProviderType,
    val scope: String
) {
    val storageKey: String = "${provider.name}|$scope"
}

internal fun normalizeProviderModels(models: List<String>): List<String> =
    models.asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .toList()

internal fun filterProviderModels(models: List<String>, query: String): List<String> {
    val terms = query.trim()
        .lowercase(Locale.ROOT)
        .split(Regex("\\s+"))
        .filter(String::isNotEmpty)
    if (terms.isEmpty()) return models
    return models.filter { model ->
        val searchable = model.lowercase(Locale.ROOT)
        terms.all(searchable::contains)
    }
}

internal fun expectedCatalogSource(
    provider: ProviderType,
    moonshotEndpoint: MoonshotEndpointType? = null
): ModelCatalogSource = ModelCatalogSource.PROVIDER_API
