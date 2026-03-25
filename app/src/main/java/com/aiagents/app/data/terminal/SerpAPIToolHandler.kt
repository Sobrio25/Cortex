package com.aiagents.app.data.terminal

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class SerpAPISearchResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

@Singleton
class SerpAPIToolHandler @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "SerpAPIToolHandler"
        const val TOOL_NAME = "serpapi_search"
        private const val API_URL = "https://serpapi.com/search.json"

        fun getToolDefinitionsJson(): List<Map<String, Any>> {
            return listOf(
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TOOL_NAME,
                        "description" to """Busqueda multi-motor via SerpAPI. Usa el parametro 'engine' para elegir el motor:
- 'google' (default): busqueda web con snippets enriquecidos
- 'google_maps': buscar lugares, restaurantes, negocios, puntos de interes. Usa 'location' para centrar la busqueda (ej: 'Mexico City, Mexico'). Ideal para "restaurantes cerca de", "farmacias en zona X", etc.
- 'google_flights': buscar vuelos. El query debe ser tipo "flights from CDMX to NYC". Devuelve precios, aerolineas, escalas y duracion.
- 'google_hotels': buscar hoteles. Usa 'location' para la ciudad. Devuelve precios, calificaciones y amenidades.
- 'youtube': buscar videos en YouTube
- 'google_news': noticias recientes
- 'google_images': buscar imagenes
- 'google_shopping': buscar productos con precios
PREFIERE esta herramienta sobre brave_web_search para busquedas especializadas (mapas, vuelos, hoteles, videos, noticias, imagenes, shopping).""",
                        "parameters" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "query" to mapOf(
                                    "type" to "string",
                                    "description" to "La consulta de busqueda. Para google_flights usa formato 'flights from [origen] to [destino]'. Para google_maps incluye el tipo de lugar."
                                ),
                                "engine" to mapOf(
                                    "type" to "string",
                                    "description" to "Motor de busqueda. Opciones: google, google_maps, google_flights, google_hotels, youtube, google_news, google_images, google_shopping, bing, duckduckgo",
                                    "enum" to listOf("google", "google_maps", "google_flights", "google_hotels", "youtube", "google_news", "google_images", "google_shopping", "bing", "duckduckgo")
                                ),
                                "location" to mapOf(
                                    "type" to "string",
                                    "description" to "Ubicacion para contextualizar la busqueda (ej: 'Mexico City, Mexico', 'New York, USA'). Especialmente util para google_maps, google_hotels y google_shopping. Si tienes coordenadas del usuario, usa primero get_user_location y luego indica la ciudad aqui."
                                ),
                                "num" to mapOf(
                                    "type" to "integer",
                                    "description" to "Numero de resultados a devolver (por defecto 5, max 20)"
                                )
                            ),
                            "required" to listOf("query")
                        )
                    )
                )
            )
        }
    }

    suspend fun executeTool(
        toolCallId: String,
        arguments: String,
        apiKey: String
    ): SerpAPISearchResult {
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            val query = args.get("query")?.asString
                ?: return SerpAPISearchResult(toolCallId, false, "Error: parámetro 'query' requerido")
            val engine = args.get("engine")?.asString ?: "google"
            val location = args.get("location")?.asString
            val num = args.get("num")?.asInt?.coerceIn(1, 20) ?: 5

            Log.d(TAG, "Ejecutando SerpAPI Search: query='$query', engine='$engine', location=$location, num=$num")

            // YouTube usa "search_query", los demás motores usan "q"
            val queryParam = if (engine == "youtube") "search_query" else "q"

            // Para google_maps, SerpAPI no soporta el parámetro 'location' (solo 'll' con coordenadas).
            // Si location es texto, lo concatenamos al query para que la búsqueda incluya el contexto geográfico.
            val effectiveQuery = if (engine == "google_maps" && !location.isNullOrBlank() &&
                !location.matches(Regex("""^-?\d+\.?\d*,-?\d+\.?\d*$"""))) {
                "$query in $location"
            } else {
                query
            }

            val url = buildString {
                append("$API_URL?")
                append("$queryParam=${java.net.URLEncoder.encode(effectiveQuery, "UTF-8")}")
                append("&engine=${java.net.URLEncoder.encode(engine, "UTF-8")}")
                if (!location.isNullOrBlank()) {
                    if (engine == "google_maps" && location.matches(Regex("""^-?\d+\.?\d*,-?\d+\.?\d*$"""))) {
                        // google_maps acepta 'll' para coordenadas
                        val parts = location.split(",")
                        append("&ll=@${parts[0]},${parts[1]},15.1z")
                    } else if (engine != "google_maps") {
                        // 'location' solo es válido para otros engines (google, google_news, etc.)
                        append("&location=${java.net.URLEncoder.encode(location, "UTF-8")}")
                    }
                }
                if (engine != "google_flights" && engine != "google_hotels") {
                    append("&num=$num")
                }
                append("&api_key=${java.net.URLEncoder.encode(apiKey, "UTF-8")}")
                // Idioma español por defecto para mejores resultados locales
                if (engine in listOf("google", "google_maps", "google_news", "google_shopping", "google_hotels")) {
                    append("&hl=es")
                }
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .build()

            val (responseCode, body) = withContext(Dispatchers.IO) {
                val resp = okHttpClient.newCall(request).execute()
                resp.code to (resp.body?.string() ?: "")
            }

            if (responseCode !in 200..299) {
                Log.e(TAG, "SerpAPI error: $responseCode - $body")
                return SerpAPISearchResult(toolCallId, false, "Error HTTP $responseCode al buscar en SerpAPI")
            }

            val json = JsonParser.parseString(body).asJsonObject

            val formatted = when (engine) {
                "youtube" -> formatYouTubeResults(query, json)
                "google_news" -> formatNewsResults(query, json)
                "google_images" -> formatImageResults(query, json)
                "google_maps" -> formatMapsResults(query, json)
                "google_flights" -> formatFlightsResults(query, json)
                "google_hotels" -> formatHotelsResults(query, json)
                "google_shopping" -> formatShoppingResults(query, json)
                else -> formatGoogleResults(query, json)
            }

            SerpAPISearchResult(toolCallId, true, formatted)
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando SerpAPI Search", e)
            SerpAPISearchResult(toolCallId, false, "Error al buscar: ${e.message}")
        }
    }

    private fun formatGoogleResults(query: String, json: com.google.gson.JsonObject): String {
        val results = json.getAsJsonArray("organic_results")
        if (results == null || results.size() == 0) {
            return "No se encontraron resultados para: \"$query\""
        }

        return buildString {
            appendLine("Resultados de búsqueda para: \"$query\"")
            appendLine()
            results.forEachIndexed { index, item ->
                val r = item.asJsonObject
                val title = r.get("title")?.asString ?: "(sin título)"
                val link = r.get("link")?.asString ?: ""
                val snippet = r.get("snippet")?.asString ?: ""
                appendLine("${index + 1}. **$title**")
                if (link.isNotBlank()) appendLine("   URL: $link")
                if (snippet.isNotBlank()) appendLine("   $snippet")
                appendLine()
            }
        }.trim()
    }

    private fun formatYouTubeResults(query: String, json: com.google.gson.JsonObject): String {
        val results = json.getAsJsonArray("video_results")
        if (results == null || results.size() == 0) {
            return "No se encontraron videos para: \"$query\""
        }

        return buildString {
            appendLine("Videos de YouTube para: \"$query\"")
            appendLine()
            results.forEachIndexed { index, item ->
                val r = item.asJsonObject
                val title = r.get("title")?.asString ?: "(sin título)"
                val link = r.get("link")?.asString ?: ""
                val channel = r.get("channel")?.asJsonObject?.get("name")?.asString ?: ""
                val length = r.get("length")?.asString ?: ""
                appendLine("${index + 1}. **$title**")
                if (channel.isNotBlank()) appendLine("   Canal: $channel")
                if (length.isNotBlank()) appendLine("   Duración: $length")
                if (link.isNotBlank()) appendLine("   URL: $link")
                appendLine()
            }
        }.trim()
    }

    private fun formatImageResults(query: String, json: com.google.gson.JsonObject): String {
        val results = json.getAsJsonArray("images_results")
        if (results == null || results.size() == 0) {
            return "No se encontraron imágenes para: \"$query\""
        }

        return buildString {
            appendLine("Imágenes encontradas para: \"$query\"")
            appendLine()
            appendLine("IMPORTANTE: En tu respuesta, muestra las imágenes usando EXACTAMENTE el formato ![titulo](url) para cada imagen. NO uses links de texto ni formato [titulo](url) sin el '!'. Usa el formato de imagen markdown.")
            appendLine()
            results.forEachIndexed { index, item ->
                val r = item.asJsonObject
                val title = r.get("title")?.asString ?: "imagen ${index + 1}"
                val original = r.get("original")?.asString ?: ""
                val thumbnail = r.get("thumbnail")?.asString ?: ""
                val source = r.get("source")?.asString ?: ""
                val imageUrl = original.ifBlank { thumbnail }
                if (imageUrl.isNotBlank()) {
                    appendLine("${index + 1}. ![${title}](${imageUrl})")
                    if (source.isNotBlank()) appendLine("   Fuente: $source")
                    appendLine()
                }
            }
        }.trim()
    }

    private fun formatNewsResults(query: String, json: com.google.gson.JsonObject): String {
        val results = json.getAsJsonArray("news_results")
        if (results == null || results.size() == 0) {
            return "No se encontraron noticias para: \"$query\""
        }

        return buildString {
            appendLine("Noticias para: \"$query\"")
            appendLine()
            results.forEachIndexed { index, item ->
                val r = item.asJsonObject
                val title = r.get("title")?.asString ?: "(sin titulo)"
                val link = r.get("link")?.asString ?: ""
                val source = r.get("source")?.asString ?: ""
                val date = r.get("date")?.asString ?: ""
                val snippet = r.get("snippet")?.asString ?: ""
                appendLine("${index + 1}. **$title**")
                if (source.isNotBlank()) appendLine("   Fuente: $source")
                if (date.isNotBlank()) appendLine("   Fecha: $date")
                if (snippet.isNotBlank()) appendLine("   $snippet")
                if (link.isNotBlank()) appendLine("   URL: $link")
                appendLine()
            }
        }.trim()
    }

    private fun formatMapsResults(query: String, json: com.google.gson.JsonObject): String {
        val results = json.getAsJsonArray("local_results")
        if (results == null || results.size() == 0) {
            return "No se encontraron lugares para: \"$query\""
        }

        return buildString {
            appendLine("Lugares encontrados para: \"$query\"")
            appendLine()
            results.take(10).forEachIndexed { index, item ->
                val r = item.asJsonObject
                val title = r.get("title")?.asString ?: "(sin nombre)"
                val address = r.get("address")?.asString ?: ""
                val rating = r.get("rating")?.asDouble
                val reviews = r.get("reviews")?.asInt
                val price = r.get("price")?.asString ?: ""
                val type = r.get("type")?.asString ?: ""
                val phone = r.get("phone")?.asString ?: ""
                val hours = r.get("hours")?.asString
                    ?: r.get("operating_hours")?.asJsonObject?.entrySet()?.firstOrNull()?.let {
                        "${it.key}: ${it.value.asString}"
                    } ?: ""
                val gps = r.getAsJsonObject("gps_coordinates")
                val lat = gps?.get("latitude")?.asDouble
                val lng = gps?.get("longitude")?.asDouble

                appendLine("${index + 1}. **$title**")
                if (type.isNotBlank()) appendLine("   Tipo: $type")
                if (address.isNotBlank()) appendLine("   Direccion: $address")
                if (rating != null) {
                    val reviewsStr = if (reviews != null) " ($reviews resenas)" else ""
                    appendLine("   Calificacion: $rating/5$reviewsStr")
                }
                if (price.isNotBlank()) appendLine("   Precio: $price")
                if (phone.isNotBlank()) appendLine("   Telefono: $phone")
                if (hours.isNotBlank()) appendLine("   Horario: $hours")
                if (lat != null && lng != null) appendLine("   Coordenadas: $lat, $lng")
                appendLine()
            }
            if (results.size() > 10) {
                appendLine("... y ${results.size() - 10} lugar(es) mas")
            }
        }.trim()
    }

    private fun formatFlightsResults(query: String, json: com.google.gson.JsonObject): String {
        // SerpAPI google_flights returns best_flights and other_flights arrays
        val bestFlights = json.getAsJsonArray("best_flights")
        val otherFlights = json.getAsJsonArray("other_flights")

        if ((bestFlights == null || bestFlights.size() == 0) && (otherFlights == null || otherFlights.size() == 0)) {
            return "No se encontraron vuelos para: \"$query\""
        }

        return buildString {
            appendLine("Vuelos encontrados para: \"$query\"")
            appendLine()

            fun formatFlightList(flights: com.google.gson.JsonArray, label: String) {
                appendLine("## $label")
                appendLine()
                flights.take(5).forEachIndexed { index, item ->
                    val flight = item.asJsonObject
                    val price = flight.get("price")?.asInt
                    val totalDuration = flight.get("total_duration")?.asInt
                    val stops = flight.get("layovers")?.asJsonArray?.size() ?: 0
                    val legs = flight.getAsJsonArray("flights")

                    appendLine("${index + 1}. ${if (price != null) "$$price USD" else "Precio no disponible"}")
                    if (totalDuration != null) {
                        val hours = totalDuration / 60
                        val mins = totalDuration % 60
                        appendLine("   Duracion total: ${hours}h ${mins}m | ${if (stops == 0) "Directo" else "$stops escala(s)"}")
                    }

                    legs?.forEach { legItem ->
                        val leg = legItem.asJsonObject
                        val airline = leg.get("airline")?.asString ?: ""
                        val flightNumber = leg.get("flight_number")?.asString ?: ""
                        val departure = leg.getAsJsonObject("departure_airport")
                        val arrival = leg.getAsJsonObject("arrival_airport")
                        val depName = departure?.get("name")?.asString ?: ""
                        val depId = departure?.get("id")?.asString ?: ""
                        val depTime = departure?.get("time")?.asString ?: ""
                        val arrName = arrival?.get("name")?.asString ?: ""
                        val arrId = arrival?.get("id")?.asString ?: ""
                        val arrTime = arrival?.get("time")?.asString ?: ""

                        appendLine("   $airline $flightNumber: $depId ($depTime) -> $arrId ($arrTime)")
                    }
                    appendLine()
                }
            }

            if (bestFlights != null && bestFlights.size() > 0) {
                formatFlightList(bestFlights, "Mejores vuelos")
            }
            if (otherFlights != null && otherFlights.size() > 0) {
                formatFlightList(otherFlights, "Otros vuelos")
            }
        }.trim()
    }

    private fun formatHotelsResults(query: String, json: com.google.gson.JsonObject): String {
        val results = json.getAsJsonArray("properties")
        if (results == null || results.size() == 0) {
            return "No se encontraron hoteles para: \"$query\""
        }

        return buildString {
            appendLine("Hoteles encontrados para: \"$query\"")
            appendLine()
            results.take(10).forEachIndexed { index, item ->
                val r = item.asJsonObject
                val name = r.get("name")?.asString ?: "(sin nombre)"
                val rate = r.get("rate_per_night")?.asJsonObject?.get("lowest")?.asString
                val rating = r.get("overall_rating")?.asDouble
                val reviews = r.get("reviews")?.asInt
                val stars = r.get("hotel_class")?.asString ?: ""
                val checkIn = r.get("check_in_time")?.asString ?: ""
                val checkOut = r.get("check_out_time")?.asString ?: ""
                val amenities = r.getAsJsonArray("amenities")
                    ?.take(5)?.joinToString(", ") { it.asString } ?: ""
                val link = r.get("link")?.asString ?: ""

                appendLine("${index + 1}. **$name** $stars")
                if (rate != null) appendLine("   Precio: $rate/noche")
                if (rating != null) {
                    val reviewsStr = if (reviews != null) " ($reviews resenas)" else ""
                    appendLine("   Calificacion: $rating/5$reviewsStr")
                }
                if (checkIn.isNotBlank()) appendLine("   Check-in: $checkIn | Check-out: $checkOut")
                if (amenities.isNotBlank()) appendLine("   Amenidades: $amenities")
                if (link.isNotBlank()) appendLine("   URL: $link")
                appendLine()
            }
        }.trim()
    }

    private fun formatShoppingResults(query: String, json: com.google.gson.JsonObject): String {
        val results = json.getAsJsonArray("shopping_results")
        if (results == null || results.size() == 0) {
            return "No se encontraron productos para: \"$query\""
        }

        return buildString {
            appendLine("Productos encontrados para: \"$query\"")
            appendLine()
            results.take(10).forEachIndexed { index, item ->
                val r = item.asJsonObject
                val title = r.get("title")?.asString ?: "(sin titulo)"
                val price = r.get("price")?.asString ?: r.get("extracted_price")?.asString ?: ""
                val source = r.get("source")?.asString ?: ""
                val rating = r.get("rating")?.asDouble
                val reviews = r.get("reviews")?.asInt
                val link = r.get("link")?.asString ?: ""
                val thumbnail = r.get("thumbnail")?.asString ?: ""

                appendLine("${index + 1}. **$title**")
                if (price.isNotBlank()) appendLine("   Precio: $price")
                if (source.isNotBlank()) appendLine("   Tienda: $source")
                if (rating != null) {
                    val reviewsStr = if (reviews != null) " ($reviews resenas)" else ""
                    appendLine("   Calificacion: $rating/5$reviewsStr")
                }
                if (link.isNotBlank()) appendLine("   URL: $link")
                if (thumbnail.isNotBlank()) appendLine("   ![${title}](${thumbnail})")
                appendLine()
            }
        }.trim()
    }
}
