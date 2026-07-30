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
        private const val KEY_DAILY_POINTS = "daily_points_"
    }

    private fun isHaidhToday(): Boolean {
        val haidhPrefs = context.getSharedPreferences("haidh_status", Context.MODE_PRIVATE)
        return haidhPrefs.getString("current_status", "tuhr") == "haidh"
    }

    fun getMaxDailyPoints(): Int {
        val state = IbadatStateEngine(context)
        return state.getMaxDailyPoints()
    }

    fun recordDailyPoints(points: Int) {
        val today = dateFormat.format(Date())
        prefs.edit().putInt("${KEY_DAILY_POINTS}$today", points).apply()
    }

    fun getDailyPoints(date: String): Int {
        return prefs.getInt("${KEY_DAILY_POINTS}$date", 0)
    }

    fun getDailyAchievable(): Int = getMaxDailyPoints()

    fun getDailyAchieved(): Int = if (isHaidhToday()) getMaxDailyPoints() else getDailyPoints(dateFormat.format(Date()))

    fun getDailyProgress(): Pair<Float, String> {
        if (isHaidhToday()) {
            return 100f to "🌸 بیٹی، حیض کے دن اللہ نے راحت بخشی — حصہ خود بخود لکھ لیا گیا ہے"
        }
        val max = getDailyAchievable()
        val earned = getDailyAchieved()
        val percent = if (max > 0) (earned.toFloat() / max * 100).coerceAtMost(100f) else 0f
        val status = if (percent >= THRESHOLD_PERCENT) "✅ ماشاءاللہ! ہدف پورا ہوا — اللہ خوش رکھے" else "🤲 مزید ${"%.0f".format(THRESHOLD_PERCENT - percent)}% پوائنٹس چاہیے — نماز، اذکار، ورزش یا دوائی مکمل کریں"
        return percent to status
    }

    fun isQualified(): Boolean = if (isHaidhToday()) true else getDailyProgress().first >= THRESHOLD_PERCENT

    fun getStatusText(): String {
        val (percent, status) = getDailyProgress()
        val todayStr = java.text.SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
        return buildString {
            appendLine("🏆 انعام: عمرہ")
            appendLine("📅 $todayStr")
            if (isHaidhToday()) {
                appendLine("🌸 بیٹی، حیض اللہ کی طرف سے راحت کا ذریعہ ہے — حصہ محبت سے لکھ لیا گیا ہے")
                appendLine()
            } else {
                appendLine("قاعدہ: اگلے 3 مہینے روزانہ 80% پوائنٹس لیں، عمرہ کی قرعہ اندازی میں شریک ہوں")
                appendLine("💖 پیشرفت: ${"%.0f".format(percent)}% — ہر قدم اللہ کو پسند ہے")
                appendLine()
                appendLine(status)
                appendLine()
            }
        }
    }
}