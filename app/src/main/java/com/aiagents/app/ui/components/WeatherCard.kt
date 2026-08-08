package com.aiagents.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiagents.app.domain.model.formatTemperatureValue
import org.json.JSONObject

data class CurrentWeatherData(
    val city: String,
    val country: String,
    val conditionId: Int,
    val icon: String,
    val description: String,
    val temp: Double,
    val feelsLike: Double,
    val humidity: Int,
    val pressure: Int,
    val windSpeed: Double,
    val windDeg: Int,
    val visibility: Double,
    val clouds: Int,
    val sunrise: String,
    val sunset: String,
    val unitSymbol: String,
    val speedUnit: String,
    val minTemp: Double = temp,
    val maxTemp: Double = temp,
    val precipitationMm: Double = 0.0,
    val visibilityUnit: String = "km",
    val aqi: Int? = null,
    val aqiLabel: String? = null,
    val updatedAt: String = "",
    val timezone: String = "",
    val source: String = "Open-Meteo",
    val locationSource: String = "search",
    val isStale: Boolean = false,
    val isCached: Boolean = false
)

data class ForecastWeatherData(
    val city: String,
    val country: String,
    val unitSymbol: String,
    val days: List<ForecastDayData>,
    val aqi: Int? = null,
    val aqiLabel: String? = null,
    val updatedAt: String = "",
    val timezone: String = "",
    val source: String = "Open-Meteo",
    val locationSource: String = "search",
    val isStale: Boolean = false,
    val isCached: Boolean = false,
    val requestedDate: String = "",
    val dayOffset: Int? = null
)

data class ForecastDayData(
    val date: String,
    val isoDate: String = "",
    val minTemp: Double,
    val maxTemp: Double,
    val avgHumidity: Int,
    val maxPop: Int,
    val conditionId: Int,
    val icon: String,
    val description: String
)

data class AirQualityWeatherData(
    val city: String,
    val country: String,
    val aqi: Int,
    val aqiLabel: String,
    val recommendation: String,
    val pm2_5: Double? = null,
    val pm10: Double? = null,
    val o3: Double? = null,
    val no2: Double? = null,
    val updatedAt: String = "",
    val timezone: String = "UTC",
    val source: String = "Open-Meteo",
    val isStale: Boolean = false,
    val isCached: Boolean = false
)

data class WeatherErrorData(
    val code: String,
    val message: String,
    val source: String = "Open-Meteo"
)

sealed interface WeatherCardState {
    data class Current(val data: CurrentWeatherData) : WeatherCardState
    data class Forecast(val data: ForecastWeatherData) : WeatherCardState
    data class AirQuality(val data: AirQualityWeatherData) : WeatherCardState
    data class Loading(val locationLabel: String = "tu ubicación") : WeatherCardState
    data class Error(val data: WeatherErrorData) : WeatherCardState
    data object Empty : WeatherCardState
}

enum class WeatherCondition {
    CLEAR_DAY, CLEAR_NIGHT, PARTLY_CLOUDY, CLOUDY, OVERCAST,
    RAIN, DRIZZLE, THUNDERSTORM, SNOW, MIST_FOG
}

internal enum class ForecastLayoutMode {
    EMPTY,
    FEATURED,
    DISTRIBUTED,
    SCROLLING
}

internal fun resolveForecastLayoutMode(
    dayCount: Int,
    availableWidthDp: Float
): ForecastLayoutMode {
    if (dayCount <= 0) return ForecastLayoutMode.EMPTY
    if (dayCount == 1) return ForecastLayoutMode.FEATURED

    val gapWidth = 10f * (dayCount - 1)
    val availablePerDay = (availableWidthDp.coerceAtLeast(0f) - gapWidth) / dayCount
    return if (availablePerDay >= 118f) {
        ForecastLayoutMode.DISTRIBUTED
    } else {
        ForecastLayoutMode.SCROLLING
    }
}

fun mapCondition(conditionId: Int, icon: String): WeatherCondition {
    val isNight = icon.endsWith("n")
    return when (conditionId) {
        in 200..299 -> WeatherCondition.THUNDERSTORM
        in 300..399 -> WeatherCondition.DRIZZLE
        in 500..599 -> WeatherCondition.RAIN
        in 600..699 -> WeatherCondition.SNOW
        in 700..799 -> WeatherCondition.MIST_FOG
        800 -> if (isNight) WeatherCondition.CLEAR_NIGHT else WeatherCondition.CLEAR_DAY
        801 -> WeatherCondition.PARTLY_CLOUDY
        802 -> WeatherCondition.CLOUDY
        in 803..804 -> WeatherCondition.OVERCAST
        else -> WeatherCondition.CLEAR_DAY
    }
}

fun getWeatherGradient(condition: WeatherCondition): Brush = when (condition) {
    WeatherCondition.CLEAR_DAY -> Brush.linearGradient(listOf(Color(0xFF1565C0), Color(0xFF1E88E5), Color(0xFF4FC3F7)))
    WeatherCondition.CLEAR_NIGHT -> Brush.linearGradient(listOf(Color(0xFF0B1035), Color(0xFF1A237E), Color(0xFF3949AB)))
    WeatherCondition.PARTLY_CLOUDY -> Brush.linearGradient(listOf(Color(0xFF1A6FB5), Color(0xFF4F8FBF), Color(0xFF8FA9B8)))
    WeatherCondition.CLOUDY -> Brush.linearGradient(listOf(Color(0xFF37474F), Color(0xFF546E7A), Color(0xFF78909C)))
    WeatherCondition.OVERCAST -> Brush.linearGradient(listOf(Color(0xFF263238), Color(0xFF37474F), Color(0xFF546E7A)))
    WeatherCondition.RAIN -> Brush.linearGradient(listOf(Color(0xFF16232E), Color(0xFF2A3F4D), Color(0xFF46607A)))
    WeatherCondition.DRIZZLE -> Brush.linearGradient(listOf(Color(0xFF2E4756), Color(0xFF4A6572), Color(0xFF728E9E)))
    WeatherCondition.THUNDERSTORM -> Brush.linearGradient(listOf(Color(0xFF14122B), Color(0xFF2C2450), Color(0xFF4A3F6B)))
    WeatherCondition.SNOW -> Brush.linearGradient(listOf(Color(0xFFE1F5FE), Color(0xFFB3E5FC), Color(0xFF81D4FA)))
    WeatherCondition.MIST_FOG -> Brush.linearGradient(listOf(Color(0xFFCFD8DC), Color(0xFFB0BEC5), Color(0xFF90A4AE)))
}

fun getWeatherTextColor(condition: WeatherCondition): Color = when (condition) {
    WeatherCondition.SNOW, WeatherCondition.MIST_FOG -> Color(0xFF17242A)
    else -> Color.White
}

fun getConditionEmoji(condition: WeatherCondition): String = when (condition) {
    WeatherCondition.CLEAR_DAY -> "☀️"
    WeatherCondition.CLEAR_NIGHT -> "🌙"
    WeatherCondition.PARTLY_CLOUDY -> "🌤️"
    WeatherCondition.CLOUDY -> "☁️"
    WeatherCondition.OVERCAST -> "🌥️"
    WeatherCondition.RAIN -> "🌧️"
    WeatherCondition.DRIZZLE -> "🌦️"
    WeatherCondition.THUNDERSTORM -> "⛈️"
    WeatherCondition.SNOW -> "❄️"
    WeatherCondition.MIST_FOG -> "🌫️"
}

fun parseCurrentWeatherJson(json: String): CurrentWeatherData? {
    return try {
        val obj = JSONObject(json)
        if (obj.optString("type") != "current") return null
        val temp = obj.getDouble("temp")
        CurrentWeatherData(
        city = obj.optString("city", "Ubicación actual"),
        country = obj.optString("country"),
        conditionId = obj.optInt("conditionId", 800),
        icon = obj.optString("icon", "01d"),
        description = obj.optString("description", "Condiciones actuales"),
        temp = temp,
        feelsLike = obj.optDouble("feelsLike", temp),
        humidity = obj.optInt("humidity"),
        pressure = obj.optInt("pressure"),
        windSpeed = obj.optDouble("windSpeed"),
        windDeg = obj.optInt("windDeg"),
        visibility = obj.optDouble("visibility"),
        clouds = obj.optInt("clouds"),
        sunrise = obj.optString("sunrise"),
        sunset = obj.optString("sunset"),
        unitSymbol = obj.optString("unitSymbol", "°C"),
        speedUnit = obj.optString("speedUnit", "m/s"),
        minTemp = obj.optDouble("minTemp", temp),
        maxTemp = obj.optDouble("maxTemp", temp),
        precipitationMm = obj.optDouble("precipitationMm"),
        visibilityUnit = obj.optString("visibilityUnit", "km"),
        aqi = obj.optionalInt("aqi"),
        aqiLabel = obj.optionalString("aqiLabel"),
        updatedAt = obj.optString("updatedAt"),
        timezone = obj.optString("timezone"),
        source = obj.optString("source", "Open-Meteo"),
        locationSource = obj.optString("locationSource", "search"),
        isStale = obj.optBoolean("isStale"),
        isCached = obj.optBoolean("isCached")
        )
    } catch (_: Exception) {
        null
    }
}

fun parseForecastWeatherJson(json: String): ForecastWeatherData? {
    return try {
        val obj = JSONObject(json)
        if (obj.optString("type") != "forecast") return null
        val daysArray = obj.getJSONArray("days")
        val days = (0 until daysArray.length()).map { index ->
            val day = daysArray.getJSONObject(index)
            ForecastDayData(
                date = day.getString("date"),
                isoDate = day.optString("isoDate"),
                minTemp = day.getDouble("minTemp"),
                maxTemp = day.getDouble("maxTemp"),
                avgHumidity = day.optInt("avgHumidity"),
                maxPop = day.optInt("maxPop"),
                conditionId = day.optInt("conditionId", 800),
                icon = day.optString("icon", "01d"),
                description = day.optString("description")
            )
        }
        ForecastWeatherData(
            city = obj.optString("city", "Ubicación actual"),
            country = obj.optString("country"),
            unitSymbol = obj.optString("unitSymbol", "°C"),
            days = days,
            aqi = obj.optionalInt("aqi"),
            aqiLabel = obj.optionalString("aqiLabel"),
            updatedAt = obj.optString("updatedAt"),
            timezone = obj.optString("timezone"),
            source = obj.optString("source", "Open-Meteo"),
            locationSource = obj.optString("locationSource", "search"),
            isStale = obj.optBoolean("isStale"),
            isCached = obj.optBoolean("isCached"),
            requestedDate = obj.optString("requestedDate"),
            dayOffset = obj.optionalInt("dayOffset")
        )
    } catch (_: Exception) {
        null
    }
}

fun parseAirQualityWeatherJson(json: String): AirQualityWeatherData? {
    return try {
        val obj = JSONObject(json)
        if (obj.optString("type") != "air_quality") return null
        AirQualityWeatherData(
            city = obj.optString("city", "Ubicación actual"),
            country = obj.optString("country"),
            aqi = obj.getInt("aqi"),
            aqiLabel = obj.optString("aqiLabel", "Sin clasificar"),
            recommendation = obj.optString("recommendation"),
            pm2_5 = obj.optionalDouble("pm2_5"),
            pm10 = obj.optionalDouble("pm10"),
            o3 = obj.optionalDouble("o3"),
            no2 = obj.optionalDouble("no2"),
            updatedAt = obj.optString("updatedAt"),
            timezone = obj.optString("timezone", "UTC"),
            source = obj.optString("source", "Open-Meteo"),
            isStale = obj.optBoolean("isStale"),
            isCached = obj.optBoolean("isCached")
        )
    } catch (_: Exception) {
        null
    }
}

fun parseWeatherErrorJson(json: String): WeatherErrorData? {
    return try {
        val obj = JSONObject(json)
        if (obj.optString("type") != "error") return null
        WeatherErrorData(
            code = obj.optString("code", "UNKNOWN"),
            message = obj.optString("message", "No se pudieron cargar los datos del clima."),
            source = obj.optString("source", "Open-Meteo")
        )
    } catch (_: Exception) {
        null
    }
}

fun parseWeatherCardState(json: String): WeatherCardState =
    parseCurrentWeatherJson(json)?.let(WeatherCardState::Current)
        ?: parseForecastWeatherJson(json)?.let(WeatherCardState::Forecast)
        ?: parseAirQualityWeatherJson(json)?.let(WeatherCardState::AirQuality)
        ?: parseWeatherErrorJson(json)?.let(WeatherCardState::Error)
        ?: WeatherCardState.Empty

fun extractWeatherDataJson(content: String): String? =
    Regex("""<!--WEATHER_DATA:(.*?)-->""", RegexOption.DOT_MATCHES_ALL)
        .find(content)
        ?.groupValues
        ?.get(1)

@Composable
fun WeatherResultCard(
    weatherJson: String,
    modifier: Modifier = Modifier
) {
    WeatherCard(state = parseWeatherCardState(weatherJson), modifier = modifier)
}

@Composable
fun WeatherCard(
    state: WeatherCardState,
    modifier: Modifier = Modifier
) {
    when (state) {
        is WeatherCardState.Current -> CurrentWeatherCard(state.data, modifier)
        is WeatherCardState.Forecast -> ForecastWeatherCard(state.data, modifier)
        is WeatherCardState.AirQuality -> AirQualityWeatherCard(state.data, modifier)
        is WeatherCardState.Loading -> WeatherLoadingCard(state.locationLabel, modifier)
        is WeatherCardState.Error -> WeatherErrorCard(state.data, modifier)
        WeatherCardState.Empty -> Unit
    }
}

@Composable
fun CurrentWeatherCard(
    data: CurrentWeatherData,
    modifier: Modifier = Modifier
) {
    val condition = mapCondition(data.conditionId, data.icon)
    val textColor = getWeatherTextColor(condition)
    val secondaryColor = textColor.copy(alpha = 0.84f)
    val location = displayLocation(data.city, data.country)
    val accessibilityText = buildString {
        append("Clima en $location. ${data.description}. ")
        append("Temperatura ${formatTemperatureValue(data.temp)}${data.unitSymbol}, ")
        append("mínima ${formatTemperatureValue(data.minTemp)}, máxima ${formatTemperatureValue(data.maxTemp)}. ")
        append("Precipitación ${data.precipitationMm.rounded()} milímetros. ")
        data.aqi?.let { append("Calidad del aire $it de 5, ${data.aqiLabel.orEmpty()}. ") }
        if (data.updatedAt.isNotBlank()) append("Actualizado ${data.updatedAt}. ")
        append("Fuente ${data.source}.")
    }

    val shape = RoundedCornerShape(28.dp)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(getWeatherGradient(condition))
            .border(1.dp, textColor.copy(alpha = 0.16f), shape)
            .semantics(mergeDescendants = true) { contentDescription = accessibilityText }
            .padding(20.dp)
    ) {
        val compact = maxWidth < 310.dp
        val statsColumns = if (maxWidth < 360.dp) 2 else 3
        Column(modifier = Modifier.fillMaxWidth()) {
            WeatherHeader(
                title = location,
                badge = locationSourceLabel(data.locationSource),
                textColor = textColor,
                secondaryColor = secondaryColor
            )
            if (data.isStale) {
                Spacer(Modifier.height(10.dp))
                StatusPill("Datos guardados; la fuente no respondió", textColor)
            }
            Spacer(Modifier.height(14.dp))
            CurrentWeatherHero(
                data = data,
                condition = condition,
                textColor = textColor,
                secondaryColor = secondaryColor,
                compact = compact
            )
            Spacer(Modifier.height(16.dp))
            AdaptiveWeatherStats(
                stats = buildList {
                    add(WeatherStatData("Sensación", "${formatTemperatureValue(data.feelsLike)}${data.unitSymbol}"))
                    add(WeatherStatData("Humedad", "${data.humidity}%"))
                    add(WeatherStatData("Precipitación", "${data.precipitationMm.rounded()} mm"))
                    add(WeatherStatData("Viento", "${data.windSpeed.rounded()} ${data.speedUnit}"))
                    if (data.visibility > 0) add(WeatherStatData("Visibilidad", "${data.visibility.rounded()} ${data.visibilityUnit}"))
                    data.aqi?.let { add(WeatherStatData("AQI", "$it/5 · ${data.aqiLabel.orEmpty()}")) }
                },
                columns = statsColumns,
                textColor = textColor,
                secondaryColor = secondaryColor
            )
            if (data.sunrise.isNotBlank() && data.sunset.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(textColor.copy(alpha = 0.10f))
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text("🌅 ${data.sunrise}", color = textColor, style = MaterialTheme.typography.bodyMedium)
                    Text("🌇 ${data.sunset}", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
            }
            WeatherFooter(data.updatedAt, data.timezone, data.source, data.isCached, textColor)
        }
    }
}

@Composable
fun ForecastWeatherCard(
    data: ForecastWeatherData,
    modifier: Modifier = Modifier
) {
    val condition = data.days.firstOrNull()?.let { mapCondition(it.conditionId, it.icon) }
        ?: WeatherCondition.CLEAR_DAY
    val textColor = getWeatherTextColor(condition)
    val secondaryColor = textColor.copy(alpha = 0.84f)
    val location = displayLocation(data.city, data.country)

    val shape = RoundedCornerShape(28.dp)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(getWeatherGradient(condition))
            .border(1.dp, textColor.copy(alpha = 0.16f), shape)
            .semantics {
                contentDescription = "Pronóstico para $location, ${data.days.size} días. Fuente ${data.source}."
            }
            .padding(20.dp)
    ) {
        val layoutMode = resolveForecastLayoutMode(data.days.size, maxWidth.value)
        Column(Modifier.fillMaxWidth()) {
            WeatherHeader(
                title = "Pronóstico · $location",
                badge = locationSourceLabel(data.locationSource),
                textColor = textColor,
                secondaryColor = secondaryColor
            )
            if (data.isStale) {
                Spacer(Modifier.height(10.dp))
                StatusPill("Pronóstico guardado; puede estar desactualizado", textColor)
            }
            data.aqi?.let {
                Spacer(Modifier.height(10.dp))
                StatusPill("AQI $it/5 · ${data.aqiLabel.orEmpty()}", textColor)
            }
            Spacer(Modifier.height(14.dp))
            when (layoutMode) {
                ForecastLayoutMode.EMPTY -> EmptyForecast(
                    textColor = textColor,
                    secondaryColor = secondaryColor
                )
                ForecastLayoutMode.FEATURED -> FeaturedForecastDay(
                    day = data.days.first(),
                    unitSymbol = data.unitSymbol,
                    textColor = textColor,
                    secondaryColor = secondaryColor
                )
                ForecastLayoutMode.DISTRIBUTED,
                ForecastLayoutMode.SCROLLING -> AdaptiveForecastDays(
                    days = data.days,
                    unitSymbol = data.unitSymbol,
                    textColor = textColor,
                    secondaryColor = secondaryColor,
                    scrolling = layoutMode == ForecastLayoutMode.SCROLLING
                )
            }
            WeatherFooter(data.updatedAt, data.timezone, data.source, data.isCached, textColor)
        }
    }
}

@Composable
fun AirQualityWeatherCard(
    data: AirQualityWeatherData,
    modifier: Modifier = Modifier
) {
    val accent = aqiColor(data.aqi)
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Calidad del aire en ${displayLocation(data.city, data.country)}: ${data.aqi} de 5, ${data.aqiLabel}. ${data.recommendation}. Fuente ${data.source}."
            },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), shape)
                .padding(18.dp)
        ) {
            val compact = maxWidth < 310.dp
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "Calidad del aire · ${displayLocation(data.city, data.country)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(14.dp))
                if (compact) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AirQualityIndexBadge(data.aqi, accent)
                        Spacer(Modifier.height(10.dp))
                        AirQualitySummary(
                            data = data,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AirQualityIndexBadge(data.aqi, accent)
                        Spacer(Modifier.width(14.dp))
                        AirQualitySummary(data = data, modifier = Modifier.weight(1f))
                    }
                }
                val pollutants = buildList {
                    data.pm2_5?.let { add(WeatherStatData("PM2.5", "${it.rounded()} μg/m³")) }
                    data.pm10?.let { add(WeatherStatData("PM10", "${it.rounded()} μg/m³")) }
                    data.o3?.let { add(WeatherStatData("Ozono", "${it.rounded()} μg/m³")) }
                    data.no2?.let { add(WeatherStatData("NO₂", "${it.rounded()} μg/m³")) }
                }
                if (pollutants.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    AdaptiveWeatherStats(
                        stats = pollutants,
                        columns = if (compact) 1 else 2,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        darkTile = false
                    )
                }
                WeatherFooter(
                    data.updatedAt,
                    data.timezone,
                    data.source,
                    data.isCached,
                    MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun AirQualityIndexBadge(aqi: Int, accent: Color) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$aqi/5",
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "AQI",
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = 0.82f)
            )
        }
    }
}

@Composable
private fun AirQualitySummary(
    data: AirQualityWeatherData,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(
            text = data.aqiLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = textAlign
        )
        if (data.recommendation.isNotBlank()) {
            Text(
                text = data.recommendation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = textAlign
            )
        }
    }
}

@Composable
private fun CurrentWeatherHero(
    data: CurrentWeatherData,
    condition: WeatherCondition,
    textColor: Color,
    secondaryColor: Color,
    compact: Boolean
) {
    if (compact) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WeatherConditionBadge(condition, compact = true, textColor = textColor)
            Spacer(Modifier.height(9.dp))
            CurrentTemperatureSummary(
                data = data,
                textColor = textColor,
                secondaryColor = secondaryColor,
                horizontalAlignment = Alignment.CenterHorizontally,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherConditionBadge(condition, compact = false, textColor = textColor)
            Spacer(Modifier.width(18.dp))
            CurrentTemperatureSummary(
                data = data,
                textColor = textColor,
                secondaryColor = secondaryColor,
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun WeatherConditionBadge(condition: WeatherCondition, compact: Boolean, textColor: Color) {
    Box(
        modifier = Modifier
            .size(if (compact) 72.dp else 80.dp)
            .clip(RoundedCornerShape(if (compact) 22.dp else 24.dp))
            .background(textColor.copy(alpha = 0.14f))
            .border(
                1.dp,
                textColor.copy(alpha = 0.18f),
                RoundedCornerShape(if (compact) 22.dp else 24.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = getConditionEmoji(condition),
            fontSize = if (compact) 40.sp else 46.sp,
            modifier = Modifier.clearAndSetSemantics { }
        )
    }
}

@Composable
private fun CurrentTemperatureSummary(
    data: CurrentWeatherData,
    textColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal,
    textAlign: TextAlign
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(
            text = "${formatTemperatureValue(data.temp)}${data.unitSymbol}",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1
        )
        Text(
            text = data.description,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = secondaryColor,
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Mín. ${formatTemperatureValue(data.minTemp)}${data.unitSymbol}  ·  Máx. ${formatTemperatureValue(data.maxTemp)}${data.unitSymbol}",
            style = MaterialTheme.typography.labelLarge,
            color = textColor.copy(alpha = 0.92f),
            textAlign = textAlign
        )
    }
}

@Composable
private fun FeaturedForecastDay(
    day: ForecastDayData,
    unitSymbol: String,
    textColor: Color,
    secondaryColor: Color
) {
    val condition = mapCondition(day.conditionId, day.icon)
    val shape = RoundedCornerShape(22.dp)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(textColor.copy(alpha = 0.10f))
            .border(1.dp, textColor.copy(alpha = 0.14f), shape)
            .semantics(mergeDescendants = true) {
                contentDescription = forecastDayDescription(day, unitSymbol)
            }
            .padding(16.dp)
    ) {
        val compact = maxWidth < 280.dp
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "DÍA CONSULTADO",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = secondaryColor
            )
            Spacer(Modifier.height(8.dp))
            FeaturedForecastSummary(
                day = day,
                condition = condition,
                unitSymbol = unitSymbol,
                textColor = textColor,
                secondaryColor = secondaryColor
            )
            Spacer(Modifier.height(14.dp))
            AdaptiveWeatherStats(
                stats = listOf(
                    WeatherStatData("Humedad media", "${day.avgHumidity}%"),
                    WeatherStatData("Prob. de lluvia", "${day.maxPop}%")
                ),
                columns = if (compact) 1 else 2,
                textColor = textColor,
                secondaryColor = secondaryColor
            )
        }
    }
}

@Composable
private fun FeaturedForecastSummary(
    day: ForecastDayData,
    condition: WeatherCondition,
    unitSymbol: String,
    textColor: Color,
    secondaryColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = getConditionEmoji(condition),
            fontSize = 44.sp,
            modifier = Modifier.clearAndSetSemantics { }
        )
        Spacer(Modifier.width(14.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = day.date,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (day.description.isNotBlank()) {
                Text(
                    text = day.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = "${formatTemperatureValue(day.maxTemp)}${unitSymbol} / ${formatTemperatureValue(day.minTemp)}${unitSymbol}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EmptyForecast(textColor: Color, secondaryColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(textColor.copy(alpha = 0.08f))
            .border(1.dp, textColor.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("—", style = MaterialTheme.typography.headlineSmall, color = textColor)
        Text(
            "No hay días disponibles",
            color = secondaryColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AdaptiveForecastDays(
    days: List<ForecastDayData>,
    unitSymbol: String,
    textColor: Color,
    secondaryColor: Color,
    scrolling: Boolean
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 10.dp
        val availablePerDay = (maxWidth - gap * (days.size - 1)) / days.size
        if (!scrolling) {
            val cardWidth = minOf(availablePerDay, 154.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
            ) {
                days.forEach { day ->
                    ForecastDayMiniCard(
                        day = day,
                        unitSymbol = unitSymbol,
                        textColor = textColor,
                        secondaryColor = secondaryColor,
                        modifier = Modifier.width(cardWidth)
                    )
                }
            }
        } else {
            val cardWidth = minOf(146.dp, maxOf(122.dp, maxWidth * 0.44f))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                items(days, key = { it.isoDate.ifBlank { it.date } }) { day ->
                    ForecastDayMiniCard(
                        day = day,
                        unitSymbol = unitSymbol,
                        textColor = textColor,
                        secondaryColor = secondaryColor,
                        modifier = Modifier.width(cardWidth)
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastDayMiniCard(
    day: ForecastDayData,
    unitSymbol: String,
    textColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    val condition = mapCondition(day.conditionId, day.icon)
    Column(
        modifier = modifier
            .heightIn(min = 174.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(textColor.copy(alpha = 0.10f))
            .border(1.dp, textColor.copy(alpha = 0.13f), RoundedCornerShape(18.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = forecastDayDescription(day, unitSymbol)
            }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = day.date,
            style = MaterialTheme.typography.labelMedium,
            color = secondaryColor,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Text(
            getConditionEmoji(condition),
            fontSize = 30.sp,
            modifier = Modifier.clearAndSetSemantics { }
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "${formatTemperatureValue(day.maxTemp)}${unitSymbol} / ${formatTemperatureValue(day.minTemp)}${unitSymbol}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1
        )
        Text(
            text = day.description.ifBlank { "Condiciones previstas" },
            style = MaterialTheme.typography.labelSmall,
            color = secondaryColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = 32.dp)
        )
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = "💧 ${day.maxPop}%",
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor
            )
            Text(
                text = "H ${day.avgHumidity}%",
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor
            )
        }
    }
}

@Composable
private fun AdaptiveWeatherStats(
    stats: List<WeatherStatData>,
    columns: Int,
    textColor: Color,
    secondaryColor: Color,
    darkTile: Boolean = true
) {
    stats.chunked(columns.coerceAtLeast(1)).forEachIndexed { rowIndex, rowStats ->
        if (rowIndex > 0) Spacer(Modifier.height(8.dp))
        val missingSlotWeight = (columns - rowStats.size).coerceAtLeast(0) / 2f
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (missingSlotWeight > 0f) Spacer(Modifier.weight(missingSlotWeight))
            rowStats.forEach { stat ->
                WeatherStat(
                    stat = stat,
                    textColor = textColor,
                    secondaryColor = secondaryColor,
                    darkTile = darkTile,
                    modifier = Modifier.weight(1f)
                )
            }
            if (missingSlotWeight > 0f) Spacer(Modifier.weight(missingSlotWeight))
        }
    }
}

@Composable
private fun WeatherStat(
    stat: WeatherStatData,
    textColor: Color,
    secondaryColor: Color,
    darkTile: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (darkTile) textColor.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceContainerHighest
            )
            .then(
                if (darkTile) Modifier.border(
                    1.dp,
                    textColor.copy(alpha = 0.14f),
                    RoundedCornerShape(14.dp)
                ) else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stat.value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stat.label,
            style = MaterialTheme.typography.labelSmall,
            color = secondaryColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun WeatherHeader(
    title: String,
    badge: String,
    textColor: Color,
    secondaryColor: Color
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            Text("Ubicación: $badge", style = MaterialTheme.typography.labelSmall, color = secondaryColor)
        }
    }
}

@Composable
private fun StatusPill(text: String, textColor: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(textColor.copy(alpha = 0.12f))
            .border(1.dp, textColor.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun WeatherFooter(
    updatedAt: String,
    timezone: String,
    source: String,
    isCached: Boolean,
    textColor: Color
) {
    if (updatedAt.isBlank() && source.isBlank()) return
    Spacer(Modifier.height(13.dp))
    val timeLabel = buildString {
        if (updatedAt.isNotBlank()) append("Actualizado $updatedAt")
        if (timezone.isNotBlank()) append(" $timezone")
        if (isCached) append(" · caché")
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val sourceLabel = "Fuente: $source"
        val stacked = maxWidth < 340.dp || timeLabel.length + sourceLabel.length > 54
        if (stacked) {
            Column(Modifier.fillMaxWidth()) {
                if (timeLabel.isNotBlank()) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.78f),
                        maxLines = 2
                    )
                }
                if (source.isNotBlank()) {
                    if (timeLabel.isNotBlank()) Spacer(Modifier.height(2.dp))
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (timeLabel.isNotBlank()) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.78f),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (source.isNotBlank()) {
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.78f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherLoadingCard(locationLabel: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Consultando clima para $locationLabel"
            },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Consultando el clima…", fontWeight = FontWeight.SemiBold)
                Text(locationLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WeatherErrorCard(data: WeatherErrorData, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "No se pudo cargar el clima. ${data.message}"
            },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
            Text("⚠️", fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "No se pudo cargar el clima",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(data.message, color = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.height(5.dp))
                Text(
                    "${data.code} · ${data.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.76f)
                )
            }
        }
    }
}

private data class WeatherStatData(val label: String, val value: String)

private fun forecastDayDescription(day: ForecastDayData, unitSymbol: String): String = buildString {
    append("${day.date}. ")
    if (day.description.isNotBlank()) append("${day.description}. ")
    append("Máxima ${formatTemperatureValue(day.maxTemp)}$unitSymbol, ")
    append("mínima ${formatTemperatureValue(day.minTemp)}$unitSymbol. ")
    append("Probabilidad de precipitación ${day.maxPop} por ciento. ")
    append("Humedad media ${day.avgHumidity} por ciento.")
}

private fun JSONObject.optionalString(name: String): String? =
    if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null

private fun JSONObject.optionalInt(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONObject.optionalDouble(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name) else null

private fun Double.rounded(): String = if (this % 1.0 == 0.0) {
    toInt().toString()
} else {
    String.format(java.util.Locale.getDefault(), "%.1f", this)
}

private fun displayLocation(city: String, country: String): String =
    if (country.isBlank()) city else "$city, $country"

private fun locationSourceLabel(source: String): String = when (source) {
    "device" -> "dispositivo"
    "coordinates" -> "coordenadas elegidas"
    else -> "búsqueda"
}

private fun aqiColor(aqi: Int): Color = when (aqi) {
    1 -> Color(0xFF2E7D32)
    2 -> Color(0xFF8A6D00)
    3 -> Color(0xFFEF6C00)
    4 -> Color(0xFFC62828)
    5 -> Color(0xFF6A1B9A)
    else -> Color(0xFF546E7A)
}
