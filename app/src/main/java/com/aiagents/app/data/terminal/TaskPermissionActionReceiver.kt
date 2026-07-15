package com.aiagents.app.data.terminal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TaskPermissionActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var taskCompletionNotifier: TaskCompletionNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val workspaceId = intent.getLongExtra(TaskCompletionNotifier.EXTRA_WORKSPACE_ID, -1L)
        if (workspaceId < 0L) return

        when (intent.action) {
            TaskCompletionNotifier.ACTION_APPROVE ->
                taskCompletionNotifier.handlePermissionAction(workspaceId, approved = true)

            TaskCompletionNotifier.ACTION_DENY ->
                taskCompletionNotifier.handlePermissionAction(workspaceId, approved = false)
        }
    }
}
