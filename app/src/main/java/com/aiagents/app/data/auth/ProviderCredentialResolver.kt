package com.aiagents.app.data.auth

import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.domain.model.MoonshotEndpointType
import com.aiagents.app.domain.model.ProviderType
import com.aiagents.app.domain.model.ZAIPlanType
import javax.inject.Inject
import javax.inject.Singleton

data class ProviderCredentials(
    val apiKey: String,
    val baseUrl: String?
)

/**
 * Single source of truth for the credential and destination paired with a provider request.
 * In particular, OpenAI direct credentials can never be paired with a user-controlled URL.
 */
@Singleton
class ProviderCredentialResolver @Inject constructor(
    private val securePreferences: SecurePreferences
) {
    fun resolve(provider: ProviderType): ProviderCredentials? = when (provider) {
        ProviderType.LOCAL -> ProviderCredentials(apiKey = "", baseUrl = null)
        ProviderType.OLLAMA -> ProviderCredentials(
            apiKey = "",
            baseUrl = securePreferences.getBaseUrl(provider)
        )
        ProviderType.LM_STUDIO -> ProviderCredentials(
            apiKey = securePreferences.getApiKey(provider).orEmpty(),
            baseUrl = securePreferences.getBaseUrl(provider)
        )
        ProviderType.OPENAI -> resolveOpenAI()
        ProviderType.MOONSHOT -> resolveVariant(
            active = securePreferences.getActiveMoonshotEndpoint(),
            variants = MoonshotEndpointType.entries,
            key = securePreferences::getMoonshotApiKey,
            baseUrl = MoonshotEndpointType::baseUrl
        )
        ProviderType.ZAI -> resolveVariant(
            active = securePreferences.getActiveZAIPlan(),
            variants = ZAIPlanType.entries,
            key = securePreferences::getZAIApiKey,
            baseUrl = ZAIPlanType::baseUrl
        )
        ProviderType.OPENCODE -> securePreferences.getActiveOpenCodeVariant().let { active ->
            securePreferences.getOpenCodeApiKey(active)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { ProviderCredentials(it, active.baseUrl) }
        }
        else -> securePreferences.getApiKey(provider)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { ProviderCredentials(it, securePreferences.getBaseUrl(provider)) }
    }

    fun resolveOpenAI(): ProviderCredentials? =
        securePreferences.getOpenAIProviderApiKey()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { ProviderCredentials(it, OpenAIEndpointPolicy.OFFICIAL_API_BASE_URL) }

    private fun <T> resolveVariant(
        active: T,
        variants: Iterable<T>,
        key: (T) -> String?,
        baseUrl: (T) -> String
    ): ProviderCredentials? {
        val ordered = buildList {
            add(active)
            variants.forEach { if (it != active) add(it) }
        }
        return ordered.firstNotNullOfOrNull { variant ->
            key(variant)?.trim()?.takeIf(String::isNotEmpty)?.let {
                ProviderCredentials(it, baseUrl(variant))
            }
        }
    }
}

object OpenAIEndpointPolicy {
    const val OFFICIAL_API_BASE_URL = "https://api.openai.com/v1/"
}
