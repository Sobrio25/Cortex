package com.aiagents.app.data.local

import android.content.Context
import com.google.gson.Gson
import com.aiagents.app.domain.model.ProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class CachedProviderModelCatalog(
    val models: List<String>,
    val updatedAtEpochMillis: Long
)

/**
 * Small, non-sensitive persistent cache for provider model ids.
 *
 * Catalogs are scoped by provider and endpoint/plan so a failed refresh can never
 * display a catalog belonging to another provider (or another provider variant).
 */
@Singleton
class ProviderModelCatalogCache @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun read(key: String): CachedProviderModelCatalog? {
        val raw = preferences.getString(storageKey(key), null) ?: return null
        return runCatching {
            gson.fromJson(raw, CachedProviderModelCatalog::class.java)
                ?.takeIf { it.models.isNotEmpty() }
        }.getOrNull()
    }

    fun write(key: String, models: List<String>, updatedAtEpochMillis: Long) {
        val payload = CachedProviderModelCatalog(
            models = models,
            updatedAtEpochMillis = updatedAtEpochMillis
        )
        preferences.edit()
            .putString(storageKey(key), gson.toJson(payload))
            .apply()
    }

    /** Context limits are public model metadata, persisted independently of credentials. */
    fun writeContextWindows(
        provider: ProviderType,
        contextWindows: Map<String, Int>,
        scope: String? = null
    ) {
        if (contextWindows.isEmpty()) return
        preferences.edit().apply {
            contextWindows.forEach { (modelId, contextWindow) ->
                if (contextWindow > 0) {
                    putInt(contextStorageKey(provider, modelId, scope), contextWindow)
                }
            }
        }.apply()
    }

    fun readContextWindow(provider: ProviderType, modelId: String, scope: String? = null): Int? {
        val key = contextStorageKey(provider, modelId, scope)
        if (!preferences.contains(key)) return null
        return preferences.getInt(key, 0).takeIf { it > 0 }
    }

    private fun storageKey(key: String) = "catalog:$key"

    private fun contextStorageKey(provider: ProviderType, modelId: String, scope: String?) =
        buildString {
            append("context:")
            append(provider.name)
            scope?.trim()?.takeIf(String::isNotEmpty)?.let {
                append('@')
                append(it.lowercase())
            }
            append('|')
            append(modelId.trim().lowercase())
        }

    private companion object {
        const val PREFERENCES_NAME = "provider_model_catalogs"
    }
}
