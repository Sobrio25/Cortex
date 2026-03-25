package com.aiagents.app.data.terminal

import android.util.Log
import com.aiagents.app.data.location.LocationProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationToolHandler @Inject constructor(
    private val locationProvider: LocationProvider
) {
    companion object {
        private const val TAG = "LocationToolHandler"
        const val TOOL_NAME = "get_user_location"

        fun getToolDefinitionsJson(): List<Map<String, Any>> {
            return listOf(
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_NAME,
                        "description" to "Get user's current GPS location. Returns coordinates, address, city, country. Use when user asks about their location, nearby places, or when you need to determine the user's city for weather queries, local recommendations, or any location-dependent request. If the user asks about weather without specifying a city, call this tool first to get their location.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to emptyMap<String, Any>(),
                            "required" to emptyList<String>()
                        )
                    )
                )
            )
        }
    }

    fun hasPermission(): Boolean = locationProvider.hasLocationPermission()

    suspend fun executeTool(toolCallId: String): LocationToolResult {
        if (!locationProvider.hasLocationPermission()) {
            return LocationToolResult(
                toolCallId = toolCallId,
                success = false,
                content = "Error: El usuario no ha concedido permiso de ubicacion. Pide al usuario que conceda el permiso de ubicacion en la configuracion de la app.",
                latitude = null,
                longitude = null
            )
        }

        val location = locationProvider.getCurrentLocation()
        if (location == null) {
            return LocationToolResult(
                toolCallId = toolCallId,
                success = false,
                content = "Error: No se pudo obtener la ubicacion actual. El GPS puede estar desactivado o no hay senal suficiente.",
                latitude = null,
                longitude = null
            )
        }

        Log.d(TAG, "Location obtained: ${location.latitude}, ${location.longitude}")

        val content = buildString {
            appendLine("Ubicacion actual del usuario:")
            appendLine("Coordenadas: ${location.latitude}, ${location.longitude}")
            if (location.address != null) appendLine("Direccion: ${location.address}")
            if (location.city != null) appendLine("Ciudad: ${location.city}")
            if (location.country != null) appendLine("Pais: ${location.country}")
            appendLine()
            appendLine("Usa estas coordenadas para busquedas cercanas. Formato para Google Maps: ${location.latitude},${location.longitude}")
        }

        return LocationToolResult(
            toolCallId = toolCallId,
            success = true,
            content = content.trim(),
            latitude = location.latitude,
            longitude = location.longitude
        )
    }
}

data class LocationToolResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String,
    val latitude: Double?,
    val longitude: Double?
)
