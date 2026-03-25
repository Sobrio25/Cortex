package com.aiagents.app.data.google.discovery

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

data class ApiExecutionResult(
    val success: Boolean,
    val statusCode: Int,
    val body: String,
    val method: String,
    val url: String
)

@Singleton
class GoogleApiExecutor @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val gson = Gson()

    companion object {
        private const val MAX_RESPONSE_SIZE = 50_000 // 50KB limit for LLM consumption
    }

    /**
     * Execute any Google Workspace API method dynamically.
     *
     * @param doc The discovery document for the service
     * @param method The REST method metadata from discovery
     * @param params URL/query parameters as JSON string
     * @param body Request body as JSON string (for POST/PUT/PATCH)
     * @param accessToken OAuth2 access token
     * @param pageAll Whether to auto-paginate
     * @param pageLimit Max pages when paginating
     */
    suspend fun execute(
        doc: RestDescription,
        method: RestMethod,
        params: String? = null,
        body: String? = null,
        accessToken: String,
        pageAll: Boolean = false,
        pageLimit: Int = 10
    ): ApiExecutionResult = withContext(Dispatchers.IO) {
        try {
            val parsedParams: Map<String, Any> = if (params != null) {
                gson.fromJson(params, object : TypeToken<Map<String, Any>>() {}.type)
            } else emptyMap()

            // Build URL
            val baseUrl = doc.baseUrl ?: "${doc.rootUrl}${doc.servicePath}"
            val resolvedPath = resolvePath(method.path, method.flatPath, parsedParams)
            val fullUrl = "$baseUrl$resolvedPath"

            // Check for unresolved path parameters
            if (fullUrl.contains("{") && fullUrl.contains("}")) {
                val unresolved = Regex("\\{\\+?([^}]+)\\}").findAll(fullUrl).map { it.groupValues[1] }.toList()
                Log.w("GoogleApiExecutor", "Unresolved path params in URL: $unresolved. URL: $fullUrl. Params provided: ${parsedParams.keys}")
                return@withContext ApiExecutionResult(
                    success = false,
                    statusCode = 400,
                    body = "Missing required path parameters: ${unresolved.joinToString(", ")}. Provide them in the 'params' JSON.",
                    method = method.httpMethod,
                    url = fullUrl
                )
            }

            Log.d("GoogleApiExecutor", "${method.httpMethod} $fullUrl")

            // Separate path params from query params
            val pathParamNames = extractPathParams(method.path) + extractPathParams(method.flatPath ?: "")
            val queryParams = parsedParams.filter { it.key !in pathParamNames }

            if (pageAll && method.httpMethod == "GET") {
                executePaginated(fullUrl, queryParams, accessToken, pageLimit, method)
            } else {
                executeSingle(fullUrl, queryParams, body, accessToken, method)
            }
        } catch (e: Exception) {
            ApiExecutionResult(
                success = false,
                statusCode = 0,
                body = "Error: ${e.message}",
                method = method.httpMethod,
                url = ""
            )
        }
    }

    private fun executeSingle(
        fullUrl: String,
        queryParams: Map<String, Any>,
        body: String?,
        accessToken: String,
        method: RestMethod
    ): ApiExecutionResult {
        val urlBuilder = fullUrl.toHttpUrlOrNull()?.newBuilder()
            ?: return ApiExecutionResult(false, 0, "Invalid URL: $fullUrl", method.httpMethod, fullUrl)

        for ((key, value) in queryParams) {
            urlBuilder.addQueryParameter(key, value.toString())
        }

        val requestBuilder = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Accept", "application/json")

        when (method.httpMethod.uppercase()) {
            "GET" -> requestBuilder.get()
            "DELETE" -> requestBuilder.delete()
            "POST" -> {
                val requestBody = (body ?: "{}").toRequestBody("application/json".toMediaType())
                requestBuilder.post(requestBody)
            }
            "PUT" -> {
                val requestBody = (body ?: "{}").toRequestBody("application/json".toMediaType())
                requestBuilder.put(requestBody)
            }
            "PATCH" -> {
                val requestBody = (body ?: "{}").toRequestBody("application/json".toMediaType())
                requestBuilder.patch(requestBody)
            }
        }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        val responseBody = response.body?.string() ?: ""
        val truncatedBody = if (responseBody.length > MAX_RESPONSE_SIZE) {
            responseBody.take(MAX_RESPONSE_SIZE) + "\n... [truncated, ${responseBody.length} total chars]"
        } else responseBody

        return ApiExecutionResult(
            success = response.isSuccessful,
            statusCode = response.code,
            body = truncatedBody,
            method = method.httpMethod,
            url = fullUrl
        )
    }

    private fun executePaginated(
        fullUrl: String,
        queryParams: Map<String, Any>,
        accessToken: String,
        pageLimit: Int,
        method: RestMethod
    ): ApiExecutionResult {
        val allResults = mutableListOf<String>()
        var pageToken: String? = null
        var pagesProcessed = 0
        var lastStatusCode = 200

        do {
            val urlBuilder = fullUrl.toHttpUrlOrNull()?.newBuilder()
                ?: return ApiExecutionResult(false, 0, "Invalid URL: $fullUrl", method.httpMethod, fullUrl)

            for ((key, value) in queryParams) {
                urlBuilder.addQueryParameter(key, value.toString())
            }
            pageToken?.let { urlBuilder.addQueryParameter("pageToken", it) }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            lastStatusCode = response.code

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return ApiExecutionResult(false, response.code, errorBody, method.httpMethod, fullUrl)
            }

            val responseBody = response.body?.string() ?: "{}"
            allResults.add(responseBody)

            // Extract nextPageToken
            val jsonObj = gson.fromJson(responseBody, JsonObject::class.java)
            pageToken = jsonObj?.get("nextPageToken")?.asString
            pagesProcessed++

        } while (pageToken != null && pagesProcessed < pageLimit)

        val combined = allResults.joinToString("\n")
        val truncated = if (combined.length > MAX_RESPONSE_SIZE) {
            combined.take(MAX_RESPONSE_SIZE) + "\n... [truncated, ${combined.length} total chars, $pagesProcessed pages]"
        } else combined

        return ApiExecutionResult(
            success = true,
            statusCode = lastStatusCode,
            body = truncated,
            method = method.httpMethod,
            url = fullUrl
        )
    }

    private fun resolvePath(path: String, flatPath: String?, params: Map<String, Any>): String {
        var resolved = flatPath ?: path
        for ((key, value) in params) {
            // Handle both {key} and {+key} path parameter formats
            resolved = resolved.replace("{+$key}", value.toString())
            resolved = resolved.replace("{$key}", value.toString())
        }
        return resolved
    }

    private fun extractPathParams(path: String): Set<String> {
        val regex = Regex("\\{\\+?([^}]+)\\}")
        return regex.findAll(path).map { it.groupValues[1] }.toSet()
    }
}
