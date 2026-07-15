package com.aiagents.app.data.auth

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
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

    suspend fun linkGoogleIdToken(idToken: String) {
        val user = auth.currentUser ?: auth.signInAnonymously().awaitResult().user
            ?: error("No se pudo crear la cuenta anónima")
        user.linkWithCredential(GoogleAuthProvider.getCredential(idToken, null)).awaitResult()
    }

    val uid: String? get() = auth.currentUser?.uid
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
