package com.aiagents.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aiagents.app.domain.model.MoonshotEndpointType
import com.aiagents.app.domain.model.ProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
            // Fallback a SharedPreferences normales si EncryptedSharedPreferences falla
            android.util.Log.w("SecurePreferences", "Error inicializando EncryptedSharedPreferences, usando fallback", e)
            context.getSharedPreferences("fallback_prefs", Context.MODE_PRIVATE)
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

    fun isModelSelected(providerType: ProviderType, modelId: String): Boolean {
        return getSelectedModels().contains("${providerType.name}|$modelId")
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

    // Google Drive OAuth
    fun saveGoogleDriveClientId(id: String) { encryptedPrefs.edit().putString("GDRIVE_CLIENT_ID", id).apply() }
    fun getGoogleDriveClientId(): String? = encryptedPrefs.getString("GDRIVE_CLIENT_ID", null)
    fun saveGoogleDriveClientSecret(secret: String) { encryptedPrefs.edit().putString("GDRIVE_CLIENT_SECRET", secret).apply() }
    fun getGoogleDriveClientSecret(): String? = encryptedPrefs.getString("GDRIVE_CLIENT_SECRET", null)
    fun saveGoogleDriveAccessToken(token: String) { encryptedPrefs.edit().putString("GDRIVE_ACCESS_TOKEN", token).apply() }
    fun getGoogleDriveAccessToken(): String? = encryptedPrefs.getString("GDRIVE_ACCESS_TOKEN", null)
    fun saveGoogleDriveRefreshToken(token: String) { encryptedPrefs.edit().putString("GDRIVE_REFRESH_TOKEN", token).apply() }
    fun getGoogleDriveRefreshToken(): String? = encryptedPrefs.getString("GDRIVE_REFRESH_TOKEN", null)
    fun hasGoogleDriveConfig(): Boolean = !getGoogleDriveAccessToken().isNullOrBlank()
    fun clearGoogleDrive() {
        encryptedPrefs.edit()
            .remove("GDRIVE_CLIENT_ID").remove("GDRIVE_CLIENT_SECRET")
            .remove("GDRIVE_ACCESS_TOKEN").remove("GDRIVE_REFRESH_TOKEN")
            .apply()
    }

    // Google Workspace (unified)
    fun saveGoogleWorkspaceAccessToken(token: String) = encryptedPrefs.edit().putString("gws_access_token", token).apply()
    fun getGoogleWorkspaceAccessToken(): String? = encryptedPrefs.getString("gws_access_token", null)
    fun saveGoogleWorkspaceRefreshToken(token: String) = encryptedPrefs.edit().putString("gws_refresh_token", token).apply()
    fun getGoogleWorkspaceRefreshToken(): String? = encryptedPrefs.getString("gws_refresh_token", null)
    fun saveGoogleWorkspaceScopes(scopes: String) = encryptedPrefs.edit().putString("gws_scopes", scopes).apply()
    fun getGoogleWorkspaceScopes(): String? = encryptedPrefs.getString("gws_scopes", null)
    fun saveGoogleWorkspaceTokenExpiry(expiry: Long) = encryptedPrefs.edit().putLong("gws_token_expiry", expiry).apply()
    fun getGoogleWorkspaceTokenExpiry(): Long = encryptedPrefs.getLong("gws_token_expiry", 0L)
    fun hasGoogleWorkspaceConfig(): Boolean = getGoogleWorkspaceAccessToken() != null
    fun clearGoogleWorkspace() {
        encryptedPrefs.edit()
            .remove("gws_access_token")
            .remove("gws_refresh_token")
            .remove("gws_scopes")
            .remove("gws_token_expiry")
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

    // Onboarding
    fun isOnboardingCompleted(): Boolean = encryptedPrefs.getBoolean("ONBOARDING_COMPLETED", false)

    fun setOnboardingCompleted(completed: Boolean) {
        encryptedPrefs.edit().putBoolean("ONBOARDING_COMPLETED", completed).apply()
    }

    fun getAppLanguage(): String = encryptedPrefs.getString("APP_LANGUAGE", "") ?: ""

    fun setAppLanguage(language: String) {
        encryptedPrefs.edit().putString("APP_LANGUAGE", language).apply()
    }

    // OpenWeather API
    fun saveOpenWeatherApiKey(apiKey: String) {
        encryptedPrefs.edit().putString("OPENWEATHER_API_KEY", apiKey.trim()).apply()
    }

    fun getOpenWeatherApiKey(): String? {
        return encryptedPrefs.getString("OPENWEATHER_API_KEY", null)
    }

    fun removeOpenWeatherApiKey() {
        encryptedPrefs.edit().remove("OPENWEATHER_API_KEY").apply()
    }

    fun hasOpenWeatherApiKey(): Boolean {
        return !getOpenWeatherApiKey().isNullOrBlank()
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
        return encryptedPrefs.getBoolean("WEATHER_ENABLED", false)
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
}
