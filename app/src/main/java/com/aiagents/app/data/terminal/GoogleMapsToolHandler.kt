package com.aiagents.app.data.terminal

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class GoogleMapsResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

@Singleton
class GoogleMapsToolHandler @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "GoogleMapsToolHandler"
        const val TOOL_NAME_GEOCODE = "google_maps_geocode"
        const val TOOL_NAME_PLACES = "google_maps_places"
        const val TOOL_NAME_DIRECTIONS = "google_maps_directions"
        const val TOOL_NAME_DISTANCE = "google_maps_distance"
        const val TOOL_NAME_ELEVATION = "google_maps_elevation"
        
        private const val GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json"
        private const val PLACES_URL = "https://maps.googleapis.com/maps/api/place/textsearch/json"
        private const val DIRECTIONS_URL = "https://maps.googleapis.com/maps/api/directions/json"
        private const val DISTANCE_URL = "https://maps.googleapis.com/maps/api/distancematrix/json"
        private const val ELEVATION_URL = "https://maps.googleapis.com/maps/api/elevation/json"

        fun getToolDefinitionsJson(): List<Map<String, Any>> {
            return listOf(
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_NAME_GEOCODE,
                        "description" to "Convierte una dirección o lugar a coordenadas geográficas (latitud/longitud) usando Google Maps Geocoding API. Úsala cuando necesites obtener la ubicación exacta de una dirección, ciudad, país o punto de interés.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "address" to mapOf(
                                    "type" to "string",
                                    "description" to "La dirección, lugar o nombre de la ubicación a geocodificar. Ejemplos: '1600 Amphitheatre Parkway, Mountain View, CA', 'Torre Eiffel, París', 'Ciudad de México'"
                                )
                            ),
                            "required" to listOf("address")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_NAME_PLACES,
                        "description" to "Busca lugares, negocios o puntos de interés usando Google Maps Places API. Úsala cuando necesites encontrar restaurantes, hoteles, tiendas, atracciones turísticas o cualquier tipo de establecimiento cercano a una ubicación.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "query" to mapOf(
                                    "type" to "string",
                                    "description" to "La consulta de búsqueda. Ejemplos: 'restaurantes italianos en Roma', 'hoteles cerca de la playa en Cancún', 'museos en Madrid'"
                                ),
                                "location" to mapOf(
                                    "type" to "string",
                                    "description" to "Ubicación opcional para centrar la búsqueda (latitud,longitud o dirección). Ejemplo: '40.4168,-3.7038' o 'Madrid, España'"
                                ),
                                "radius" to mapOf(
                                    "type" to "integer",
                                    "description" to "Radio de búsqueda en metros (máximo 50000). Por defecto 5000."
                                )
                            ),
                            "required" to listOf("query")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_NAME_DIRECTIONS,
                        "description" to "Obtiene direcciones de rutas entre dos puntos usando Google Maps Directions API. Úsala cuando necesites indicaciones para llegar de un lugar a otro, incluyendo distancia, tiempo estimado y pasos detallados.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "origin" to mapOf(
                                    "type" to "string",
                                    "description" to "El punto de origen. Puede ser una dirección, coordenadas (lat,lng) o el nombre de un lugar. Ejemplos: 'Aeropuerto CDMX', '19.4363,-99.0721', 'Hotel Reforma, Ciudad de México'"
                                ),
                                "destination" to mapOf(
                                    "type" to "string",
                                    "description" to "El destino. Puede ser una dirección, coordenadas (lat,lng) o el nombre de un lugar."
                                ),
                                "mode" to mapOf(
                                    "type" to "string",
                                    "description" to "Modo de transporte: driving (auto, por defecto), walking (caminando), bicycling (bicicleta), transit (transporte público)",
                                    "enum" to listOf("driving", "walking", "bicycling", "transit")
                                ),
                                "avoid_tolls" to mapOf(
                                    "type" to "boolean",
                                    "description" to "Si es true, evita rutas con peajes"
                                ),
                                "avoid_highways" to mapOf(
                                    "type" to "boolean",
                                    "description" to "Si es true, evita autopistas"
                                )
                            ),
                            "required" to listOf("origin", "destination")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_NAME_DISTANCE,
                        "description" to "Calcula la distancia y tiempo de viaje entre múltiples puntos de origen y destino usando Google Maps Distance Matrix API. Úsala cuando necesites comparar distancias entre varias ubicaciones.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "origins" to mapOf(
                                    "type" to "string",
                                    "description" to "Orígenes separados por |. Ejemplos: 'CDMX|Guadalajara', '19.4363,-99.0721|20.6597,-103.3496'"
                                ),
                                "destinations" to mapOf(
                                    "type" to "string",
                                    "description" to "Destinos separados por |. Ejemplos: 'Monterrey|Puebla', '25.6866,-100.3161|19.0414,-98.2063'"
                                ),
                                "mode" to mapOf(
                                    "type" to "string",
                                    "description" to "Modo de transporte: driving (por defecto), walking, bicycling, transit",
                                    "enum" to listOf("driving", "walking", "bicycling", "transit")
                                )
                            ),
                            "required" to listOf("origins", "destinations")
                        )
                    )
                ),
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_NAME_ELEVATION,
                        "description" to "Obtiene la elevación (altura sobre el nivel del mar) de coordenadas geográficas específicas usando Google Maps Elevation API. Úsala cuando necesites saber la altitud de una ubicación.",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "locations" to mapOf(
                                    "type" to "string",
                                    "description" to "Coordenadas separadas por | en formato latitud,longitud. Ejemplos: '19.4326,-99.1332' (CDMX), '27.9881,86.9250|28.0012,86.9284' (múltiples puntos en el Everest)"
                                )
                            ),
                            "required" to listOf("locations")
                        )
                    )
                )
            )
        }
    }

    suspend fun executeTool(
        toolCallId: String,
        toolName: String,
        arguments: String,
        apiKey: String
    ): GoogleMapsResult {
        return try {
            when (toolName) {
                TOOL_NAME_GEOCODE -> geocode(toolCallId, arguments, apiKey)
                TOOL_NAME_PLACES -> placesSearch(toolCallId, arguments, apiKey)
                TOOL_NAME_DIRECTIONS -> directions(toolCallId, arguments, apiKey)
                TOOL_NAME_DISTANCE -> distanceMatrix(toolCallId, arguments, apiKey)
                TOOL_NAME_ELEVATION -> elevation(toolCallId, arguments, apiKey)
                else -> GoogleMapsResult(toolCallId, false, "Herramienta desconocida: $toolName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando Google Maps tool: $toolName", e)
            GoogleMapsResult(toolCallId, false, "Error al ejecutar $toolName: ${e.message}")
        }
    }

    private suspend fun geocode(
        toolCallId: String,
        arguments: String,
        apiKey: String
    ): GoogleMapsResult {
        val args = JsonParser.parseString(arguments).asJsonObject
        val address = args.get("address")?.asString
            ?: return GoogleMapsResult(toolCallId, false, "Error: parámetro 'address' requerido")

        Log.d(TAG, "Geocoding address: $address")

        val url = "$GEOCODE_URL?address=${java.net.URLEncoder.encode(address, "UTF-8")}&key=$apiKey"
        
        val (success, body) = makeRequest(url)
        
        if (!success) {
            return GoogleMapsResult(toolCallId, false, "Error HTTP al geocodificar: $body")
        }

        val json = JsonParser.parseString(body).asJsonObject
        val status = json.get("status")?.asString ?: "UNKNOWN"
        
        if (status != "OK") {
            return GoogleMapsResult(toolCallId, false, "Error de Google Maps: $status")
        }

        val results = json.getAsJsonArray("results")
        if (results == null || results.size() == 0) {
            return GoogleMapsResult(toolCallId, true, "No se encontraron resultados para: \"$address\"")
        }

        val formatted = buildString {
            appendLine("Resultados de geocodificación para: \"$address\"")
            appendLine()
            
            results.take(3).forEachIndexed { index, item ->
                val r = item.asJsonObject
                val formattedAddress = r.get("formatted_address")?.asString ?: "(sin dirección)"
                val location = r.getAsJsonObject("geometry")?.getAsJsonObject("location")
                val lat = location?.get("lat")?.asDouble
                val lng = location?.get("lng")?.asDouble
                val types = r.getAsJsonArray("types")?.map { it.asString }?.joinToString(", ") ?: ""
                
                appendLine("${index + 1}. $formattedAddress")
                appendLine("   Coordenadas: $lat, $lng")
                if (types.isNotBlank()) appendLine("   Tipo: $types")
                appendLine()
            }
            
            if (results.size() > 3) {
                appendLine("... y ${results.size() - 3} resultado(s) más")
            }
        }

        return GoogleMapsResult(toolCallId, true, formatted.trim())
    }

    private suspend fun placesSearch(
        toolCallId: String,
        arguments: String,
        apiKey: String
    ): GoogleMapsResult {
        val args = JsonParser.parseString(arguments).asJsonObject
        val query = args.get("query")?.asString
            ?: return GoogleMapsResult(toolCallId, false, "Error: parámetro 'query' requerido")
        val location = args.get("location")?.asString
        val radius = args.get("radius")?.asInt?.coerceIn(1, 50000) ?: 5000

        Log.d(TAG, "Places search: query='$query', location=$location, radius=$radius")

        val urlBuilder = StringBuilder("$PLACES_URL?query=${java.net.URLEncoder.encode(query, "UTF-8")}&radius=$radius&key=$apiKey")
        if (!location.isNullOrBlank()) {
            urlBuilder.append("&location=${java.net.URLEncoder.encode(location, "UTF-8")}")
        }

        val (success, body) = makeRequest(urlBuilder.toString())
        
        if (!success) {
            return GoogleMapsResult(toolCallId, false, "Error HTTP al buscar lugares: $body")
        }

        val json = JsonParser.parseString(body).asJsonObject
        val status = json.get("status")?.asString ?: "UNKNOWN"
        
        if (status != "OK" && status != "ZERO_RESULTS") {
            return GoogleMapsResult(toolCallId, false, "Error de Google Maps: $status")
        }

        val results = json.getAsJsonArray("results")
        if (results == null || results.size() == 0) {
            return GoogleMapsResult(toolCallId, true, "No se encontraron lugares para: \"$query\"")
        }

        val formatted = buildString {
            appendLine("Lugares encontrados para: \"$query\"")
            appendLine()
            
            results.take(10).forEachIndexed { index, item ->
                val r = item.asJsonObject
                val name = r.get("name")?.asString ?: "(sin nombre)"
                val address = r.get("formatted_address")?.asString ?: "(sin dirección)"
                val rating = r.get("rating")?.asDouble
                val priceLevel = r.get("price_level")?.asInt
                val placeLocation = r.getAsJsonObject("geometry")?.getAsJsonObject("location")
                val lat = placeLocation?.get("lat")?.asDouble
                val lng = placeLocation?.get("lng")?.asDouble
                
                appendLine("${index + 1}. $name")
                appendLine("   Dirección: $address")
                if (rating != null) appendLine("   Calificación: $rating/5")
                if (priceLevel != null) appendLine("   Nivel de precios: ${"$".repeat(priceLevel)}")
                if (lat != null && lng != null) appendLine("   Coordenadas: $lat, $lng")
                appendLine()
            }
            
            if (results.size() > 10) {
                appendLine("... y ${results.size() - 10} lugar(es) más")
            }
        }

        return GoogleMapsResult(toolCallId, true, formatted.trim())
    }

    private suspend fun directions(
        toolCallId: String,
        arguments: String,
        apiKey: String
    ): GoogleMapsResult {
        val args = JsonParser.parseString(arguments).asJsonObject
        val origin = args.get("origin")?.asString
            ?: return GoogleMapsResult(toolCallId, false, "Error: parámetro 'origin' requerido")
        val destination = args.get("destination")?.asString
            ?: return GoogleMapsResult(toolCallId, false, "Error: parámetro 'destination' requerido")
        val mode = args.get("mode")?.asString ?: "driving"
        val avoidTolls = args.get("avoid_tolls")?.asBoolean ?: false
        val avoidHighways = args.get("avoid_highways")?.asBoolean ?: false

        Log.d(TAG, "Directions: from='$origin' to='$destination', mode=$mode")

        val urlBuilder = StringBuilder("$DIRECTIONS_URL?origin=${java.net.URLEncoder.encode(origin, "UTF-8")}&destination=${java.net.URLEncoder.encode(destination, "UTF-8")}&mode=$mode&key=$apiKey")
        
        val avoidParams = mutableListOf<String>()
        if (avoidTolls) avoidParams.add("tolls")
        if (avoidHighways) avoidParams.add("highways")
        if (avoidParams.isNotEmpty()) {
            urlBuilder.append("&avoid=${avoidParams.joinToString("|")}")
        }

        val (success, body) = makeRequest(urlBuilder.toString())
        
        if (!success) {
            return GoogleMapsResult(toolCallId, false, "Error HTTP al obtener direcciones: $body")
        }

        val json = JsonParser.parseString(body).asJsonObject
        val status = json.get("status")?.asString ?: "UNKNOWN"
        
        if (status != "OK") {
            return GoogleMapsResult(toolCallId, false, "Error de Google Maps: $status")
        }

        val routes = json.getAsJsonArray("routes")
        if (routes == null || routes.size() == 0) {
            return GoogleMapsResult(toolCallId, true, "No se encontraron rutas entre \"$origin\" y \"$destination\"")
        }

        val route = routes[0].asJsonObject
        val legs = route.getAsJsonArray("legs")
        val leg = legs?.get(0)?.asJsonObject
        
        val distance = leg?.getAsJsonObject("distance")?.get("text")?.asString
        val duration = leg?.getAsJsonObject("duration")?.get("text")?.asString
        val steps = leg?.getAsJsonArray("steps")

        val formatted = buildString {
            appendLine("Ruta de \"$origin\" a \"$destination\"")
            appendLine("Modo: ${modeToSpanish(mode)}")
            appendLine()
            
            if (distance != null && duration != null) {
                appendLine("📍 Distancia: $distance")
                appendLine("⏱️ Tiempo estimado: $duration")
                appendLine()
            }
            
            if (steps != null && steps.size() > 0) {
                appendLine("Instrucciones paso a paso:")
                appendLine()
                
                steps.forEachIndexed { index, stepItem ->
                    val step = stepItem.asJsonObject
                    val instruction = step.get("html_instructions")?.asString?.replace(Regex("<[^>]*>"), "") ?: ""
                    val stepDistance = step.getAsJsonObject("distance")?.get("text")?.asString ?: ""
                    
                    appendLine("${index + 1}. $instruction (${stepDistance})")
                }
            }
        }

        return GoogleMapsResult(toolCallId, true, formatted.trim())
    }

    private suspend fun distanceMatrix(
        toolCallId: String,
        arguments: String,
        apiKey: String
    ): GoogleMapsResult {
        val args = JsonParser.parseString(arguments).asJsonObject
        val origins = args.get("origins")?.asString
            ?: return GoogleMapsResult(toolCallId, false, "Error: parámetro 'origins' requerido")
        val destinations = args.get("destinations")?.asString
            ?: return GoogleMapsResult(toolCallId, false, "Error: parámetro 'destinations' requerido")
        val mode = args.get("mode")?.asString ?: "driving"

        Log.d(TAG, "Distance matrix: origins='$origins', destinations='$destinations', mode=$mode")

        val url = "$DISTANCE_URL?origins=${java.net.URLEncoder.encode(origins, "UTF-8")}&destinations=${java.net.URLEncoder.encode(destinations, "UTF-8")}&mode=$mode&key=$apiKey"

        val (success, body) = makeRequest(url)
        
        if (!success) {
            return GoogleMapsResult(toolCallId, false, "Error HTTP al calcular distancias: $body")
        }

        val json = JsonParser.parseString(body).asJsonObject
        val status = json.get("status")?.asString ?: "UNKNOWN"
        
        if (status != "OK") {
            return GoogleMapsResult(toolCallId, false, "Error de Google Maps: $status")
        }

        val originAddresses = json.getAsJsonArray("origin_addresses")
        val destinationAddresses = json.getAsJsonArray("destination_addresses")
        val rows = json.getAsJsonArray("rows")

        val formatted = buildString {
            appendLine("Matriz de distancias (${modeToSpanish(mode)})")
            appendLine()
            
            val originsList = originAddresses?.map { it.asString } ?: emptyList()
            val destinationsList = destinationAddresses?.map { it.asString } ?: emptyList()
            
            rows?.forEachIndexed { i, rowItem ->
                val row = rowItem.asJsonObject
                val elements = row.getAsJsonArray("elements")
                
                appendLine("Desde: ${originsList.getOrElse(i) { "Origen ${i + 1}" }}")
                
                elements?.forEachIndexed { j, elemItem ->
                    val elem = elemItem.asJsonObject
                    val elemStatus = elem.get("status")?.asString
                    
                    if (elemStatus == "OK") {
                        val distance = elem.getAsJsonObject("distance")?.get("text")?.asString
                        val duration = elem.getAsJsonObject("duration")?.get("text")?.asString
                        appendLine("  → ${destinationsList.getOrElse(j) { "Destino ${j + 1}" }}: $distance, $duration")
                    } else {
                        appendLine("  → ${destinationsList.getOrElse(j) { "Destino ${j + 1}" }}: No disponible ($elemStatus)")
                    }
                }
                appendLine()
            }
        }

        return GoogleMapsResult(toolCallId, true, formatted.trim())
    }

    private suspend fun elevation(
        toolCallId: String,
        arguments: String,
        apiKey: String
    ): GoogleMapsResult {
        val args = JsonParser.parseString(arguments).asJsonObject
        val locations = args.get("locations")?.asString
            ?: return GoogleMapsResult(toolCallId, false, "Error: parámetro 'locations' requerido")

        Log.d(TAG, "Elevation: locations='$locations'")

        val url = "$ELEVATION_URL?locations=${java.net.URLEncoder.encode(locations, "UTF-8")}&key=$apiKey"

        val (success, body) = makeRequest(url)
        
        if (!success) {
            return GoogleMapsResult(toolCallId, false, "Error HTTP al obtener elevación: $body")
        }

        val json = JsonParser.parseString(body).asJsonObject
        val status = json.get("status")?.asString ?: "UNKNOWN"
        
        if (status != "OK") {
            return GoogleMapsResult(toolCallId, false, "Error de Google Maps: $status")
        }

        val results = json.getAsJsonArray("results")
        if (results == null || results.size() == 0) {
            return GoogleMapsResult(toolCallId, true, "No se encontraron resultados de elevación")
        }

        val formatted = buildString {
            appendLine("Elevación sobre el nivel del mar")
            appendLine()
            
            results.forEachIndexed { index, item ->
                val r = item.asJsonObject
                val elevation = r.get("elevation")?.asDouble
                val location = r.getAsJsonObject("location")
                val lat = location?.get("lat")?.asDouble
                val lng = location?.get("lng")?.asDouble
                val resolution = r.get("resolution")?.asDouble
                
                appendLine("${index + 1}. Ubicación: $lat, $lng")
                if (elevation != null) {
                    val elevationM = String.format("%.2f", elevation)
                    val elevationFt = String.format("%.2f", elevation * 3.28084)
                    appendLine("   Elevación: ${elevationM}m (${elevationFt}ft)")
                }
                if (resolution != null) {
                    appendLine("   Resolución: ${String.format("%.2f", resolution)}m")
                }
                appendLine()
            }
        }

        return GoogleMapsResult(toolCallId, true, formatted.trim())
    }

    private suspend fun makeRequest(url: String): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Accept", "application/json")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""
                
                (response.code in 200..299) to body
            } catch (e: Exception) {
                Log.e(TAG, "Error en la petición HTTP", e)
                false to e.message.toString()
            }
        }
    }

    private fun modeToSpanish(mode: String): String = when (mode) {
        "driving" -> "en auto"
        "walking" -> "caminando"
        "bicycling" -> "en bicicleta"
        "transit" -> "transporte público"
        else -> mode
    }
}
