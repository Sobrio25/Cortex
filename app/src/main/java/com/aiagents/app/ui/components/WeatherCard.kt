package com.aiagents.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

// --- Data classes ---

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
    val speedUnit: String
)

data class ForecastWeatherData(
    val city: String,
    val country: String,
    val unitSymbol: String,
    val days: List<ForecastDayData>
)

data class ForecastDayData(
    val date: String,
    val minTemp: Double,
    val maxTemp: Double,
    val avgHumidity: Int,
    val maxPop: Int,
    val conditionId: Int,
    val icon: String,
    val description: String
)

// --- Weather condition mapping ---

enum class WeatherCondition {
    CLEAR_DAY, CLEAR_NIGHT, PARTLY_CLOUDY, CLOUDY, OVERCAST,
    RAIN, DRIZZLE, THUNDERSTORM, SNOW, MIST_FOG
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

fun getWeatherGradient(condition: WeatherCondition): Brush {
    return when (condition) {
        WeatherCondition.CLEAR_DAY -> Brush.verticalGradient(listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)))
        WeatherCondition.CLEAR_NIGHT -> Brush.verticalGradient(listOf(Color(0xFF1A237E), Color(0xFF0D1B2A)))
        WeatherCondition.PARTLY_CLOUDY -> Brush.verticalGradient(listOf(Color(0xFF90CAF9), Color(0xFF546E7A)))
        WeatherCondition.CLOUDY -> Brush.verticalGradient(listOf(Color(0xFF78909C), Color(0xFF455A64)))
        WeatherCondition.OVERCAST -> Brush.verticalGradient(listOf(Color(0xFF607D8B), Color(0xFF37474F)))
        WeatherCondition.RAIN -> Brush.verticalGradient(listOf(Color(0xFF455A64), Color(0xFF263238)))
        WeatherCondition.DRIZZLE -> Brush.verticalGradient(listOf(Color(0xFF607D8B), Color(0xFF455A64)))
        WeatherCondition.THUNDERSTORM -> Brush.verticalGradient(listOf(Color(0xFF37474F), Color(0xFF1B1B2F)))
        WeatherCondition.SNOW -> Brush.verticalGradient(listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB)))
        WeatherCondition.MIST_FOG -> Brush.verticalGradient(listOf(Color(0xFFB0BEC5), Color(0xFF78909C)))
    }
}

fun getWeatherTextColor(condition: WeatherCondition): Color {
    return when (condition) {
        WeatherCondition.SNOW, WeatherCondition.MIST_FOG -> Color(0xFF263238)
        else -> Color.White
    }
}

fun getConditionEmoji(condition: WeatherCondition): String {
    return when (condition) {
        WeatherCondition.CLEAR_DAY -> "\u2600\uFE0F"
        WeatherCondition.CLEAR_NIGHT -> "\uD83C\uDF19"
        WeatherCondition.PARTLY_CLOUDY -> "\u26C5"
        WeatherCondition.CLOUDY -> "\u2601\uFE0F"
        WeatherCondition.OVERCAST -> "\uD83C\uDF25\uFE0F"
        WeatherCondition.RAIN -> "\uD83C\uDF27\uFE0F"
        WeatherCondition.DRIZZLE -> "\uD83C\uDF26\uFE0F"
        WeatherCondition.THUNDERSTORM -> "\u26C8\uFE0F"
        WeatherCondition.SNOW -> "\uD83C\uDF28\uFE0F"
        WeatherCondition.MIST_FOG -> "\uD83C\uDF2B\uFE0F"
    }
}

// --- JSON parsers ---

fun parseCurrentWeatherJson(json: String): CurrentWeatherData? {
    return try {
        val obj = JSONObject(json)
        if (obj.optString("type") != "current") return null
        CurrentWeatherData(
            city = obj.getString("city"),
            country = obj.getString("country"),
            conditionId = obj.getInt("conditionId"),
            icon = obj.getString("icon"),
            description = obj.getString("description"),
            temp = obj.getDouble("temp"),
            feelsLike = obj.getDouble("feelsLike"),
            humidity = obj.getInt("humidity"),
            pressure = obj.getInt("pressure"),
            windSpeed = obj.getDouble("windSpeed"),
            windDeg = obj.getInt("windDeg"),
            visibility = obj.getDouble("visibility"),
            clouds = obj.getInt("clouds"),
            sunrise = obj.getString("sunrise"),
            sunset = obj.getString("sunset"),
            unitSymbol = obj.getString("unitSymbol"),
            speedUnit = obj.getString("speedUnit")
        )
    } catch (e: Exception) {
        null
    }
}

fun parseForecastWeatherJson(json: String): ForecastWeatherData? {
    return try {
        val obj = JSONObject(json)
        if (obj.optString("type") != "forecast") return null
        val daysArray = obj.getJSONArray("days")
        val days = (0 until daysArray.length()).map { i ->
            val day = daysArray.getJSONObject(i)
            ForecastDayData(
                date = day.getString("date"),
                minTemp = day.getDouble("minTemp"),
                maxTemp = day.getDouble("maxTemp"),
                avgHumidity = day.getInt("avgHumidity"),
                maxPop = day.getInt("maxPop"),
                conditionId = day.getInt("conditionId"),
                icon = day.getString("icon"),
                description = day.getString("description")
            )
        }
        ForecastWeatherData(
            city = obj.getString("city"),
            country = obj.getString("country"),
            unitSymbol = obj.getString("unitSymbol"),
            days = days
        )
    } catch (e: Exception) {
        null
    }
}

fun extractWeatherDataJson(content: String): String? {
    val regex = Regex("""<!--WEATHER_DATA:(.*?)-->""")
    return regex.find(content)?.groupValues?.get(1)
}

// --- Composables ---

@Composable
fun CurrentWeatherCard(
    data: CurrentWeatherData,
    modifier: Modifier = Modifier
) {
    val condition = mapCondition(data.conditionId, data.icon)
    val gradient = getWeatherGradient(condition)
    val textColor = getWeatherTextColor(condition)
    val secondaryColor = textColor.copy(alpha = 0.7f)
    val emoji = getConditionEmoji(condition)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // City name
            Text(
                text = "${data.city}, ${data.country}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Main weather icon + temperature
            Text(
                text = emoji,
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "%.1f%s".format(data.temp, data.unitSymbol),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            )
            Text(
                text = data.description,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = secondaryColor
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WeatherStat(label = "Sensacion", value = "%.0f%s".format(data.feelsLike, data.unitSymbol), textColor = textColor, secondaryColor = secondaryColor)
                WeatherStat(label = "Humedad", value = "${data.humidity}%", textColor = textColor, secondaryColor = secondaryColor)
                WeatherStat(label = "Viento", value = "%.1f %s".format(data.windSpeed, data.speedUnit), textColor = textColor, secondaryColor = secondaryColor)
                WeatherStat(label = "Visibilidad", value = "%.0f km".format(data.visibility), textColor = textColor, secondaryColor = secondaryColor)
            }

            // Sunrise / Sunset
            if (data.sunrise.isNotEmpty() && data.sunset.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = "\uD83C\uDF05 ${data.sunrise}",
                        style = MaterialTheme.typography.bodyMedium.copy(color = secondaryColor)
                    )
                    Text(
                        text = "\uD83C\uDF07 ${data.sunset}",
                        style = MaterialTheme.typography.bodyMedium.copy(color = secondaryColor)
                    )
                }
            }
        }
    }
}

@Composable
fun ForecastWeatherCard(
    data: ForecastWeatherData,
    modifier: Modifier = Modifier
) {
    val firstCondition = if (data.days.isNotEmpty()) {
        mapCondition(data.days[0].conditionId, data.days[0].icon)
    } else WeatherCondition.CLEAR_DAY
    val gradient = getWeatherGradient(firstCondition)
    val textColor = getWeatherTextColor(firstCondition)
    val secondaryColor = textColor.copy(alpha = 0.7f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Pronostico: ${data.city}, ${data.country}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(data.days) { day ->
                    ForecastDayMiniCard(day = day, unitSymbol = data.unitSymbol, parentTextColor = textColor, parentSecondaryColor = secondaryColor)
                }
            }
        }
    }
}

@Composable
private fun ForecastDayMiniCard(
    day: ForecastDayData,
    unitSymbol: String,
    parentTextColor: Color,
    parentSecondaryColor: Color
) {
    val condition = mapCondition(day.conditionId, day.icon)
    val emoji = getConditionEmoji(condition)

    Column(
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.15f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Date (short)
        val shortDate = day.date.split(" ").take(2).joinToString(" ")
        Text(
            text = shortDate,
            style = MaterialTheme.typography.labelSmall.copy(
                color = parentSecondaryColor,
                fontWeight = FontWeight.Medium
            ),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = emoji, fontSize = 28.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "%.0f / %.0f".format(day.maxTemp, day.minTemp),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                color = parentTextColor
            )
        )
        Text(
            text = unitSymbol,
            style = MaterialTheme.typography.labelSmall.copy(color = parentSecondaryColor)
        )
        if (day.maxPop > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "\uD83D\uDCA7 ${day.maxPop}%",
                style = MaterialTheme.typography.labelSmall.copy(color = parentSecondaryColor)
            )
        }
    }
}

@Composable
private fun WeatherStat(
    label: String,
    value: String,
    textColor: Color,
    secondaryColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = secondaryColor
            )
        )
    }
}
