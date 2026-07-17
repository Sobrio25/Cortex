package com.aiagents.app.data.auth

import android.content.Context
import com.google.firebase.FirebaseApp

/**
 * Initializes Firebase before Hilt creates objects that depend on FirebaseAuth.
 *
 * Android creates the application class independently in every declared process. The voice
 * interaction process can therefore request Hilt dependencies before FirebaseInitProvider has
 * made the default app visible there. Keeping this bootstrap idempotent makes both the main and
 * `:cortex_voice` processes safe without storing any credentials in code.
 */
object FirebaseBootstrap {
    private val lock = Any()

    fun ensureInitialized(context: Context): FirebaseApp {
        val applicationContext = context.applicationContext ?: context
        existingDefaultApp(applicationContext)?.let { return it }

        return synchronized(lock) {
            existingDefaultApp(applicationContext)
                ?: FirebaseApp.initializeApp(applicationContext)
                ?: error(
                    "Firebase configuration is unavailable. " +
                        "Check that google-services resources are packaged."
                )
        }
    }

    private fun existingDefaultApp(context: Context): FirebaseApp? =
        FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
}
