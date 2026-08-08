package com.aiagents.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aiagents.app.domain.model.MoonshotEndpointType
import com.aiagents.app.domain.model.FREE_DATA_CONSENT_VERSION
import com.aiagents.app.domain.model.OpenCodeVariantType
import com.aiagents.app.domain.model.ProviderType
import com.aiagents.app.domain.model.WebSearchProvider
import com.aiagents.app.domain.model.ZAIPlanType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val encryptedPrefs by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Never downgrade API keys/tokens to plaintext storage.
            android.util.Log.e("SecurePreferences", "Encrypted storage is unavailable", e)
            throw IllegalStateException("No se pudo abrir el almacenamiento seguro", e)
        }
    }

    fun saveApiKey(provider: ProviderType, apiKey: String) {
        encryptedPrefs.edit().putString("${provider.name}_API_KEY", apiKey.trim()).apply()
    }

    fun getApiKey(provider: ProviderType): String? {
        return encryptedPrefs.getString("${provider.name}_API_KEY", null)
    }

    fun removeApiKey(provider: ProviderType) {
        encryptedPrefs.edit().remove("${provider.name}_API_KEY").apply()
    }

    fun saveBaseUrl(provider: ProviderType, baseUrl: String) {
        if (baseUrl.isBlank()) {
            encryptedPrefs.edit().remove("${provider.name}_BASE_URL").apply()
        } else {
            encryptedPrefs.edit().putString("${provider.name}_BASE_URL", baseUrl.trim()).apply()
        }
    }

    fun getBaseUrl(provider: ProviderType): String? {
        return encryptedPrefs.getString("${provider.name}_BASE_URL", null)
    }

    fun hasApiKey(provider: ProviderType): Boolean {
        return getApiKey(provider) != null
    }

    /**
     * OpenAI's inference API is configured only with its official API key flow.
     * Legacy proxy-OAuth values are deleted so obsolete session tokens are not retained.
     */
    fun saveOpenAIProviderApiKey(apiKey: String) {
        encryptedPrefs.edit()
            .putString("${ProviderType.OPENAI.name}_API_KEY", apiKey.trim())
            .remove("OPENAI_AUTH_MODE")
            .remove("OPENAI_BACKEND_BASE_URL")
            .remove("OPENAI_BACKEND_TOKEN")
            .remove("${ProviderType.OPENAI.name}_BASE_URL")
            .apply()
    }

    fun getOpenAIProviderApiKey(): String? {
        clearLegacyOpenAIOAuthConfig()
        return getApiKey(ProviderType.OPENAI)
    }

    private fun clearLegacyOpenAIOAuthConfig() {
        val legacyKeys = listOf(
            "OPENAI_AUTH_MODE",
            "OPENAI_BACKEND_BASE_URL",
            "OPENAI_BACKEND_TOKEN",
            "${ProviderType.OPENAI.name}_BASE_URL"
        )
        if (legacyKeys.none(encryptedPrefs::contains)) return
        encryptedPrefs.edit().also { editor ->
            legacyKeys.forEach(editor::remove)
        }.apply()
    }

    /** Stable, salted identifier for cache isolation; never persists a raw credential or URL. */
    @Synchronized
    fun credentialScope(
        provider: ProviderType,
        credential: String,
        destination: String
    ): String {
        val saltKey = "PROVIDER_SCOPE_SALT"
        val salt = encryptedPrefs.getString(saltKey, null) ?: ByteArray(32).also {
            SecureRandom().nextBytes(it)
        }.joinToString("") { "%02x".format(it.toInt() and 0xff) }.also {
            encryptedPrefs.edit().putString(saltKey, it).commit()
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$salt\u0000${provider.name}\u0000$destination\u0000${credential.trim()}".toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun setActiveProvider(provider: ProviderType) {
        encryptedPrefs.edit().putString("ACTIVE_PROVIDER", provider.name).apply()
    }

    fun getActiveProvider(): ProviderType? {
        val name = encryptedPrefs.getString("ACTIVE_PROVIDER", null) ?: return null
        return try {
            ProviderType.valueOf(name)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    // Flow reactivo para notificar cambios en modelos seleccionados
    private val _selectedModelsChanged = MutableSharedFlow<Set<String>>(replay = 1)
    val selectedModelsChanged: SharedFlow<Set<String>> = _selectedModelsChanged.asSharedFlow()

    // Modelos seleccionados: formato "PROVIDER|modelId", e.g. "OPENAI|gpt-4o"
    fun saveSelectedModels(models: Set<String>) {
        encryptedPrefs.edit().putString("SELECTED_MODELS", models.joinToString(";;")).apply()
        _selectedModelsChanged.tryEmit(models)
    }

    fun getSelectedModels(): Set<String> {
        val raw = encryptedPrefs.getString("SELECTED_MODELS", "") ?: ""
        return if (raw.isBlank()) emptySet()
        else raw.split(";;").filter { it.isNotBlank() }.toSet()
    }

    fun addSelectedModel(providerType: ProviderType, modelId: String) {
        val current = getSelectedModels().toMutableSet()
        current.add("${providerType.name}|$modelId")
        saveSelectedModels(current)
    }

    fun removeSelectedModel(providerType: ProviderType, modelId: String) {
        val current = getSelectedModels().toMutableSet()
        current.remove("${providerType.name}|$modelId")
        saveSelectedModels(current)
    }

    fun clearSelectedModels(providerType: ProviderType) {
        saveSelectedModels(
            getSelectedModels().filterNot { it.startsWith("${providerType.name}|") }.toSet()
        )
    }

    fun isModelSelected(providerType: ProviderType, modelId: String): Boolean {
        return getSelectedModels().contains("${providerType.name}|$modelId")
    }

    fun setManagedPrivacyAccepted(accepted: Boolean) {
        encryptedPrefs.edit().also { editor ->
            if (accepted) {
                editor.putInt("MANAGED_FREE_DATA_CONSENT_VERSION", FREE_DATA_CONSENT_VERSION)
            } else {
                editor.remove("MANAGED_FREE_DATA_CONSENT_VERSION")
            }
            editor.remove("MANAGED_PRIVACY_ACCEPTED")
        }.apply()
    }

    fun isManagedPrivacyAccepted(): Boolean =
        encryptedPrefs.getInt("MANAGED_FREE_DATA_CONSENT_VERSION", 0) >= FREE_DATA_CONSENT_VERSION

    fun enableManagedFreePlan() {
        setManagedPrivacyAccepted(true)
        addSelectedModel(ProviderType.MANAGED, "auto")
        setActiveProvider(ProviderType.MANAGED)
    }

    /** Stops routing through the managed plan without revoking consent already granted by the user. */
    fun disableManagedFreePlanSelection() {
        clearSelectedModels(ProviderType.MANAGED)
        if (getActiveProvider() == ProviderType.MANAGED) {
            encryptedPrefs.edit().remove("ACTIVE_PROVIDER").apply()
        }
    }

    fun saveHuggingFaceToken(token: String) {
        encryptedPrefs.edit().putString("HUGGINGFACE_TOKEN", token).apply()
    }

    fun getHuggingFaceToken(): String? {
        return encryptedPrefs.getString("HUGGINGFACE_TOKEN", null)
    }

    fun removeHuggingFaceToken() {
        encryptedPrefs.edit().remove("HUGGINGFACE_TOKEN").apply()
    }

    fun hasHuggingFaceToken(): Boolean {
        return !getHuggingFaceToken().isNullOrBlank()
    }

    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }

    // User identity entered during onboarding. Kept here as well as in semantic memory so the
    // synchronous streaming path can always build truthful runtime context.
    fun saveUserIdentity(name: String, preferredName: String) {
        encryptedPrefs.edit()
            .putString("USER_NAME", name.trim())
            .putString("USER_PREFERRED_NAME", preferredName.trim())
            .apply()
    }

    fun getUserName(): String? = encryptedPrefs.getString("USER_NAME", null)
        ?.trim()?.takeIf { it.isNotEmpty() }

    fun getPreferredUserName(): String? = encryptedPrefs.getString("USER_PREFERRED_NAME", null)
        ?.trim()?.takeIf { it.isNotEmpty() }

    fun saveAssistantName(name: String) {
        encryptedPrefs.edit().putString("ASSISTANT_NAME", name.trim()).apply()
    }

    fun getAssistantName(): String? {
        encryptedPrefs.getString("ASSISTANT_NAME", null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        // One-time compatibility read for installations created before the assistant name
        // became a first-class configurable identity.
        val legacyName = encryptedPrefs.getString("CORTEX_NAME", null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        encryptedPrefs.edit()
            .putString("ASSISTANT_NAME", legacyName)
            .remove("CORTEX_NAME")
            .apply()
        return legacyName
    }

    fun saveAssistantRemoteSttApiKey(apiKey: String) {
        encryptedPrefs.edit().also { editor ->
            if (apiKey.isBlank()) editor.remove("ASSISTANT_REMOTE_STT_API_KEY")
            else editor.putString("ASSISTANT_REMOTE_STT_API_KEY", apiKey.trim())
        }.apply()
    }

    fun getAssistantRemoteSttApiKey(): String =
        encryptedPrefs.getString("ASSISTANT_REMOTE_STT_API_KEY", null).orEmpty()

    fun saveVoiceSttApiKey(providerId: String, apiKey: String) {
        encryptedPrefs.edit().also { editor ->
            val key = "VOICE_STT_${providerId.uppercase()}_API_KEY"
            if (apiKey.isBlank()) editor.remove(key) else editor.putString(key, apiKey.trim())
        }.apply()
    }

    fun saveVoiceSttApiKeySync(providerId: String, apiKey: String): Boolean {
        val key = "VOICE_STT_${providerId.uppercase()}_API_KEY"
        return encryptedPrefs.edit().also { editor ->
            if (apiKey.isBlank()) editor.remove(key) else editor.putString(key, apiKey.trim())
        }.commit()
    }

    fun getVoiceSttApiKey(providerId: String): String =
        encryptedPrefs.getString("VOICE_STT_${providerId.uppercase()}_API_KEY", null).orEmpty()

    fun saveVoiceTtsApiKey(providerId: String, apiKey: String) {
        encryptedPrefs.edit().also { editor ->
            val key = "VOICE_TTS_${providerId.uppercase()}_API_KEY"
            if (apiKey.isBlank()) editor.remove(key) else editor.putString(key, apiKey.trim())
        }.apply()
    }

    fun getVoiceTtsApiKey(providerId: String): String =
        encryptedPrefs.getString("VOICE_TTS_${providerId.uppercase()}_API_KEY", null).orEmpty()

    fun saveAssistantRemoteTtsApiKey(apiKey: String) {
        encryptedPrefs.edit().also { editor ->
            if (apiKey.isBlank()) editor.remove("ASSISTANT_REMOTE_TTS_API_KEY")
            else editor.putString("ASSISTANT_REMOTE_TTS_API_KEY", apiKey.trim())
        }.apply()
    }

    fun getAssistantRemoteTtsApiKey(): String =
        encryptedPrefs.getString("ASSISTANT_REMOTE_TTS_API_KEY", null).orEmpty()

    // Preferencia para mostrar/ocultar razonamiento de modelos
    fun setShowReasoning(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean("SHOW_REASONING", enabled).apply()
    }

    fun getShowReasoning(): Boolean {
        return encryptedPrefs.getBoolean("SHOW_REASONING", false)
    }

    // Preferencia para mostrar/ocultar comandos ejecutados
    fun setShowCommands(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean("SHOW_COMMANDS", enabled).apply()
    }

    fun getShowCommands(): Boolean {
        return encryptedPrefs.getBoolean("SHOW_COMMANDS", false)
    }

    // Brave Search MCP
    fun saveBraveApiKey(apiKey: String) {
        encryptedPrefs.edit().putString("BRAVE_SEARCH_API_KEY", apiKey.trim()).apply()
    }

    fun getBraveApiKey(): String? {
        return encryptedPrefs.getString("BRAVE_SEARCH_API_KEY", null)
    }

    fun removeBraveApiKey() {
        encryptedPrefs.edit().remove("BRAVE_SEARCH_API_KEY").apply()
    }

    fun hasBraveApiKey(): Boolean {
        return !getBraveApiKey().isNullOrBlank()
    }

    // Google Maps MCP
    fun saveGoogleMapsApiKey(apiKey: String) {
        encryptedPrefs.edit().putString("GOOGLE_MAPS_API_KEY", apiKey.trim()).apply()
    }

    fun getGoogleMapsApiKey(): String? {
        return encryptedPrefs.getString("GOOGLE_MAPS_API_KEY", null)
    }

    fun removeGoogleMapsApiKey() {
        encryptedPrefs.edit().remove("GOOGLE_MAPS_API_KEY").apply()
    }

    fun hasGoogleMapsApiKey(): Boolean {
        return !getGoogleMapsApiKey().isNullOrBlank()
    }

    // SerpAPI MCP
    fun saveSerpApiKey(apiKey: String) {
        encryptedPrefs.edit().putString("SERPAPI_API_KEY", apiKey.trim()).apply()
    }

    fun getSerpApiKey(): String? {
        return encryptedPrefs.getString("SERPAPI_API_KEY", null)
    }

    fun removeSerpApiKey() {
        encryptedPrefs.edit().remove("SERPAPI_API_KEY").apply()
    }

    fun hasSerpApiKey(): Boolean {
        return !getSerpApiKey().isNullOrBlank()
    }

    fun setWebSearchProvider(provider: WebSearchProvider) {
        encryptedPrefs.edit().putString("WEB_SEARCH_PROVIDER", provider.name).apply()
    }

    fun getWebSearchProvider(): WebSearchProvider = WebSearchProvider.fromStoredValue(
        encryptedPrefs.getString("WEB_SEARCH_PROVIDER", null)
    )

    // Canva
    fun saveCanvaAccessToken(token: String) {
        encryptedPrefs.edit().putString("CANVA_ACCESS_TOKEN", token.trim()).apply()
    }

    fun getCanvaAccessToken(): String? {
        return encryptedPrefs.getString("CANVA_ACCESS_TOKEN", null)
    }

    fun removeCanvaAccessToken() {
        encryptedPrefs.edit().remove("CANVA_ACCESS_TOKEN").apply()
    }

    fun hasCanvaAccessToken(): Boolean {
        return !getCanvaAccessToken().isNullOrBlank()
    }

    // Finance (local, solo toggle habilitado/deshabilitado)
    fun setFinanceEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean("FINANCE_ENABLED", enabled).apply()
    }

    fun isFinanceEnabled(): Boolean {
        return encryptedPrefs.getBoolean("FINANCE_ENABLED", false)
    }

    // PubMed (gratuito, solo toggle habilitado/deshabilitado)
    fun setPubMedEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean("PUBMED_ENABLED", enabled).apply()
    }

    fun isPubMedEnabled(): Boolean {
        return encryptedPrefs.getBoolean("PUBMED_ENABLED", false)
    }

    // GitHub
    fun saveGitHubToken(token: String) { encryptedPrefs.edit().putString("GITHUB_TOKEN", token).apply() }
    fun getGitHubToken(): String? = encryptedPrefs.getString("GITHUB_TOKEN", null)
    fun hasGitHubToken(): Boolean = !getGitHubToken().isNullOrBlank()
    fun removeGitHubToken() { encryptedPrefs.edit().remove("GITHUB_TOKEN").apply() }

    // Notion
    fun saveNotionToken(token: String) { encryptedPrefs.edit().putString("NOTION_TOKEN", token).apply() }
    fun getNotionToken(): String? = encryptedPrefs.getString("NOTION_TOKEN", null)
    fun hasNotionToken(): Boolean = !getNotionToken().isNullOrBlank()
    fun removeNotionToken() { encryptedPrefs.edit().remove("NOTION_TOKEN").apply() }

    // Slack
    fun saveSlackToken(token: String) { encryptedPrefs.edit().putString("SLACK_TOKEN", token).apply() }
    fun getSlackToken(): String? = encryptedPrefs.getString("SLACK_TOKEN", null)
    fun hasSlackToken(): Boolean = !getSlackToken().isNullOrBlank()
    fun removeSlackToken() { encryptedPrefs.edit().remove("SLACK_TOKEN").apply() }

    // Google Workspace authorization through Google Identity Services
    fun saveGoogleWorkspaceAccessToken(token: String) = encryptedPrefs.edit().putString("gws_access_token", token).apply()
    fun getGoogleWorkspaceAccessToken(): String? = encryptedPrefs.getString("gws_access_token", null)
    fun saveGoogleWorkspaceScopes(scopes: String) = encryptedPrefs.edit().putString("gws_scopes", scopes).apply()
    fun getGoogleWorkspaceScopes(): String? = encryptedPrefs.getString("gws_scopes", null)
    fun saveGoogleWorkspaceTokenExpiry(expiry: Long) = encryptedPrefs.edit().putLong("gws_token_expiry", expiry).apply()
    fun getGoogleWorkspaceTokenExpiry(): Long = encryptedPrefs.getLong("gws_token_expiry", 0L)
    fun saveGoogleWorkspaceAccountEmail(email: String) = encryptedPrefs.edit().putString("gws_account_email", email).apply()
    fun getGoogleWorkspaceAccountEmail(): String? = encryptedPrefs.getString("gws_account_email", null)
    fun hasGoogleWorkspaceConfig(): Boolean = !getGoogleWorkspaceAccessToken().isNullOrBlank()
    fun clearLegacyGoogleOAuthCredentials() {
        encryptedPrefs.edit()
            .remove("GDRIVE_CLIENT_ID")
            .remove("GDRIVE_CLIENT_SECRET")
            .remove("GDRIVE_ACCESS_TOKEN")
            .remove("GDRIVE_REFRESH_TOKEN")
            .apply()
    }
    fun clearGoogleWorkspace() {
        encryptedPrefs.edit()
            .remove("gws_access_token")
            .remove("gws_refresh_token")
            .remove("gws_scopes")
            .remove("gws_token_expiry")
            .remove("gws_account_email")
            // Purge credentials left by the removed localhost/client-secret flow.
            .remove("GDRIVE_CLIENT_ID")
            .remove("GDRIVE_CLIENT_SECRET")
            .remove("GDRIVE_ACCESS_TOKEN")
            .remove("GDRIVE_REFRESH_TOKEN")
            .apply()
    }

    // Obsidian vault path
    fun saveObsidianVaultPath(path: String) {
        encryptedPrefs.edit().putString("OBSIDIAN_VAULT_PATH", path).apply()
    }

    fun getObsidianVaultPath(): String? {
        return encryptedPrefs.getString("OBSIDIAN_VAULT_PATH", null)
    }

    fun hasObsidianVaultPath(): Boolean {
        return !getObsidianVaultPath().isNullOrBlank()
    }

    fun clearObsidianVaultPath() {
        encryptedPrefs.edit().remove("OBSIDIAN_VAULT_PATH").apply()
    }

    // Active workspace persistence
    fun setActiveWorkspaceId(id: Long) {
        encryptedPrefs.edit().putLong("ACTIVE_WORKSPACE_ID", id).apply()
    }

    fun getActiveWorkspaceId(): Long {
        return encryptedPrefs.getLong("ACTIVE_WORKSPACE_ID", -1L)
    }

    // Moonshot multi-endpoint API keys
    fun saveMoonshotApiKey(endpointType: MoonshotEndpointType, apiKey: String) {
        encryptedPrefs.edit().putString("MOONSHOT_${endpointType.preferenceKey}_API_KEY", apiKey.trim()).apply()
    }

    fun getMoonshotApiKey(endpointType: MoonshotEndpointType): String? {
        return encryptedPrefs.getString("MOONSHOT_${endpointType.preferenceKey}_API_KEY", null)
    }

    fun removeMoonshotApiKey(endpointType: MoonshotEndpointType) {
        encryptedPrefs.edit().remove("MOONSHOT_${endpointType.preferenceKey}_API_KEY").apply()
    }

    fun hasMoonshotApiKey(endpointType: MoonshotEndpointType): Boolean {
        return getMoonshotApiKey(endpointType) != null
    }

    fun getActiveMoonshotEndpoint(): MoonshotEndpointType {
        val saved = encryptedPrefs.getString("MOONSHOT_ACTIVE_ENDPOINT", null)
        return try {
            saved?.let { MoonshotEndpointType.valueOf(it) } ?: MoonshotEndpointType.GLOBAL
        } catch (e: IllegalArgumentException) {
            MoonshotEndpointType.GLOBAL
        }
    }

    fun setActiveMoonshotEndpoint(endpointType: MoonshotEndpointType) {
        encryptedPrefs.edit().putString("MOONSHOT_ACTIVE_ENDPOINT", endpointType.name).apply()
    }

    // Z.AI multi-plan API keys
    fun saveZAIApiKey(planType: ZAIPlanType, apiKey: String) {
        encryptedPrefs.edit().putString("ZAI_${planType.preferenceKey}_API_KEY", apiKey.trim()).apply()
    }

    fun getZAIApiKey(planType: ZAIPlanType): String? {
        return encryptedPrefs.getString("ZAI_${planType.preferenceKey}_API_KEY", null)
    }

    fun removeZAIApiKey(planType: ZAIPlanType) {
        encryptedPrefs.edit().remove("ZAI_${planType.preferenceKey}_API_KEY").apply()
    }

    fun hasZAIApiKey(planType: ZAIPlanType): Boolean {
        return getZAIApiKey(planType) != null
    }

    fun getActiveZAIPlan(): ZAIPlanType {
        val saved = encryptedPrefs.getString("ZAI_ACTIVE_PLAN", null)
        return try {
            saved?.let { ZAIPlanType.valueOf(it) } ?: ZAIPlanType.STANDARD
        } catch (e: IllegalArgumentException) {
            ZAIPlanType.STANDARD
        }
    }

    fun setActiveZAIPlan(planType: ZAIPlanType) {
        encryptedPrefs.edit().putString("ZAI_ACTIVE_PLAN", planType.name).apply()
    }

    // OpenCode multi-variant API keys
    fun saveOpenCodeApiKey(variantType: OpenCodeVariantType, apiKey: String) {
        encryptedPrefs.edit()
            .putString("OPENCODE_${variantType.preferenceKey}_API_KEY", apiKey.trim())
            .remove("OPENCODE_API_KEY")
            .apply()
        // Auto-activar esta variante al guardar la key
        setActiveOpenCodeVariant(variantType)
    }

    fun getOpenCodeApiKey(variantType: OpenCodeVariantType): String? {
        migrateLegacyOpenCodeApiKeyIfNeeded()
        return encryptedPrefs.getString("OPENCODE_${variantType.preferenceKey}_API_KEY", null)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    fun removeOpenCodeApiKey(variantType: OpenCodeVariantType) {
        encryptedPrefs.edit().remove("OPENCODE_${variantType.preferenceKey}_API_KEY").apply()
    }

    fun hasOpenCodeApiKey(variantType: OpenCodeVariantType): Boolean {
        return getOpenCodeApiKey(variantType) != null
    }

    fun getActiveOpenCodeVariant(): OpenCodeVariantType {
        migrateLegacyOpenCodeApiKeyIfNeeded()
        val saved = encryptedPrefs.getString("OPENCODE_ACTIVE_VARIANT", null)
        val savedVariant = try {
            saved?.let { OpenCodeVariantType.valueOf(it) }
        } catch (e: IllegalArgumentException) {
            null
        }

        // Si hay una variante guardada y tiene key, usarla
        if (savedVariant != null && hasOpenCodeApiKey(savedVariant)) {
            return savedVariant
        }

        // Si no hay variante guardada o no tiene key, auto-detectar cuál variante tiene key
        // Priorizar ZEN si ambas existen, sino usar la que tenga key
        val hasZenKey = hasOpenCodeApiKey(OpenCodeVariantType.ZEN)
        val hasGoKey = hasOpenCodeApiKey(OpenCodeVariantType.GO)

        return when {
            hasZenKey -> OpenCodeVariantType.ZEN
            hasGoKey -> OpenCodeVariantType.GO
            else -> OpenCodeVariantType.ZEN // Por defecto si no hay ninguna
        }
    }

    fun setActiveOpenCodeVariant(variantType: OpenCodeVariantType) {
        encryptedPrefs.edit().putString("OPENCODE_ACTIVE_VARIANT", variantType.name).apply()
    }

    /**
     * Old builds stored one OpenCode key and exposed it as both Zen and Go. Move it once to the
     * selected destination so a credential is never silently sent to the other service.
     */
    @Synchronized
    private fun migrateLegacyOpenCodeApiKeyIfNeeded() {
        val legacyKey = encryptedPrefs.getString("OPENCODE_API_KEY", null)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return
        val hasZen = !encryptedPrefs
            .getString("OPENCODE_${OpenCodeVariantType.ZEN.preferenceKey}_API_KEY", null)
            .isNullOrBlank()
        val hasGo = !encryptedPrefs
            .getString("OPENCODE_${OpenCodeVariantType.GO.preferenceKey}_API_KEY", null)
            .isNullOrBlank()
        val savedVariant = runCatching {
            OpenCodeVariantType.valueOf(
                encryptedPrefs.getString("OPENCODE_ACTIVE_VARIANT", null).orEmpty()
            )
        }.getOrDefault(OpenCodeVariantType.ZEN)

        val migrationTarget = when {
            !hasZen && !hasGo -> savedVariant
            !hasZen && hasGo -> OpenCodeVariantType.ZEN
            hasZen && !hasGo && savedVariant == OpenCodeVariantType.GO -> OpenCodeVariantType.GO
            else -> null
        }
        val editor = encryptedPrefs.edit().remove("OPENCODE_API_KEY")
        migrationTarget?.let { target ->
            editor.putString("OPENCODE_${target.preferenceKey}_API_KEY", legacyKey)
        }
        editor.apply()
    }

    // Anthropic OAuth
    fun saveAnthropicClientId(id: String) = encryptedPrefs.edit().putString("ANTHROPIC_CLIENT_ID", id).apply()
    fun getAnthropicClientId(): String? = encryptedPrefs.getString("ANTHROPIC_CLIENT_ID", null)
    fun saveAnthropicClientSecret(secret: String) = encryptedPrefs.edit().putString("ANTHROPIC_CLIENT_SECRET", secret).apply()
    fun getAnthropicClientSecret(): String? = encryptedPrefs.getString("ANTHROPIC_CLIENT_SECRET", null)
    fun saveAnthropicAccessToken(token: String) = encryptedPrefs.edit().putString("ANTHROPIC_ACCESS_TOKEN", token).apply()
    fun getAnthropicAccessToken(): String? = encryptedPrefs.getString("ANTHROPIC_ACCESS_TOKEN", null)
    fun saveAnthropicRefreshToken(token: String) = encryptedPrefs.edit().putString("ANTHROPIC_REFRESH_TOKEN", token).apply()
    fun getAnthropicRefreshToken(): String? = encryptedPrefs.getString("ANTHROPIC_REFRESH_TOKEN", null)
    fun saveAnthropicTokenExpiry(expiry: Long) = encryptedPrefs.edit().putLong("ANTHROPIC_TOKEN_EXPIRY", expiry).apply()
    fun getAnthropicTokenExpiry(): Long = encryptedPrefs.getLong("ANTHROPIC_TOKEN_EXPIRY", 0L)
    fun hasAnthropicOAuthConfig(): Boolean = !getAnthropicAccessToken().isNullOrBlank()
    fun clearAnthropic() {
        encryptedPrefs.edit()
            .remove("ANTHROPIC_CLIENT_ID").remove("ANTHROPIC_CLIENT_SECRET")
            .remove("ANTHROPIC_ACCESS_TOKEN").remove("ANTHROPIC_REFRESH_TOKEN")
            .remove("ANTHROPIC_TOKEN_EXPIRY").apply()
    }

    // Onboarding
    fun isOnboardingCompleted(): Boolean = encryptedPrefs.getBoolean("ONBOARDING_COMPLETED", false)

    fun setOnboardingCompleted(completed: Boolean) {
        encryptedPrefs.edit().putBoolean("ONBOARDING_COMPLETED", completed).apply()
    }

    fun setOnboardingMode(mode: String) {
        encryptedPrefs.edit().putString("ONBOARDING_MODE", mode).apply()
    }

    fun getOnboardingMode(): String? = encryptedPrefs.getString("ONBOARDING_MODE", null)

    /** UI and assistant language always follow the device language. */
    fun getAppLanguage(): String =
        if (android.content.res.Resources.getSystem().configuration.locales[0].language == "es") {
            "es"
        } else {
            "en"
        }

    fun clearStoredAppLanguage() {
        encryptedPrefs.edit().remove("APP_LANGUAGE").apply()
    }

    // OpenAI API (para DALL-E)
    fun saveOpenAIApiKey(apiKey: String) {
        encryptedPrefs.edit().putString("OPENAI_IMAGE_API_KEY", apiKey.trim()).apply()
    }

    fun getOpenAIApiKey(): String? {
        return encryptedPrefs.getString("OPENAI_IMAGE_API_KEY", null)
    }

    fun removeOpenAIApiKey() {
        encryptedPrefs.edit().remove("OPENAI_IMAGE_API_KEY").apply()
    }

    fun hasOpenAIApiKey(): Boolean {
        return !getOpenAIApiKey().isNullOrBlank()
    }

    // Weather toggle
    fun setWeatherEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean("WEATHER_ENABLED", enabled).apply()
    }

    fun isWeatherEnabled(): Boolean {
        return encryptedPrefs.getBoolean("WEATHER_ENABLED", true)
    }

    // Image Generation toggle
    fun setImageGenerationEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean("IMAGE_GENERATION_ENABLED", enabled).apply()
    }

    fun isImageGenerationEnabled(): Boolean {
        return encryptedPrefs.getBoolean("IMAGE_GENERATION_ENABLED", false)
    }

    // Google Gemini API (para generación de imágenes con Nano Banana / Gemini 2.0 Flash)
    fun saveGoogleImagenApiKey(apiKey: String) {
        encryptedPrefs.edit().putString("GOOGLE_GEMINI_IMAGE_API_KEY", apiKey.trim()).apply()
    }

    fun getGoogleImagenApiKey(): String? {
        return encryptedPrefs.getString("GOOGLE_GEMINI_IMAGE_API_KEY", null)
    }

    fun removeGoogleImagenApiKey() {
        encryptedPrefs.edit().remove("GOOGLE_GEMINI_IMAGE_API_KEY").apply()
    }

    fun hasGoogleImagenApiKey(): Boolean {
        return !getGoogleImagenApiKey().isNullOrBlank()
    }

    // ── Task Completion Notifications ────────────────────────────────────────

    fun setTaskNotificationsEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean("TASK_NOTIFICATIONS_ENABLED", enabled).apply()
    }

    fun isTaskNotificationsEnabled(): Boolean {
        return encryptedPrefs.getBoolean("TASK_NOTIFICATIONS_ENABLED", true)
    }

    // ── Privacy-preserving global usage analytics ───────────────────────────

    fun setGlobalUsageAnalyticsEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean("GLOBAL_USAGE_ANALYTICS_ENABLED", enabled).apply()
    }

    fun isGlobalUsageAnalyticsEnabled(): Boolean {
        return encryptedPrefs.getBoolean("GLOBAL_USAGE_ANALYTICS_ENABLED", false)
    }

    fun setErrorReportingEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean("ERROR_REPORTING_ENABLED", enabled).apply()
    }

    fun isErrorReportingEnabled(): Boolean {
        return encryptedPrefs.getBoolean("ERROR_REPORTING_ENABLED", true)
    }

    /** Minimum duration in seconds for a task to be considered "long" (default 15s) */
    fun setLongTaskThresholdSeconds(seconds: Int) {
        encryptedPrefs.edit().putInt("LONG_TASK_THRESHOLD_SECONDS", seconds).apply()
    }

    fun getLongTaskThresholdSeconds(): Int {
        return encryptedPrefs.getInt("LONG_TASK_THRESHOLD_SECONDS", 15)
    }

    // ── Draft Auto-Save ──────────────────────────────────────────────────────

    fun saveDraft(workspaceId: Long, text: String) {
        encryptedPrefs.edit().putString("DRAFT_$workspaceId", text).apply()
    }

    fun getDraft(workspaceId: Long): String {
        return encryptedPrefs.getString("DRAFT_$workspaceId", "") ?: ""
    }

    fun clearDraft(workspaceId: Long) {
        encryptedPrefs.edit().remove("DRAFT_$workspaceId").apply()
    }

    /** Conversation-scoped drafts. The explicit `NEW` scope is used before a chat is created. */
    fun saveDraft(workspaceId: Long, conversationId: Long?, text: String) {
        encryptedPrefs.edit().putString(draftKey(workspaceId, conversationId), text).apply()
    }

    fun getDraft(workspaceId: Long, conversationId: Long?): String =
        encryptedPrefs.getString(draftKey(workspaceId, conversationId), "") ?: ""

    fun clearDraft(workspaceId: Long, conversationId: Long?) {
        encryptedPrefs.edit().remove(draftKey(workspaceId, conversationId)).apply()
    }

    /** Removes the old workspace-wide slot, which could leak a sent message into another chat. */
    fun clearLegacyDraft(workspaceId: Long) {
        clearDraft(workspaceId)
    }

    private fun draftKey(workspaceId: Long, conversationId: Long?): String =
        "DRAFT_V2_${workspaceId}_${conversationId?.takeIf { it > 0L } ?: "NEW"}"

}
