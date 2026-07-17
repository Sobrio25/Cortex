package com.aiagents.app.data.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Content-free health signals kept locally for release triage. */
data class AppHealthSnapshot(
    val previousStartupMs: Long? = null,
    val currentStartupMs: Long? = null,
    val lastExitReason: String? = null,
    val lastExitAtEpochMs: Long? = null,
    val lastUncaughtErrorType: String? = null,
    val lastUncaughtErrorAtEpochMs: Long? = null
)

@Singleton
class AppHealthMonitor @Inject constructor(
    private val errorReporter: AppErrorReporter
) {
    private val mutableSnapshot = MutableStateFlow(AppHealthSnapshot())
    val snapshot: StateFlow<AppHealthSnapshot> = mutableSnapshot.asStateFlow()

    private var appContext: Context? = null
    private var processStartedElapsedRealtimeMs: Long = 0L
    private var firstDrawRecorded = false

    fun start(context: Context, processStartedElapsedRealtimeMs: Long) {
        if (appContext != null) return
        appContext = context.applicationContext
        this.processStartedElapsedRealtimeMs = processStartedElapsedRealtimeMs

        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val exit = latestProcessExit(context)
        mutableSnapshot.value = AppHealthSnapshot(
            previousStartupMs = prefs.getLong(KEY_LAST_STARTUP_MS, -1L).takeIf { it >= 0L },
            lastExitReason = exit?.first,
            lastExitAtEpochMs = exit?.second,
            lastUncaughtErrorType = prefs.getString(KEY_UNCAUGHT_ERROR_TYPE, null),
            lastUncaughtErrorAtEpochMs = prefs.getLong(KEY_UNCAUGHT_ERROR_AT, -1L)
                .takeIf { it >= 0L }
        )
        prefs.edit()
            .remove(KEY_UNCAUGHT_ERROR_TYPE)
            .remove(KEY_UNCAUGHT_ERROR_AT)
            .apply()
        installCrashHandler(context)
    }

    fun recordFirstDraw(nowElapsedRealtimeMs: Long) {
        val context = appContext ?: return
        if (firstDrawRecorded || processStartedElapsedRealtimeMs <= 0L) return
        firstDrawRecorded = true
        val duration = (nowElapsedRealtimeMs - processStartedElapsedRealtimeMs).coerceAtLeast(0L)
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_STARTUP_MS, duration)
            .apply()
        mutableSnapshot.value = mutableSnapshot.value.copy(currentStartupMs = duration)
    }

    fun installCrashHandler(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is HealthCrashHandler) return
        Thread.setDefaultUncaughtExceptionHandler(
            HealthCrashHandler(context, previous, errorReporter)
        )
    }

    private fun latestProcessExit(context: Context): Pair<String, Long>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return null
        val exit = runCatching {
            activityManager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
                .firstOrNull()
        }.getOrNull() ?: return null
        return exitReasonLabel(exit.reason) to exit.timestamp
    }

    private class HealthCrashHandler(
        context: Context,
        private val delegate: Thread.UncaughtExceptionHandler?,
        private val errorReporter: AppErrorReporter
    ) : Thread.UncaughtExceptionHandler {
        private val applicationContext = context.applicationContext

        override fun uncaughtException(thread: Thread, error: Throwable) {
            runCatching {
                applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_UNCAUGHT_ERROR_TYPE, error.javaClass.simpleName.take(80))
                    .putLong(KEY_UNCAUGHT_ERROR_AT, System.currentTimeMillis())
                    .commit()
            }
            if (delegate != null) {
                delegate.uncaughtException(
                    thread,
                    errorReporter.sanitizeFatal(
                        error,
                        ErrorReportContext("application", "uncaught_exception")
                    )
                )
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "app_health_metrics"
        private const val KEY_LAST_STARTUP_MS = "last_startup_ms"
        private const val KEY_UNCAUGHT_ERROR_TYPE = "uncaught_error_type"
        private const val KEY_UNCAUGHT_ERROR_AT = "uncaught_error_at"

        internal fun exitReasonLabel(reason: Int): String = when (reason) {
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_CRASH -> "CRASH"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
            ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
            else -> "OTHER_$reason"
        }
    }
}
