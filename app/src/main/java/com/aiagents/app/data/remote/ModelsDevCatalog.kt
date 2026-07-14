package com.aiagents.app.data.remote

import com.aiagents.app.domain.model.ProviderType
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale

/**
 * Dynamic public metadata used by OpenCode for standard providers.
 *
 * A provider's own catalog remains authoritative: models.dev only fills missing
 * context limits, or supplies the catalog when that provider has no usable list endpoint.
 */
internal class ModelsDevCatalog(private val okHttpClient: OkHttpClient) {
    suspend fun getModels(providerId: String): Result<List<RemoteModelInfo>> = runCatching {
        val providers = loadProviders()
        providers[providerId].orEmpty().also { models ->
            check(models.isNotEmpty()) { "models.dev no devolvió modelos para $providerId" }
        }
    }

    private suspend fun loadProviders(): Map<String, List<RemoteModelInfo>> {
        val now = System.currentTimeMillis()
        cache?.takeIf { now - it.loadedAtEpochMillis < CACHE_TTL_MILLIS }?.let { return it.providers }

        return cacheMutex.withLock {
            val refreshedAt = System.currentTimeMillis()
            cache?.takeIf { refreshedAt - it.loadedAtEpochMillis < CACHE_TTL_MILLIS }
                ?.let { return@withLock it.providers }

            val rawJson = withContext(Dispatchers.IO) {
                val request = Request.Builder().url(API_URL).get().build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("models.dev respondió HTTP ${response.code}")
                    }
                    response.body?.string() ?: throw IOException("models.dev devolvió una respuesta vacía")
                }
            }
            ModelsDevCatalogParser.parse(rawJson).also { providers ->
                check(providers.isNotEmpty()) { "El catálogo de models.dev está vacío" }
                cache = Cache(refreshedAt, providers)
            }
        }
    }

    private data class Cache(
        val loadedAtEpochMillis: Long,
        val providers: Map<String, List<RemoteModelInfo>>
    )

    private companion object {
        const val API_URL = "https://models.dev/api.json"
        const val CACHE_TTL_MILLIS = 6 * 60 * 60 * 1_000L
        val cacheMutex = Mutex()

        @Volatile
        var cache: Cache? = null
    }
}

internal object ModelsDevCatalogParser {
    fun parse(rawJson: String): Map<String, List<RemoteModelInfo>> {
        val root = runCatching { JsonParser.parseString(rawJson).asJsonObject }.getOrNull()
            ?: return emptyMap()
        return root.entrySet().mapNotNull { (providerId, providerValue) ->
            val models = providerValue.asJsonObjectOrNull()
                ?.getAsJsonObject("models")
                ?.entrySet()
                ?.mapNotNull { (fallbackId, modelValue) ->
                    val model = modelValue.asJsonObjectOrNull() ?: return@mapNotNull null
                    val id = model.stringValue("id") ?: fallbackId
                    if (id.isBlank()) return@mapNotNull null
                    val context = model.getAsJsonObject("limit")
                        ?.positiveIntValue("context")
                    RemoteModelInfo(id = id, contextWindow = context)
                }
                ?.distinctBy { it.id.lowercase(Locale.ROOT) }
                .orEmpty()
            providerId.takeIf { models.isNotEmpty() }?.let { it to models }
        }.toMap()
    }
}

/** Adds dynamic context metadata without changing the provider's inference protocol. */
internal class ModelsDevBackedClient(
    private val delegate: AIClient,
    private val catalog: ModelsDevCatalog,
    private val modelsDevProviderId: String
) : AIClient by delegate {
    override suspend fun getAvailableModels(): Result<List<String>> =
        getAvailableModelInfos().map { models -> models.map { it.id } }

    override suspend fun getAvailableModelInfos(): Result<List<RemoteModelInfo>> {
        val providerResult = delegate.getAvailableModelInfos()
        val publicResult = catalog.getModels(modelsDevProviderId)
        val providerModels = providerResult.getOrNull().orEmpty()
        val publicModels = publicResult.getOrNull().orEmpty()

        if (providerModels.isNotEmpty()) {
            return Result.success(ProviderModelMetadataMerger.enrich(providerModels, publicModels))
        }
        if (publicModels.isNotEmpty()) return Result.success(publicModels)
        return providerResult
    }
}

internal object ProviderModelMetadataMerger {
    fun enrich(
        providerModels: List<RemoteModelInfo>,
        publicModels: List<RemoteModelInfo>
    ): List<RemoteModelInfo> {
        val contextById = publicModels.associate { model ->
            model.id.trim().lowercase(Locale.ROOT) to model.contextWindow
        }
        return providerModels
            .distinctBy { it.id.trim().lowercase(Locale.ROOT) }
            .map { model ->
                if (model.contextWindow != null) model
                else model.copy(contextWindow = contextById[model.id.trim().lowercase(Locale.ROOT)])
            }
    }
}

internal fun modelsDevProviderId(provider: ProviderType, baseUrl: String?): String? {
    val endpoint = baseUrl.orEmpty().lowercase(Locale.ROOT)
    return when (provider) {
        ProviderType.OPENAI -> if (endpoint.isBlank() || "api.openai.com" in endpoint) "openai" else null
        ProviderType.ANTHROPIC -> "anthropic"
        ProviderType.GOOGLE_AI -> "google"
        ProviderType.OPENROUTER -> "openrouter"
        ProviderType.MINIMAX -> "minimax"
        ProviderType.MOONSHOT -> if ("moonshot.cn" in endpoint) "moonshotai-cn" else "moonshotai"
        ProviderType.DEEPSEEK -> "deepseek"
        ProviderType.GROK -> "xai"
        ProviderType.KILO -> "kilo"
        ProviderType.ALIBABA -> "alibaba"
        ProviderType.ZAI -> if ("/coding/" in endpoint) "zai-coding-plan" else "zai"
        // NVIDIA and Ollama expose their own model metadata. OpenCode already merges its
        // gateway-specific catalog with the appropriate opencode/opencode-go entry.
        ProviderType.NVIDIA,
        ProviderType.OPENCODE,
        ProviderType.OLLAMA,
        ProviderType.LOCAL -> null
    }
}

private fun com.google.gson.JsonElement.asJsonObjectOrNull(): JsonObject? =
    if (isJsonObject) asJsonObject else null

private fun JsonObject.stringValue(key: String): String? = get(key)?.let { value ->
    if (!value.isJsonPrimitive) null else runCatching { value.asString }.getOrNull()
}

private fun JsonObject.positiveIntValue(key: String): Int? = get(key)?.let { value ->
    if (!value.isJsonPrimitive) return@let null
    runCatching { value.asLong }
        .getOrNull()
        ?.takeIf { it in 1..Int.MAX_VALUE.toLong() }
        ?.toInt()
}
