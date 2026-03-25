package com.aiagents.app.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.aiagents.app.data.local.SecurePreferences
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Manages OAuth 2.0 authentication for all Google Workspace APIs.
 * Uses the same localhost callback pattern as GoogleDriveOAuthManager.
 */
@Singleton
class GoogleWorkspaceOAuthManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val securePreferences: SecurePreferences
) {
    companion object {
        private const val TAG = "GoogleWorkspaceOAuth"
        private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val REDIRECT_PORT = 8082
        private const val REDIRECT_URI = "http://localhost:$REDIRECT_PORT/callback"

        val SCOPES = mapOf(
            "drive" to listOf(
                "https://www.googleapis.com/auth/drive",
                "https://www.googleapis.com/auth/drive.file",
                "https://www.googleapis.com/auth/drive.readonly"
            ),
            "gmail" to listOf(
                "https://www.googleapis.com/auth/gmail.modify",
                "https://www.googleapis.com/auth/gmail.compose",
                "https://www.googleapis.com/auth/gmail.readonly",
                "https://www.googleapis.com/auth/gmail.send",
                "https://www.googleapis.com/auth/gmail.labels"
            ),
            "calendar" to listOf(
                "https://www.googleapis.com/auth/calendar",
                "https://www.googleapis.com/auth/calendar.events",
                "https://www.googleapis.com/auth/calendar.readonly"
            ),
            "sheets" to listOf(
                "https://www.googleapis.com/auth/spreadsheets",
                "https://www.googleapis.com/auth/spreadsheets.readonly"
            ),
            "docs" to listOf(
                "https://www.googleapis.com/auth/documents",
                "https://www.googleapis.com/auth/documents.readonly"
            ),
            "slides" to listOf(
                "https://www.googleapis.com/auth/presentations",
                "https://www.googleapis.com/auth/presentations.readonly"
            )
        )

        fun getRecommendedScopes(): List<String> {
            return SCOPES.values.flatten().distinct()
        }

        fun getScopesForServices(services: List<String>): List<String> {
            return services.flatMap { SCOPES[it] ?: emptyList() }.distinct()
        }
    }

    /**
     * Perform the full OAuth flow: open browser, wait for callback, exchange code.
     * Uses the same pattern as GoogleDriveOAuthManager which works on Android.
     */
    suspend fun performFullAuthFlow(context: Context, services: List<String>? = null): Result<String> {
        val clientId = securePreferences.getGoogleDriveClientId()
        if (clientId.isNullOrBlank()) return Result.failure(Exception("Client ID no configurado"))

        val scopes = if (services != null) {
            getScopesForServices(services)
        } else {
            getRecommendedScopes()
        }
        val scopeString = scopes.joinToString(" ")

        return try {
            val code = suspendCancellableCoroutine<String?> { cont ->
                Thread {
                    try {
                        val server = ServerSocket(REDIRECT_PORT)
                        cont.invokeOnCancellation { server.close() }

                        val authUrl = buildString {
                            append(AUTH_URL)
                            append("?client_id=${java.net.URLEncoder.encode(clientId, "UTF-8")}")
                            append("&redirect_uri=${java.net.URLEncoder.encode(REDIRECT_URI, "UTF-8")}")
                            append("&response_type=code")
                            append("&scope=${java.net.URLEncoder.encode(scopeString, "UTF-8")}")
                            append("&access_type=offline")
                            append("&prompt=consent")
                        }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)

                        server.soTimeout = 120000
                        val socket = server.accept()
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        val requestLine = reader.readLine() ?: ""
                        Log.d(TAG, "Received: $requestLine")

                        val uri = Uri.parse("http://localhost${requestLine.split(" ").getOrNull(1) ?: ""}")
                        val authCode = uri.getQueryParameter("code")
                        val error = uri.getQueryParameter("error")

                        val responseHtml = if (authCode != null) {
                            "<html><body><h2>Google Workspace conectado</h2><p>Puedes cerrar esta ventana y volver a la app.</p></body></html>"
                        } else {
                            "<html><body><h2>Error: ${error ?: "desconocido"}</h2></body></html>"
                        }
                        val httpResponse = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n$responseHtml"
                        socket.getOutputStream().write(httpResponse.toByteArray())
                        socket.close()
                        server.close()

                        cont.resume(authCode)
                    } catch (e: Exception) {
                        Log.e(TAG, "OAuth server error", e)
                        if (cont.isActive) cont.resume(null)
                    }
                }.start()
            }

            if (code == null) return Result.failure(Exception("No se recibio codigo de autorizacion"))

            // Small delay to let network stabilize after browser redirect
            kotlinx.coroutines.delay(1500)

            // Retry token exchange up to 3 times with increasing delay
            var lastError: Exception? = null
            for (attempt in 1..3) {
                val result = exchangeCodeForTokens(code, clientId, scopeString)
                if (result.isSuccess) return result
                lastError = result.exceptionOrNull() as? Exception
                Log.w(TAG, "Token exchange attempt $attempt failed: ${lastError?.message}")
                kotlinx.coroutines.delay(attempt * 2000L)
            }
            Result.failure(lastError ?: Exception("Token exchange failed after retries"))
        } catch (e: Exception) {
            Log.e(TAG, "OAuth flow error", e)
            Result.failure(e)
        }
    }

    private suspend fun exchangeCodeForTokens(code: String, clientId: String, scopes: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val clientSecret = securePreferences.getGoogleDriveClientSecret() ?: ""

            // Use HttpURLConnection for more reliable DNS resolution on Android
            val url = URL(TOKEN_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.connectTimeout = 30000
            conn.readTimeout = 30000

            val postData = buildString {
                append("code=${java.net.URLEncoder.encode(code, "UTF-8")}")
                append("&client_id=${java.net.URLEncoder.encode(clientId, "UTF-8")}")
                append("&redirect_uri=${java.net.URLEncoder.encode(REDIRECT_URI, "UTF-8")}")
                append("&grant_type=authorization_code")
                if (clientSecret.isNotBlank()) {
                    append("&client_secret=${java.net.URLEncoder.encode(clientSecret, "UTF-8")}")
                }
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(postData)
                writer.flush()
            }

            val responseCode = conn.responseCode
            val body = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            }
            conn.disconnect()

            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("Error $responseCode: $body"))
            }

            val json = JsonParser.parseString(body).asJsonObject
            val accessToken = json.get("access_token")?.asString
                ?: return@withContext Result.failure(Exception("No access_token in response"))
            val refreshToken = json.get("refresh_token")?.asString
            val expiresIn = json.get("expires_in")?.asInt
            val grantedScope = json.get("scope")?.asString

            // Save Google Workspace tokens
            securePreferences.saveGoogleWorkspaceAccessToken(accessToken)
            if (refreshToken != null) securePreferences.saveGoogleWorkspaceRefreshToken(refreshToken)
            grantedScope?.let { securePreferences.saveGoogleWorkspaceScopes(it) }
            expiresIn?.let {
                securePreferences.saveGoogleWorkspaceTokenExpiry(System.currentTimeMillis() + (it * 1000L))
            }

            // Backward compatibility with Google Drive
            securePreferences.saveGoogleDriveAccessToken(accessToken)
            if (refreshToken != null) securePreferences.saveGoogleDriveRefreshToken(refreshToken)

            Log.i(TAG, "Google Workspace tokens saved. Scopes: $grantedScope")
            Result.success(accessToken)
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange failed", e)
            Result.failure(e)
        }
    }

    suspend fun refreshAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val clientId = securePreferences.getGoogleDriveClientId()
                ?: return@withContext Result.failure(Exception("Missing client ID"))
            val clientSecret = securePreferences.getGoogleDriveClientSecret() ?: ""
            val refreshToken = securePreferences.getGoogleWorkspaceRefreshToken()
                ?: securePreferences.getGoogleDriveRefreshToken()
                ?: return@withContext Result.failure(Exception("No refresh token. Please re-authenticate."))

            val url = URL(TOKEN_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.connectTimeout = 30000
            conn.readTimeout = 30000

            val postData = buildString {
                append("client_id=${java.net.URLEncoder.encode(clientId, "UTF-8")}")
                append("&refresh_token=${java.net.URLEncoder.encode(refreshToken, "UTF-8")}")
                append("&grant_type=refresh_token")
                if (clientSecret.isNotBlank()) {
                    append("&client_secret=${java.net.URLEncoder.encode(clientSecret, "UTF-8")}")
                }
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(postData)
                writer.flush()
            }

            val responseCode = conn.responseCode
            val body = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            }
            conn.disconnect()

            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("Refresh error $responseCode: $body"))
            }

            val json = JsonParser.parseString(body).asJsonObject
            val accessToken = json.get("access_token")?.asString
                ?: return@withContext Result.failure(Exception("No access_token in refresh response"))

            securePreferences.saveGoogleWorkspaceAccessToken(accessToken)
            securePreferences.saveGoogleDriveAccessToken(accessToken)
            json.get("expires_in")?.asInt?.let {
                securePreferences.saveGoogleWorkspaceTokenExpiry(System.currentTimeMillis() + (it * 1000L))
            }

            Log.i(TAG, "Google Workspace token refreshed")
            Result.success(accessToken)
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed", e)
            Result.failure(e)
        }
    }

    suspend fun getValidAccessToken(): Result<String> {
        val token = securePreferences.getGoogleWorkspaceAccessToken()
            ?: securePreferences.getGoogleDriveAccessToken()
            ?: return Result.failure(Exception("Not authenticated with Google Workspace."))

        val expiry = securePreferences.getGoogleWorkspaceTokenExpiry()
        if (expiry > 0 && System.currentTimeMillis() > expiry - 60_000) {
            return refreshAccessToken()
        }

        return Result.success(token)
    }

    fun isAuthenticated(): Boolean {
        return securePreferences.hasGoogleWorkspaceConfig()
    }

    fun getAuthorizedScopes(): List<String> {
        return securePreferences.getGoogleWorkspaceScopes()
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    fun logout() {
        securePreferences.clearGoogleWorkspace()
    }
}
