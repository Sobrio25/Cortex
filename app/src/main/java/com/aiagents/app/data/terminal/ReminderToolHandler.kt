package com.aiagents.app.data.terminal

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aiagents.app.R
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

data class ReminderData(
    val id: Int,
    val message: String,
    val triggerTimeMillis: Long,
    val createdAt: Long = System.currentTimeMillis()
)

data class ReminderToolResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

@Singleton
class ReminderToolHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ReminderToolHandler"
        const val CHANNEL_ID = "agent_reminders"
        const val PREFS_NAME = "agent_reminders_prefs"
        const val PREFS_KEY = "reminders_json"
        const val TOOL_SET_REMINDER = "set_reminder"
        const val TOOL_LIST_REMINDERS = "list_reminders"
        const val TOOL_CANCEL_REMINDER = "cancel_reminder"
        const val TOOL_SET_ALARM = "set_alarm"

        val ALL_TOOL_NAMES = setOf(
            TOOL_SET_REMINDER, TOOL_LIST_REMINDERS, TOOL_CANCEL_REMINDER, TOOL_SET_ALARM
        )

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_SET_REMINDER,
                "description" to "Create a reminder notification. Specify relative minutes OR exact time.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "message" to mapOf("type" to "string", "description" to "Reminder message"),
                        "minutes" to mapOf("type" to "integer", "description" to "Minutes from now. Use this OR 'time', not both."),
                        "time" to mapOf("type" to "string", "description" to "Exact time HH:mm (24h). If past today, schedules for tomorrow."),
                        "date" to mapOf("type" to "string", "description" to "Date yyyy-MM-dd (optional, default: today)")
                    ),
                    "required" to listOf("message"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_SET_ALARM,
                "description" to "Open system clock app to create an alarm. Use set_reminder for app notifications instead.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "hour" to mapOf("type" to "integer", "description" to "Hour (0-23)"),
                        "minute" to mapOf("type" to "integer", "description" to "Minute (0-59)"),
                        "message" to mapOf("type" to "string", "description" to "Alarm message (optional)")
                    ),
                    "required" to listOf("hour", "minute"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_LIST_REMINDERS,
                "description" to "List all active (pending) reminders.",
                "parameters" to mapOf("type" to "object",
                    "properties" to emptyMap<String, Any>(),
                    "required" to emptyList<String>())
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_CANCEL_REMINDER,
                "description" to "Cancel a reminder by ID.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "reminder_id" to mapOf("type" to "integer", "description" to "Reminder ID")
                    ),
                    "required" to listOf("reminder_id"))
            ))
        )
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recordatorios de Agentes",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificaciones de recordatorios creados por agentes de IA"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun executeTool(toolCallId: String, toolName: String, arguments: String): ReminderToolResult {
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            when (toolName) {
                TOOL_SET_REMINDER -> setReminder(toolCallId, args)
                TOOL_SET_ALARM -> setSystemAlarm(toolCallId, args)
                TOOL_LIST_REMINDERS -> listReminders(toolCallId)
                TOOL_CANCEL_REMINDER -> cancelReminder(toolCallId, args)
                else -> ReminderToolResult(toolCallId, false, "Herramienta desconocida: $toolName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando $toolName", e)
            ReminderToolResult(toolCallId, false, "Error: ${e.message}")
        }
    }

    private fun setReminder(id: String, args: com.google.gson.JsonObject): ReminderToolResult {
        val message = args.get("message")?.asString ?: return ReminderToolResult(id, false, "Parametro 'message' requerido")
        val minutes = args.get("minutes")?.asInt
        val time = args.get("time")?.asString
        val dateStr = args.get("date")?.asString

        val triggerTime: Long = when {
            minutes != null -> System.currentTimeMillis() + (minutes * 60 * 1000L)
            time != null -> {
                val parts = time.split(":")
                if (parts.size != 2) return ReminderToolResult(id, false, "Formato de hora invalido. Usa HH:mm")
                val hour = parts[0].toIntOrNull() ?: return ReminderToolResult(id, false, "Hora invalida")
                val minute = parts[1].toIntOrNull() ?: return ReminderToolResult(id, false, "Minuto invalido")

                val cal = Calendar.getInstance()
                if (dateStr != null) {
                    try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val date = sdf.parse(dateStr) ?: throw Exception()
                        val dateCal = Calendar.getInstance().apply { this.time = date }
                        cal.set(Calendar.YEAR, dateCal.get(Calendar.YEAR))
                        cal.set(Calendar.MONTH, dateCal.get(Calendar.MONTH))
                        cal.set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH))
                    } catch (e: Exception) {
                        return ReminderToolResult(id, false, "Formato de fecha invalido. Usa yyyy-MM-dd")
                    }
                }
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                // If time already passed today and no specific date, schedule for tomorrow
                if (cal.timeInMillis <= System.currentTimeMillis() && dateStr == null) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                cal.timeInMillis
            }
            else -> return ReminderToolResult(id, false, "Indica 'minutes' o 'time' para programar el recordatorio")
        }

        val reminderId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val reminder = ReminderData(reminderId, message, triggerTime)

        // Schedule the alarm
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_id", reminderId)
            putExtra("message", message)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, reminderId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    // Fallback to inexact alarm
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }

        // Save reminder to preferences
        saveReminder(reminder)

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val timeStr = sdf.format(Date(triggerTime))

        return ReminderToolResult(id, true,
            "Recordatorio creado (ID: $reminderId)\nMensaje: \"$message\"\nProgramado para: $timeStr")
    }

    private fun setSystemAlarm(id: String, args: com.google.gson.JsonObject): ReminderToolResult {
        val hour = args.get("hour")?.asInt ?: return ReminderToolResult(id, false, "Parametro 'hour' requerido")
        val minute = args.get("minute")?.asInt ?: return ReminderToolResult(id, false, "Parametro 'minute' requerido")
        val message = args.get("message")?.asString ?: ""

        val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
            putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
            if (message.isNotBlank()) putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, message)
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            ReminderToolResult(id, true, "Alarma configurada para las ${String.format("%02d:%02d", hour, minute)}")
        } catch (e: Exception) {
            ReminderToolResult(id, false, "No se pudo abrir la app de reloj: ${e.message}")
        }
    }

    private fun listReminders(id: String): ReminderToolResult {
        val reminders = getReminders().filter { it.triggerTimeMillis > System.currentTimeMillis() }
        if (reminders.isEmpty()) return ReminderToolResult(id, true, "No hay recordatorios activos.")

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val formatted = buildString {
            appendLine("Recordatorios activos:")
            appendLine()
            reminders.sortedBy { it.triggerTimeMillis }.forEachIndexed { i, r ->
                appendLine("${i + 1}. [ID: ${r.id}] \"${r.message}\"")
                appendLine("   Programado: ${sdf.format(Date(r.triggerTimeMillis))}")
                val remaining = (r.triggerTimeMillis - System.currentTimeMillis()) / 60000
                appendLine("   En: ${remaining}min")
                appendLine()
            }
        }
        return ReminderToolResult(id, true, formatted.trim())
    }

    private fun cancelReminder(id: String, args: com.google.gson.JsonObject): ReminderToolResult {
        val reminderId = args.get("reminder_id")?.asInt ?: return ReminderToolResult(id, false, "Parametro 'reminder_id' requerido")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, reminderId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        removeReminder(reminderId)

        return ReminderToolResult(id, true, "Recordatorio $reminderId cancelado.")
    }

    // SharedPreferences storage
    private fun getReminders(): List<ReminderData> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(PREFS_KEY, "[]") ?: "[]"
        return try {
            Gson().fromJson(json, object : TypeToken<List<ReminderData>>() {}.type)
        } catch (e: Exception) { emptyList() }
    }

    private fun saveReminder(reminder: ReminderData) {
        val list = getReminders().toMutableList()
        // Clean up expired reminders while we're at it
        list.removeAll { it.triggerTimeMillis < System.currentTimeMillis() - 3600000 }
        list.add(reminder)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREFS_KEY, Gson().toJson(list)).apply()
    }

    private fun removeReminder(id: Int) {
        val list = getReminders().toMutableList()
        list.removeAll { it.id == id }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREFS_KEY, Gson().toJson(list)).apply()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("message") ?: "Recordatorio"
        val reminderId = intent.getIntExtra("reminder_id", 0)

        val notification = NotificationCompat.Builder(context, ReminderToolHandler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Recordatorio")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(reminderId, notification)
    }
}
