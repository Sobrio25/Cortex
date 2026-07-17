package com.aiagents.app.data.telemetry

import android.util.Log
import com.aiagents.app.BuildConfig
import com.aiagents.app.data.auth.FirebaseAuthManager
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.domain.model.ProviderType
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class AppUsageSource(val wireValue: String) {
    BYOK("byok"),
    LOCAL("local")
}

enum class AppUsageOperation(val wireValue: String) {
    CHAT("chat"),
    CHAT_WITH_TOOLS("chat_with_tools"),
    STREAM("stream")
}

data class AppUsageEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val source: String,
    val provider: String,
    val model: String,
    val promptTokens: Long,
    val completionTokens: Long,
    val usageEstimated: Boolean = true,
    val durationMs: Long,
    val status: String,
    val operation: String,
    val appVersion: String = BuildConfig.VERSION_NAME
)

fun interface AppUsageSink {
    fun record(event: AppUsageEvent)
}

/** Best-effort metadata telemetry. It never runs on or blocks the inference coroutine. */
@Singleton
class AppUsageTelemetry @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val authManager: FirebaseAuthManager,
    private val gson: Gson,
    private val securePreferences: SecurePreferences
) : AppUsageSink {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun record(event: AppUsageEvent) {
        if (!securePreferences.isGlobalUsageAnalyticsEnabled()) return
        scope.launch {
            runCatching {
                val token = authManager.idToken()
                val request = Request.Builder()
                    .url(APP_USAGE_URL)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .post(gson.toJson(event).toRequestBody(jsonMediaType))
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Global usage telemetry rejected with HTTP ${response.code}")
                    }
                }
            }.onFailure { error ->
                Log.w(TAG, "Could not report global usage metadata", error)
            }
        }
    }

    private companion object {
        const val TAG = "AppUsageTelemetry"
        const val APP_USAGE_URL =
            "https://us-central1-cortex-agents-ai.cloudfunctions.net/api/v1/app-usage"
    }
}

internal fun ProviderType.telemetryName(): String = when (this) {
    ProviderType.MANAGED -> "Cortex managed"
    ProviderType.OPENROUTER -> "OpenRouter"
    ProviderType.GOOGLE_AI -> "Google AI"
    ProviderType.OPENAI -> "OpenAI"
    ProviderType.NVIDIA -> "NVIDIA"
    ProviderType.OLLAMA -> "Ollama"
    ProviderType.LM_STUDIO -> "LM Studio"
    ProviderType.MINIMAX -> "MiniMax"
    ProviderType.MOONSHOT -> "Moonshot AI"
    ProviderType.ANTHROPIC -> "Anthropic"
    ProviderType.DEEPSEEK -> "DeepSeek"
    ProviderType.GROK -> "xAI"
    ProviderType.KILO -> "Kilo"
    ProviderType.ALIBABA -> "Alibaba"
    ProviderType.OPENCODE -> "OpenCode"
    ProviderType.ZAI -> "Z.AI"
    ProviderType.LOCAL -> "Local"
}
