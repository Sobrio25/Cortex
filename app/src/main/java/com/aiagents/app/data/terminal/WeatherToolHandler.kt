package com.aiagents.app.data.terminal

import com.aiagents.app.data.location.DeviceLocationResult
import com.aiagents.app.data.location.LocationErrorCode
import com.aiagents.app.data.location.LocationProvider
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

enum class WeatherErrorCode {
    INVALID_ARGUMENT,
    LOCATION_PERMISSION_REQUIRED,
    LOCATION_UNAVAILABLE,
    LOCATION_NOT_FOUND,
    RATE_LIMITED,
    NETWORK_UNAVAILABLE,
    SERVICE_UNAVAILABLE,
    INVALID_RESPONSE
}

data class WeatherResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String,
    val errorCode: WeatherErrorCode? = null,
    val fromCache: Boolean = false
)

/**
 * Open-Meteo weather facade. Public non-commercial endpoints do not require an API key.
 *
 * Device coordinates are resolved inside the operation and are never included in returned text or
 * WEATHER_DATA. They live only in memory for the duration of the request/cache window.
 */
@Singleton
class WeatherToolHandler @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val locationProvider: LocationProvider
) {
    companion object {
        const val TOOL_NAME_CURRENT = "weather_current"
        const val TOOL_NAME_FORECAST = "weather_forecast"
        const val TOOL_NAME_AIR_QUALITY = "weather_air_quality"

        val ALL_TOOL_NAMES = setOf(TOOL_NAME_CURRENT, TOOL_NAME_FORECAST, TOOL_NAME_AIR_QUALITY)

        private const val SOURCE_NAME = "Open-Meteo"
        private const val AIR_SOURCE_NAME = "Open-Meteo · CAMS"
        private const val CURRENT_CACHE_TTL_MS = 10 * 60 * 1000L
        private const val FORECAST_CACHE_TTL_MS = 30 * 60 * 1000L
        private const val AIR_CACHE_TTL_MS = 15 * 60 * 1000L
        private const val GEOCODE_CACHE_TTL_MS = 24 * 60 * 60 * 1000L
        private const val STALE_CACHE_MAX_AGE_MS = 2 * 60 * 60 * 1000L

        private val IMPERIAL_COUNTRIES = setOf("US", "BS", "BZ", "KY", "PW")

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            weatherToolDefinition(
                name = TOOL_NAME_CURRENT,
                description = "Get current weather from Open-Meteo without an API key, including local min/max temperature, precipitation, wind, humidity, visibility, sunrise/sunset and air quality when available. Pass a place, lat/lon, or omit all location fields to use the Android device location privately. The result includes structured WEATHER_DATA for the native weather widget.",
                extraProperties = emptyMap()
            ),
            weatherToolDefinition(
                name = TOOL_NAME_FORECAST,
                description = "Get an Open-Meteo daily forecast without an API key for up to 5 days, including min/max temperature, humidity and precipitation probability. Pass a place, lat/lon, or omit location fields to use the Android device location privately. The result includes structured WEATHER_DATA for the native weather widget.",
                extraProperties = mapOf(
                    "days" to mapOf(
                        "type" to "integer",
                        "description" to "Number of forecast days (default 3, range 1-5)",
                        "minimum" to 1,
                        "maximum" to 5
                    )
                )
            ),
            weatherToolDefinition(
                name = TOOL_NAME_AIR_QUALITY,
                description = "Get current European AQI and pollutant concentrations from Open-Meteo/CAMS without an API key. Pass a place, lat/lon, or omit location fields to use the Android device location privately. The result includes structured WEATHER_DATA for the native weather widget.",
                extraProperties = emptyMap(),
                includeUnits = false
            )
        )

        private fun weatherToolDefinition(
            name: String,
            description: String,
            extraProperties: Map<String, Any>,
            includeUnits: Boolean = true
        ): Map<String, Any> {
            val properties = linkedMapOf<String, Any>(
                "location" to mapOf(
                    "type" to "string",
                    "description" to "Optional city or place name, preferably with country. Omit to use device location."
                ),
                "lat" to mapOf(
                    "type" to "number",
                    "description" to "Optional latitude (-90 to 90); must be provided together with lon."
                ),
                "lon" to mapOf(
                    "type" to "number",
                    "description" to "Optional longitude (-180 to 180); must be provided together with lat."
                )
            )
            if (includeUnits) {
                properties["units"] = mapOf(
                    "type" to "string",
                    "description" to "Optional units. When omitted, units are selected for the queried country/device locale.",
                    "enum" to listOf("metric", "imperial", "standard", "kelvin")
                )
            }
            properties.putAll(extraProperties)
            return mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to name,
                    "description" to description,
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to properties,
                        "required" to emptyList<String>()
                    )
                )
            )
        }

        /** Maps WMO codes to the legacy condition ids already understood by the native widget. */
        internal fun mapWmoToConditionId(code: Int): Int = when (code) {
            0 -> 800
            1 -> 801
            2 -> 802
            3 -> 804
            45, 48 -> 741
            51 -> 300
            53 -> 301
            55 -> 302
            56, 57 -> 311
            61 -> 500
            63 -> 501
            65 -> 502
            66, 67 -> 511
            71 -> 600
            73 -> 601
            75 -> 602
            77 -> 611
            80 -> 520
            81 -> 521
            82 -> 522
            85 -> 620
            86 -> 622
            95 -> 211
            96 -> 201
            99 -> 202
            else -> 800
        }

        internal fun mapEuropeanAqiToFivePointScale(value: Double): Int = when {
            value <= 20.0 -> 1
            value <= 40.0 -> 2
            value <= 60.0 -> 3
            value <= 80.0 -> 4
            else -> 5
        }
    }

    private val forecastUrl = "https://api.open-meteo.com/v1/forecast"
    private val geocodingUrl = "https://geocoding-api.open-meteo.com/v1/search"
    private val airQualityUrl = "https://air-quality-api.open-meteo.com/v1/air-quality"
    private val responseCache = ConcurrentHashMap<String, CacheEntry>()
    private val airDataCache = ConcurrentHashMap<String, AirQualityData>()
    private val geocodeCache = ConcurrentHashMap<String, GeoCacheEntry>()

    fun hasLocationPermission(): Boolean = locationProvider.hasLocationPermission()

    /** Returns true only when valid coordinates/place text are both absent. */
    fun requiresDeviceLocation(arguments: String): Boolean = try {
        val args = parseArguments(arguments)
        val location = args.optionalString("location")
        val hasLat = args.hasNonNull("lat") || args.hasNonNull("latitude")
        val hasLon = args.hasNonNull("lon") || args.hasNonNull("longitude")
        location.isNullOrBlank() && !hasLat && !hasLon
    } catch (_: Exception) {
        false
    }

    suspend fun executeCurrentWeather(
        toolCallId: String,
        arguments: String
    ): WeatherResult = executeWeatherOperation(
        toolCallId = toolCallId,
        arguments = arguments,
        operation = "current",
        ttlMillis = CURRENT_CACHE_TTL_MS
    ) { args, target ->
        val units = resolveUnits(args.optionalString("units"), target.countryCode)
        val url = forecastUrl.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", target.latitude.toString())
            .addQueryParameter("longitude", target.longitude.toString())
            .addQueryParameter(
                "current",
                "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,cloud_cover,surface_pressure,wind_speed_10m,wind_direction_10m"
            )
            .addQueryParameter("hourly", "visibility")
            .addQueryParameter("daily", "temperature_2m_min,temperature_2m_max,sunrise,sunset")
            .addQueryParameter("temperature_unit", units.openMeteoTemperatureUnit)
            .addQueryParameter("wind_speed_unit", units.openMeteoWindUnit)
            .addQueryParameter("precipitation_unit", "mm")
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", "1")
            .addQueryParameter("timeformat", "unixtime")
            .build()
        val json = getJsonObject(url.toString())
        val current = json.objectOrNull("current")
            ?: throw WeatherFailure(WeatherErrorCode.INVALID_RESPONSE, "Open-Meteo no devolvió condiciones actuales.")
        val daily = json.objectOrNull("daily")
        val timezoneOffset = json.intOrNull("utc_offset_seconds") ?: 0
        val observedAtEpoch = current.longOrNull("time") ?: Instant.now().epochSecond
        val wmoCode = current.intOrNull("weather_code") ?: 0
        val isDay = current.intOrNull("is_day") != 0
        val condition = describeWmoCode(wmoCode, isDay)
        val rawTemp = current.doubleOrNull("temperature_2m")
            ?: throw WeatherFailure(WeatherErrorCode.INVALID_RESPONSE, "Open-Meteo no devolvió temperatura.")
        val temp = units.convertTemperature(rawTemp)
        val feelsLike = units.convertTemperature(current.doubleOrNull("apparent_temperature") ?: rawTemp)
        val minTemp = units.convertTemperature(daily?.arrayOrNull("temperature_2m_min").doubleAtOrNull(0) ?: rawTemp)
        val maxTemp = units.convertTemperature(daily?.arrayOrNull("temperature_2m_max").doubleAtOrNull(0) ?: rawTemp)
        val humidity = current.intOrNull("relative_humidity_2m") ?: 0
        val pressure = current.doubleOrNull("surface_pressure")?.toInt() ?: 0
        val windSpeed = current.doubleOrNull("wind_speed_10m") ?: 0.0
        val windDeg = current.intOrNull("wind_direction_10m") ?: 0
        val clouds = current.intOrNull("cloud_cover") ?: 0
        val precipitationMm = current.doubleOrNull("precipitation") ?: 0.0
        val rawVisibilityMeters = nearestHourlyValue(
            json.objectOrNull("hourly"),
            "visibility",
            observedAtEpoch
        ) ?: 0.0
        val visibility = if (units.visibilityUnit == "mi") rawVisibilityMeters / 1609.344 else rawVisibilityMeters / 1000.0
        val sunriseEpoch = daily?.arrayOrNull("sunrise").longAtOrNull(0)
        val sunsetEpoch = daily?.arrayOrNull("sunset").longAtOrNull(0)
        val sunrise = sunriseEpoch?.let { formatTime(it, timezoneOffset) }.orEmpty()
        val sunset = sunsetEpoch?.let { formatTime(it, timezoneOffset) }.orEmpty()
        val country = target.countryName ?: target.countryCode.orEmpty()
        val air = fetchAirQualityOrNull(target)

        val formatted = buildString {
            appendLine("🌤️ Clima actual en **${displayLocation(target.displayName, country)}**")
            appendLine()
            appendLine("**${condition.description}**")
            appendLine("🌡️ ${formatNumber(temp)}${units.temperatureSymbol} · mín. ${formatNumber(minTemp)}${units.temperatureSymbol} · máx. ${formatNumber(maxTemp)}${units.temperatureSymbol}")
            appendLine("🤔 Sensación: ${formatNumber(feelsLike)}${units.temperatureSymbol}")
            appendLine("💧 Humedad: $humidity%")
            if (precipitationMm > 0.0) appendLine("🌧️ Precipitación: ${formatNumber(precipitationMm)} mm")
            appendLine("💨 Viento: ${formatNumber(windSpeed)} ${units.speedUnit} ${getWindDirection(windDeg)}")
            if (rawVisibilityMeters > 0.0) appendLine("👁️ Visibilidad: ${formatNumber(visibility)} ${units.visibilityUnit}")
            air?.let { appendLine("🌬️ Calidad del aire: ${it.aqi}/5 (${it.label})") }
            if (sunrise.isNotEmpty() && sunset.isNotEmpty()) appendLine("🌅 $sunrise · 🌇 $sunset")
            appendLine("Actualizado: ${formatUpdated(observedAtEpoch, timezoneOffset)} · Fuente: $SOURCE_NAME")
            if (target.source == LocationSource.DEVICE) {
                append("La ubicación del dispositivo se usó de forma privada; sus coordenadas no se incluyeron en este resultado.")
            }
        }.trim()

        val data = JSONObject().apply {
            put("type", "current")
            put("city", target.displayName)
            put("country", country)
            put("conditionId", condition.conditionId)
            put("icon", condition.icon)
            put("description", condition.description)
            put("temp", temp)
            put("minTemp", minTemp)
            put("maxTemp", maxTemp)
            put("feelsLike", feelsLike)
            put("humidity", humidity)
            put("pressure", pressure)
            put("windSpeed", windSpeed)
            put("windDeg", windDeg)
            put("visibility", visibility)
            put("visibilityUnit", units.visibilityUnit)
            put("clouds", clouds)
            put("precipitationMm", precipitationMm)
            put("sunrise", sunrise)
            put("sunset", sunset)
            put("unitSymbol", units.temperatureSymbol)
            put("speedUnit", units.speedUnit)
            put("updatedAt", formatUpdated(observedAtEpoch, timezoneOffset))
            put("updatedAtEpoch", observedAtEpoch)
            put("timezone", formatUtcOffset(timezoneOffset))
            put("source", SOURCE_NAME)
            put("locationSource", target.source.serialized)
            put("isStale", target.locationIsStale)
            target.accuracyMeters?.let { put("locationAccuracyMeters", it.toInt().coerceAtLeast(1)) }
            air?.let {
                put("aqi", it.aqi)
                put("aqiLabel", it.label)
            }
        }
        formatted + weatherDataMarker(data)
    }

    suspend fun executeForecast(
        toolCallId: String,
        arguments: String
    ): WeatherResult = executeWeatherOperation(
        toolCallId = toolCallId,
        arguments = arguments,
        operation = "forecast",
        ttlMillis = FORECAST_CACHE_TTL_MS
    ) { args, target ->
        val days = args.optionalInt("days")?.coerceIn(1, 5) ?: 3
        val units = resolveUnits(args.optionalString("units"), target.countryCode)
        val url = forecastUrl.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", target.latitude.toString())
            .addQueryParameter("longitude", target.longitude.toString())
            .addQueryParameter("hourly", "relative_humidity_2m")
            .addQueryParameter(
                "daily",
                "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset"
            )
            .addQueryParameter("temperature_unit", units.openMeteoTemperatureUnit)
            .addQueryParameter("wind_speed_unit", units.openMeteoWindUnit)
            .addQueryParameter("precipitation_unit", "mm")
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", days.toString())
            .addQueryParameter("timeformat", "unixtime")
            .build()
        val json = getJsonObject(url.toString())
        val daily = json.objectOrNull("daily")
            ?: throw WeatherFailure(WeatherErrorCode.INVALID_RESPONSE, "Open-Meteo no devolvió pronóstico.")
        val time = daily.arrayOrNull("time")
            ?: throw WeatherFailure(WeatherErrorCode.INVALID_RESPONSE, "El pronóstico recibido está vacío.")
        val timezoneOffset = json.intOrNull("utc_offset_seconds") ?: 0
        val humiditiesByDate = hourlyHumidityByDate(json.objectOrNull("hourly"), timezoneOffset)
        val summaries = (0 until minOf(days, time.size())).mapNotNull { index ->
            val epoch = time.longAtOrNull(index) ?: return@mapNotNull null
            val rawMin = daily.arrayOrNull("temperature_2m_min").doubleAtOrNull(index) ?: return@mapNotNull null
            val rawMax = daily.arrayOrNull("temperature_2m_max").doubleAtOrNull(index) ?: return@mapNotNull null
            val wmoCode = daily.arrayOrNull("weather_code").intAtOrNull(index) ?: 0
            val condition = describeWmoCode(wmoCode, isDay = true)
            ForecastDaySummary(
                epochSeconds = epoch,
                minTemp = units.convertTemperature(rawMin),
                maxTemp = units.convertTemperature(rawMax),
                avgHumidity = humiditiesByDate[formatDateKey(epoch, timezoneOffset)]
                    ?.average()?.toInt()?.coerceIn(0, 100) ?: 0,
                maxPop = (daily.arrayOrNull("precipitation_probability_max").intAtOrNull(index) ?: 0)
                    .coerceIn(0, 100),
                conditionId = condition.conditionId,
                icon = condition.icon,
                description = condition.description
            )
        }
        if (summaries.isEmpty()) {
            throw WeatherFailure(WeatherErrorCode.INVALID_RESPONSE, "El pronóstico recibido está vacío.")
        }

        val country = target.countryName ?: target.countryCode.orEmpty()
        val air = fetchAirQualityOrNull(target)
        val updatedEpoch = Instant.now().epochSecond
        val formatted = buildString {
            appendLine("🌤️ Pronóstico para **${displayLocation(target.displayName, country)}**")
            appendLine()
            summaries.forEach { summary ->
                appendLine("**${formatDateLabel(summary.epochSeconds, timezoneOffset)}**")
                appendLine("  ${getWeatherEmoji(summary.description)} ${summary.description}")
                appendLine("  Mín. ${formatNumber(summary.minTemp)}${units.temperatureSymbol} · máx. ${formatNumber(summary.maxTemp)}${units.temperatureSymbol} · lluvia ${summary.maxPop}%")
                if (summary.avgHumidity > 0) appendLine("  Humedad promedio: ${summary.avgHumidity}%")
            }
            air?.let { appendLine("🌬️ Calidad del aire actual: ${it.aqi}/5 (${it.label})") }
            appendLine("Actualizado: ${formatUpdated(updatedEpoch, timezoneOffset)} · Fuente: $SOURCE_NAME")
            if (target.source == LocationSource.DEVICE) {
                append("La ubicación del dispositivo se usó de forma privada; sus coordenadas no se incluyeron en este resultado.")
            }
        }.trim()

        val data = JSONObject().apply {
            put("type", "forecast")
            put("city", target.displayName)
            put("country", country)
            put("unitSymbol", units.temperatureSymbol)
            put("updatedAt", formatUpdated(updatedEpoch, timezoneOffset))
            put("updatedAtEpoch", updatedEpoch)
            put("timezone", formatUtcOffset(timezoneOffset))
            put("source", SOURCE_NAME)
            put("locationSource", target.source.serialized)
            put("isStale", target.locationIsStale)
            air?.let {
                put("aqi", it.aqi)
                put("aqiLabel", it.label)
            }
            put("days", JSONArray().apply {
                summaries.forEach { summary ->
                    put(JSONObject().apply {
                        put("date", formatDateLabel(summary.epochSeconds, timezoneOffset))
                        put("minTemp", summary.minTemp)
                        put("maxTemp", summary.maxTemp)
                        put("avgHumidity", summary.avgHumidity)
                        put("maxPop", summary.maxPop)
                        put("conditionId", summary.conditionId)
                        put("icon", summary.icon)
                        put("description", summary.description)
                    })
                }
            })
        }
        formatted + weatherDataMarker(data)
    }

    suspend fun executeAirQuality(
        toolCallId: String,
        arguments: String
    ): WeatherResult = executeWeatherOperation(
        toolCallId = toolCallId,
        arguments = arguments,
        operation = "air",
        ttlMillis = AIR_CACHE_TTL_MS
    ) { _, target ->
        val air = fetchAirQuality(target)
        val updatedEpoch = air.observedAtEpoch ?: Instant.now().epochSecond
        val country = target.countryName ?: target.countryCode.orEmpty()
        val formatted = buildString {
            appendLine("🌬️ Calidad del aire en **${displayLocation(target.displayName, country)}**")
            appendLine()
            appendLine("${air.emoji} Índice europeo: ${air.aqi}/5 — ${air.label}")
            appendLine("💡 ${air.recommendation}")
            appendLine()
            appendLine("**Contaminantes (μg/m³):**")
            air.pm2_5?.let { appendLine("• PM2.5: ${formatNumber(it)}") }
            air.pm10?.let { appendLine("• PM10: ${formatNumber(it)}") }
            air.o3?.let { appendLine("• Ozono: ${formatNumber(it)}") }
            air.no2?.let { appendLine("• NO₂: ${formatNumber(it)}") }
            air.so2?.let { appendLine("• SO₂: ${formatNumber(it)}") }
            air.co?.let { appendLine("• CO: ${formatNumber(it)}") }
            appendLine("Actualizado: ${formatUpdated(updatedEpoch, air.timezoneOffsetSeconds)} · Fuente: $AIR_SOURCE_NAME")
            if (target.source == LocationSource.DEVICE) {
                append("La ubicación del dispositivo se usó de forma privada; sus coordenadas no se incluyeron en este resultado.")
            }
        }.trim()

        val data = JSONObject().apply {
            put("type", "air_quality")
            put("city", target.displayName)
            put("country", country)
            put("aqi", air.aqi)
            put("aqiLabel", air.label)
            put("recommendation", air.recommendation)
            put("updatedAt", formatUpdated(updatedEpoch, air.timezoneOffsetSeconds))
            put("updatedAtEpoch", updatedEpoch)
            put("timezone", formatUtcOffset(air.timezoneOffsetSeconds))
            put("source", AIR_SOURCE_NAME)
            put("locationSource", target.source.serialized)
            put("isStale", target.locationIsStale)
            air.pm2_5?.let { put("pm2_5", it) }
            air.pm10?.let { put("pm10", it) }
            air.o3?.let { put("o3", it) }
            air.no2?.let { put("no2", it) }
        }
        formatted + weatherDataMarker(data)
    }

    private suspend fun executeWeatherOperation(
        toolCallId: String,
        arguments: String,
        operation: String,
        ttlMillis: Long,
        producer: suspend (JsonObject, ResolvedLocation) -> String
    ): WeatherResult {
        return try {
            val args = parseArguments(arguments)
            val target = resolveLocation(args)
            val unitKey = args.optionalString("units").orEmpty().lowercase(Locale.ROOT)
            val daysKey = args.optionalInt("days")?.coerceIn(1, 5) ?: 0
            val cacheKey = listOf(
                operation,
                coordinateBucket(target.latitude, target.longitude),
                unitKey,
                daysKey.toString()
            ).joinToString("|")
            val now = System.currentTimeMillis()
            val cached = responseCache[cacheKey]
            if (cached != null && now <= cached.freshUntilMillis) {
                WeatherResult(
                    toolCallId = toolCallId,
                    success = true,
                    content = updateWeatherMetadata(cached.content, isCached = true, isStale = null),
                    fromCache = true
                )
            } else {
                try {
                    val content = producer(args, target)
                    responseCache[cacheKey] = CacheEntry(
                        content = content,
                        createdAtMillis = now,
                        freshUntilMillis = now + ttlMillis
                    )
                    pruneCaches(now)
                    WeatherResult(toolCallId, true, content)
                } catch (failure: WeatherFailure) {
                    if (cached != null && now - cached.createdAtMillis <= STALE_CACHE_MAX_AGE_MS) {
                        WeatherResult(
                            toolCallId = toolCallId,
                            success = true,
                            content = "⚠️ La fuente no respondió; se muestran los últimos datos guardados.\n" +
                                updateWeatherMetadata(cached.content, isCached = true, isStale = true),
                            errorCode = failure.code,
                            fromCache = true
                        )
                    } else {
                        failureResult(toolCallId, failure.code, failure.userMessage)
                    }
                }
            }
        } catch (failure: WeatherFailure) {
            failureResult(toolCallId, failure.code, failure.userMessage)
        } catch (_: Exception) {
            failureResult(
                toolCallId,
                WeatherErrorCode.INVALID_RESPONSE,
                "No se pudo interpretar la respuesta del servicio del clima."
            )
        }
    }

    private suspend fun resolveLocation(args: JsonObject): ResolvedLocation {
        val lat = args.optionalDouble("lat") ?: args.optionalDouble("latitude")
        val lon = args.optionalDouble("lon") ?: args.optionalDouble("longitude")
        if (lat != null || lon != null) {
            if (lat == null || lon == null) {
                throw WeatherFailure(
                    WeatherErrorCode.INVALID_ARGUMENT,
                    "Los parámetros lat y lon deben enviarse juntos."
                )
            }
            if (!lat.isFinite() || lat !in -90.0..90.0 || !lon.isFinite() || lon !in -180.0..180.0) {
                throw WeatherFailure(WeatherErrorCode.INVALID_ARGUMENT, "Las coordenadas están fuera de rango.")
            }
            return ResolvedLocation(
                latitude = lat,
                longitude = lon,
                displayName = "Ubicación seleccionada",
                countryCode = null,
                countryName = null,
                source = LocationSource.COORDINATES
            )
        }

        val requestedLocation = args.optionalString("location")?.trim().orEmpty()
        if (requestedLocation.isNotEmpty()) return geocodeLocation(requestedLocation)

        return when (val result = locationProvider.getCurrentLocation(includeAddress = true)) {
            is DeviceLocationResult.Success -> ResolvedLocation(
                latitude = result.location.latitude,
                longitude = result.location.longitude,
                displayName = result.location.city ?: "Ubicación actual",
                countryCode = result.location.countryCode,
                countryName = result.location.country,
                source = LocationSource.DEVICE,
                accuracyMeters = result.location.accuracyMeters,
                locationIsStale = result.location.isStale
            )

            is DeviceLocationResult.Failure -> {
                val weatherCode = when (result.code) {
                    LocationErrorCode.PERMISSION_REQUIRED -> WeatherErrorCode.LOCATION_PERMISSION_REQUIRED
                    LocationErrorCode.UNAVAILABLE,
                    LocationErrorCode.INACCURATE -> WeatherErrorCode.LOCATION_UNAVAILABLE
                }
                throw WeatherFailure(weatherCode, result.userMessage)
            }
        }
    }

    private suspend fun geocodeLocation(location: String): ResolvedLocation {
        val cacheKey = "search:${location.trim().lowercase(Locale.ROOT)}"
        val now = System.currentTimeMillis()
        geocodeCache[cacheKey]
            ?.takeIf { now - it.cachedAtMillis <= GEOCODE_CACHE_TTL_MS }
            ?.let { return it.location }
        val url = geocodingUrl.toHttpUrl().newBuilder()
            .addQueryParameter("name", location)
            .addQueryParameter("count", "1")
            .addQueryParameter("language", "es")
            .addQueryParameter("format", "json")
            .build()
        val json = getJsonObject(url.toString())
        val result = json.arrayOrNull("results")?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw WeatherFailure(
                WeatherErrorCode.LOCATION_NOT_FOUND,
                "No se encontró la ubicación solicitada. Añade ciudad y país para hacerla más precisa."
            )
        val resolved = ResolvedLocation(
            latitude = result.doubleOrNull("latitude")
                ?: throw WeatherFailure(WeatherErrorCode.INVALID_RESPONSE, "La geocodificación no devolvió latitud."),
            longitude = result.doubleOrNull("longitude")
                ?: throw WeatherFailure(WeatherErrorCode.INVALID_RESPONSE, "La geocodificación no devolvió longitud."),
            displayName = result.stringOrNull("name") ?: location,
            countryCode = result.stringOrNull("country_code"),
            countryName = result.stringOrNull("country"),
            source = LocationSource.SEARCH
        )
        geocodeCache[cacheKey] = GeoCacheEntry(resolved, now)
        return resolved
    }

    private suspend fun fetchAirQualityOrNull(target: ResolvedLocation): AirQualityData? = try {
        fetchAirQuality(target)
    } catch (_: WeatherFailure) {
        null
    }

    private suspend fun fetchAirQuality(target: ResolvedLocation): AirQualityData {
        val key = coordinateBucket(target.latitude, target.longitude)
        val now = System.currentTimeMillis()
        airDataCache[key]?.takeIf { now - it.cachedAtMillis <= AIR_CACHE_TTL_MS }?.let { return it }

        val url = airQualityUrl.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", target.latitude.toString())
            .addQueryParameter("longitude", target.longitude.toString())
            .addQueryParameter(
                "current",
                "european_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone"
            )
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("timeformat", "unixtime")
            .build()
        val json = getJsonObject(url.toString())
        val current = json.objectOrNull("current")
            ?: throw WeatherFailure(WeatherErrorCode.INVALID_RESPONSE, "No hay datos de calidad del aire.")
        val europeanAqi = current.doubleOrNull("european_aqi")
            ?: throw WeatherFailure(WeatherErrorCode.INVALID_RESPONSE, "Open-Meteo no devolvió un índice de calidad del aire.")
        val aqi = mapEuropeanAqiToFivePointScale(europeanAqi)
        val descriptor = describeAqi(aqi)
        val data = AirQualityData(
            aqi = aqi,
            label = descriptor.label,
            emoji = descriptor.emoji,
            recommendation = descriptor.recommendation,
            co = current.doubleOrNull("carbon_monoxide"),
            no2 = current.doubleOrNull("nitrogen_dioxide"),
            o3 = current.doubleOrNull("ozone"),
            so2 = current.doubleOrNull("sulphur_dioxide"),
            pm2_5 = current.doubleOrNull("pm2_5"),
            pm10 = current.doubleOrNull("pm10"),
            observedAtEpoch = current.longOrNull("time"),
            timezoneOffsetSeconds = json.intOrNull("utc_offset_seconds") ?: 0,
            cachedAtMillis = now
        )
        airDataCache[key] = data
        return data
    }

    private suspend fun getJsonObject(url: String): JsonObject = parseNetworkJson(url).let { element ->
        if (!element.isJsonObject) {
            throw WeatherFailure(WeatherErrorCode.INVALID_RESPONSE, "El servicio devolvió una respuesta inesperada.")
        }
        element.asJsonObject
    }

    private suspend fun parseNetworkJson(url: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "AIAgents-Android/0.3.0")
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw httpFailure(response.code, body)
                try {
                    JsonParser.parseString(body)
                } catch (_: Exception) {
                    throw WeatherFailure(
                        WeatherErrorCode.INVALID_RESPONSE,
                        "El servicio del clima devolvió datos no válidos."
                    )
                }
            }
        } catch (failure: WeatherFailure) {
            throw failure
        } catch (_: SocketTimeoutException) {
            throw WeatherFailure(
                WeatherErrorCode.NETWORK_UNAVAILABLE,
                "La consulta del clima tardó demasiado. Inténtalo de nuevo."
            )
        } catch (_: IOException) {
            throw WeatherFailure(
                WeatherErrorCode.NETWORK_UNAVAILABLE,
                "No hay conexión disponible para consultar el clima."
            )
        }
    }

    private fun httpFailure(status: Int, body: String): WeatherFailure {
        val reason = try {
            JsonParser.parseString(body).asJsonObject.stringOrNull("reason")
        } catch (_: Exception) {
            null
        }
        return when (status) {
            400 -> WeatherFailure(
                WeatherErrorCode.INVALID_ARGUMENT,
                reason?.let { "Open-Meteo rechazó la consulta: $it" }
                    ?: "Open-Meteo rechazó los parámetros de la consulta."
            )
            404 -> WeatherFailure(
                WeatherErrorCode.LOCATION_NOT_FOUND,
                "Open-Meteo no encontró datos para esa ubicación."
            )
            429 -> WeatherFailure(
                WeatherErrorCode.RATE_LIMITED,
                "Open-Meteo alcanzó temporalmente el límite de consultas. Inténtalo más tarde."
            )
            in 500..599 -> WeatherFailure(
                WeatherErrorCode.SERVICE_UNAVAILABLE,
                "Open-Meteo no está disponible temporalmente."
            )
            else -> WeatherFailure(
                WeatherErrorCode.SERVICE_UNAVAILABLE,
                "Open-Meteo rechazó la consulta (HTTP $status)."
            )
        }
    }

    private fun failureResult(
        toolCallId: String,
        code: WeatherErrorCode,
        message: String
    ): WeatherResult {
        val data = JSONObject().apply {
            put("type", "error")
            put("code", code.name)
            put("message", message)
            put("source", SOURCE_NAME)
        }
        return WeatherResult(
            toolCallId = toolCallId,
            success = false,
            content = "Error [${code.name}]: $message" + weatherDataMarker(data),
            errorCode = code
        )
    }

    private fun resolveUnits(requested: String?, countryCode: String?): WeatherUnits {
        val normalized = requested?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
            ?: if ((countryCode ?: Locale.getDefault().country).uppercase(Locale.ROOT) in IMPERIAL_COUNTRIES) {
                "imperial"
            } else {
                "metric"
            }
        return when (normalized) {
            "metric" -> WeatherUnits("celsius", "ms", "°C", "m/s", "km", false)
            "imperial" -> WeatherUnits("fahrenheit", "mph", "°F", "mph", "mi", false)
            "standard", "kelvin" -> WeatherUnits("celsius", "ms", "K", "m/s", "km", true)
            else -> throw WeatherFailure(
                WeatherErrorCode.INVALID_ARGUMENT,
                "Las unidades deben ser metric, imperial, standard o kelvin."
            )
        }
    }

    private fun describeWmoCode(code: Int, isDay: Boolean): WeatherConditionDescriptor {
        val description = when (code) {
            0 -> "Despejado"
            1 -> "Mayormente despejado"
            2 -> "Parcialmente nublado"
            3 -> "Cubierto"
            45 -> "Niebla"
            48 -> "Niebla con escarcha"
            51 -> "Llovizna ligera"
            53 -> "Llovizna moderada"
            55 -> "Llovizna intensa"
            56 -> "Llovizna helada ligera"
            57 -> "Llovizna helada intensa"
            61 -> "Lluvia ligera"
            63 -> "Lluvia moderada"
            65 -> "Lluvia intensa"
            66 -> "Lluvia helada ligera"
            67 -> "Lluvia helada intensa"
            71 -> "Nieve ligera"
            73 -> "Nieve moderada"
            75 -> "Nieve intensa"
            77 -> "Granos de nieve"
            80 -> "Chubascos ligeros"
            81 -> "Chubascos moderados"
            82 -> "Chubascos intensos"
            85 -> "Chubascos de nieve ligeros"
            86 -> "Chubascos de nieve intensos"
            95 -> "Tormenta eléctrica"
            96 -> "Tormenta con granizo ligero"
            99 -> "Tormenta con granizo intenso"
            else -> "Condiciones actuales"
        }
        val conditionId = mapWmoToConditionId(code)
        val iconPrefix = when (conditionId) {
            in 200..299 -> "11"
            in 300..399, in 520..599 -> "09"
            in 500..519 -> "10"
            in 600..699 -> "13"
            in 700..799 -> "50"
            800 -> "01"
            801 -> "02"
            802 -> "03"
            else -> "04"
        }
        return WeatherConditionDescriptor(
            conditionId = conditionId,
            icon = "$iconPrefix${if (isDay) "d" else "n"}",
            description = description
        )
    }

    private fun describeAqi(aqi: Int): AqiDescriptor = when (aqi) {
        1 -> AqiDescriptor("Buena", "🟢", "La calidad del aire es satisfactoria para actividades al aire libre.")
        2 -> AqiDescriptor("Aceptable", "🟡", "La calidad es aceptable; personas muy sensibles pueden moderar esfuerzos prolongados.")
        3 -> AqiDescriptor("Moderada", "🟠", "Grupos sensibles deberían reducir actividad prolongada al aire libre.")
        4 -> AqiDescriptor("Mala", "🔴", "Conviene reducir la actividad al aire libre.")
        5 -> AqiDescriptor("Muy mala", "🟣", "Evita esfuerzos al aire libre y sigue las alertas sanitarias locales.")
        else -> AqiDescriptor("Sin clasificar", "⚪", "No hay una clasificación disponible.")
    }

    private fun nearestHourlyValue(hourly: JsonObject?, field: String, epoch: Long): Double? {
        val times = hourly?.arrayOrNull("time") ?: return null
        val values = hourly.arrayOrNull(field) ?: return null
        val count = minOf(times.size(), values.size())
        if (count == 0) return null
        val index = (0 until count).minByOrNull { abs((times.longAtOrNull(it) ?: epoch) - epoch) } ?: return null
        return values.doubleAtOrNull(index)
    }

    private fun hourlyHumidityByDate(hourly: JsonObject?, timezoneOffset: Int): Map<String, List<Int>> {
        val times = hourly?.arrayOrNull("time") ?: return emptyMap()
        val humidities = hourly.arrayOrNull("relative_humidity_2m") ?: return emptyMap()
        val result = linkedMapOf<String, MutableList<Int>>()
        repeat(minOf(times.size(), humidities.size())) { index ->
            val epoch = times.longAtOrNull(index) ?: return@repeat
            val humidity = humidities.intAtOrNull(index) ?: return@repeat
            result.getOrPut(formatDateKey(epoch, timezoneOffset)) { mutableListOf() }.add(humidity)
        }
        return result
    }

    private fun pruneCaches(now: Long) {
        responseCache.entries.removeAll { now - it.value.createdAtMillis > STALE_CACHE_MAX_AGE_MS }
        airDataCache.entries.removeAll { now - it.value.cachedAtMillis > STALE_CACHE_MAX_AGE_MS }
        geocodeCache.entries.removeAll { now - it.value.cachedAtMillis > GEOCODE_CACHE_TTL_MS }
    }

    private fun updateWeatherMetadata(content: String, isCached: Boolean, isStale: Boolean?): String {
        val match = WEATHER_MARKER_REGEX.find(content) ?: return content
        return try {
            val data = JSONObject(match.groupValues[1])
            data.put("isCached", isCached)
            if (isStale != null) data.put("isStale", isStale)
            content.replaceRange(match.range, weatherDataMarker(data).trimStart())
        } catch (_: Exception) {
            content
        }
    }

    private fun parseArguments(arguments: String): JsonObject {
        if (arguments.isBlank()) return JsonObject()
        val parsed = try {
            JsonParser.parseString(arguments)
        } catch (_: Exception) {
            throw WeatherFailure(WeatherErrorCode.INVALID_ARGUMENT, "Los argumentos de la tool no son JSON válido.")
        }
        if (!parsed.isJsonObject) {
            throw WeatherFailure(WeatherErrorCode.INVALID_ARGUMENT, "Los argumentos de la tool deben ser un objeto JSON.")
        }
        return parsed.asJsonObject
    }

    private fun JsonObject.hasNonNull(name: String): Boolean = has(name) && !get(name).isJsonNull

    private fun JsonObject.optionalString(name: String): String? =
        if (!hasNonNull(name)) null else try {
            get(name).asString
        } catch (_: Exception) {
            throw WeatherFailure(WeatherErrorCode.INVALID_ARGUMENT, "$name debe ser texto.")
        }

    private fun JsonObject.optionalDouble(name: String): Double? =
        if (!hasNonNull(name)) null else try {
            get(name).asDouble
        } catch (_: Exception) {
            throw WeatherFailure(WeatherErrorCode.INVALID_ARGUMENT, "$name debe ser numérico.")
        }

    private fun JsonObject.optionalInt(name: String): Int? =
        if (!hasNonNull(name)) null else try {
            get(name).asInt
        } catch (_: Exception) {
            throw WeatherFailure(WeatherErrorCode.INVALID_ARGUMENT, "$name debe ser un entero.")
        }

    private fun JsonObject.objectOrNull(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arrayOrNull(name: String): JsonArray? =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.stringOrNull(name: String): String? =
        get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString

    private fun JsonObject.doubleOrNull(name: String): Double? = try {
        get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asDouble
    } catch (_: Exception) {
        null
    }

    private fun JsonObject.intOrNull(name: String): Int? = doubleOrNull(name)?.toInt()
    private fun JsonObject.longOrNull(name: String): Long? = doubleOrNull(name)?.toLong()

    private fun JsonArray?.doubleAtOrNull(index: Int): Double? = try {
        this?.get(index)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asDouble
    } catch (_: Exception) {
        null
    }

    private fun JsonArray?.intAtOrNull(index: Int): Int? = doubleAtOrNull(index)?.toInt()
    private fun JsonArray?.longAtOrNull(index: Int): Long? = doubleAtOrNull(index)?.toLong()
    private fun weatherDataMarker(data: JSONObject): String = "\n<!--WEATHER_DATA:$data-->"

    private fun coordinateBucket(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.2f,%.2f", latitude, longitude)

    private fun displayLocation(city: String, country: String): String =
        if (country.isBlank()) city else "$city, $country"

    private fun formatNumber(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

    private fun formatTime(epochSeconds: Long, timezoneOffsetSeconds: Int): String =
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
            .withZone(safeZoneOffset(timezoneOffsetSeconds))
            .format(Instant.ofEpochSecond(epochSeconds))

    private fun formatUpdated(epochSeconds: Long, timezoneOffsetSeconds: Int): String =
        DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())
            .withZone(safeZoneOffset(timezoneOffsetSeconds))
            .format(Instant.ofEpochSecond(epochSeconds))

    private fun formatDateKey(epochSeconds: Long, timezoneOffsetSeconds: Int): String =
        DateTimeFormatter.ISO_LOCAL_DATE
            .withZone(safeZoneOffset(timezoneOffsetSeconds))
            .format(Instant.ofEpochSecond(epochSeconds))

    private fun formatDateLabel(epochSeconds: Long, timezoneOffsetSeconds: Int): String =
        DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("es"))
            .withZone(safeZoneOffset(timezoneOffsetSeconds))
            .format(Instant.ofEpochSecond(epochSeconds))

    private fun safeZoneOffset(seconds: Int): ZoneOffset =
        ZoneOffset.ofTotalSeconds(seconds.coerceIn(-18 * 3600, 18 * 3600))

    private fun formatUtcOffset(seconds: Int): String {
        val offset = safeZoneOffset(seconds)
        return if (offset == ZoneOffset.UTC) "UTC" else "UTC${offset.id}"
    }

    private fun getWindDirection(degrees: Int): String {
        val directions = listOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO"
        )
        val normalized = ((degrees % 360) + 360) % 360
        return directions[((normalized / 22.5) + 0.5).toInt() % directions.size]
    }

    private fun getWeatherEmoji(description: String): String {
        val normalized = description.lowercase(Locale.ROOT)
        return when {
            "despejado" in normalized -> "☀️"
            "tormenta" in normalized -> "⛈️"
            "llovizna" in normalized -> "🌦️"
            "lluvia" in normalized || "chubasco" in normalized -> "🌧️"
            "nieve" in normalized -> "🌨️"
            "niebla" in normalized -> "🌫️"
            "nublado" in normalized || "cubierto" in normalized -> "☁️"
            else -> "🌡️"
        }
    }

    private enum class LocationSource(val serialized: String) {
        DEVICE("device"),
        SEARCH("search"),
        COORDINATES("coordinates")
    }

    private data class ResolvedLocation(
        val latitude: Double,
        val longitude: Double,
        val displayName: String,
        val countryCode: String?,
        val countryName: String?,
        val source: LocationSource,
        val accuracyMeters: Float? = null,
        val locationIsStale: Boolean = false
    )

    private data class WeatherUnits(
        val openMeteoTemperatureUnit: String,
        val openMeteoWindUnit: String,
        val temperatureSymbol: String,
        val speedUnit: String,
        val visibilityUnit: String,
        val convertCelsiusToKelvin: Boolean
    ) {
        fun convertTemperature(value: Double): Double =
            if (convertCelsiusToKelvin) value + 273.15 else value
    }

    private data class CacheEntry(
        val content: String,
        val createdAtMillis: Long,
        val freshUntilMillis: Long
    )

    private data class GeoCacheEntry(
        val location: ResolvedLocation,
        val cachedAtMillis: Long
    )

    private data class ForecastDaySummary(
        val epochSeconds: Long,
        val minTemp: Double,
        val maxTemp: Double,
        val avgHumidity: Int,
        val maxPop: Int,
        val conditionId: Int,
        val icon: String,
        val description: String
    )

    private data class WeatherConditionDescriptor(
        val conditionId: Int,
        val icon: String,
        val description: String
    )

    private data class AirQualityData(
        val aqi: Int,
        val label: String,
        val emoji: String,
        val recommendation: String,
        val co: Double?,
        val no2: Double?,
        val o3: Double?,
        val so2: Double?,
        val pm2_5: Double?,
        val pm10: Double?,
        val observedAtEpoch: Long?,
        val timezoneOffsetSeconds: Int,
        val cachedAtMillis: Long
    )

    private data class AqiDescriptor(
        val label: String,
        val emoji: String,
        val recommendation: String
    )

    private class WeatherFailure(
        val code: WeatherErrorCode,
        val userMessage: String
    ) : Exception(userMessage)
}

private val WEATHER_MARKER_REGEX = Regex(
    """<!--WEATHER_DATA:(.*?)-->""",
    setOf(RegexOption.DOT_MATCHES_ALL)
)
