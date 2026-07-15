package com.aiagents.app.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.aiagents.app.data.local.SecurePreferences
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Anthropic OAuth using Authorization Code flow with PKCE.
 *
 * Uses the same client_id and endpoints as Claude CLI / OpenClaw.
 *
 * Flow:
 *  1. [openAuthorizationPage] – opens the browser to Anthropic's OAuth page
 *  2. User logs in and gets redirected to a page that shows the authorization code
 *  3. [exchangeCodeAndCreateKey] – exchanges the code for an access token,
 *     then uses it to auto-create an API key via Anthropic's API
 */
@Singleton
class AnthropicOAuthManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val securePreferences: SecurePreferences
) {
    companion object {
        private const val TAG = "AnthropicOAuth"

        // Same client_id used by Claude CLI and other open-source tools
        private const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"

        private const val AUTH_URL = "https://console.anthropic.com/oauth/authorize"
        private const val TOKEN_URL = "https://console.anthropic.com/v1/oauth/token"
        private const val CREATE_KEY_URL = "https://api.anthropic.com/api/oauth/claude_cli/create_api_key"
        private const val REDIRECT_URI = "https://console.anthropic.com/oauth/code/callback"
        private const val SCOPES = "org:create_api_key user:profile user:inference"
    }

    // PKCE verifier kept between the two steps
    private var codeVerifier: String? = null

    /**
     * Step 1 – Opens the browser to Anthropic's OAuth authorization page.
     * The user logs in and gets a code displayed on the callback page.
     */
    fun openAuthorizationPage(context: Context) {
        val verifier = generateCodeVerifier()
        codeVerifier = verifier
        val challenge = generateCodeChallenge(verifier)

        val authUrl = buildString {
            append(AUTH_URL)
            append("?client_id=${enc(CLIENT_ID)}")
            append("&response_type=code")
            append("&redirect_uri=${enc(REDIRECT_URI)}")
            append("&scope=${enc(SCOPES)}")
            append("&code_challenge=${enc(challenge)}")
            append("&code_challenge_method=S256")
            append("&state=${enc(verifier)}")
        }

        Log.d(TAG, "Opening OAuth page")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Step 2 – Exchange the authorization code for tokens, then create an API key.
     *
     * @param codeInput The code the user pasted (may include "#state" suffix from the callback URL)
     */
    suspend fun exchangeCodeAndCreateKey(codeInput: String): Result<String> {
        val verifier = codeVerifier
            ?: return Result.failure(Exception("Inicia el flujo OAuth primero"))

        // The callback URL format is: code#state — strip the state part
        val code = codeInput.trim().substringBefore("#")

        return withContext(Dispatchers.IO) {
            try {
                // ── Step 2a: Exchange code for access token ──────────────
                val tokenBody = FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", REDIRECT_URI)
                    .add("client_id", CLIENT_ID)
                    .add("code_verifier", verifier)
                    .build()

                val tokenRequest = Request.Builder()
                    .url(TOKEN_URL)
                    .post(tokenBody)
                    .build()

                val tokenResponse = okHttpClient.newCall(tokenRequest).execute()
                val tokenResponseBody = tokenResponse.body?.string() ?: ""

                if (!tokenResponse.isSuccessful) {
                    Log.e(TAG, "Token exchange failed: $tokenResponseBody")
                    return@withContext Result.failure(
                        Exception("Error al intercambiar código (${tokenResponse.code})")
                    )
                }

                val tokenJson = JsonParser.parseString(tokenResponseBody).asJsonObject
                val accessToken = tokenJson.get("access_token")?.asString
                val refreshToken = tokenJson.get("refresh_token")?.asString
                val expiresIn = tokenJson.get("expires_in")?.asLong ?: 3600

                if (accessToken.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("No se recibió access token"))
                }

                Log.d(TAG, "Access token obtained, creating API key...")

                // ── Step 2b: Create a permanent API key using the token ──
                val apiKey = createApiKey(accessToken)
                if (apiKey != null) {
                    // Save as regular Anthropic API key — works forever
                    securePreferences.saveApiKey(
                        com.aiagents.app.domain.model.ProviderType.ANTHROPIC,
                        apiKey
                    )
                    Log.d(TAG, "API key created and saved via OAuth")
                    codeVerifier = null
                    return@withContext Result.success(apiKey)
                }

                // Fallback: save the OAuth tokens directly if key creation fails
                Log.w(TAG, "API key creation failed, saving OAuth tokens as fallback")
                securePreferences.saveAnthropicAccessToken(accessToken)
                if (!refreshToken.isNullOrBlank()) {
                    securePreferences.saveAnthropicRefreshToken(refreshToken)
                }
                securePreferences.saveAnthropicTokenExpiry(
                    System.currentTimeMillis() / 1000 + expiresIn
                )

                codeVerifier = null
                Result.success(accessToken)
            } catch (e: Exception) {
                Log.e(TAG, "OAuth flow error", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Creates a permanent API key using the OAuth access token.
     * Uses the same endpoint as Claude CLI.
     */
    private fun createApiKey(accessToken: String): String? {
        return try {
            val body = """{"name":"AI Agents"}"""
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(CREATE_KEY_URL)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("anthropic-beta", "oauth-2025-04-20")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Create API key failed: $responseBody")
                return null
            }

            val json = JsonParser.parseString(responseBody).asJsonObject
            json.get("api_key")?.asString ?: json.get("key")?.asString
        } catch (e: Exception) {
            Log.e(TAG, "Error creating API key", e)
            null
        }
    }

    /** Returns a valid access token, refreshing if needed. */
    fun getValidAccessToken(): String? {
        val accessToken = securePreferences.getAnthropicAccessToken()
        if (accessToken.isNullOrBlank()) return null

        val currentTime = System.currentTimeMillis() / 1000
        val expiryTime = securePreferences.getAnthropicTokenExpiry()
        if (currentTime >= expiryTime - 300) {
            return refreshAccessToken()
        }
        return accessToken
    }

    private fun refreshAccessToken(): String? {
        val refreshToken = securePreferences.getAnthropicRefreshToken()
        if (refreshToken.isNullOrBlank()) return null

        return try {
            val formBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", CLIENT_ID)
                .build()

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(formBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Token refresh failed: $body")
                return null
            }

            val json = JsonParser.parseString(body).asJsonObject
            val newAccessToken = json.get("access_token")?.asString
            val newRefreshToken = json.get("refresh_token")?.asString
            val expiresIn = json.get("expires_in")?.asLong ?: 3600

            if (newAccessToken.isNullOrBlank()) return null

            securePreferences.saveAnthropicAccessToken(newAccessToken)
            if (!newRefreshToken.isNullOrBlank()) {
                securePreferences.saveAnthropicRefreshToken(newRefreshToken)
            }
            securePreferences.saveAnthropicTokenExpiry(
                System.currentTimeMillis() / 1000 + expiresIn
            )

            Log.d(TAG, "Access token refreshed")
            newAccessToken
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing token", e)
            null
        }
    }

    fun isConnected(): Boolean {
        // Connected via OAuth token or via API key created through OAuth
        return securePreferences.hasAnthropicOAuthConfig() ||
            !securePreferences.getApiKey(com.aiagents.app.domain.model.ProviderType.ANTHROPIC).isNullOrBlank()
    }

    fun clearAuth() {
        codeVerifier = null
        securePreferences.clearAnthropic()
    }

    // ── PKCE helpers ─────────────────────────────────────────────────────

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun enc(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")
}
