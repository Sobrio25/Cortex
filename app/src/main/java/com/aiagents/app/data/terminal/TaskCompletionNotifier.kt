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
        private const val NOTIFICATION_ID_BASE = 50000
    }

    private var notificationCounter = 0

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tareas completadas",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notificaciones cuando un agente termina una tarea larga"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun notifyTaskCompleted(agentName: String, durationMs: Long, preview: String) {
        if (!securePreferences.isTaskNotificationsEnabled()) return

        val thresholdMs = securePreferences.getLongTaskThresholdSeconds() * 1000L
        if (durationMs < thresholdMs) return

        val durationText = formatDuration(durationMs)
        val shortPreview = if (preview.length > 120) preview.take(120) + "…" else preview

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationCounter, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$agentName terminó ($durationText)")
            .setContentText(shortPreview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(shortPreview))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID_BASE + notificationCounter, notification)
            notificationCounter++
            Log.d(TAG, "Notificación enviada: $agentName completó en $durationText")
        } catch (e: SecurityException) {
            Log.w(TAG, "Sin permiso para mostrar notificaciones", e)
        }
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
