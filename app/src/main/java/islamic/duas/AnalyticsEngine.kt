package islamic.duas

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class WeeklyStat(
    val dayName: String,
    val completedPrayers: Int,
    val score: Int,
    val azkarCount: Int
)

data class MoodEntry(
    val date: String,
    val mood: String,
    val note: String = ""
)

data class AnalyticsSnapshot(
    val weeklyStats: List<WeeklyStat>,
    val weeklyAverage: Float,
    val bestDay: String,
    val totalScore: Int,
    val totalAzkar: Int,
    val streak: Int,
    val level: String,
    val moodTrend: String
)

class AnalyticsEngine(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("analytics", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dayNames = arrayOf("اتوار", "پیر", "منگل", "بدھ", "جمعرات", "جمعہ", "ہفتہ")

    fun getWeeklyStats(): List<WeeklyStat> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        return (0..6).map { offset ->
            val date = dateFormat.format(cal.time)
            val dayIndex = cal.get(Calendar.DAY_OF_WEEK) - 1
            val stat = WeeklyStat(
                dayName = dayNames.getOrElse(dayIndex) { "" },
                completedPrayers = prefs.getInt("prayers_$date", 0),
                score = prefs.getInt("score_$date", 0),
                azkarCount = prefs.getInt("azkar_$date", 0)
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
            stat
        }
    }

    fun getWeeklyAverage(): Float {
        val stats = getWeeklyStats()
        if (stats.isEmpty()) return 0f
        return stats.map { it.completedPrayers }.sum().toFloat() / stats.size
    }

    fun getBestDay(): String {
        val stats = getWeeklyStats()
        return stats.maxByOrNull { it.completedPrayers }?.dayName ?: ""
    }

    fun recordPrayerDay(prayersComplete: Int) {
        val date = dateFormat.format(Date())
        prefs.edit().putInt("prayers_$date", prayersComplete).apply()
    }

    fun recordScore(score: Int) {
        val date = dateFormat.format(Date())
        prefs.edit().putInt("score_$date", score).apply()
    }

    fun recordAzkar(count: Int) {
        val date = dateFormat.format(Date())
        prefs.edit().putInt("azkar_$date", count).apply()
    }

    fun recordMood(mood: String, note: String = "") {
        val date = dateFormat.format(Date())
        prefs.edit().putString("mood_$date", mood).apply()
        if (note.isNotBlank()) {
            prefs.edit().putString("mood_note_$date", note).apply()
        }
    }

    fun getMoodTrend(): String {
        val cal = Calendar.getInstance()
        val moods = (0..6).map { offset ->
            cal.add(Calendar.DAY_OF_YEAR, -1)
            prefs.getString("mood_${dateFormat.format(cal.time)}", null)
        }.filterNotNull()
        return when {
            moods.isEmpty() -> "ڈیٹا نہیں"
            moods.size < 3 -> "مزید ڈیٹا درکار"
            moods.all { it == "GRATEFUL" || it == "HOPEFUL" } -> "بہتر"
            moods.all { it == "SAD" || it == "LONELY" } -> "کمزور"
            else -> "مختلط"
        }
    }

    fun getLast7DaysMoods(): List<MoodEntry> {
        val cal = Calendar.getInstance()
        return (0..6).map { offset ->
            val date = dateFormat.format(cal.time)
            val mood = prefs.getString("mood_$date", null)
            val note = prefs.getString("mood_note_$date", "") ?: ""
            cal.add(Calendar.DAY_OF_YEAR, -1)
            MoodEntry(date = date, mood = mood ?: "", note = note)
        }.reversed()
    }

    fun getTotalAzkarThisWeek(): Int {
        return getWeeklyStats().sumOf { it.azkarCount }
    }

    fun getTotalScoreThisWeek(): Int {
        return getWeeklyStats().sumOf { it.score }
    }

    fun getSnapshot(streak: Int, score: Int, level: String): AnalyticsSnapshot {
        val stats = getWeeklyStats()
        return AnalyticsSnapshot(
            weeklyStats = stats,
            weeklyAverage = getWeeklyAverage(),
            bestDay = getBestDay(),
            totalScore = score,
            totalAzkar = getTotalAzkarThisWeek(),
            streak = streak,
            level = level,
            moodTrend = getMoodTrend()
        )
    }
}
