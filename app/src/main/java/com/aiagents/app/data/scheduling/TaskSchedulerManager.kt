package com.aiagents.app.data.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.aiagents.app.data.local.ScheduledTaskDao
import com.aiagents.app.data.model.ScheduledTaskEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskSchedulerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduledTaskDao: ScheduledTaskDao
) {
    companion object {
        private const val TAG = "TaskSchedulerManager"
        const val EXTRA_TASK_ID = "scheduled_task_id"
        private const val REQUEST_CODE_BASE = 90000
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedule the next alarm for a task. Idempotent — safe to call multiple times.
     */
    fun scheduleAlarm(task: ScheduledTaskEntity) {
        if (!task.enabled) {
            cancelAlarm(task.id)
            return
        }

        val intent = Intent(context, ScheduledTaskReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, task.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (REQUEST_CODE_BASE + task.id).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, task.nextRunAt, pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, task.nextRunAt, pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, task.nextRunAt, pendingIntent
                )
            }
            Log.d(TAG, "Scheduled alarm for task ${task.id} '${task.label}' at ${task.nextRunAt}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm for task ${task.id}", e)
        }
    }

    fun cancelAlarm(taskId: Long) {
        val intent = Intent(context, ScheduledTaskReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (REQUEST_CODE_BASE + taskId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Cancelled alarm for task $taskId")
    }

    /**
     * Re-schedule all enabled tasks. Call on boot and app startup.
     */
    suspend fun rescheduleAll() {
        val tasks = scheduledTaskDao.getEnabled()
        val now = System.currentTimeMillis()
        for (task in tasks) {
            if (task.nextRunAt <= now) {
                // Missed execution — update nextRunAt to next future occurrence
                val nextRun = computeNextRun(task.scheduleType, task.scheduleValue, now)
                if (nextRun != null) {
                    scheduledTaskDao.markExecuted(task.id, now, "(missed)", nextRun)
                    scheduleAlarm(task.copy(nextRunAt = nextRun))
                } else {
                    // One-time task that's past — disable
                    scheduledTaskDao.setEnabled(task.id, false)
                }
            } else {
                scheduleAlarm(task)
            }
        }
        Log.d(TAG, "Rescheduled ${tasks.size} tasks")
    }

    /**
     * Compute the next run time AFTER [afterMillis] based on schedule type/value.
     * Returns null for one-time tasks that have already passed.
     */
    fun computeNextRun(type: String, value: String, afterMillis: Long = System.currentTimeMillis()): Long? {
        val afterDateTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(afterMillis), ZoneId.systemDefault()
        )

        return when (type) {
            "once" -> {
                // ISO datetime: "2026-03-25T09:00"
                try {
                    val dt = LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    val millis = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    if (millis > afterMillis) millis else null
                } catch (_: Exception) { null }
            }

            "daily" -> {
                // "HH:mm"
                val time = LocalTime.parse(value)
                var next = LocalDateTime.of(afterDateTime.toLocalDate(), time)
                if (!next.isAfter(afterDateTime)) next = next.plusDays(1)
                next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }

            "weekly" -> {
                // "MON,WED,FRI 09:00"
                val parts = value.split(" ", limit = 2)
                if (parts.size != 2) return null
                val days = parts[0].split(",").mapNotNull { parseDayOfWeek(it.trim()) }.toSet()
                val time = LocalTime.parse(parts[1].trim())
                if (days.isEmpty()) return null

                var candidate = LocalDateTime.of(afterDateTime.toLocalDate(), time)
                if (!candidate.isAfter(afterDateTime)) candidate = candidate.plusDays(1)
                // Find next matching day (max 7 iterations)
                repeat(7) {
                    if (candidate.dayOfWeek in days) {
                        return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    candidate = candidate.plusDays(1)
                }
                null
            }

            "interval" -> {
                // "30m", "2h", "6h"
                val minutes = parseIntervalMinutes(value) ?: return null
                afterMillis + (minutes * 60_000L)
            }

            else -> null
        }
    }

    private fun parseDayOfWeek(s: String): DayOfWeek? = when (s.uppercase()) {
        "MON", "LUN" -> DayOfWeek.MONDAY
        "TUE", "MAR" -> DayOfWeek.TUESDAY
        "WED", "MIE", "MIÉ" -> DayOfWeek.WEDNESDAY
        "THU", "JUE" -> DayOfWeek.THURSDAY
        "FRI", "VIE" -> DayOfWeek.FRIDAY
        "SAT", "SAB", "SÁB" -> DayOfWeek.SATURDAY
        "SUN", "DOM" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun parseIntervalMinutes(value: String): Long? {
        val trimmed = value.trim().lowercase()
        return when {
            trimmed.endsWith("m") -> trimmed.dropLast(1).toLongOrNull()
            trimmed.endsWith("h") -> trimmed.dropLast(1).toLongOrNull()?.times(60)
            trimmed.endsWith("d") -> trimmed.dropLast(1).toLongOrNull()?.times(1440)
            else -> trimmed.toLongOrNull() // assume minutes
        }
    }
}
