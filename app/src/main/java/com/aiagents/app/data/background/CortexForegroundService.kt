package com.aiagents.app.data.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.aiagents.app.MainActivity
import com.aiagents.app.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CortexForegroundService : Service() {
    @Inject
    lateinit var taskCoordinator: CortexTaskCoordinator

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                taskCoordinator.cancelAll()
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val agentName = intent.getStringExtra(EXTRA_AGENT_NAME).orEmpty().ifBlank { "Cortex" }
                val activeTaskCount = intent.getIntExtra(EXTRA_ACTIVE_TASK_COUNT, 1).coerceAtLeast(1)
                val startedAtMillis = intent.getLongExtra(
                    EXTRA_STARTED_AT_MILLIS,
                    System.currentTimeMillis()
                )
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildWorkingNotification(agentName, activeTaskCount, startedAtMillis),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    } else {
                        0
                    }
                )
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) {
        taskCoordinator.cancelAll()
        stopForegroundAndSelf()
    }

    private fun buildWorkingNotification(
        agentName: String,
        activeTaskCount: Int,
        startedAtMillis: Long
    ) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(
                if (activeTaskCount == 1) "$agentName está trabajando"
                else "Cortex está ejecutando $activeTaskCount tareas"
            )
            .setContentText("Puedes salir de la app; el trabajo continuará en segundo plano.")
            .setContentIntent(openAppPendingIntent())
            .addAction(
                android.R.drawable.ic_media_pause,
                "Detener",
                stopPendingIntent()
            )
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(0, 0, true)
            .setWhen(startedAtMillis)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stopPendingIntent(): PendingIntent {
        val intent = Intent(this, CortexForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        return PendingIntent.getService(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Trabajo de Cortex",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantiene las tareas de Cortex activas cuando la app está en segundo plano"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun stopForegroundAndSelf() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "cortex_background_work"
        private const val NOTIFICATION_ID = 51_000
        private const val ACTION_START = "com.aiagents.app.action.START_CORTEX_WORK"
        private const val ACTION_STOP = "com.aiagents.app.action.STOP_CORTEX_WORK"
        private const val EXTRA_AGENT_NAME = "agent_name"
        private const val EXTRA_ACTIVE_TASK_COUNT = "active_task_count"
        private const val EXTRA_STARTED_AT_MILLIS = "started_at_millis"

        fun start(
            context: Context,
            agentName: String,
            activeTaskCount: Int,
            startedAtMillis: Long
        ) {
            val intent = Intent(context, CortexForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_AGENT_NAME, agentName)
                putExtra(EXTRA_ACTIVE_TASK_COUNT, activeTaskCount)
                putExtra(EXTRA_STARTED_AT_MILLIS, startedAtMillis)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CortexForegroundService::class.java))
        }
    }
}
