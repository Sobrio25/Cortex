package com.aiagents.app.data.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-schedules all enabled alarms after device reboot.
 * AlarmManager alarms are lost on reboot, so we must restore them.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    @Inject lateinit var taskSchedulerManager: TaskSchedulerManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Boot completed — rescheduling all tasks")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                taskSchedulerManager.rescheduleAll()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule tasks on boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
