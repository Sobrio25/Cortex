package com.aiagents.app.data.terminal

import com.aiagents.app.data.location.DeviceLocationResult
import com.aiagents.app.data.location.LocationErrorCode
import com.aiagents.app.data.location.LocationFixSource
import com.aiagents.app.data.location.LocationProvider
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationToolHandler @Inject constructor(
    private val locationProvider: LocationProvider
) {
    companion object {
        const val TOOL_NAME = "get_user_location"

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_NAME,
                    "description" to "Get the user's current device location for an explicitly location-dependent request such as nearby places or sharing their position. Weather tools resolve device location privately on their own, so do not call this tool before weather. This tool can return coordinates to the conversation and should be used only when coordinates or a general location are actually needed.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to emptyMap<String, Any>(),
                        "required" to emptyList<String>()
                    )
                )
            )
        )
    }

    fun hasPermission(): Boolean = locationProvider.hasLocationPermission()

    suspend fun executeTool(toolCallId: String): LocationToolResult =
        when (val result = locationProvider.getCurrentLocation(includeAddress = true)) {
            is DeviceLocationResult.Failure -> LocationToolResult(
                toolCallId = toolCallId,
                success = false,
                content = "Error [${result.code.name}]: ${result.userMessage}",
                errorCode = result.code
            )

            is DeviceLocationResult.Success -> {
                val location = result.location
                val sourceLabel = when (location.source) {
                    LocationFixSource.CURRENT -> "lectura actual"
                    LocationFixSource.LAST_KNOWN -> "última ubicación conocida"
                    LocationFixSource.MEMORY_CACHE -> "lectura reciente"
                }
                val updated = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                    .withLocale(Locale.getDefault())
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(location.capturedAtMillis))

                val content = buildString {
                    appendLine("Ubicación actual del usuario:")
                    appendLine("Coordenadas: ${location.latitude}, ${location.longitude}")
                    location.address?.let { appendLine("Dirección: $it") }
                    location.city?.let { appendLine("Ciudad: $it") }
                    location.country?.let { appendLine("País: $it") }
                    location.accuracyMeters?.let {
                        appendLine("Precisión aproximada: ${it.toInt().coerceAtLeast(1)} m")
                    }
                    appendLine("Actualizada: $updated ($sourceLabel)")
                    if (location.isStale) {
                        appendLine("Aviso: se utilizó una ubicación conocida reciente, no una lectura GPS nueva.")
                    }
                }.trim()

                LocationToolResult(
                    toolCallId = toolCallId,
                    success = true,
                    content = content,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracyMeters,
                    capturedAtMillis = location.capturedAtMillis,
                    isStale = location.isStale
                )
            }
        }
}

data class LocationToolResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val capturedAtMillis: Long? = null,
    val isStale: Boolean = false,
    val errorCode: LocationErrorCode? = null
)
