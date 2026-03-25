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
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class GoogleDriveOAuthManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val securePreferences: SecurePreferences
) {
    companion object {
        private const val TAG = "GoogleDriveOAuth"
        private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val SCOPES = "https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/documents https://www.googleapis.com/auth/generative-language"
        private const val REDIRECT_PORT = 8081
        private const val REDIRECT_URI = "http://localhost:$REDIRECT_PORT/callback"
    }

    suspend fun performFullAuthFlow(context: Context): Result<String> {
        val clientId = securePreferences.getGoogleDriveClientId()
        if (clientId.isNullOrBlank()) return Result.failure(Exception("Client ID no configurado"))

        return try {
            // Start local server to receive callback
            val code = suspendCancellableCoroutine<String?> { cont ->
                Thread {
                    try {
                        val server = ServerSocket(REDIRECT_PORT)
                        cont.invokeOnCancellation { server.close() }

                        // Open browser for authorization
                        val authUrl = buildString {
                            append(AUTH_URL)
                            append("?client_id=${java.net.URLEncoder.encode(clientId, "UTF-8")}")
                            append("&redirect_uri=${java.net.URLEncoder.encode(REDIRECT_URI, "UTF-8")}")
                            append("&response_type=code")
                            append("&scope=${java.net.URLEncoder.encode(SCOPES, "UTF-8")}")
                            append("&access_type=offline")
                            append("&prompt=consent")
                        }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)

                        // Wait for callback
                        server.soTimeout = 120000
                        val socket = server.accept()
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        val requestLine = reader.readLine() ?: ""
                        Log.d(TAG, "Received: $requestLine")

                        val uri = Uri.parse("http://localhost${ requestLine.split(" ").getOrNull(1) ?: "" }")
                        val authCode = uri.getQueryParameter("code")
                        val error = uri.getQueryParameter("error")

                        // Send response to browser
                        val responseHtml = if (authCode != null) {
                            "<html><body><h2>Google Drive conectado</h2><p>Puedes cerrar esta ventana.</p></body></html>"
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

            // Exchange code for tokens
            exchangeCodeForTokens(code, clientId)
        } catch (e: Exception) {
            Log.e(TAG, "OAuth flow error", e)
            Result.failure(e)
        }
    }

    private suspend fun exchangeCodeForTokens(code: String, clientId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val clientSecret = securePreferences.getGoogleDriveClientSecret() ?: ""
            val formBody = FormBody.Builder()
                .add("code", code)
                .add("client_id", clientId)
                .add("redirect_uri", REDIRECT_URI)
                .add("grant_type", "authorization_code")
            if (clientSecret.isNotBlank()) formBody.add("client_secret", clientSecret)

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(formBody.build())
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.code !in 200..299) {
                return@withContext Result.failure(Exception("Error ${response.code}: $body"))
            }

            val json = JsonParser.parseString(body).asJsonObject
            val accessToken = json.get("access_token")?.asString ?: return@withContext Result.failure(Exception("No access_token"))
            val refreshToken = json.get("refresh_token")?.asString

            securePreferences.saveGoogleDriveAccessToken(accessToken)
            if (refreshToken != null) securePreferences.saveGoogleDriveRefreshToken(refreshToken)

            Result.success(accessToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getValidAccessToken(): String? {
        val token = securePreferences.getGoogleDriveAccessToken()
        if (!token.isNullOrBlank()) return token

        // Try refresh
        val refreshToken = securePreferences.getGoogleDriveRefreshToken() ?: return null
        val clientId = securePreferences.getGoogleDriveClientId() ?: return null
        return refreshAccessToken(refreshToken, clientId)
    }

    private suspend fun refreshAccessToken(refreshToken: String, clientId: String): String? = withContext(Dispatchers.IO) {
        try {
            val clientSecret = securePreferences.getGoogleDriveClientSecret() ?: ""
            val formBody = FormBody.Builder()
                .add("refresh_token", refreshToken)
                .add("client_id", clientId)
                .add("grant_type", "refresh_token")
            if (clientSecret.isNotBlank()) formBody.add("client_secret", clientSecret)

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(formBody.build())
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.code !in 200..299) return@withContext null

            val json = JsonParser.parseString(body).asJsonObject
            val newToken = json.get("access_token")?.asString ?: return@withContext null
            securePreferences.saveGoogleDriveAccessToken(newToken)
            newToken
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed", e)
            null
        }
    }
}
