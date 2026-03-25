package com.aiagents.app.data.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * BroadcastReceiver that fires when an AlarmManager alarm triggers for a scheduled task.
 * Enqueues a WorkManager worker to execute the agent task in the background.
 */
class ScheduledTaskReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ScheduledTaskReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(TaskSchedulerManager.EXTRA_TASK_ID, -1L)
        if (taskId < 0) {
            Log.w(TAG, "Received alarm with no task ID")
            return
        }

        Log.d(TAG, "Alarm fired for scheduled task $taskId — enqueuing worker")

        val workRequest = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
            .setInputData(
                Data.Builder()
                    .putLong(ScheduledTaskWorker.KEY_TASK_ID, taskId)
                    .build()
            )
            .addTag("scheduled_task_$taskId")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
