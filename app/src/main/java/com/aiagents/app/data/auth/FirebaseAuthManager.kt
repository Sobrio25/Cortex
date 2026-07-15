package com.aiagents.app.data.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.aiagents.app.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class FirebaseAuthManager @Inject constructor(
    private val auth: FirebaseAuth
) {
    suspend fun idToken(forceRefresh: Boolean = false): String {
        val user = auth.currentUser ?: auth.signInAnonymously().awaitResult().user
            ?: error("No se pudo crear la cuenta anónima")
        return user.getIdToken(forceRefresh).awaitResult().token
            ?: error("Firebase no devolvió un token de sesión")
    }

    suspend fun signInWithGoogle(activity: Activity) {
        if (isGoogleSignedIn) return
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(activity.getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val credential = CredentialManager.create(activity)
            .getCredential(context = activity, request = request)
            .credential
        if (
            credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            error("Google no devolvió una credencial válida")
        }
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
        val current = auth.currentUser
        if (current?.isAnonymous == true) {
            try {
                current.linkWithCredential(firebaseCredential).awaitResult()
            } catch (_: FirebaseAuthUserCollisionException) {
                auth.signInWithCredential(firebaseCredential).awaitResult()
            }
        } else {
            auth.signInWithCredential(firebaseCredential).awaitResult()
        }
        auth.currentUser?.getIdToken(true)?.awaitResult()
    }

    val isGoogleSignedIn: Boolean
        get() = auth.currentUser?.providerData?.any {
            it.providerId == GoogleAuthProvider.PROVIDER_ID
        } == true

    val uid: String? get() = auth.currentUser?.uid
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
