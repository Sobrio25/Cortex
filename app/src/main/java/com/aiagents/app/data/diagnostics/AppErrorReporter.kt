package com.aiagents.app.data.diagnostics

import android.content.Context
import android.util.Log
import com.aiagents.app.data.local.SecurePreferences
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

data class ErrorReportContext(
    val component: String,
    val operation: String,
    val provider: String? = null,
    val model: String? = null,
    val tool: String? = null
)

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface AppErrorReporterEntryPoint {
    fun appErrorReporter(): AppErrorReporter
}

/** For UI utility functions that are not owned by a ViewModel. */
fun userVisibleError(
    context: Context,
    error: Throwable,
    component: String,
    operation: String
): String = runCatching {
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        AppErrorReporterEntryPoint::class.java
    ).appErrorReporter().present(
        error,
        ErrorReportContext(component = component, operation = operation)
    ).displayMessage
}.getOrElse {
    UserFacingErrorMapper.map(error, operation).displayMessage
}

/**
 * Reports metadata-only non-fatal errors. The original exception is never sent because provider
 * exception messages can contain request fragments, URLs or credentials. A safe exception keeps
 * the original stack trace while its message is reduced to a stable error code.
 */
@Singleton
class AppErrorReporter @Inject constructor(
    private val securePreferences: SecurePreferences
) {
    fun configureCollection() {
        setCollectionEnabled(securePreferences.isErrorReportingEnabled())
    }

    fun setCollectionEnabled(enabled: Boolean) {
        runCatching {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        }.onFailure {
            Log.w(TAG, "Could not update Crashlytics collection state")
        }
    }

    fun present(error: Throwable, context: ErrorReportContext): UserFacingError {
        val userError = UserFacingErrorMapper.map(error, context.operation)
        record(error, context, userError)
        return userError
    }

    fun record(error: Throwable, context: ErrorReportContext) {
        record(error, context, UserFacingErrorMapper.map(error, context.operation))
    }

    /** Keeps the crash location while stripping the original exception message and cause chain. */
    fun sanitizeFatal(error: Throwable, context: ErrorReportContext): Throwable {
        if (!securePreferences.isErrorReportingEnabled()) return error
        val userError = UserFacingErrorMapper.map(error, context.operation)
        runCatching {
            applyCrashlyticsContext(
                FirebaseCrashlytics.getInstance(),
                error,
                context,
                userError
            )
        }
        return PrivacySafeFatalException(userError.code).apply {
            stackTrace = error.stackTrace
        }
    }

    @Synchronized
    private fun record(
        error: Throwable,
        context: ErrorReportContext,
        userError: UserFacingError
    ) {
        if (error is CancellationException || userError.category == UserErrorCategory.CANCELLED) return
        if (!securePreferences.isErrorReportingEnabled()) return

        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            applyCrashlyticsContext(crashlytics, error, context, userError)
            crashlytics.log("Handled app error: ${userError.code}")

            val safeException = PrivacySafeNonFatalException(userError.code).apply {
                stackTrace = error.stackTrace
            }
            crashlytics.recordException(safeException)
            Log.e(TAG, "Recorded non-fatal ${userError.code}")
        }.onFailure {
            Log.w(TAG, "Could not record non-fatal ${userError.code}")
        }
    }

    private fun applyCrashlyticsContext(
        crashlytics: FirebaseCrashlytics,
        error: Throwable,
        context: ErrorReportContext,
        userError: UserFacingError
    ) {
        crashlytics.setCustomKey("error_code", userError.code)
        crashlytics.setCustomKey("error_category", userError.category.name)
        crashlytics.setCustomKey("error_retryable", userError.retryable)
        crashlytics.setCustomKey("error_component", safe(context.component))
        crashlytics.setCustomKey("error_operation", safe(context.operation))
        crashlytics.setCustomKey("error_provider", safe(context.provider))
        crashlytics.setCustomKey("error_model", safe(context.model))
        crashlytics.setCustomKey("error_tool", safe(context.tool))
        crashlytics.setCustomKey("original_error_type", safe(error::class.java.name))
    }

    private fun safe(value: String?): String = value.orEmpty()
        .let(SecureDiagnosticRedactor::redact)
        .replace(Regex("[\r\n\t]"), " ")
        .take(MAX_CONTEXT_LENGTH)

    private class PrivacySafeNonFatalException(code: String) : RuntimeException(code)
    private class PrivacySafeFatalException(code: String) : RuntimeException(code)

    private companion object {
        const val TAG = "AppErrorReporter"
        const val MAX_CONTEXT_LENGTH = 100
    }
}
