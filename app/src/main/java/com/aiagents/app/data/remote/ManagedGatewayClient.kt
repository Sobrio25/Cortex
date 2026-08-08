package com.aiagents.app.data.remote

import com.aiagents.app.data.auth.FirebaseAuthManager
import com.aiagents.app.domain.model.ManagedModel
import com.aiagents.app.domain.model.ManagedModelCatalog
import com.aiagents.app.domain.model.ManagedCapabilitySupport
import com.aiagents.app.domain.model.ManagedModelCapabilities
import com.aiagents.app.domain.model.ManagedModelPricing
import com.aiagents.app.domain.model.ManagedInferenceUsage
import com.aiagents.app.domain.model.FREE_DATA_CONSENT_VERSION
import com.aiagents.app.domain.model.SubscriptionPlan
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.UsageSnapshot
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val MANAGED_BASE_URL = "https://us-central1-cortex-agents-ai.cloudfunctions.net/api"

data class ManagedAccountResponse(
    val plan: String = "FREE",
    val freeTokensUsed: Long = 0,
    val freeTokensLimit: Long = 500_000,
    val spentMicros: Long = 0,
    val budgetMicros: Long = 0,
    val periodEndEpochMillis: Long? = null,
    val freeDataConsentVersion: Int = 0,
    val freeDataConsentRequiredVersion: Int = FREE_DATA_CONSENT_VERSION
) {
    fun toDomain() = UsageSnapshot(
        plan = SubscriptionPlan.fromId(plan),
        freeTokensUsed = freeTokensUsed,
        freeTokensLimit = freeTokensLimit,
        spentMicros = spentMicros,
        budgetMicros = budgetMicros,
        periodEndEpochMillis = periodEndEpochMillis,
        freeDataConsentVersion = freeDataConsentVersion,
        freeDataConsentRequiredVersion = freeDataConsentRequiredVersion
    )
}

data class PurchaseVerificationRequest(
    val productId: String,
    val purchaseToken: String,
    val packageName: String = "com.aiagents.app"
)

data class FreeDataConsentRequest(
    val accepted: Boolean = true,
    val version: Int = FREE_DATA_CONSENT_VERSION
)

enum class ContentReportCategory {
    OFFENSIVE,
    UNSAFE,
    INACCURATE,
    OTHER
}

data class ContentReportRequest(
    val messageId: String,
    val category: String,
    val content: String,
    val comment: String? = null,
    val model: String? = null,
    val appVersion: String = com.aiagents.app.BuildConfig.VERSION_NAME
)

/**
 * Wire model for `/v1/models`.
 *
 * The managed backend can be deployed independently from the app. Nullable fields keep an older
 * catalog response from creating invalid Kotlin objects through Gson's constructor-less adapter.
 */
internal data class ManagedModelResponse(
    val id: String? = null,
    val displayName: String? = null,
    val minimumPlan: String? = null,
    val contextWindow: Int? = null,
    val capabilities: ManagedModelCapabilitiesResponse? = null,
    val pricing: ManagedModelPricing? = null,
    val available: Boolean? = null,
    val selectable: Boolean? = null
) {
    fun toDomain(): ManagedModel? {
        val safeId = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return ManagedModel(
            id = safeId,
            displayName = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: safeId,
            minimumPlan = SubscriptionPlan.fromId(minimumPlan),
            contextWindow = contextWindow?.takeIf { it > 0 },
            capabilities = capabilities?.toDomain() ?: ManagedModelCapabilities(),
            pricing = pricing ?: ManagedModelPricing(),
            available = available ?: true,
            selectable = selectable ?: true
        )
    }
}

internal data class ManagedModelCapabilitiesResponse(
    val tools: ManagedCapabilitySupport? = null,
    val vision: ManagedCapabilitySupport? = null,
    val streaming: ManagedCapabilitySupport? = null,
    val reasoning: ManagedCapabilitySupport? = null
) {
    fun toDomain() = ManagedModelCapabilities(
        tools = tools ?: ManagedCapabilitySupport.UNKNOWN,
        vision = vision ?: ManagedCapabilitySupport.UNKNOWN,
        streaming = streaming ?: ManagedCapabilitySupport.UNKNOWN,
        reasoning = reasoning ?: ManagedCapabilitySupport.UNKNOWN
    )
}

@Singleton
class ManagedGatewayClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val authManager: FirebaseAuthManager,
    private val gson: Gson
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val _lastInferenceUsage = MutableStateFlow<ManagedInferenceUsage?>(null)
    val lastInferenceUsage: StateFlow<ManagedInferenceUsage?> = _lastInferenceUsage.asStateFlow()

    suspend fun account(): UsageSnapshot = authenticatedGet("/v1/account").let {
        gson.fromJson(it, ManagedAccountResponse::class.java).toDomain()
    }

    suspend fun models(): List<ManagedModel> {
        return parseManagedModels(gson, authenticatedGet("/v1/models"))
    }

    suspend fun verifyPurchase(request: PurchaseVerificationRequest): UsageSnapshot {
        val response = authenticatedPost("/v1/billing/google-play/verify", gson.toJson(request))
        return gson.fromJson(response, ManagedAccountResponse::class.java).toDomain()
    }

    suspend fun acceptFreeDataConsent(
        request: FreeDataConsentRequest = FreeDataConsentRequest()
    ): UsageSnapshot {
        val response = authenticatedPost("/v1/free-data-consent", gson.toJson(request))
        return gson.fromJson(response, ManagedAccountResponse::class.java).toDomain()
    }

    suspend fun deleteAccount() {
        authenticatedDelete("/v1/account")
    }

    suspend fun reportContent(request: ContentReportRequest) {
        authenticatedPost("/v1/content-reports", gson.toJson(request))
    }

    suspend fun infer(
        logicalModel: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): ChatResponseWithTools {
        val turnId = turnFingerprint(messages)
        val payload = mapOf(
            "turnId" to turnId,
            "logicalModel" to logicalModel,
            "messages" to messages,
            "systemPrompt" to systemPrompt,
            "temperature" to temperature,
            "maxTokens" to maxTokens,
            "tools" to tools
        )
        val root = gson.fromJson(authenticatedPost("/v1/inference/chat", gson.toJson(payload)), JsonObject::class.java)
        _lastInferenceUsage.value = parseManagedInferenceUsage(root)
        val response = root.getAsJsonObject("response") ?: root
        return gson.fromJson(response, ChatResponseWithTools::class.java)
    }

    private suspend fun authenticatedGet(path: String): String = request(path, "GET", null)

    private suspend fun authenticatedPost(path: String, body: String): String = request(path, "POST", body)

    private suspend fun authenticatedDelete(path: String): String = request(path, "DELETE", null)

    private suspend fun request(path: String, method: String, body: String?): String = withContext(Dispatchers.IO) {
        val token = authManager.idToken()
        val builder = Request.Builder()
            .url("$MANAGED_BASE_URL$path")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
        when (method) {
            "POST" -> builder.post((body ?: "{}").toRequestBody(jsonMediaType))
            "DELETE" -> builder.delete()
        }
        val response = okHttpClient.newCall(builder.build()).execute()
        response.use {
            val responseBody = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val message = runCatching {
                    gson.fromJson(responseBody, JsonObject::class.java).get("error")?.asString
                }.getOrNull()
                throw ManagedGatewayException(it.code, message ?: "El servicio administrado no está disponible")
            }
            responseBody
        }
    }

    private fun turnFingerprint(messages: List<ChatMessage>): String {
        val stableUserTurn = messages.lastOrNull {
            it.role.equals("user", true) && !it.clientMessageId.isNullOrBlank()
        }?.clientMessageId
        val fallback = messages.filter { it.role.equals("user", true) }
            .joinToString("\u001f") { it.content }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((stableUserTurn ?: fallback).toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }
}

internal fun parseManagedModels(gson: Gson, json: String): List<ManagedModel> {
    val type = object : TypeToken<List<ManagedModelResponse>>() {}.type
    return gson.fromJson<List<ManagedModelResponse>?>(json, type)
        .orEmpty()
        .mapNotNull(ManagedModelResponse::toDomain)
}

/**
 * Parses optional inference metadata without Gson reflection.
 *
 * This object must never make an otherwise successful inference fail. R8 cannot see reflective
 * construction through Gson and may remove the concrete constructor in optimized builds; parsing
 * the small payload explicitly keeps the release and debug paths equivalent.
 */
internal fun parseManagedInferenceUsage(root: JsonObject): ManagedInferenceUsage? {
    val usage = root.get("usage")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    return ManagedInferenceUsage(
        promptTokens = usage.longOrDefault("promptTokens"),
        completionTokens = usage.longOrDefault("completionTokens"),
        totalTokens = usage.optionalLong("totalTokens")
            ?: usage.longOrDefault("promptTokens") + usage.longOrDefault("completionTokens"),
        estimated = usage.booleanOrDefault("estimated"),
        costMicros = usage.optionalLong("costMicros"),
        free = usage.booleanOrDefault("free"),
        requestedModel = usage.optionalString("requestedModel"),
        modelUsed = usage.optionalString("modelUsed"),
        provider = usage.optionalString("provider"),
        gateway = usage.optionalString("gateway"),
        fallback = usage.booleanOrDefault("fallback"),
        fallbackCategory = usage.optionalString("fallbackCategory"),
        fallbackReason = usage.optionalString("fallbackReason")
    )
}

private fun JsonObject.optionalLong(name: String): Long? =
    get(name)?.takeUnless { it.isJsonNull }?.let { element ->
        runCatching { element.asLong }.getOrNull()
    }

private fun JsonObject.longOrDefault(name: String): Long = optionalLong(name) ?: 0L

private fun JsonObject.booleanOrDefault(name: String): Boolean =
    get(name)?.takeUnless { it.isJsonNull }?.let { element ->
        runCatching { element.asBoolean }.getOrNull()
    } ?: false

private fun JsonObject.optionalString(name: String): String? =
    get(name)?.takeUnless { it.isJsonNull }?.let { element ->
        runCatching { element.asString }.getOrNull()
    }

class ManagedGatewayException(val statusCode: Int, override val message: String) : Exception(message)

@Singleton
class ManagedAIClient @Inject constructor(
    private val gateway: ManagedGatewayClient
) : AIClient {
    override suspend fun chat(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int
    ): Result<String> = runCatching {
        gateway.infer(model, messages, systemPrompt, temperature, maxTokens, emptyList()).content.orEmpty()
    }

    override suspend fun chatWithTools(
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        tools: List<Map<String, Any>>
    ): Result<ChatResponseWithTools> = runCatching {
        gateway.infer(model, messages, systemPrompt, temperature, maxTokens, tools)
    }

    override suspend fun getAvailableModels(): Result<List<String>> = runCatching {
        gateway.models().map { it.id }
    }.recover { ManagedModelCatalog.defaults.map { it.id } }

    override suspend fun getAvailableModelInfos(): Result<List<RemoteModelInfo>> = runCatching {
        gateway.models().map { RemoteModelInfo(it.id, it.contextWindow) }
    }.recover { ManagedModelCatalog.defaults.map { RemoteModelInfo(it.id, it.contextWindow) } }
}
