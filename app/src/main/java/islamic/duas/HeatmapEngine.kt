package islamic.duas

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class HeatmapDay(
    val date: String,
    val completedPrayers: Int,
    val totalPrayers: Int = 5,
    val score: Int = 0,
    val hasData: Boolean = false
)

data class HeatmapMonth(
    val monthName: String,
    val days: List<HeatmapDay>,
    val totalScore: Int,
    val completionRate: Float
)

data class HeatmapYear(
    val months: List<HeatmapMonth>,
    val yearlyScore: Int,
    val yearlyAverage: Float
)

data class HeatmapStats(
    val totalDaysTracked: Int,
    val perfectDays: Int,
    val bestStreak: Int,
    val averagePerDay: Float,
    val mostProductiveMonth: String
)

class HeatmapEngine(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("heatmap", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun recordDay(prayersComplete: Int, score: Int) {
        val date = dateFormat.format(Date())
        prefs.edit()
            .putInt("prayers_$date", prayersComplete)
            .putInt("score_$date", score)
            .putBoolean("has_data_$date", true)
            .apply()
    }

    fun getDay(date: String): HeatmapDay {
        return HeatmapDay(
            date = date,
            completedPrayers = prefs.getInt("prayers_$date", 0),
            totalPrayers = 5,
            score = prefs.getInt("score_$date", 0),
            hasData = prefs.getBoolean("has_data_$date", false)
        )
    }

    fun getMonthData(year: Int, month: Int): HeatmapMonth {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val monthNames = arrayOf(
            "جنوری", "فروری", "مارچ", "اپریل", "مئی", "جون",
            "جولائی", "اگست", "ستمبر", "اکتوبر", "نومبر", "دسمبر"
        )

        var totalScore = 0
        var totalComplete = 0
        var totalDays = 0
        val days = (1..maxDay).map { day ->
            cal.set(year, month - 1, day)
            val date = dateFormat.format(cal.time)
            val d = getDay(date)
            if (d.hasData) {
                totalScore += d.score
                totalComplete += d.completedPrayers
                totalDays++
            }
            d
        }

        return HeatmapMonth(
            monthName = monthNames.getOrElse(month - 1) { "" },
            days = days,
            totalScore = totalScore,
            completionRate = if (totalDays > 0) totalComplete.toFloat() / (totalDays * 5) else 0f
        )
    }

    fun getYearData(year: Int = Calendar.getInstance().get(Calendar.YEAR)): HeatmapYear {
        var yearlyScore = 0
        var totalComplete = 0
        var totalDays = 0

        val months = (1..12).map { month ->
            val m = getMonthData(year, month)
            yearlyScore += m.totalScore
            totalComplete += (m.completionRate * m.days.size * 5).toInt()
            totalDays += m.days.count { it.hasData }
            m
        }

        return HeatmapYear(
            months = months,
            yearlyScore = yearlyScore,
            yearlyAverage = if (totalDays > 0) totalComplete.toFloat() / totalDays else 0f
        )
    }

    fun getStats(): HeatmapStats {
        val prefsAll = prefs.all
        val dataKeys = prefsAll.keys.filter { it.startsWith("has_data_") }
        val totalDays = dataKeys.size
        var perfectDays = 0
        var currentStreak = 0
        var bestStreak = 0
        var totalPrayers = 0

        val sortedDates = dataKeys.map { it.removePrefix("has_data_") }.sorted()

        for (date in sortedDates) {
            val prayers = prefs.getInt("prayers_$date", 0)
            totalPrayers += prayers
            if (prayers >= 5) {
                currentStreak++
                perfectDays++
                if (currentStreak > bestStreak) bestStreak = currentStreak
            } else if (prayers == 0) {
                currentStreak = 0
            } else {
                currentStreak = 0
            }
        }

        val monthCounts = sortedDates.groupBy { it.substring(0, 7) }
        val productiveMonth = monthCounts.maxByOrNull {
            it.value.sumOf { d -> prefs.getInt("score_$d", 0) }
        }?.key ?: ""

        val monthNames = arrayOf(
            "جنوری", "فروری", "مارچ", "اپریل", "مئی", "جون",
            "جولائی", "اگست", "ستمبر", "اکتوبر", "نومبر", "دسمبر"
        )
        val monthIdx = try { productiveMonth.substring(5).toInt() - 1 } catch (_: Exception) { 0 }

        return HeatmapStats(
            totalDaysTracked = totalDays,
            perfectDays = perfectDays,
            bestStreak = bestStreak,
            averagePerDay = if (totalDays > 0) totalPrayers.toFloat() / totalDays else 0f,
            mostProductiveMonth = monthNames.getOrElse(monthIdx) { "" }
        )
    }
}
