package com.aiagents.app.data.terminal

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

data class CalendarToolResult(
    val toolCallId: String,
    val toolName: String,
    val success: Boolean,
    val content: String
)

@Singleton
class CalendarToolHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CalendarToolHandler"

        fun getToolDefinitionsJson(): List<Map<String, Any>> {
            return listOf(
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to "read_calendar_events",
                        "description" to "Read user's calendar events. Dates in ISO 8601 format. Defaults to next 7 days if no dates specified.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "start_date" to mapOf(
                                    "type" to "string",
                                    "description" to "Start date ISO 8601 (optional)"
                                ),
                                "end_date" to mapOf(
                                    "type" to "string",
                                    "description" to "End date ISO 8601 (optional)"
                                ),
                                "calendar_id" to mapOf(
                                    "type" to "number",
                                    "description" to "Calendar ID (optional, uses main calendar)"
                                )
                            ),
                            "required" to emptyList<String>()
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to "add_calendar_event",
                        "description" to "Add event to user's calendar. Dates in ISO 8601. Default reminder: 15 min before.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "title" to mapOf(
                                    "type" to "string",
                                    "description" to "Título del evento"
                                ),
                                "start_time" to mapOf(
                                    "type" to "string",
                                    "description" to "Fecha/hora de inicio en formato ISO 8601"
                                ),
                                "end_time" to mapOf(
                                    "type" to "string",
                                    "description" to "Fecha/hora de fin en formato ISO 8601"
                                ),
                                "description" to mapOf(
                                    "type" to "string",
                                    "description" to "Descripción del evento (opcional)"
                                ),
                                "location" to mapOf(
                                    "type" to "string",
                                    "description" to "Ubicación del evento (opcional)"
                                ),
                                "reminder_minutes" to mapOf(
                                    "type" to "number",
                                    "description" to "Minutos antes del evento para el recordatorio (default: 15)"
                                )
                            ),
                            "required" to listOf("title", "start_time", "end_time")
                        )
                    )
                )
            )
        }
    }

    private val gson = Gson()
    private val contentResolver: ContentResolver = context.contentResolver
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    suspend fun executeTool(toolCallId: String, toolName: String, arguments: String): CalendarToolResult {
        return try {
            val args = gson.fromJson(arguments, JsonObject::class.java) ?: JsonObject()
            when (toolName) {
                "read_calendar_events" -> readCalendarEvents(toolCallId, args)
                "add_calendar_event" -> addCalendarEvent(toolCallId, args)
                else -> CalendarToolResult(
                    toolCallId = toolCallId,
                    toolName = toolName,
                    success = false,
                    content = "Tool desconocida: $toolName"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing calendar tool: $toolName", e)
            CalendarToolResult(
                toolCallId = toolCallId,
                toolName = toolName,
                success = false,
                content = "Error ejecutando $toolName: ${e.message}"
            )
        }
    }

    private fun readCalendarEvents(toolCallId: String, args: JsonObject): CalendarToolResult {
        val calendarId = args.get("calendar_id")?.asLong ?: getDefaultCalendarId()
            ?: return CalendarToolResult(
                toolCallId = toolCallId,
                toolName = "read_calendar_events",
                success = false,
                content = "No se encontró un calendario disponible"
            )

        val now = System.currentTimeMillis()
        val sevenDaysLater = now + (7 * 24 * 60 * 60 * 1000)

        val startDate = try {
            args.get("start_date")?.asString?.let { isoFormat.parse(it)?.time } ?: now
        } catch (e: Exception) {
            now
        }

        val endDate = try {
            args.get("end_date")?.asString?.let { isoFormat.parse(it)?.time } ?: sevenDaysLater
        } catch (e: Exception) {
            sevenDaysLater
        }

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.CALENDAR_ID
        )

        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(calendarId.toString(), startDate.toString(), endDate.toString())

        val events = mutableListOf<String>()

        contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(CalendarContract.Events._ID)
            val titleIndex = cursor.getColumnIndex(CalendarContract.Events.TITLE)
            val descIndex = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
            val dtStartIndex = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
            val dtEndIndex = cursor.getColumnIndex(CalendarContract.Events.DTEND)
            val locationIndex = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val title = cursor.getString(titleIndex) ?: "(Sin título)"
                val description = cursor.getString(descIndex) ?: ""
                val start = cursor.getLong(dtStartIndex)
                val end = cursor.getLong(dtEndIndex)
                val location = cursor.getString(locationIndex) ?: ""

                val startFormatted = formatDateTime(start)
                val endFormatted = formatDateTime(end)

                val eventInfo = buildString {
                    append("Evento ID: $id\n")
                    append("Título: $title\n")
                    append("Inicio: $startFormatted\n")
                    append("Fin: $endFormatted\n")
                    if (description.isNotEmpty()) append("Descripción: $description\n")
                    if (location.isNotEmpty()) append("Ubicación: $location\n")
                }
                events.add(eventInfo)
            }
        }

        val content = if (events.isEmpty()) {
            "No se encontraron eventos en el rango de fechas especificado."
        } else {
            "Eventos encontrados (${events.size}):\n\n${events.joinToString("\n---\n")}"
        }

        return CalendarToolResult(
            toolCallId = toolCallId,
            toolName = "read_calendar_events",
            success = true,
            content = content
        )
    }

    private fun addCalendarEvent(toolCallId: String, args: JsonObject): CalendarToolResult {
        val title = args.get("title")?.asString
            ?: return CalendarToolResult(
                toolCallId = toolCallId,
                toolName = "add_calendar_event",
                success = false,
                content = "Parámetro 'title' requerido"
            )

        val startTimeStr = args.get("start_time")?.asString
            ?: return CalendarToolResult(
                toolCallId = toolCallId,
                toolName = "add_calendar_event",
                success = false,
                content = "Parámetro 'start_time' requerido"
            )

        val endTimeStr = args.get("end_time")?.asString
            ?: return CalendarToolResult(
                toolCallId = toolCallId,
                toolName = "add_calendar_event",
                success = false,
                content = "Parámetro 'end_time' requerido"
            )

        val startTime = try {
            isoFormat.parse(startTimeStr)?.time
        } catch (e: Exception) {
            null
        } ?: return CalendarToolResult(
            toolCallId = toolCallId,
            toolName = "add_calendar_event",
            success = false,
            content = "Formato de fecha inválido para 'start_time'. Use formato ISO 8601 (ej: 2024-01-15T10:00:00Z)"
        )

        val endTime = try {
            isoFormat.parse(endTimeStr)?.time
        } catch (e: Exception) {
            null
        } ?: return CalendarToolResult(
            toolCallId = toolCallId,
            toolName = "add_calendar_event",
            success = false,
            content = "Formato de fecha inválido para 'end_time'. Use formato ISO 8601 (ej: 2024-01-15T11:00:00Z)"
        )

        val calendarId = getDefaultCalendarId()
            ?: return CalendarToolResult(
                toolCallId = toolCallId,
                toolName = "add_calendar_event",
                success = false,
                content = "No se encontró un calendario disponible para agregar el evento"
            )

        val description = args.get("description")?.asString ?: ""
        val location = args.get("location")?.asString ?: ""
        val reminderMinutes = args.get("reminder_minutes")?.asInt ?: 15

        val eventValues = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startTime)
            put(CalendarContract.Events.DTEND, endTime)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }

        val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, eventValues)
            ?: return CalendarToolResult(
                toolCallId = toolCallId,
                toolName = "add_calendar_event",
                success = false,
                content = "Error al insertar el evento en el calendario"
            )

        val eventId = uri.lastPathSegment?.toLong()

        if (eventId != null && reminderMinutes > 0) {
            val reminderValues = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, reminderMinutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        }

        val startFormatted = formatDateTime(startTime)
        val endFormatted = formatDateTime(endTime)

        val content = buildString {
            append("Evento agregado exitosamente\n")
            append("ID: $eventId\n")
            append("Título: $title\n")
            append("Inicio: $startFormatted\n")
            append("Fin: $endFormatted\n")
            if (description.isNotEmpty()) append("Descripción: $description\n")
            if (location.isNotEmpty()) append("Ubicación: $location\n")
            append("Recordatorio: $reminderMinutes minutos antes")
        }

        return CalendarToolResult(
            toolCallId = toolCallId,
            toolName = "add_calendar_event",
            success = true,
            content = content
        )
    }

    private fun getDefaultCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY
        )

        contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(CalendarContract.Calendars._ID)
            val isPrimaryIndex = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val isPrimary = if (isPrimaryIndex >= 0) cursor.getInt(isPrimaryIndex) == 1 else false

                if (isPrimary) {
                    return id
                }
            }

            cursor.moveToFirst()
            if (cursor.count > 0 && idIndex >= 0) {
                return cursor.getLong(idIndex)
            }
        }

        return null
    }

    private fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
