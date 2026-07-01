package islamic.duas

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class QadaBankEngine(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("qada_bank", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        private const val KEY_MISSED_PRAYERS = "missed_prayers"
        private const val KEY_MISSED_FASTS = "missed_fasts"
        private const val KEY_QADA_PRAYERS = "qada_prayers"
        private const val KEY_QADA_FASTS = "qada_fasts"
    }

    val today: String get() = dateFormat.format(Date())

    // --- Missed Prayers ---

    fun getMissedPrayers(): Int = prefs.getInt(KEY_MISSED_PRAYERS, 0)

    fun addMissedPrayer(count: Int = 1) {
        val current = getMissedPrayers()
        prefs.edit().putInt(KEY_MISSED_PRAYERS, current + count).apply()
    }

    fun getQadaPrayers(): Int = prefs.getInt(KEY_QADA_PRAYERS, 0)

    fun markPrayerQadaDone(count: Int = 1) {
        val current = getQadaPrayers()
        val missed = getMissedPrayers()
        val newQada = (current + count).coerceAtMost(missed)
        prefs.edit().putInt(KEY_QADA_PRAYERS, newQada).apply()
    }

    fun getPendingPrayers(): Int = getMissedPrayers() - getQadaPrayers()

    // --- Missed Fasts ---

    fun getMissedFasts(): Int = prefs.getInt(KEY_MISSED_FASTS, 0)

    fun addMissedFast(count: Int = 1) {
        val current = getMissedFasts()
        prefs.edit().putInt(KEY_MISSED_FASTS, current + count).apply()
    }

    fun getQadaFasts(): Int = prefs.getInt(KEY_QADA_FASTS, 0)

    fun markFastQadaDone(count: Int = 1) {
        val current = getQadaFasts()
        val missed = getMissedFasts()
        val newQada = (current + count).coerceAtMost(missed)
        prefs.edit().putInt(KEY_QADA_FASTS, newQada).apply()
    }

    fun getPendingFasts(): Int = getMissedFasts() - getQadaFasts()

    // --- Combined ---

    fun getSummary(): String {
        return "روزوں کا بینک: ${getPendingFasts()} | نمازوں کا بینک: ${getPendingPrayers()}"
    }

    fun getDetailedSummary(): String {
        val pendingPrayers = getPendingPrayers()
        val pendingFasts = getPendingFasts()
        val sb = StringBuilder()
        if (pendingPrayers > 0) {
            sb.append("قضا نمازیں: $pendingPrayers\n")
        }
        if (pendingFasts > 0) {
            sb.append("قضا روزے: $pendingFasts\n")
        }
        if (pendingPrayers == 0 && pendingFasts == 0) {
            sb.append("اللہ کا شکر ہے — کوئی قضا باقی نہیں")
        }
        return sb.toString()
    }

    fun getCompletionPrediction(dailyRate: Float): String {
        val pendingPrayers = getPendingPrayers()
        val pendingFasts = getPendingFasts()
        if (pendingPrayers == 0 && pendingFasts == 0) return "کوئی قضا باقی نہیں — اللہ کا شکر ہے"

        val sb = StringBuilder()
        if (dailyRate > 0 && pendingPrayers > 0) {
            val daysForPrayers = (pendingPrayers / dailyRate).toInt()
            sb.append("روزانہ $dailyRate نماز کی شرح سے:\n")
            sb.append("قضا نمازیں: $daysForPrayers دن میں مکمل ہوں گی\n")
        }
        if (pendingFasts > 0) {
            val monthsForFasts = (pendingFasts / 10f).toInt() + 1
            sb.append("قضا روزے: تقریباً $monthsForFasts مہینے میں مکمل ہوں گے")
        }
        return sb.toString()
    }

    fun getPredictedCompletionDate(dailyRate: Float): String {
        val pendingPrayers = getPendingPrayers()
        if (dailyRate <= 0 || pendingPrayers <= 0) return "شرائط طے کریں"

        val daysNeeded = (pendingPrayers / dailyRate).toInt()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, daysNeeded)
        val df = SimpleDateFormat("dd MMM yyyy", Locale.US)
        return df.format(cal.time)
    }

    fun resetAll() {
        prefs.edit().clear().apply()
    }
}
