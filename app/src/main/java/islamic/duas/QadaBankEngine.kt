package islamic.duas

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class QadaBankEngine(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("qada_bank_v2", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        private const val KEY_PREFIX_QADA = "qada_"
        private const val KEY_PREFIX_DONE = "qada_done_"
        private const val KEY_WEEK_START = "qada_week_start"
    }

    val today: String get() = dateFormat.format(Date())

    private fun getWeekStart(): String {
        val saved = prefs.getString(KEY_WEEK_START, "")
        val cal = Calendar.getInstance()
        val currentWeekStart = getLastFriday(cal)
        if (saved != currentWeekStart) {
            if (saved != null && saved.isNotEmpty()) {
                clearCurrentWeek()
            }
            prefs.edit().putString(KEY_WEEK_START, currentWeekStart).apply()
        }
        return currentWeekStart
    }

    private fun getLastFriday(cal: Calendar): String {
        val c = cal.clone() as Calendar
        c.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY)
        if (c.after(cal)) {
            c.add(Calendar.DAY_OF_YEAR, -7)
        }
        return dateFormat.format(c.time)
    }

    private fun clearCurrentWeek() {
        val editor = prefs.edit()
        val allKeys = prefs.all.keys
        for (key in allKeys) {
            if (key.startsWith(KEY_PREFIX_QADA) || key.startsWith(KEY_PREFIX_DONE)) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    fun addMissedPrayer(count: Int = 1) {
        val today = dateFormat.format(Date())
        val allPrayers = listOf("Fajr", "Zuhr", "Asr", "Maghrib", "Isha")
        val editor = prefs.edit()
        repeat(count.coerceAtMost(5)) {
            if (it < allPrayers.size) {
                editor.putBoolean("$KEY_PREFIX_QADA${allPrayers[it]}_$today", true)
            }
        }
        editor.apply()
    }

    fun markAsQada(prayer: String, date: String) {
        prefs.edit().putBoolean("$KEY_PREFIX_QADA${prayer}_$date", true).apply()
    }

    fun unmarkQada(prayer: String, date: String) {
        prefs.edit().remove("$KEY_PREFIX_QADA${prayer}_$date").apply()
    }

    fun isMarkedQada(prayer: String, date: String): Boolean {
        return try { prefs.getBoolean("$KEY_PREFIX_QADA${prayer}_$date", false) } catch (_: ClassCastException) { false }
    }

    fun markPrayerCompletedInQada(prayer: String, date: String) {
        prefs.edit().putBoolean("$KEY_PREFIX_DONE${prayer}_$date", true).apply()
    }

    fun isPrayerCompletedInQada(prayer: String, date: String): Boolean {
        return try { prefs.getBoolean("$KEY_PREFIX_DONE${prayer}_$date", false) } catch (_: ClassCastException) { false }
    }

    fun getThisWeekQadaPrayers(): List<Pair<String, String>> {
        val weekStart = getWeekStart()
        val weekEnd = getNextFriday(weekStart)
        val result = mutableListOf<Pair<String, String>>()
        val allKeys = prefs.all.keys
        for (key in allKeys) {
            if (!key.startsWith(KEY_PREFIX_QADA)) continue
            val marked = try { prefs.getBoolean(key, false) } catch (_: ClassCastException) { false }
            if (!marked) continue
            val rest = key.removePrefix(KEY_PREFIX_QADA)
            val parts = rest.split("_")
            if (parts.size >= 2) {
                val prayer = parts[0]
                val date = parts.drop(1).joinToString("_")
                if (date >= weekStart && date < weekEnd) {
                    result.add(prayer to date)
                }
            }
        }
        return result
    }

    private fun getNextFriday(fromDate: String): String {
        val cal = Calendar.getInstance()
        cal.time = dateFormat.parse(fromDate)!!
        cal.add(Calendar.DAY_OF_YEAR, 7)
        return dateFormat.format(cal.time)
    }

    fun getThisWeekPendingQada(): List<Pair<String, String>> {
        return getThisWeekQadaPrayers().filter { (p, d) -> !isPrayerCompletedInQada(p, d) }
    }

    fun getThisWeekCompletedQada(): List<Pair<String, String>> {
        return getThisWeekQadaPrayers().filter { (p, d) -> isPrayerCompletedInQada(p, d) }
    }

    fun getPendingQadaCount(): Int = getThisWeekPendingQada().size

    fun getCompletedQadaCount(): Int = getThisWeekCompletedQada().size

    fun getSummary(): String {
        val pending = getPendingQadaCount()
        val done = getCompletedQadaCount()
        return if (pending == 0 && done == 0) "کوئی قضا نہیں"
        else "قضا: $pending باقی، $done مکمل"
    }

    fun getDetailedSummary(): String {
        val pending = getThisWeekPendingQada()
        val done = getThisWeekCompletedQada()
        val sb = StringBuilder()
        if (pending.isNotEmpty()) {
            sb.append("باقی قضا ($pending):\n")
            pending.forEach { (p, d) -> sb.append("  • $p ($d)\n") }
        }
        if (done.isNotEmpty()) {
            sb.append("مکمل شدہ ($done):\n")
            done.forEach { (p, d) -> sb.append("  • $p ($d)\n") }
        }
        if (pending.isEmpty() && done.isEmpty()) {
            sb.append("اللہ کا شکر ہے — کوئی قضا باقی نہیں")
        }
        return sb.toString()
    }
}
