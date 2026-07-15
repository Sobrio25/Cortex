package com.aiagents.app.data.terminal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aiagents.app.MainActivity
import com.aiagents.app.R
import com.aiagents.app.data.local.SecurePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskCompletionNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePreferences: SecurePreferences
) {
    companion object {
        private const val TAG = "TaskCompletionNotifier"
        private const val CHANNEL_ID = "task_completion"
        private const val NOTIFICATION_ID_BASE = 52_000

        const val ACTION_APPROVE = "com.aiagents.app.action.APPROVE_TASK_PERMISSION"
        const val ACTION_DENY = "com.aiagents.app.action.DENY_TASK_PERMISSION"
        const val EXTRA_WORKSPACE_ID = "workspace_id"
    }

    private data class PendingApproval(
        val agentName: String,
        val onApprove: () -> Unit,
        val onDeny: () -> Unit
    )

    private val pendingApprovals = mutableMapOf<Long, PendingApproval>()

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Actividad de Cortex",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Aprobaciones y estado final de las tareas de Cortex"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun notifyTaskCompleted(
        workspaceId: Long,
        agentName: String,
        durationMs: Long,
        preview: String,
        failed: Boolean = false
    ) {
        if (!securePreferences.isTaskNotificationsEnabled()) return

        synchronized(pendingApprovals) {
            pendingApprovals.remove(workspaceId)
        }

        val durationText = formatDuration(durationMs)
        val shortPreview = if (preview.length > 120) preview.take(120) + "…" else preview

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(
                when {
                    failed -> "$agentName no pudo terminar"
                    else -> "$agentName terminó ($durationText)"
                }
            )
            .setContentText(shortPreview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(shortPreview))
            .setContentIntent(openAppPendingIntent(workspaceId))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()

        show(workspaceId, notification)
        Log.d(TAG, "Notificación actualizada: $agentName completó en $durationText")
    }

    /**
     * Removes the previous terminal state for this workspace. The foreground service then owns
     * the single visible processing notification until the task completes or needs attention.
     */
    fun notifyTaskStarted(workspaceId: Long) {
        synchronized(pendingApprovals) {
            pendingApprovals.remove(workspaceId)
        }
        notificationManager().cancel(notificationId(workspaceId))
    }

    fun notifyPermissionRequired(
        workspaceId: Long,
        agentName: String,
        durationMs: Long,
        request: PermissionRequest,
        onApprove: () -> Unit,
        onDeny: () -> Unit
    ) {
        if (!securePreferences.isTaskNotificationsEnabled()) return

        synchronized(pendingApprovals) {
            pendingApprovals[workspaceId] = PendingApproval(agentName, onApprove, onDeny)
        }

        val commandPreview = request.command.replace('\n', ' ').take(180)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$agentName necesita aprobación")
            .setContentText(commandPreview)
            .setSubText("En espera · ${formatDuration(durationMs)}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("El agente quiere ejecutar:\n$commandPreview")
            )
            .setContentIntent(openAppPendingIntent(workspaceId))
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Denegar",
                decisionPendingIntent(workspaceId, approved = false)
            )
            .addAction(
                android.R.drawable.ic_menu_send,
                "Aprobar una vez",
                decisionPendingIntent(workspaceId, approved = true)
            )
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        show(workspaceId, notification)
    }

    fun notifyAppAttentionRequired(
        workspaceId: Long,
        agentName: String,
        durationMs: Long,
        message: String
    ) {
        if (!securePreferences.isTaskNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$agentName necesita tu atención")
            .setContentText(message)
            .setSubText("En espera · ${formatDuration(durationMs)}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openAppPendingIntent(workspaceId))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        show(workspaceId, notification)
    }

    fun notifyPermissionDenied(workspaceId: Long, agentName: String) {
        synchronized(pendingApprovals) {
            pendingApprovals.remove(workspaceId)
        }
        showDecisionFinished(workspaceId, agentName, "Solicitud denegada")
    }

    /** Called by [TaskPermissionActionReceiver]. */
    fun handlePermissionAction(workspaceId: Long, approved: Boolean) {
        val pending = synchronized(pendingApprovals) {
            pendingApprovals.remove(workspaceId)
        }

        if (pending == null) {
            showDecisionFinished(workspaceId, "Cortex", "Esta solicitud ya no está activa")
            return
        }

        if (approved) {
            notificationManager().cancel(notificationId(workspaceId))
            pending.onApprove()
        } else {
            pending.onDeny()
            showDecisionFinished(workspaceId, pending.agentName, "Solicitud denegada")
        }
    }

    private fun showDecisionFinished(workspaceId: Long, agentName: String, message: String) {
        if (!securePreferences.isTaskNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(agentName)
            .setContentText(message)
            .setContentIntent(openAppPendingIntent(workspaceId))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        show(workspaceId, notification)
    }

    private fun openAppPendingIntent(workspaceId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            notificationId(workspaceId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun decisionPendingIntent(workspaceId: Long, approved: Boolean): PendingIntent {
        val intent = Intent(context, TaskPermissionActionReceiver::class.java).apply {
            action = if (approved) ACTION_APPROVE else ACTION_DENY
            putExtra(EXTRA_WORKSPACE_ID, workspaceId)
        }
        val requestCode = notificationId(workspaceId) * 2 + if (approved) 1 else 0
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun show(workspaceId: Long, notification: android.app.Notification) {
        try {
            notificationManager().notify(notificationId(workspaceId), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Sin permiso para mostrar notificaciones", e)
        }
    }

    private fun notificationManager(): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    internal fun notificationId(workspaceId: Long): Int {
        val foldedId = (workspaceId xor (workspaceId ushr 32)).toInt()
        return NOTIFICATION_ID_BASE + (foldedId and 0x3fff)
    }

    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }
}
