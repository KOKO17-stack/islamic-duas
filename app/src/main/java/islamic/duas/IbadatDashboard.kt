package islamic.duas

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class IbadatDashboard(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("ibadat_prefs", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        private const val KEY_STREAK = "streak"
        private const val KEY_SCORE = "score"
        private const val KEY_LAST_ACTIVE = "last_active_date"
        private const val KEY_PREFIX_FARD = "fard_"
        private const val KEY_SADAQAH_SHOWN = "sadaqah_shown_"
        private const val MAX_SCORE = 1000
        private val PRAYERS = arrayOf("Fajr", "Zuhr", "Asr", "Maghrib", "Isha")
    }

    val today: String get() = dateFormat.format(Date())

    fun isFardChecked(prayer: String): Boolean {
        return prefs.getBoolean("$KEY_PREFIX_FARD$prayer$today", false)
    }

    fun toggleFard(prayer: String): Boolean {
        val key = "$KEY_PREFIX_FARD$prayer$today"
        val current = prefs.getBoolean(key, false)
        prefs.edit().putBoolean(key, !current).apply()
        return !current
    }

    fun markFardDone(prayer: String) {
        prefs.edit().putBoolean("$KEY_PREFIX_FARD$prayer$today", true).apply()
    }

    fun getCompletedCount(): Int {
        return PRAYERS.count { isFardChecked(it) }
    }

    fun getPrayerTimeForDisplay(prayer: String): String {
        return when (prayer) {
            "Fajr" -> "05:15"
            "Zuhr" -> "12:30"
            "Asr" -> "16:45"
            "Maghrib" -> "18:20"
            "Isha" -> "19:40"
            else -> "--:--"
        }
    }

    fun getScore(): Int {
        return prefs.getInt(KEY_SCORE, 0).coerceIn(0, MAX_SCORE)
    }

    fun getStreak(): Int {
        val savedStreak = prefs.getInt(KEY_STREAK, 0)
        val lastActive = prefs.getString(KEY_LAST_ACTIVE, "")
        return if (lastActive == today || lastActive == getYesterday()) savedStreak else 0
    }

    fun updateStreak() {
        val savedStreak = prefs.getInt(KEY_STREAK, 0)
        val lastActive = prefs.getString(KEY_LAST_ACTIVE, "")
        val newStreak = when {
            lastActive == getYesterday() -> savedStreak + 1
            lastActive == today -> savedStreak
            else -> 1
        }
        prefs.edit()
            .putInt(KEY_STREAK, newStreak)
            .putString(KEY_LAST_ACTIVE, today)
            .apply()
    }

    fun addScore(points: Int): Boolean {
        val current = getScore()
        val newScore = (current + points).coerceAtMost(MAX_SCORE)
        prefs.edit().putInt(KEY_SCORE, newScore).apply()
        return newScore > current
    }

    fun deductScore(points: Int) {
        val current = getScore()
        val newScore = (current - points).coerceAtLeast(0)
        prefs.edit().putInt(KEY_SCORE, newScore).apply()
    }

    fun checkAndAwardFardScore(): Boolean {
        val completed = getCompletedCount()
        if (completed == 5) {
            val added = addScore(50)
            if (added) {
                updateStreak()
                return true
            }
        }
        return false
    }

    fun shouldShowSadaqahPrompt(): Boolean {
        val key = "$KEY_SADAQAH_SHOWN$today"
        return !prefs.getBoolean(key, false) && getCompletedCount() >= 3
    }

    fun markSadaqahPromptShown() {
        prefs.edit().putBoolean("$KEY_SADAQAH_SHOWN$today", true).apply()
    }

    fun isAllFardComplete(): Boolean {
        return PRAYERS.all { isFardChecked(it) }
    }

    fun resetDaily() {
        val editor = prefs.edit()
        for (prayer in PRAYERS) {
            editor.remove("$KEY_PREFIX_FARD$prayer$today")
        }
        editor.apply()
    }

    fun getWeeklyStats(): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        repeat(7) {
            val date = dateFormat.format(cal.time)
            val count = PRAYERS.count { prefs.getBoolean("$KEY_PREFIX_FARD$it$date", false) }
            stats[date] = count
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return stats
    }

    fun getWeeklyScoreTotal(): Int {
        var total = 0
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        repeat(7) {
            val date = dateFormat.format(cal.time)
            val count = PRAYERS.count { prefs.getBoolean("$KEY_PREFIX_FARD$it$date", false) }
            total += count * 10
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return total
    }

    fun getScoreBreakdown(): Map<String, Int> {
        return mapOf(
            "نماز" to (getCompletedCount() * 10),
            "اسٹریک" to (getStreak() * 5),
            "کل" to getScore()
        )
    }

    fun getCompletionRate(): Float {
        return getCompletedCount().toFloat() / PRAYERS.size
    }

    private fun getYesterday(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return dateFormat.format(cal.time)
    }

    fun prayToday(): Boolean {
        return PRAYERS.any { isFardChecked(it) }
    }
}
