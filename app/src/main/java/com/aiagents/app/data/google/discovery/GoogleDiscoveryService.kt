package com.aiagents.app.data.google.discovery

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// Discovery Document data classes
data class RestDescription(
    val name: String = "",
    val version: String = "",
    val title: String? = null,
    val description: String? = null,
    val rootUrl: String = "",
    val servicePath: String = "",
    val baseUrl: String? = null,
    val schemas: Map<String, JsonSchema> = emptyMap(),
    val resources: Map<String, RestResource> = emptyMap(),
    val parameters: Map<String, MethodParameter> = emptyMap(),
    val auth: AuthDescription? = null
)

data class AuthDescription(
    val oauth2: OAuth2Description? = null
)

data class OAuth2Description(
    val scopes: Map<String, ScopeDescription>? = null
)

data class ScopeDescription(
    val description: String? = null
)

data class RestResource(
    val methods: Map<String, RestMethod> = emptyMap(),
    val resources: Map<String, RestResource> = emptyMap()
)

data class RestMethod(
    val id: String? = null,
    val description: String? = null,
    val httpMethod: String = "GET",
    val path: String = "",
    val parameters: Map<String, MethodParameter> = emptyMap(),
    val parameterOrder: List<String> = emptyList(),
    val request: SchemaRef? = null,
    val response: SchemaRef? = null,
    val scopes: List<String> = emptyList(),
    val flatPath: String? = null,
    val supportsMediaDownload: Boolean = false,
    val supportsMediaUpload: Boolean = false,
    val mediaUpload: MediaUpload? = null
)

data class MediaUpload(
    val protocols: MediaUploadProtocols? = null,
    val accept: List<String>? = null
)

data class MediaUploadProtocols(
    val simple: MediaUploadProtocol? = null
)

data class MediaUploadProtocol(
    val path: String = "",
    val multipart: Boolean? = null
)

data class SchemaRef(
    @SerializedName("\$ref") val ref_: String? = null,
    val parameterName: String? = null
)

data class MethodParameter(
    @SerializedName("type") val paramType: String? = null,
    val description: String? = null,
    val location: String? = null,
    val required: Boolean = false,
    val format: String? = null,
    val default: String? = null,
    @SerializedName("enum") val enumValues: List<String>? = null,
    val enumDescriptions: List<String>? = null,
    val repeated: Boolean = false,
    val minimum: String? = null,
    val maximum: String? = null,
    val deprecated: Boolean = false
)

data class JsonSchema(
    val id: String? = null,
    @SerializedName("type") val type_: String? = null,
    val description: String? = null,
    val properties: Map<String, JsonSchema>? = null,
    val items: JsonSchema? = null,
    @SerializedName("\$ref") val ref_: String? = null,
    val required: Boolean = false,
    val format: String? = null,
    @SerializedName("enum") val enumValues: List<String>? = null,
    val additionalProperties: JsonSchema? = null,
    val annotations: Map<String, Any>? = null
)

// Service registry matching gws CLI
data class ServiceEntry(
    val aliases: List<String>,
    val apiName: String,
    val version: String,
    val description: String
)

@Singleton
class GoogleDiscoveryService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val gson = Gson()
    private val cache = mutableMapOf<String, CachedDocument>()

    private data class CachedDocument(
        val doc: RestDescription,
        val timestamp: Long
    )

    companion object {
        private const val DISCOVERY_BASE_URL = "https://www.googleapis.com/discovery/v1/apis"
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

        val SERVICES = listOf(
            ServiceEntry(listOf("drive"), "drive", "v3", "Manage files, folders, and shared drives"),
            ServiceEntry(listOf("sheets"), "sheets", "v4", "Read and write spreadsheets"),
            ServiceEntry(listOf("gmail"), "gmail", "v1", "Send, read, and manage email"),
            ServiceEntry(listOf("calendar"), "calendar", "v3", "Manage calendars and events"),
            ServiceEntry(listOf("docs"), "docs", "v1", "Read and write Google Docs"),
            ServiceEntry(listOf("slides"), "slides", "v1", "Read and write presentations"),
            ServiceEntry(listOf("tasks"), "tasks", "v1", "Manage task lists and tasks"),
            ServiceEntry(listOf("people"), "people", "v1", "Manage contacts and profiles"),
            ServiceEntry(listOf("chat"), "chat", "v1", "Manage Chat spaces and messages"),
            ServiceEntry(listOf("classroom"), "classroom", "v1", "Manage classes, rosters, and coursework"),
            ServiceEntry(listOf("forms"), "forms", "v1", "Read and write Google Forms"),
            ServiceEntry(listOf("keep"), "keep", "v1", "Manage Google Keep notes"),
            ServiceEntry(listOf("meet"), "meet", "v2", "Manage Google Meet conferences"),
            ServiceEntry(listOf("admin-reports", "reports"), "admin", "reports_v1", "Audit logs and usage reports")
        )

        fun resolveService(name: String): ServiceEntry? {
            return SERVICES.find { entry -> entry.aliases.any { it == name } }
        }
    }

    suspend fun fetchDiscoveryDocument(apiName: String, version: String): Result<RestDescription> = withContext(Dispatchers.IO) {
        val cacheKey = "$apiName:$version"

        // Check cache
        cache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
                return@withContext Result.success(cached.doc)
            }
        }

        try {
            val url = "$DISCOVERY_BASE_URL/$apiName/$version/rest"
            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Discovery fetch failed: ${response.code} ${response.message}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty discovery response"))
            val doc = gson.fromJson(body, RestDescription::class.java)

            // Cache it
            cache[cacheKey] = CachedDocument(doc, System.currentTimeMillis())

            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchForService(serviceName: String): Result<RestDescription> {
        val entry = resolveService(serviceName)
            ?: return Result.failure(Exception("Unknown service '$serviceName'. Known: ${SERVICES.flatMap { it.aliases }.joinToString(", ")}"))
        return fetchDiscoveryDocument(entry.apiName, entry.version)
    }

    // Get all methods for a service, flattened
    fun flattenMethods(doc: RestDescription): Map<String, Pair<RestMethod, String>> {
        val result = mutableMapOf<String, Pair<RestMethod, String>>()
        flattenResource("", doc.resources, result)
        return result
    }

    private fun flattenResource(
        prefix: String,
        resources: Map<String, RestResource>,
        result: MutableMap<String, Pair<RestMethod, String>>
    ) {
        for ((name, resource) in resources) {
            val resourcePath = if (prefix.isEmpty()) name else "$prefix.$name"
            for ((methodName, method) in resource.methods) {
                val fullName = "$resourcePath.$methodName"
                result[fullName] = Pair(method, resourcePath)
            }
            flattenResource(resourcePath, resource.resources, result)
        }
    }

    fun clearCache() {
        cache.clear()
    }
}
