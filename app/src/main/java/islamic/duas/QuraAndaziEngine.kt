package islamic.duas

import android.content.Context
import android.content.SharedPreferences
import islamic.duas.haidh.HealthEngine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class QuraAndaziEngine(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("qura_andazi", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        private const val THRESHOLD_PERCENT = 80
    }

    data class Quarter(val year: Int, val quarter: Int) {
            fun label(): String = "Q$quarter $year"
        fun startDate(): String {
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, (quarter - 1) * 3)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return fmt.format(cal.time)
        }
        fun endDate(): String {
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, (quarter - 1) * 3 + 2)
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return fmt.format(cal.time)
        }
    }

    fun getCurrentQuarter(): Quarter {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val quarter = (month / 3) + 1
        return Quarter(year, quarter)
    }

    fun getQuarterDays(quarter: Quarter): Int {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, quarter.year)
        cal.set(Calendar.MONTH, (quarter.quarter - 1) * 3)
        val startDay = cal.getActualMinimum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.MONTH, (quarter.quarter - 1) * 3 + 2)
        val endDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val startCal = Calendar.getInstance().apply {
            set(quarter.year, (quarter.quarter - 1) * 3, startDay)
        }
        val endCal = Calendar.getInstance().apply {
            set(quarter.year, (quarter.quarter - 1) * 3 + 2, endDay)
        }
        return ((endCal.timeInMillis - startCal.timeInMillis) / (1000 * 60 * 60 * 24) + 1).toInt()
    }

    private fun getMaxDailyPoints(): Int {
        val state = IbadatStateEngine(context)
        var max = 0
        for (prayer in IbadatStateEngine.FARD_PRAYERS) {
            max += 20
        }
        max += 10 // Tahajjud
        max += 5  // Subah Azkar
        max += 5  // Sham Azkar
        max += 20 // Perfect day bonus
        max += 5  // Exercise
        max += 10 // Quran Tilawat
        return max // NO medicine points
    }

    fun getQuarterAchievable(): Int {
        val quarter = getCurrentQuarter()
        val startDate = quarter.startDate()
        val endDate = quarter.endDate()
        val startCal = Calendar.getInstance().apply { time = dateFormat.parse(startDate)!! }
        val endCal = Calendar.getInstance().apply { time = dateFormat.parse(endDate)!! }
        val todayCal = Calendar.getInstance()
        val daysElapsed = ((todayCal.timeInMillis - startCal.timeInMillis) / (1000 * 60 * 60 * 24) + 1).toInt().coerceAtLeast(1)
        return daysElapsed * getMaxDailyPoints()
    }

    fun getQuarterAchieved(): Int {
        val quarter = getCurrentQuarter()
        val state = IbadatStateEngine(context)
        val health = HealthEngine(context)
        val startDate = quarter.startDate()
        val endDate = quarter.endDate()
        val startCal = Calendar.getInstance().apply { time = dateFormat.parse(startDate)!! }
        val endCal = Calendar.getInstance().apply { time = dateFormat.parse(endDate)!! }
        val todayCal = Calendar.getInstance()
        val daysElapsed = ((todayCal.timeInMillis - startCal.timeInMillis) / (1000 * 60 * 60 * 24) + 1).toInt().coerceAtLeast(1)
        var earned = 0
        val cal = Calendar.getInstance().apply { time = startCal.time }
        repeat(daysElapsed) {
            val date = dateFormat.format(cal.time)
            for (prayer in IbadatStateEngine.FARD_PRAYERS) {
                if (state.getPrayerState(prayer) == PrayerState.DONE) earned += 20
            }
            if (state.isTahajjudDone()) earned += 10
            if (state.isSubahAzkarDone()) earned += 5
            if (state.isShamAzkarDone()) earned += 5
            if (IbadatStateEngine.FARD_PRAYERS.all { state.getPrayerState(it) == PrayerState.DONE }) earned += 20
            val exerciseDate = dateFormat.format(cal.time)
            if (health.getTodayExerciseMinutes() >= 30) earned += 5
            if (state.isQuranTilawatDone()) earned += 10
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return earned
    }

    fun getQuarterProgress(): Pair<Float, String> {
        val quarter = getCurrentQuarter()
        val state = IbadatStateEngine(context)
        val health = HealthEngine(context)

        val startDate = quarter.startDate()
        val endDate = quarter.endDate()
        val todayStr = dateFormat.format(Date())

        // Count days elapsed in quarter
        val startCal = Calendar.getInstance().apply { time = dateFormat.parse(startDate)!! }
        val endCal = Calendar.getInstance().apply { time = dateFormat.parse(endDate)!! }
        val todayCal = Calendar.getInstance()
        val daysElapsed = ((todayCal.timeInMillis - startCal.timeInMillis) / (1000 * 60 * 60 * 24) + 1).toInt().coerceAtLeast(1)
        val totalDays = ((endCal.timeInMillis - startCal.timeInMillis) / (1000 * 60 * 60 * 24) + 1).toInt()

        // Max possible ibadat + exercise points so far
        val maxSoFar = daysElapsed * getMaxDailyPoints()

        // Actual earned (ibadat + exercise only, NO medicine)
        var earned = 0
        val cal = Calendar.getInstance().apply { time = startCal.time }
        repeat(daysElapsed) {
            val date = dateFormat.format(cal.time)
            // Count fard done
            for (prayer in IbadatStateEngine.FARD_PRAYERS) {
                if (state.getPrayerState(prayer) == PrayerState.DONE) earned += 20
            }
            // Tahajjud
            if (state.isTahajjudDone()) earned += 10
            // Azkar
            if (state.isSubahAzkarDone()) earned += 5
            if (state.isShamAzkarDone()) earned += 5
            // Perfect day bonus
            if (IbadatStateEngine.FARD_PRAYERS.all { state.getPrayerState(it) == PrayerState.DONE }) earned += 20
            // Exercise
            val exerciseDate = dateFormat.format(cal.time)
            if (health.getTodayExerciseMinutes() >= 30) earned += 5
            // Quran Tilawat
            if (state.isQuranTilawatDone()) earned += 10
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val percent = if (maxSoFar > 0) (earned.toFloat() / maxSoFar * 100).coerceAtMost(100f) else 0f
        val status = if (percent >= THRESHOLD_PERCENT) "✅ ماشاءاللہ! آپ قرعہ اندازی میں شامل ہیں!"
        else "⚠️ مزید ${"%.0f".format(THRESHOLD_PERCENT - percent)}% پوائنٹس درکار — محنت کرو بیٹا!"

        return percent to status
    }

    fun isQualified(): Boolean {
        val (percent, _) = getQuarterProgress()
        return percent >= THRESHOLD_PERCENT
    }

    fun getStatusText(): String {
        val quarter = getCurrentQuarter()
        val (percent, status) = getQuarterProgress()
        return buildString {
            appendLine("🕋 قرعہ اندازی — عمرہ کا موقع")
            appendLine("$quarter")
            appendLine("پیشرفت: ${"%.0f".format(percent)}%") 
            appendLine()
            appendLine(status)
            appendLine()
            appendLine("⚠️ میڈیسن کے پوائنٹس شمار نہیں ہوتے")
        }
    }
}
