package com.aiagents.app.data.auth

import android.accounts.Account
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import com.aiagents.app.data.local.SecurePreferences
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface GoogleAuthorizationOutcome {
    data class Authorized(
        val accessToken: String,
        val accountEmail: String?,
        val grantedScopes: List<String>
    ) : GoogleAuthorizationOutcome

    data class RequiresConsent(
        val pendingIntent: PendingIntent
    ) : GoogleAuthorizationOutcome
}

/**
 * Authorizes Cortex to call Google Workspace APIs through Google Identity Services.
 *
 * Android applications cannot protect a client secret. Token exchange, account selection and
 * consent are therefore delegated to Google Play services instead of a localhost callback.
 */
@Singleton
class GoogleWorkspaceOAuthManager @Inject constructor(
    private val securePreferences: SecurePreferences
) {
    init {
        securePreferences.clearLegacyGoogleOAuthCredentials()
    }

    companion object {
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val DEFAULT_TOKEN_VALIDITY_MILLIS = 55 * 60 * 1000L

        /** Write scopes match the actions exposed by the existing Cortex tools. */
        val SCOPES: Map<String, List<String>> = linkedMapOf(
            "drive" to listOf("https://www.googleapis.com/auth/drive"),
            "gmail" to listOf(
                "https://www.googleapis.com/auth/gmail.modify",
                "https://www.googleapis.com/auth/gmail.compose",
                "https://www.googleapis.com/auth/gmail.send"
            ),
            "calendar" to listOf("https://www.googleapis.com/auth/calendar.events"),
            "sheets" to listOf("https://www.googleapis.com/auth/spreadsheets"),
            "docs" to listOf("https://www.googleapis.com/auth/documents"),
            "slides" to listOf("https://www.googleapis.com/auth/presentations")
        )

        fun getRecommendedScopes(): List<String> = SCOPES.values.flatten().distinct()

        fun getScopesForServices(services: Collection<String>): List<String> =
            services.flatMap { SCOPES[it].orEmpty() }.distinct()
    }

    suspend fun beginAuthorization(
        activity: Activity,
        services: Collection<String>
    ): Result<GoogleAuthorizationOutcome> = runCatching {
        val requestedScopes = getScopesForServices(services)
        require(requestedScopes.isNotEmpty()) {
            "Selecciona al menos un servicio de Google Workspace."
        }

        val request = AuthorizationRequest.builder()
            .setRequestedScopes(requestedScopes.map(::Scope))
            .build()
        val result = Identity.getAuthorizationClient(activity)
            .authorize(request)
            .awaitResult()

        if (result.hasResolution()) {
            GoogleAuthorizationOutcome.RequiresConsent(
                requireNotNull(result.pendingIntent) {
                    "Google no devolvió la pantalla de consentimiento."
                }
            )
        } else {
            persist(result)
        }
    }

    fun completeAuthorization(
        activity: Activity,
        data: Intent
    ): Result<GoogleAuthorizationOutcome.Authorized> = runCatching {
        val result = Identity.getAuthorizationClient(activity)
            .getAuthorizationResultFromIntent(data)
        persist(result)
    }

    /** Refreshes an existing grant without presenting UI; consent changes stay user initiated. */
    suspend fun refreshIfPossible(activity: Activity): Result<Boolean> = runCatching {
        val scopes = getAuthorizedScopes()
        if (scopes.isEmpty()) return@runCatching false

        val requestBuilder = AuthorizationRequest.builder()
            .setRequestedScopes(scopes.map(::Scope))
        getAccountEmail()?.takeIf(String::isNotBlank)?.let { email ->
            requestBuilder.setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
        }

        val result = Identity.getAuthorizationClient(activity)
            .authorize(requestBuilder.build())
            .awaitResult()
        if (result.hasResolution()) {
            false
        } else {
            persist(result)
            true
        }
    }

    suspend fun disconnect(activity: Activity): Result<Unit> {
        val client = Identity.getAuthorizationClient(activity)
        val token = securePreferences.getGoogleWorkspaceAccessToken()
        val email = securePreferences.getGoogleWorkspaceAccountEmail()
        val scopes = getAuthorizedScopes()
        var failure: Throwable? = null

        if (!email.isNullOrBlank() && scopes.isNotEmpty()) {
            try {
                client.revokeAccess(
                    RevokeAccessRequest.builder()
                        .setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
                        .setScopes(scopes.map(::Scope))
                        .build()
                ).awaitResult()
            } catch (error: Throwable) {
                failure = error
            }
        }

        if (!token.isNullOrBlank()) {
            try {
                client.clearToken(
                    ClearTokenRequest.builder().setToken(token).build()
                ).awaitResult()
            } catch (error: Throwable) {
                if (failure == null) failure = error
            }
        }

        securePreferences.clearGoogleWorkspace()
        return failure?.let { Result.failure<Unit>(it) } ?: Result.success(Unit)
    }

    suspend fun getValidAccessToken(): Result<String> {
        val token = securePreferences.getGoogleWorkspaceAccessToken()
        if (token.isNullOrBlank()) {
            return Result.failure(
                IllegalStateException("Google Workspace no está conectado. Autorízalo en Ajustes.")
            )
        }

        val expiry = securePreferences.getGoogleWorkspaceTokenExpiry()
        if (expiry > 0L && System.currentTimeMillis() >= expiry) {
            return Result.failure(
                IllegalStateException(
                    "La autorización de Google venció. Abre Ajustes > Google Workspace para renovarla."
                )
            )
        }
        return Result.success(token)
    }

    fun isAuthenticated(): Boolean = securePreferences.hasGoogleWorkspaceConfig()

    fun isAuthorizationExpired(): Boolean {
        val expiry = securePreferences.getGoogleWorkspaceTokenExpiry()
        return expiry > 0L && System.currentTimeMillis() >= expiry
    }

    fun getAuthorizedScopes(): List<String> = securePreferences.getGoogleWorkspaceScopes()
        ?.split(' ')
        ?.filter(String::isNotBlank)
        .orEmpty()

    fun getAccountEmail(): String? = securePreferences.getGoogleWorkspaceAccountEmail()

    private fun persist(result: AuthorizationResult): GoogleAuthorizationOutcome.Authorized {
        val accessToken = result.accessToken
        require(!accessToken.isNullOrBlank()) {
            "Google no devolvió un token de acceso."
        }
        val grantedScopes = result.grantedScopes.orEmpty().distinct()
        val accountEmail = result.toGoogleSignInAccount()?.email

        securePreferences.saveGoogleWorkspaceAccessToken(accessToken)
        securePreferences.saveGoogleWorkspaceScopes(grantedScopes.joinToString(" "))
        securePreferences.saveGoogleWorkspaceTokenExpiry(
            System.currentTimeMillis() + DEFAULT_TOKEN_VALIDITY_MILLIS
        )
        accountEmail?.let(securePreferences::saveGoogleWorkspaceAccountEmail)

        return GoogleAuthorizationOutcome.Authorized(
            accessToken = accessToken,
            accountEmail = accountEmail,
            grantedScopes = grantedScopes
        )
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value ->
        if (continuation.isActive) continuation.resume(value)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
}
