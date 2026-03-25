package com.aiagents.app.data.terminal

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class WeatherResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

@Singleton
class WeatherToolHandler @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "WeatherToolHandler"
        const val TOOL_NAME_CURRENT = "weather_current"
        const val TOOL_NAME_FORECAST = "weather_forecast"
        const val TOOL_NAME_AIR_QUALITY = "weather_air_quality"

        val ALL_TOOL_NAMES = setOf(TOOL_NAME_CURRENT, TOOL_NAME_FORECAST, TOOL_NAME_AIR_QUALITY)

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_NAME_CURRENT,
                    "description" to "Get current weather conditions for a location. Use for: current temperature, humidity, wind, visibility, feels-like temperature, sunrise/sunset times. If the user doesn't specify a city, use 'get_user_location' first to determine their location.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "location" to mapOf(
                                "type" to "string",
                                "description" to "City name (e.g., 'Mexico City', 'Madrid, Spain', 'New York, USA'). Include country for better accuracy."
                            ),
                            "units" to mapOf(
                                "type" to "string",
                                "description" to "Temperature units",
                                "enum" to listOf("metric", "imperial", "kelvin")
                            )
                        ),
                        "required" to listOf("location")
                    )
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_NAME_FORECAST,
                    "description" to "Get weather forecast for the next 5 days with 3-hour intervals. Use for: weather prediction, planning trips, checking if it will rain tomorrow, temperature trends. If the user doesn't specify a city, use 'get_user_location' first to determine their location.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "location" to mapOf(
                                "type" to "string",
                                "description" to "City name (e.g., 'Mexico City', 'Madrid, Spain')"
                            ),
                            "days" to mapOf(
                                "type" to "integer",
                                "description" to "Number of days to forecast (default 3, max 5)"
                            ),
                            "units" to mapOf(
                                "type" to "string",
                                "description" to "Temperature units",
                                "enum" to listOf("metric", "imperial", "kelvin")
                            )
                        ),
                        "required" to listOf("location")
                    )
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_NAME_AIR_QUALITY,
                    "description" to "Get current air quality index (AQI) and pollutant levels. Use for: checking pollution levels, health recommendations, PM2.5, PM10, ozone levels. If the user doesn't specify a city, use 'get_user_location' first to determine their location.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "location" to mapOf(
                                "type" to "string",
                                "description" to "City name (e.g., 'Mexico City', 'Beijing')"
                            )
                        ),
                        "required" to listOf("location")
                    )
                )
            )
        )
    }

    private val baseUrl = "https://api.openweathermap.org/data/2.5"
    private val geoUrl = "https://api.openweathermap.org/geo/1.0"

    suspend fun executeCurrentWeather(
        toolCallId: String,
        arguments: String,
        apiKey: String
    ): WeatherResult {
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            val location = args.get("location")?.asString
                ?: return WeatherResult(toolCallId, false, "Error: parámetro 'location' requerido")
            val units = args.get("units")?.asString ?: "metric"

            Log.d(TAG, "Obteniendo clima actual: location='$location', units='$units'")

            val (lat, lon, cityName, country) = geocodeLocation(location, apiKey)
                ?: return WeatherResult(toolCallId, false, "No se pudo encontrar la ubicación: '$location'")

            val unitSymbol = when (units) {
                "imperial" -> "°F"
                "kelvin" -> "K"
                else -> "°C"
            }
            val speedUnit = if (units == "imperial") "mph" else "m/s"

            val url = "$baseUrl/weather?lat=$lat&lon=$lon&appid=$apiKey&units=$units&lang=es"

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .build()

            val (responseCode, body) = withContext(Dispatchers.IO) {
                val resp = okHttpClient.newCall(request).execute()
                resp.code to (resp.body?.string() ?: "")
            }

            if (responseCode !in 200..299) {
                Log.e(TAG, "Weather API error: $responseCode")
                return WeatherResult(toolCallId, false, "Error HTTP $responseCode al obtener el clima")
            }

            val json = JsonParser.parseString(body).asJsonObject

            val weather = json.getAsJsonArray("weather")?.get(0)?.asJsonObject
            val main = json.getAsJsonObject("main")
            val wind = json.getAsJsonObject("wind")
            val sys = json.getAsJsonObject("sys")

            val description = weather?.get("description")?.asString?.replaceFirstChar { it.uppercase() } ?: ""
            val conditionId = weather?.get("id")?.asInt ?: 0
            val icon = weather?.get("icon")?.asString ?: "01d"
            val temp = main?.get("temp")?.asDouble
            val feelsLike = main?.get("feels_like")?.asDouble
            val humidity = main?.get("humidity")?.asInt
            val pressure = main?.get("pressure")?.asInt
            val windSpeed = wind?.get("speed")?.asDouble
            val windDeg = wind?.get("deg")?.asInt
            val visibility = json.get("visibility")?.asInt?.let { it / 1000.0 } // meters to km
            val clouds = json.getAsJsonObject("clouds")?.get("all")?.asInt

            val sunrise = sys?.get("sunrise")?.asLong?.let { formatTime(it * 1000) }
            val sunset = sys?.get("sunset")?.asLong?.let { formatTime(it * 1000) }

            val formatted = buildString {
                appendLine("🌤️ Clima actual en **$cityName**, $country")
                appendLine()
                appendLine("**$description**")
                appendLine()
                appendLine("🌡️ Temperatura: ${temp?.let { "%.1f".format(it) } ?: "N/A"}$unitSymbol")
                appendLine("🤔 Sensación térmica: ${feelsLike?.let { "%.1f".format(it) } ?: "N/A"}$unitSymbol")
                appendLine("💧 Humedad: ${humidity ?: "N/A"}%")
                if (pressure != null) appendLine("📊 Presión: $pressure hPa")
                if (windSpeed != null) {
                    val direction = windDeg?.let { getWindDirection(it) } ?: ""
                    appendLine("💨 Viento: %.1f $speedUnit $direction".format(windSpeed))
                }
                if (visibility != null) appendLine("👁️ Visibilidad: %.1f km".format(visibility))
                if (clouds != null) appendLine("☁️ Nubosidad: $clouds%")
                appendLine()
                if (sunrise != null && sunset != null) {
                    appendLine("🌅 Amanecer: $sunrise")
                    appendLine("🌇 Atardecer: $sunset")
                }
            }

            val weatherDataJson = JSONObject().apply {
                put("type", "current")
                put("city", cityName)
                put("country", country)
                put("conditionId", conditionId)
                put("icon", icon)
                put("description", description)
                put("temp", temp ?: 0.0)
                put("feelsLike", feelsLike ?: 0.0)
                put("humidity", humidity ?: 0)
                put("pressure", pressure ?: 0)
                put("windSpeed", windSpeed ?: 0.0)
                put("windDeg", windDeg ?: 0)
                put("visibility", visibility ?: 0.0)
                put("clouds", clouds ?: 0)
                put("sunrise", sunrise ?: "")
                put("sunset", sunset ?: "")
                put("unitSymbol", unitSymbol)
                put("speedUnit", speedUnit)
            }

            WeatherResult(toolCallId, true, formatted.trim() + "\n<!--WEATHER_DATA:$weatherDataJson-->")
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo clima", e)
            WeatherResult(toolCallId, false, "Error al obtener el clima: ${e.message}")
        }
    }

    suspend fun executeForecast(
        toolCallId: String,
        arguments: String,
        apiKey: String
    ): WeatherResult {
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            val location = args.get("location")?.asString
                ?: return WeatherResult(toolCallId, false, "Error: parámetro 'location' requerido")
            val days = args.get("days")?.asInt?.coerceIn(1, 5) ?: 3
            val units = args.get("units")?.asString ?: "metric"

            Log.d(TAG, "Obteniendo pronóstico: location='$location', days=$days")

            val (lat, lon, cityName, country) = geocodeLocation(location, apiKey)
                ?: return WeatherResult(toolCallId, false, "No se pudo encontrar la ubicación: '$location'")

            val unitSymbol = when (units) {
                "imperial" -> "°F"
                "kelvin" -> "K"
                else -> "°C"
            }

            val url = "$baseUrl/forecast?lat=$lat&lon=$lon&appid=$apiKey&units=$units&lang=es"

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .build()

            val (responseCode, body) = withContext(Dispatchers.IO) {
                val resp = okHttpClient.newCall(request).execute()
                resp.code to (resp.body?.string() ?: "")
            }

            if (responseCode !in 200..299) {
                return WeatherResult(toolCallId, false, "Error HTTP $responseCode al obtener el pronóstico")
            }

            val json = JsonParser.parseString(body).asJsonObject
            val list = json.getAsJsonArray("list") ?: return WeatherResult(toolCallId, false, "No hay datos de pronóstico")

            // Group by day
            val dailyForecasts = mutableMapOf<String, MutableList<ForecastItem>>()

            list.forEach { item ->
                val obj = item.asJsonObject
                val dt = obj.get("dt")?.asLong ?: return@forEach
                val date = formatDateShort(dt * 1000)
                val time = formatTime(dt * 1000)

                val main = obj.getAsJsonObject("main")
                val weather = obj.getAsJsonArray("weather")?.get(0)?.asJsonObject
                val wind = obj.getAsJsonObject("wind")

                val forecast = ForecastItem(
                    time = time,
                    temp = main?.get("temp")?.asDouble ?: 0.0,
                    feelsLike = main?.get("feels_like")?.asDouble ?: 0.0,
                    description = weather?.get("description")?.asString ?: "",
                    icon = weather?.get("icon")?.asString ?: "",
                    conditionId = weather?.get("id")?.asInt ?: 0,
                    humidity = main?.get("humidity")?.asInt ?: 0,
                    windSpeed = wind?.get("speed")?.asDouble ?: 0.0,
                    pop = obj.get("pop")?.asDouble?.let { (it * 100).toInt() } ?: 0 // probability of precipitation
                )

                dailyForecasts.getOrPut(date) { mutableListOf() }.add(forecast)
            }

            val formatted = buildString {
                appendLine("🌤️ Pronóstico del tiempo para **$cityName**, $country")
                appendLine()

                dailyForecasts.entries.take(days).forEach { (date, forecasts) ->
                    appendLine("**$date**")

                    // Calculate daily summary
                    val minTemp = forecasts.minOf { it.temp }
                    val maxTemp = forecasts.maxOf { it.temp }
                    val avgHumidity = forecasts.map { it.humidity }.average().toInt()
                    val maxPop = forecasts.maxOf { it.pop }

                    appendLine("  Temperatura: %.1f - %.1f $unitSymbol".format(minTemp, maxTemp))
                    appendLine("  Humedad promedio: $avgHumidity%")
                    if (maxPop > 0) {
                        appendLine("  🌧️ Prob. de lluvia máx: $maxPop%")
                    }

                    // Show key times
                    forecasts.filter { it.time in listOf("06:00", "12:00", "18:00", "21:00") }
                        .forEach { f ->
                            val emoji = getWeatherEmoji(f.description)
                            appendLine("  $emoji ${f.time}: %.1f$unitSymbol - ${f.description.replaceFirstChar { it.uppercase() }}".format(f.temp))
                        }
                    appendLine()
                }
            }

            val forecastDataJson = JSONObject().apply {
                put("type", "forecast")
                put("city", cityName)
                put("country", country)
                put("unitSymbol", unitSymbol)
                put("days", JSONArray().apply {
                    dailyForecasts.entries.take(days).forEach { (date, forecasts) ->
                        put(JSONObject().apply {
                            put("date", date)
                            put("minTemp", forecasts.minOf { it.temp })
                            put("maxTemp", forecasts.maxOf { it.temp })
                            put("avgHumidity", forecasts.map { it.humidity }.average().toInt())
                            put("maxPop", forecasts.maxOf { it.pop })
                            val dominant = forecasts.groupingBy { it.conditionId }.eachCount().maxByOrNull { it.value }
                            put("conditionId", dominant?.key ?: 800)
                            put("icon", forecasts.firstOrNull()?.icon ?: "01d")
                            put("description", forecasts.firstOrNull()?.description?.replaceFirstChar { it.uppercase() } ?: "")
                            put("entries", JSONArray().apply {
                                forecasts.forEach { f ->
                                    put(JSONObject().apply {
                                        put("time", f.time)
                                        put("temp", f.temp)
                                        put("conditionId", f.conditionId)
                                        put("icon", f.icon)
                                        put("description", f.description)
                                        put("pop", f.pop)
                                    })
                                }
                            })
                        })
                    }
                })
            }

            WeatherResult(toolCallId, true, formatted.trim() + "\n<!--WEATHER_DATA:$forecastDataJson-->")
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo pronóstico", e)
            WeatherResult(toolCallId, false, "Error al obtener el pronóstico: ${e.message}")
        }
    }

    suspend fun executeAirQuality(
        toolCallId: String,
        arguments: String,
        apiKey: String
    ): WeatherResult {
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            val location = args.get("location")?.asString
                ?: return WeatherResult(toolCallId, false, "Error: parámetro 'location' requerido")

            Log.d(TAG, "Obteniendo calidad del aire: location='$location'")

            val (lat, lon, cityName, country) = geocodeLocation(location, apiKey)
                ?: return WeatherResult(toolCallId, false, "No se pudo encontrar la ubicación: '$location'")

            val url = "https://api.openweathermap.org/data/2.5/air_pollution?lat=$lat&lon=$lon&appid=$apiKey"

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .build()

            val (responseCode, body) = withContext(Dispatchers.IO) {
                val resp = okHttpClient.newCall(request).execute()
                resp.code to (resp.body?.string() ?: "")
            }

            if (responseCode !in 200..299) {
                return WeatherResult(toolCallId, false, "Error HTTP $responseCode al obtener calidad del aire")
            }

            val json = JsonParser.parseString(body).asJsonObject
            val aqiData = json.getAsJsonArray("list")?.get(0)?.asJsonObject
                ?: return WeatherResult(toolCallId, false, "No hay datos de calidad del aire")

            val main = aqiData.getAsJsonObject("main")
            val components = aqiData.getAsJsonObject("components")

            val aqi = main?.get("aqi")?.asInt ?: 0

            val co = components?.get("co")?.asDouble // Carbon monoxide
            val no = components?.get("no")?.asDouble // Nitrogen monoxide
            val no2 = components?.get("no2")?.asDouble // Nitrogen dioxide
            val o3 = components?.get("o3")?.asDouble // Ozone
            val so2 = components?.get("so2")?.asDouble // Sulphur dioxide
            val pm2_5 = components?.get("pm2_5")?.asDouble // Fine particles
            val pm10 = components?.get("pm10")?.asDouble // Coarse particles
            val nh3 = components?.get("nh3")?.asDouble // Ammonia

            val (aqiDescription, aqiEmoji, healthRec) = when (aqi) {
                1 -> Triple("Buena", "🟢", "La calidad del aire es satisfactoria. Ideal para actividades al aire libre.")
                2 -> Triple("Moderada", "🟡", "Calidad del aire aceptable. Personas sensibles deben considerar limitar actividades prolongadas al aire libre.")
                3 -> Triple("Insana para grupos sensibles", "🟠", "Personas con enfermedades respiratorias o cardíacas, niños y adultos mayores deben evitar actividades prolongadas al aire libre.")
                4 -> Triple("Insana", "🔴", "Todos pueden experimentar efectos en la salud. Evita actividades al aire libre.")
                5 -> Triple("Muy insana", "🟣", "Alerta de salud: todos pueden experimentar efectos graves en la salud. Permanece en interiores.")
                else -> Triple("Desconocida", "⚪", "No hay información disponible.")
            }

            val formatted = buildString {
                appendLine("🌬️ Calidad del aire en **$cityName**, $country")
                appendLine()
                appendLine("$aqiEmoji Índice AQI: $aqi - $aqiDescription")
                appendLine()
                appendLine("💡 Recomendación: $healthRec")
                appendLine()
                appendLine("**Concentraciones de contaminantes (μg/m³):**")
                pm2_5?.let { appendLine("  • PM2.5: %.1f".format(it)) }
                pm10?.let { appendLine("  • PM10: %.1f".format(it)) }
                o3?.let { appendLine("  • Ozono (O₃): %.1f".format(it)) }
                no2?.let { appendLine("  • Dióxido de nitrógeno (NO₂): %.1f".format(it)) }
                so2?.let { appendLine("  • Dióxido de azufre (SO₂): %.1f".format(it)) }
                co?.let { appendLine("  • Monóxido de carbono (CO): %.1f".format(it)) }
                nh3?.let { appendLine("  • Amoníaco (NH₃): %.1f".format(it)) }
            }

            WeatherResult(toolCallId, true, formatted.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo calidad del aire", e)
            WeatherResult(toolCallId, false, "Error al obtener calidad del aire: ${e.message}")
        }
    }

    private suspend fun geocodeLocation(location: String, apiKey: String): GeocodedLocation? {
        return try {
            val url = "$geoUrl/direct?q=${java.net.URLEncoder.encode(location, "UTF-8")}&limit=1&appid=$apiKey"

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .build()

            val body = withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute().body?.string()
            } ?: return null

            val json = JsonParser.parseString(body).asJsonArray
            if (json.size() == 0) return null

            val result = json.get(0).asJsonObject
            GeocodedLocation(
                lat = result.get("lat")?.asDouble ?: return null,
                lon = result.get("lon")?.asDouble ?: return null,
                name = result.get("name")?.asString ?: location,
                country = result.get("country")?.asString ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error geocodificando ubicación", e)
            null
        }
    }

    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    private fun formatDateShort(timestamp: Long): String {
        return SimpleDateFormat("EEEE d MMM", Locale("es")).format(Date(timestamp))
    }

    private fun getWindDirection(degrees: Int): String {
        val directions = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        return directions[(degrees / 22.5).toInt() % 16]
    }

    private fun getWeatherEmoji(description: String): String {
        return when {
            description.contains("clear") || description.contains("despejado") -> "☀️"
            description.contains("cloud") || description.contains("nube") -> "☁️"
            description.contains("rain") || description.contains("lluvia") -> "🌧️"
            description.contains("thunder") || description.contains("tormenta") -> "⛈️"
            description.contains("snow") || description.contains("nieve") -> "🌨️"
            description.contains("mist") || description.contains("fog") || description.contains("niebla") -> "🌫️"
            description.contains("drizzle") || description.contains("llovizna") -> "🌦️"
            else -> "🌡️"
        }
    }

    data class GeocodedLocation(
        val lat: Double,
        val lon: Double,
        val name: String,
        val country: String
    )

    data class ForecastItem(
        val time: String,
        val temp: Double,
        val feelsLike: Double,
        val description: String,
        val icon: String,
        val conditionId: Int,
        val humidity: Int,
        val windSpeed: Double,
        val pop: Int
    )
}
