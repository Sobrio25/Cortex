package com.aiagents.app.data.remote

import com.aiagents.app.data.auth.FirebaseAuthManager
import com.aiagents.app.domain.model.ManagedModel
import com.aiagents.app.domain.model.ManagedModelCatalog
import com.aiagents.app.domain.model.SubscriptionPlan
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.UsageSnapshot
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
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
    val freeTokensLimit: Long = 2_000_000,
    val spentMicros: Long = 0,
    val budgetMicros: Long = 0,
    val periodEndEpochMillis: Long? = null
) {
    fun toDomain() = UsageSnapshot(
        plan = SubscriptionPlan.fromId(plan),
        freeTokensUsed = freeTokensUsed,
        freeTokensLimit = freeTokensLimit,
        spentMicros = spentMicros,
        budgetMicros = budgetMicros,
        periodEndEpochMillis = periodEndEpochMillis
    )
}

data class PurchaseVerificationRequest(
    val productId: String,
    val purchaseToken: String,
    val packageName: String = "com.aiagents.app"
)

@Singleton
class ManagedGatewayClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val authManager: FirebaseAuthManager,
    private val gson: Gson
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun account(): UsageSnapshot = authenticatedGet("/v1/account").let {
        gson.fromJson(it, ManagedAccountResponse::class.java).toDomain()
    }

    suspend fun models(): List<ManagedModel> {
        val type = object : TypeToken<List<ManagedModel>>() {}.type
        return gson.fromJson<List<ManagedModel>>(authenticatedGet("/v1/models"), type)
    }

    suspend fun verifyPurchase(request: PurchaseVerificationRequest): UsageSnapshot {
        val response = authenticatedPost("/v1/billing/google-play/verify", gson.toJson(request))
        return gson.fromJson(response, ManagedAccountResponse::class.java).toDomain()
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
        val response = root.getAsJsonObject("response") ?: root
        return gson.fromJson(response, ChatResponseWithTools::class.java)
    }

    private suspend fun authenticatedGet(path: String): String = request(path, "GET", null)

    private suspend fun authenticatedPost(path: String, body: String): String = request(path, "POST", body)

    private suspend fun request(path: String, method: String, body: String?): String = withContext(Dispatchers.IO) {
        val token = authManager.idToken()
        val builder = Request.Builder()
            .url("$MANAGED_BASE_URL$path")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
        if (method == "POST") builder.post((body ?: "{}").toRequestBody(jsonMediaType))
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
