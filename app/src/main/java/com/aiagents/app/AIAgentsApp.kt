package com.aiagents.app

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.play.core.splitcompat.SplitCompat
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.diagnostics.AppHealthMonitor
import com.aiagents.app.data.diagnostics.AppErrorReporter
import com.aiagents.app.data.diagnostics.ErrorReportContext
import com.aiagents.app.data.auth.FirebaseBootstrap
import com.aiagents.app.data.scheduling.TaskSchedulerManager
import com.aiagents.app.data.runtime.RuntimeContextProvider
import com.aiagents.app.data.terminal.MemoryExtractor
import com.aiagents.app.data.terminal.MemoryMaintenanceWorker
import com.aiagents.app.domain.model.ProviderType
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class AIAgentsApp : Application(), Configuration.Provider {

    private val processStartedElapsedRealtimeMs = SystemClock.elapsedRealtime()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var memoryExtractor: MemoryExtractor

    @Inject
    lateinit var securePreferences: SecurePreferences

    @Inject
    lateinit var taskSchedulerManager: TaskSchedulerManager

    @Inject
    lateinit var runtimeContextProvider: RuntimeContextProvider

    @Inject
    lateinit var appHealthMonitor: AppHealthMonitor

    @Inject
    lateinit var errorReporter: AppErrorReporter

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        SplitCompat.install(this)
    }

    override fun onCreate() {
        // Hilt performs application injection from super.onCreate(). At this point Android has
        // attached the Application context, so Crashlytics and FirebaseAuth can both initialize
        // safely in the main process and in the dedicated assistant voice process.
        FirebaseBootstrap.ensureInitialized(this)
        super.onCreate()
        errorReporter.configureCollection()
        appHealthMonitor.installCrashHandler(this)
        if (!isMainProcess()) {
            Log.i("AIAgentsApp", "Skipping main-process startup work in ${currentProcessName()}")
            return
        }
        appHealthMonitor.start(this, processStartedElapsedRealtimeMs)
        scheduleMemoryMaintenance()
        rescheduleTaskAlarms()
        refreshRuntimeIdentity()
        triggerStartupMemoryExtraction()
    }

    private fun refreshRuntimeIdentity() {
        applicationScope.launch {
            runCatching { runtimeContextProvider.refreshIdentityFromMemory() }
                .onFailure {
                    Log.w("AIAgentsApp", "Could not backfill runtime identity", it)
                    errorReporter.record(
                        it,
                        ErrorReportContext("application", "startup_identity_refresh")
                    )
                }
        }
    }

    private fun rescheduleTaskAlarms() {
        applicationScope.launch {
            try {
                taskSchedulerManager.rescheduleAll()
            } catch (e: Exception) {
                Log.e("AIAgentsApp", "Failed to reschedule task alarms", e)
                errorReporter.record(
                    e,
                    ErrorReportContext("application", "startup_task_reschedule")
                )
            }
        }
    }

    private fun scheduleMemoryMaintenance() {
        val request = PeriodicWorkRequestBuilder<MemoryMaintenanceWorker>(
            1, TimeUnit.DAYS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            MemoryMaintenanceWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Triggers memory extraction on app startup.
     * Runs after a delay to let the app fully initialize.
     */
    private fun triggerStartupMemoryExtraction() {
        applicationScope.launch {
            try {
                // Wait for app to fully initialize
                delay(5000)  // 5 seconds

                // Get active provider
                val provider = securePreferences.getActiveProvider()
                if (provider == null) {
                    Log.d("AIAgentsApp", "No active provider, skipping startup extraction")
                    return@launch
                }
                // Managed requests may fall back to privacy-restricted free capacity.
                // Never send semantic memory through an unattended managed request.
                if (provider == ProviderType.MANAGED) {
                    Log.d("AIAgentsApp", "Managed provider active, skipping startup memory extraction")
                    return@launch
                }

                // Get a selected model for this provider
                val selectedModels = securePreferences.getSelectedModels()
                val modelEntry = selectedModels.find { it.startsWith("${provider.name}|") }
                
                if (modelEntry == null) {
                    Log.d("AIAgentsApp", "No selected model for provider $provider")
                    return@launch
                }

                val modelId = modelEntry.substringAfter("|")
                
                Log.i("AIAgentsApp", "Triggering startup memory extraction with $provider|$modelId")

                // Trigger extraction for all conversations (no exclusion on startup)
                memoryExtractor.triggerExtraction(
                    excludeConversationId = null,
                    modelId = modelId,
                    provider = provider
                )

            } catch (e: Exception) {
                Log.e("AIAgentsApp", "Startup memory extraction failed", e)
                errorReporter.record(
                    e,
                    ErrorReportContext("application", "startup_memory_extraction")
                )
            }
        }
    }

    private fun isMainProcess(): Boolean {
        val processName = currentProcessName()
        return processName == null || processName == applicationInfo.processName
    }

    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getProcessName()
        }
        val pid = Process.myPid()
        return getSystemService(ActivityManager::class.java)
            ?.runningAppProcesses
            ?.firstOrNull { it.pid == pid }
            ?.processName
    }
}
