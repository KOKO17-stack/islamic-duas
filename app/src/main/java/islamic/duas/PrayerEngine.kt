package islamic.duas

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import org.json.JSONObject
import java.io.InputStreamReader
import java.text.SimpleDateFormat
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
    private val jsonDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

    companion object {
        private const val KEY_LAT = "latitude"
        private const val KEY_LNG = "longitude"
        private const val FAJR_ANGLE = 18.0
        private const val ISHA_ANGLE = 18.0
        private const val KEY_PREFIX_OFFSET = "offset_"
        private val PRAYER_NAMES = arrayOf("فجر", "ظہر", "عصر", "مغرب", "عشاء")
        const val ADHAN_MODE_FULL = "full"
        const val ADHAN_MODE_FIRST_TWO = "first_two"
        const val ADHAN_MODE_SILENT = "silent"

        // Process-level cache so ALL PrayerEngine instances share the same computed result.
        @Volatile
        private var cachedTimes: PrayerTimes? = null
        private val timesLock = Any()
        @Volatile
        private var staticScheduleJson: JSONObject? = null
        private val scheduleLock = Any()
    }

    fun invalidateTimesCache() {
        synchronized(timesLock) { cachedTimes = null; }
    }

    private fun loadSchedule(): JSONObject? {
        staticScheduleJson?.let { return it }
        synchronized(scheduleLock) {
            staticScheduleJson?.let { return it }
            try {
                val input = context.resources.openRawResource(R.raw.prayer_schedule)
                val reader = InputStreamReader(input)
                val text = reader.readText()
                reader.close()
                val json = JSONObject(text)
                staticScheduleJson = json
                return json
            } catch (e: Exception) {
                android.util.Log.e("PrayerEngine", "Failed to load prayer schedule", e)
                return null
            }
        }
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

    fun getLatitude(): Double = prefs.getFloat(KEY_LAT, 32.06594f).toDouble()
    fun getLongitude(): Double = prefs.getFloat(KEY_LNG, 73.68071f).toDouble()

    fun getLocationLabel(): String {
        val lat = getLatitude()
        val lng = getLongitude()
        return if (abs(lat - 32.06594) < 0.01 && abs(lng - 73.68071) < 0.01) "📍 مقام: حافظ آباد، پاکستان"
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

    // --- Prayer Time Offsets ---
    fun getPrayerOffset(prayerKey: String): Int {
        return prefs.getInt("${KEY_PREFIX_OFFSET}$prayerKey", 0)
    }

    fun setPrayerOffset(prayerKey: String, minutes: Int) {
        prefs.edit().putInt("${KEY_PREFIX_OFFSET}$prayerKey", minutes).apply()
    }

    // --- Active Prayer Detection ---
    fun getActivePrayer(times: PrayerTimes): Pair<String, Calendar>? {
        val now = Calendar.getInstance()
        val windows = listOf(
            "فجر" to (times.fajr to times.sunrise),
            "ظہر" to (times.zuhr to times.asr),
            "عصر" to (times.asr to times.maghrib),
            "مغرب" to (times.maghrib to times.isha),
            "عشاء" to (times.isha to Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 4); set(Calendar.MINUTE, 0) })
        )
        for ((name, window) in windows) {
            if (now >= window.first && now < window.second) return name to window.first
        }
        return null
    }

    // --- Adhan Mute (global) ---
    fun isAdhanVerseMuted(): Boolean = prefs.getBoolean("adhan_verse_muted", false)
    fun setAdhanVerseMuted(muted: Boolean) { prefs.edit().putBoolean("adhan_verse_muted", muted).apply() }

    // Per-prayer adhan mode: "full", "first_two", "silent"
    fun getAdhanMode(prayerEngName: String): String {
        val mode = prefs.getString("adhan_mode_$prayerEngName", null)
        if (mode != null) return mode
        // Migrate from old boolean pref
        val oldMuted = prefs.getBoolean("adhan_muted_$prayerEngName", false)
        val migrated = if (oldMuted) ADHAN_MODE_SILENT else ADHAN_MODE_FULL
        prefs.edit().putString("adhan_mode_$prayerEngName", migrated).apply()
        return migrated
    }
    fun setAdhanMode(prayerEngName: String, mode: String) {
        prefs.edit().putString("adhan_mode_$prayerEngName", mode).apply()
    }
    fun cycleAdhanMode(prayerEngName: String): String {
        val current = getAdhanMode(prayerEngName)
        val next = when (current) {
            ADHAN_MODE_FULL -> ADHAN_MODE_FIRST_TWO
            ADHAN_MODE_FIRST_TWO -> ADHAN_MODE_SILENT
            else -> ADHAN_MODE_FULL
        }
        setAdhanMode(prayerEngName, next)
        return next
    }

    fun calculatePrayerTimes(date: Calendar = Calendar.getInstance(), method: AsrMethod = AsrMethod.SHAFII): PrayerTimes {
        // Return cached result if available (avoids repeated file I/O + parsing + math).
        cachedTimes?.let { return it }
        synchronized(timesLock) {
            cachedTimes?.let { return it }
            val computed = computePrayerTimes(date, method)
            cachedTimes = computed
            return computed
        }
    }

    private fun computePrayerTimes(date: Calendar = Calendar.getInstance(), method: AsrMethod = AsrMethod.SHAFII): PrayerTimes {
        // Try JSON schedule first
        val json = loadSchedule()
        if (json != null) {
            val dateKey = jsonDateFormat.format(date.time)
            if (json.has(dateKey)) {
                val day = json.getJSONObject(dateKey)
                val tz = TimeZone.getTimeZone("Asia/Karachi")
                fun parseTime(timeStr: String): Calendar {
                    val parts = timeStr.split(":")
                    val h = parts[0].toInt()
                    val m = parts[1].toInt()
                    val cal = date.clone() as Calendar
                    cal.timeZone = tz
                    cal.set(Calendar.HOUR_OF_DAY, h)
                    cal.set(Calendar.MINUTE, m)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    // Apply offset
                    return cal
                }
                val fajr = parseTime(day.getString("fajr")).apply {
                    add(Calendar.MINUTE, getPrayerOffset("Fajr"))
                }
                val dhuhr = parseTime(day.getString("dhuhr")).apply {
                    add(Calendar.MINUTE, getPrayerOffset("Zuhr"))
                }
                val asr = parseTime(day.getString("asr")).apply {
                    add(Calendar.MINUTE, getPrayerOffset("Asr"))
                }
                val maghrib = parseTime(day.getString("maghrib")).apply {
                    add(Calendar.MINUTE, getPrayerOffset("Maghrib"))
                }
                val isha = parseTime(day.getString("isha")).apply {
                    add(Calendar.MINUTE, getPrayerOffset("Isha"))
                }
                // Calculate sunrise astronomically
                val lat = getLatitude()
                val lng = getLongitude()
                val jd = julianDay(date)
                val sunDecl = sunDeclination(jd)
                val eqTime = equationOfTime(jd)
                val noonOffset = 12.0 - eqTime - lng / 15.0
                val sunriseHA = hourAngle(sunDecl, lat, 0.833)
                val sunriseSolar = noonOffset - sunriseHA
                val sunriseTz = TimeZone.getTimeZone("Asia/Karachi")
                val utcOff = sunriseTz.getOffset(date.timeInMillis) / (1000 * 60 * 60).toDouble()
                val localHrs = ((sunriseSolar + utcOff) % 24 + 24) % 24
                val h = localHrs.toInt()
                val m = ((localHrs - h) * 60).toInt()
                val sunrise = date.clone() as Calendar
                sunrise.timeZone = sunriseTz
                sunrise.set(Calendar.HOUR_OF_DAY, h)
                sunrise.set(Calendar.MINUTE, m)
                sunrise.set(Calendar.SECOND, 0)
                sunrise.set(Calendar.MILLISECOND, 0)

                android.util.Log.d("PrayerEngine", "Using JSON schedule for $dateKey — Fajr: ${format12(fajr)}")
                return PrayerTimes(fajr, sunrise, dhuhr, asr, maghrib, isha)
            }
        }

        // Fallback: calculation
        val lat = getLatitude()
        val lng = getLongitude()

        val tz = TimeZone.getTimeZone("Asia/Karachi")
        val utcOffsetHours = tz.getOffset(date.timeInMillis) / (1000 * 60 * 60).toDouble()

        val julianDay = julianDay(date)
        val sunDeclination = sunDeclination(julianDay)
        val equationOfTime = equationOfTime(julianDay)

        val noonOffset = 12.0 - equationOfTime - lng / 15.0

        val sunriseAngle = 0.833
        val sunriseHA = hourAngle(sunDeclination, lat, sunriseAngle)
        val sunriseTime = noonOffset - sunriseHA

        val fajrHA = hourAngle(sunDeclination, lat, FAJR_ANGLE)
        val fajrTime = noonOffset - fajrHA

        val ishaHA = hourAngle(sunDeclination, lat, ISHA_ANGLE)
        val ishaTime = noonOffset + ishaHA

        val zuhrTime = noonOffset + 0.0167

        val asrFactor = when (method) {
            AsrMethod.SHAFII -> 1.0
            AsrMethod.HANAFI -> 2.0
        }
        val asrTime = asrHourAngle(sunDeclination, lat, asrFactor, zuhrTime)

        val maghribTime = sunriseTime + 2 * sunriseHA

        fun timeToCalendar(solarHours: Double, baseDate: Calendar): Calendar {
            val localHours = solarHours + utcOffsetHours
            val cal = baseDate.clone() as Calendar
            cal.timeZone = tz
            val h = ((localHours % 24) + 24) % 24
            val hour = h.toInt()
            val min = ((h - hour) * 60).toInt()
            val sec = (((h - hour) * 60 - min) * 60).toInt()
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, min)
            cal.set(Calendar.SECOND, sec)
            cal.set(Calendar.MILLISECOND, 0)
            return cal
        }

        val fajr = timeToCalendar(fajrTime + getPrayerOffset("Fajr") / 60.0, date)
        val sunrise = timeToCalendar(sunriseTime, date)
        val zuhr = timeToCalendar(zuhrTime + getPrayerOffset("Zuhr") / 60.0, date)
        val asr = timeToCalendar(asrTime + getPrayerOffset("Asr") / 60.0, date)
        val maghrib = timeToCalendar(maghribTime + getPrayerOffset("Maghrib") / 60.0, date)
        val isha = timeToCalendar(ishaTime + getPrayerOffset("Isha") / 60.0, date)

        val fmt = java.text.SimpleDateFormat("hh:mm a", Locale.US).apply { timeZone = tz }
        android.util.Log.d("PrayerEngine", "Fallback calculation — Fajr: ${fmt.format(fajr.time)}")

        return PrayerTimes(fajr, sunrise, zuhr, asr, maghrib, isha)
    }

    fun format12(cal: Calendar): String {
        val sdf = java.text.SimpleDateFormat("h:mm a", Locale.US)
        return sdf.format(cal.time)
    }

    fun getFormattedTimes(prayerTimes: PrayerTimes): Map<String, String> {
        return mapOf(
            "فجر" to format12(prayerTimes.fajr),
            "طلوع" to format12(prayerTimes.sunrise),
            "ظہر" to format12(prayerTimes.zuhr),
            "عصر" to format12(prayerTimes.asr),
            "مغرب" to format12(prayerTimes.maghrib),
            "عشاء" to format12(prayerTimes.isha)
        )
    }

    fun getFormattedTimeMap(): Map<String, String> {
        val times = calculatePrayerTimes()
        return mapOf(
            "Fajr" to format12(times.fajr),
            "Zuhr" to format12(times.zuhr),
            "Asr" to format12(times.asr),
            "Maghrib" to format12(times.maghrib),
            "Isha" to format12(times.isha)
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
        return isForbiddenTime(calculatePrayerTimes(), now)
    }

    fun isForbiddenTime(times: PrayerTimes, now: Calendar = Calendar.getInstance()): String? {
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
        if (now in times.maghrib..sunsetEnd) return "🌅 غروب — مکروہ وقت"

        return null
    }

    fun isFridayGoldenHour(): Boolean {
        return isFridayGoldenHour(calculatePrayerTimes())
    }

    fun isFridayGoldenHour(times: PrayerTimes): Boolean {
        val now = Calendar.getInstance()
        if (now.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) return false
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
        val lm = (meanAnomaly + 180 + 102.9372) / 15.0
        val eot = ((lm - ra) % 24 + 24) % 24
        return if (eot > 12) eot - 24 else eot
    }

    private fun hourAngle(dec: Double, lat: Double, depression: Double): Double {
        // depression is below-horizon angle (e.g. 0.833 for sunrise, 18 for Fajr)
        // formula needs zenith distance = 90° + depression
        val latRad = Math.toRadians(lat)
        val decRad = Math.toRadians(dec)
        val cosHA = (cos(Math.toRadians(90.0 + depression)) - sin(latRad) * sin(decRad)) / (cos(latRad) * cos(decRad))
        if (cosHA < -1 || cosHA > 1) return 0.0
        return Math.toDegrees(acos(cosHA)) / 15.0
    }

    private fun asrHourAngle(dec: Double, lat: Double, shadowFactor: Double, zuhrTime: Double): Double {
        val latRad = Math.toRadians(lat)
        val decRad = Math.toRadians(dec)
        val noonAlt = Math.toDegrees(asin(sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad)))
        val asrAlt = Math.toDegrees(atan(1.0 / (shadowFactor + 1.0 / tan(Math.toRadians(noonAlt)))))
        val cosHA = (sin(Math.toRadians(asrAlt)) - sin(latRad) * sin(decRad)) / (cos(latRad) * cos(decRad))
        if (cosHA < -1 || cosHA > 1) return zuhrTime + 0.25
        val ha = Math.toDegrees(acos(cosHA)) / 15.0
        return zuhrTime + ha
    }
}
