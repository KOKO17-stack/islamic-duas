package islamic.duas

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class RainWindow(
    val startHour: String,
    val endHour: String,
    val peakChance: Int
)

data class HourlyWeather(
    val hour: String,
    val rainChance: Int,
    val temp: Double,
    val feelsLike: Double
)

enum class HeatLevel(val label: String) {
    MILD("معتدل"),
    MILDY_HOT("گرم"),
    HOT("شدید گرم"),
    EXTREME("انتہائی گرم")
}

data class RainForecast(
    val maxChance: Int,
    val hourlyData: List<HourlyWeather>,
    val isRainExpected: Boolean,
    val rainWindows: List<RainWindow>,
    val todayMaxTemp: Double,
    val todayMinTemp: Double,
    val todayMaxFeelsLike: Double,
    val tomorrowMaxTemp: Double,
    val tomorrowMinTemp: Double,
    val heatLevel: HeatLevel
)

class WeatherEngine(private val context: Context) {

    companion object {
        private const val LAT = 32.05687
        private const val LON = 73.55269
        private const val API_URL =
            "https://api.open-meteo.com/v1/ecmwf?latitude=$LAT&longitude=$LON&hourly=precipitation_probability,temperature_2m,apparent_temperature&daily=temperature_2m_max,temperature_2m_min&timezone=Asia%2FKarachi&forecast_days=2"
        private const val CACHE_TTL_MS = 15 * 60 * 1000L

        private var cachedForecast: RainForecast? = null
        private var lastFetchMs = 0L

        fun getCachedForecast(): RainForecast? {
            if (System.currentTimeMillis() - lastFetchMs < CACHE_TTL_MS) {
                return cachedForecast
            }
            return null
        }

        fun setCachedForecast(forecast: RainForecast?) {
            cachedForecast = forecast
            lastFetchMs = System.currentTimeMillis()
        }

        fun conditionEmoji(rainChance: Int, heatLevel: HeatLevel = HeatLevel.MILD, isNight: Boolean = false): String = when {
            rainChance >= 70 -> "⛈"
            rainChance >= 50 -> "🌧"
            rainChance >= 30 -> "🌦"
            rainChance >= 10 -> "☁️"
            isNight -> "🌙"
            heatLevel >= HeatLevel.HOT -> "☀️"
            else -> "🌤"
        }
    }

    fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun fetchRainForecast(): RainForecast? {
        return try {
            val url = URL(API_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val response = reader.readText()
            reader.close()
            conn.disconnect()
            parseForecast(response)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseForecast(json: String): RainForecast {
        val obj = JSONObject(json)
        val hourly = obj.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val probs = hourly.getJSONArray("precipitation_probability")
        val temps = hourly.getJSONArray("temperature_2m")
        val feels = hourly.getJSONArray("apparent_temperature")

        val daily = obj.getJSONObject("daily")
        val dailyMax = daily.getJSONArray("temperature_2m_max")
        val dailyMin = daily.getJSONArray("temperature_2m_min")

        val now = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        val hourFormat = SimpleDateFormat("h a", Locale.US)

        val hourlyData = mutableListOf<HourlyWeather>()
        var maxChance = 0
        var todayMaxTemp = dailyMax.optDouble(0, 0.0)
        var todayMinTemp = dailyMin.optDouble(0, 0.0)
        var tomorrowMaxTemp = dailyMax.optDouble(1, 0.0)
        var tomorrowMinTemp = dailyMin.optDouble(1, 0.0)
        var todayMaxFeels = 0.0
        val rainWindows = mutableListOf<RainWindow>()
        var windowStart: String? = null
        var windowPeak = 0

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)

        for (i in 0 until times.length()) {
            val timeStr = times.getString(i)
            val prob = probs.optInt(i, 0)
            val temp = temps.optDouble(i, 0.0)
            val feel = feels.optDouble(i, 0.0)
            val timeCal = Calendar.getInstance()
            timeCal.time = dateFormat.parse(timeStr) ?: continue

            val diffHours = (timeCal.timeInMillis - now.timeInMillis) / (60 * 60 * 1000)
            if (diffHours < -1) continue
            if (diffHours > 24) continue

            val hourLabel = hourFormat.format(timeCal.time)
            hourlyData.add(HourlyWeather(hourLabel, prob, temp, feel))
            if (prob > maxChance) maxChance = prob

            val rowDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(timeCal.time)
            if (rowDateStr == todayStr) {
                if (feel > todayMaxFeels) todayMaxFeels = feel
            }

            if (prob >= 30) {
                if (windowStart == null) windowStart = hourLabel
                if (prob > windowPeak) windowPeak = prob
            } else {
                if (windowStart != null) {
                    rainWindows.add(RainWindow(windowStart, hourLabel, windowPeak))
                    windowStart = null
                    windowPeak = 0
                }
            }
        }
        if (windowStart != null) {
            rainWindows.add(RainWindow(windowStart, hourlyData.last().hour, windowPeak))
        }

        val maxTemp = maxOf(todayMaxTemp, tomorrowMaxTemp)
        val heatLevel = when {
            maxTemp > 44 -> HeatLevel.EXTREME
            maxTemp > 38 -> HeatLevel.HOT
            maxTemp > 32 -> HeatLevel.MILDY_HOT
            else -> HeatLevel.MILD
        }

        return RainForecast(
            maxChance = maxChance,
            hourlyData = hourlyData,
            isRainExpected = maxChance > 30,
            rainWindows = rainWindows,
            todayMaxTemp = todayMaxTemp,
            todayMinTemp = todayMinTemp,
            todayMaxFeelsLike = todayMaxFeels,
            tomorrowMaxTemp = tomorrowMaxTemp,
            tomorrowMinTemp = tomorrowMinTemp,
            heatLevel = heatLevel
        )
    }
}
