package islamic.duas

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*

data class PrayerTimes(
    val fajr: Calendar,
    val sunrise: Calendar,
    val zuhr: Calendar,
    val asr: Calendar,
    val maghrib: Calendar,
    val isha: Calendar
)

enum class AsrMethod(val label: String) {
    SHAFII("مذہب اول (اہل حدیث)"),
    HANAFI("مذہب دوم (حنفی)");
}

class PrayerEngine(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("prayer_prefs", Context.MODE_PRIVATE)
    private var location: Location? = null

    companion object {
        private const val KEY_LAT = "latitude"
        private const val KEY_LNG = "longitude"

        // Fajr angle: Egyptian General Authority of Survey = 19.5°
        private const val FAJR_ANGLE = 19.5
        // Isha angle: Egyptian General Authority = 17.5°
        private const val ISHA_ANGLE = 17.5

        private val PRAYER_NAMES = arrayOf("فجر", "ظہر", "عصر", "مغرب", "عشاء")
    }

    fun setLocation(lat: Double, lng: Double) {
        location = Location("prayer").apply {
            latitude = lat
            longitude = lng
        }
        prefs.edit()
            .putFloat(KEY_LAT, lat.toFloat())
            .putFloat(KEY_LNG, lng.toFloat())
            .apply()
    }

    fun getLatitude(): Double = prefs.getFloat(KEY_LAT, 32.07f).toDouble()
    fun getLongitude(): Double = prefs.getFloat(KEY_LNG, 73.68f).toDouble()

    fun getLocationLabel(): String {
        val lat = getLatitude()
        val lng = getLongitude()
        return if (lat == 32.07 && lng == 73.68) "📍 مقام: حافظ آباد، پاکستان"
        else "📍 مقام: %.2f, %.2f".format(lat, lng)
    }

    fun getQiblaDirection(): Float {
        val lat = Math.toRadians(getLatitude())
        val lng = Math.toRadians(getLongitude())
        val kaabaLat = Math.toRadians(21.4225)
        val kaabaLng = Math.toRadians(39.8262)

        val dLng = kaabaLng - lng
        val y = sin(dLng) * cos(kaabaLat)
        val x = cos(lat) * sin(kaabaLat) - sin(lat) * cos(kaabaLat) * cos(dLng)
        val qibla = Math.toDegrees(atan2(y, x))
        return ((qibla + 360) % 360).toFloat()
    }

    fun getQiblaLabel(): String {
        val degrees = getQiblaDirection()
        val direction = when {
            degrees in 45.0..135.0 -> "مشرق"
            degrees in 135.0..225.0 -> "جنوب"
            degrees in 225.0..315.0 -> "مغرب"
            else -> "شمال"
        }
        return "🕌 قبلہ: %.0f° (%s)".format(degrees, direction)
    }

    fun calculatePrayerTimes(date: Calendar = Calendar.getInstance(), method: AsrMethod = AsrMethod.SHAFII): PrayerTimes {
        val lat = getLatitude()
        val lng = getLongitude()
        val timeZone = TimeZone.getDefault()

        val julianDay = julianDay(date)
        val sunDeclination = sunDeclination(julianDay)
        val equationOfTime = equationOfTime(julianDay)

        val noonOffset = 12.0 - equationOfTime - lng / 15.0

        val sunriseAngle = 0.833
        val sunriseHA = hourAngle(sunDeclination, lat, sunriseAngle)
        val sunriseTime = noonOffset - sunriseHA

        val fajrHA = hourAngle(sunDeclination, lat, FAJR_ANGLE + sunriseAngle)
        val fajrTime = noonOffset - fajrHA

        val ishaHA = hourAngle(sunDeclination, lat, ISHA_ANGLE + sunriseAngle)
        val ishaTime = noonOffset + ishaHA

        val zuhrTime = noonOffset + 0.0167 // ~1 min after noon

        val asrFactor = when (method) {
            AsrMethod.SHAFII -> 1.0
            AsrMethod.HANAFI -> 2.0
        }
        val asrTime = asrHourAngle(sunDeclination, lat, asrFactor, zuhrTime)

        val maghribTime = sunriseTime + 2 * sunriseHA

        fun timeToCalendar(hours: Double, baseDate: Calendar): Calendar {
            val cal = baseDate.clone() as Calendar
            val h = hours.toInt()
            val m = ((hours - h) * 60).toInt()
            val s = (((hours - h) * 60 - m) * 60).toInt()
            cal.set(Calendar.HOUR_OF_DAY, h)
            cal.set(Calendar.MINUTE, m)
            cal.set(Calendar.SECOND, s)
            return cal
        }

        return PrayerTimes(
            fajr = timeToCalendar(fajrTime, date),
            sunrise = timeToCalendar(sunriseTime, date),
            zuhr = timeToCalendar(zuhrTime, date),
            asr = timeToCalendar(asrTime, date),
            maghrib = timeToCalendar(maghribTime, date),
            isha = timeToCalendar(ishaTime, date)
        )
    }

    fun getFormattedTimes(prayerTimes: PrayerTimes): Map<String, String> {
        val fmt = java.text.SimpleDateFormat("HH:mm", Locale.US)
        return mapOf(
            "فجر" to fmt.format(prayerTimes.fajr.time),
            "طلوع" to fmt.format(prayerTimes.sunrise.time),
            "ظہر" to fmt.format(prayerTimes.zuhr.time),
            "عصر" to fmt.format(prayerTimes.asr.time),
            "مغرب" to fmt.format(prayerTimes.maghrib.time),
            "عشاء" to fmt.format(prayerTimes.isha.time)
        )
    }

    fun getNextPrayer(prayerTimes: PrayerTimes): Pair<String, Calendar> {
        val now = Calendar.getInstance()
        val prayers = listOf(
            "فجر" to prayerTimes.fajr,
            "ظہر" to prayerTimes.zuhr,
            "عصر" to prayerTimes.asr,
            "مغرب" to prayerTimes.maghrib,
            "عشاء" to prayerTimes.isha
        )
        for ((name, time) in prayers) {
            if (time.after(now)) return name to time
        }
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowTimes = calculatePrayerTimes(tomorrow)
        return "فجر" to tomorrowTimes.fajr
    }

    fun isForbiddenTime(now: Calendar = Calendar.getInstance()): String? {
        val times = calculatePrayerTimes()
        val fmt = java.text.SimpleDateFormat("HH:mm", Locale.US)
        val nowStr = fmt.format(now.time)

        // Sunrise forbidden window (sunrise + 15 min)
        val sunriseEnd = times.sunrise.clone() as Calendar
        sunriseEnd.add(Calendar.MINUTE, 15)
        if (now in times.sunrise..sunriseEnd) return "🌅 طلوع — مکروہ وقت"

        // Istiwa (zenith) forbidden window (5 min before zuhr to zuhr)
        val istiwaStart = times.zuhr.clone() as Calendar
        istiwaStart.add(Calendar.MINUTE, -5)
        if (now in istiwaStart..times.zuhr) return "☀️ زوال — مکروہ وقت"

        // Sunset forbidden window (sunset + 15 min)
        val sunsetEnd = times.maghrib.clone() as Calendar
        sunsetEnd.add(Calendar.MINUTE, 15)
        if (now in times.maghrib..sunsetEnd) return "🌇 غروب — مکروہ وقت"

        return null
    }

    fun isFridayGoldenHour(): Boolean {
        val now = Calendar.getInstance()
        if (now.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) return false
        val times = calculatePrayerTimes()
        val fajrEnd = times.fajr.clone() as Calendar
        fajrEnd.add(Calendar.HOUR_OF_DAY, 1)
        return now in times.fajr..fajrEnd
    }

    fun getPrayerTimeList(): List<Pair<String, Long>> {
        val times = calculatePrayerTimes()
        return listOf(
            "فجر" to times.fajr.timeInMillis,
            "ظہر" to times.zuhr.timeInMillis,
            "عصر" to times.asr.timeInMillis,
            "مغرب" to times.maghrib.timeInMillis,
            "عشاء" to times.isha.timeInMillis
        )
    }

    // --- Astronomical Calculations ---

    private fun julianDay(date: Calendar): Double {
        val year = date.get(Calendar.YEAR)
        val month = date.get(Calendar.MONTH) + 1
        val day = date.get(Calendar.DAY_OF_MONTH)
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045.5
    }

    private fun sunDeclination(julianDay: Double): Double {
        val n = julianDay - 2451545.0
        val obliquity = 23.439291 - 0.00000036 * n
        val meanAnomaly = (357.5291 + 0.98560028 * n) % 360.0
        val center = 1.9148 * sin(Math.toRadians(meanAnomaly)) +
                0.0200 * sin(Math.toRadians(2 * meanAnomaly)) +
                0.0003 * sin(Math.toRadians(3 * meanAnomaly))
        val eclipticLon = (meanAnomaly + center + 180 + 102.9372) % 360.0
        return Math.toDegrees(asin(sin(Math.toRadians(obliquity)) * sin(Math.toRadians(eclipticLon))))
    }

    private fun equationOfTime(julianDay: Double): Double {
        val n = julianDay - 2451545.0
        val meanAnomaly = (357.5291 + 0.98560028 * n) % 360.0
        val center = 1.9148 * sin(Math.toRadians(meanAnomaly)) +
                0.0200 * sin(Math.toRadians(2 * meanAnomaly)) +
                0.0003 * sin(Math.toRadians(3 * meanAnomaly))
        val eclipticLon = (meanAnomaly + center + 180 + 102.9372) % 360.0
        val ra = Math.toDegrees(atan2(
            cos(Math.toRadians(23.439291)) * sin(Math.toRadians(eclipticLon)),
            cos(Math.toRadians(eclipticLon))
        )) / 15.0
        val lm = meanAnomaly / 15.0
        return lm - ra
    }

    private fun hourAngle(dec: Double, lat: Double, angle: Double): Double {
        val latRad = Math.toRadians(lat)
        val decRad = Math.toRadians(dec)
        val cosHA = (cos(Math.toRadians(angle)) - sin(latRad) * sin(decRad)) / (cos(latRad) * cos(decRad))
        if (cosHA < -1 || cosHA > 1) return 0.0
        return Math.toDegrees(acos(cosHA)) / 15.0
    }

    private fun asrHourAngle(dec: Double, lat: Double, shadowFactor: Double, zuhrTime: Double): Double {
        val latRad = Math.toRadians(lat)
        val decRad = Math.toRadians(dec)
        val noonAlt = Math.toDegrees(asin(sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad)))
        val asrAlt = Math.toDegrees(atan(1.0 / (shadowFactor + tan(Math.toRadians(noonAlt)))))
        val cosHA = (sin(Math.toRadians(asrAlt)) - sin(latRad) * sin(decRad)) / (cos(latRad) * cos(decRad))
        if (cosHA < -1 || cosHA > 1) return zuhrTime + 0.25
        val ha = Math.toDegrees(acos(cosHA)) / 15.0
        return zuhrTime + ha
    }
}
