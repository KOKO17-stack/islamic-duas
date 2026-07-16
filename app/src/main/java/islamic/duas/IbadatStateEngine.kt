package islamic.duas

import android.content.Context
import android.content.SharedPreferences
import islamic.duas.haidh.HealthEngine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class PrayerState { DONE, PENDING, QADA }

class IbadatStateEngine(private val context: Context) {

    private val prefs: SharedPreferences by lazy { context.getSharedPreferences("ibadat_state", Context.MODE_PRIVATE) }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        val FARD_PRAYERS = listOf("Fajr", "Zuhr", "Asr", "Maghrib", "Isha")
    private const val KEY_SCORE = "unified_score"
    private const val KEY_BONUS_SCORE = "bonus_score"
    private const val KEY_PERFECT_DAYS = "perfect_days"
    private const val KEY_PERFECT_AWARDED = "perfect_awarded_"
    private const val KEY_LAST_ACTIVE = "last_active_date"
    }

    val today: String get() = dateFormat.format(Date())

    fun getPrayerState(prayer: String): PrayerState {
        val key = "${prayer}_$today"
        val value = prefs.getString(key, "PENDING") ?: "PENDING"
        return try { PrayerState.valueOf(value) } catch (_: Exception) { PrayerState.PENDING }
    }

    fun setPrayerState(prayer: String, state: PrayerState) {
        prefs.edit().putString("${prayer}_$today", state.name).apply()
    }

    fun togglePrayerState(prayer: String): PrayerState {
        val current = getPrayerState(prayer)
        val next = when (current) {
            PrayerState.DONE -> PrayerState.PENDING
            PrayerState.PENDING -> if (prayer in FARD_PRAYERS) PrayerState.QADA else PrayerState.DONE
            PrayerState.QADA -> PrayerState.DONE
        }
        setPrayerState(prayer, next)
        return next
    }

    fun isPrayerDone(prayer: String): Boolean = getPrayerState(prayer) == PrayerState.DONE

    fun isAllFardDone(): Boolean = FARD_PRAYERS.all { isPrayerDone(it) }

    fun getFardDoneCount(): Int = FARD_PRAYERS.count { isPrayerDone(it) }

    fun isJummahToday(): Boolean {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
    }

    fun getEffectiveZuhrLabel(): String = if (isJummahToday()) "جمعہ" else "ظہر"

    fun getEffectiveZuhrPrayer(): String = if (isJummahToday()) "Jummah" else "Zuhr"

    private val adhkarEngine by lazy { AdhkarEngine(context) }

    fun isSubahAzkarDone(): Boolean = try { prefs.getBoolean("subah_azkar_$today", false) } catch (_: ClassCastException) { false }

    fun setSubahAzkarDone(done: Boolean) {
        prefs.edit().putBoolean("subah_azkar_$today", done).apply()
        if (done) {
            AdhkarEngine.MORNING_ADHKAR.forEach { adhkarEngine.markDhikrDone(it.id) }
        }
    }

    fun isShamAzkarDone(): Boolean = try { prefs.getBoolean("sham_azkar_$today", false) } catch (_: ClassCastException) { false }

    fun setShamAzkarDone(done: Boolean) {
        prefs.edit().putBoolean("sham_azkar_$today", done).apply()
        if (done) {
            AdhkarEngine.EVENING_ADHKAR.forEach { adhkarEngine.markDhikrDone(it.id) }
        }
    }

    fun isTahajjudDone(): Boolean = try { prefs.getBoolean("tahajjud_$today", false) } catch (_: ClassCastException) { false }

    fun setTahajjudDone(done: Boolean) {
        prefs.edit().putBoolean("tahajjud_$today", done).apply()
    }

    fun isQuranTilawatDone(): Boolean = try { prefs.getBoolean("quran_tilawat_$today", false) } catch (_: ClassCastException) { false }

    fun setQuranTilawatDone(done: Boolean) {
        prefs.edit().putBoolean("quran_tilawat_$today", done).apply()
    }

    fun toggleTahajjud(): Boolean {
        val current = isTahajjudDone()
        setTahajjudDone(!current)
        return !current
    }

    fun toggleSubahAzkar(): Boolean {
        val current = isSubahAzkarDone()
        if (!current) {
            AdhkarEngine.MORNING_ADHKAR.forEach { adhkarEngine.markDhikrDone(it.id) }
        }
        setSubahAzkarDone(!current)
        return !current
    }

    fun toggleShamAzkar(): Boolean {
        val current = isShamAzkarDone()
        if (!current) {
            AdhkarEngine.EVENING_ADHKAR.forEach { adhkarEngine.markDhikrDone(it.id) }
        }
        setShamAzkarDone(!current)
        return !current
    }

    fun syncAzkarFromTab() {
        val morningDone = adhkarEngine.isMorningComplete()
        val eveningDone = adhkarEngine.isEveningComplete()
        setSubahAzkarDone(morningDone)
        setShamAzkarDone(eveningDone)
    }

    fun calculateScore(): Int {
        var total = 0
        val qadaEngine = QadaBankEngine(context)
        val healthEngine = HealthEngine(context)

        for (prayer in FARD_PRAYERS) {
            val state = getPrayerState(prayer)
            val isJummah = prayer == "Zuhr" && isJummahToday()
            when (state) {
                PrayerState.DONE -> total += if (isJummah) 30 else 20
                PrayerState.QADA -> {
                    if (qadaEngine.isPrayerCompletedInQada(prayer, today)) total += 10
                }
                PrayerState.PENDING -> {}
            }
        }

        if (isAllFardDone()) {
            val alreadyAwarded = try { prefs.getBoolean("$KEY_PERFECT_AWARDED$today", false) } catch (_: ClassCastException) { false }
            if (!alreadyAwarded) {
                total += 20
                prefs.edit().putBoolean("$KEY_PERFECT_AWARDED$today", true).apply()
                val curPerfect = prefs.getInt(KEY_PERFECT_DAYS, 0)
                prefs.edit().putInt(KEY_PERFECT_DAYS, curPerfect + 1).apply()
            } else {
                total += 20
            }
        }

        if (isTahajjudDone()) total += 10
        if (isSubahAzkarDone()) total += 5
        if (isShamAzkarDone()) total += 5
        if (isQuranTilawatDone()) total += 10
        if (healthEngine.getTodayExerciseMinutes() >= 30) total += 18

        val medLogs = healthEngine.getTodayMedicationLog()
        val activeMeds = healthEngine.getMedications().filter { it.isActive }
        if (activeMeds.isNotEmpty() && medLogs.all { it.taken }) total += 5

        total += prefs.getInt(KEY_BONUS_SCORE, 0)
        prefs.edit().putInt(KEY_SCORE, total).apply()
        return total
    }

    fun addBonusScore(amount: Int) {
        val current = prefs.getInt(KEY_BONUS_SCORE, 0)
        prefs.edit().putInt(KEY_BONUS_SCORE, current + amount).apply()
    }

    fun getScore(): Int = prefs.getInt(KEY_SCORE, 0)

    fun getPerfectDays(): Int = prefs.getInt(KEY_PERFECT_DAYS, 0)

    data class LevelDef(val level: Int, val title: String, val translation: String, val scoreRequired: Int)

    val LEVELS = listOf(
        LevelDef(1, "صابر", "Patient", 0),
        LevelDef(2, "شاکر", "Grateful", 1000),
        LevelDef(3, "محسن", "Doer of Good", 3000),
        LevelDef(4, "خاشع", "Humble in Prayer", 6000),
        LevelDef(5, "مخبت", "Humble before Allah", 10000),
        LevelDef(6, "منیب", "Turner to Allah", 15000),
        LevelDef(7, "اواب", "Frequently Returning", 22000),
        LevelDef(8, "سابق", "Foremost in Faith", 35000),
        LevelDef(9, "مقرب", "Near to Allah", 50000),
        LevelDef(10, "صدیق", "Truthful", 75000)
    )

    fun getLevel(score: Int): LevelDef {
        var current = LEVELS.first()
        for (level in LEVELS) {
            if (score >= level.scoreRequired) current = level
        }
        return current
    }

    fun getNextLevel(score: Int): LevelDef? {
        for (level in LEVELS) {
            if (score < level.scoreRequired) return level
        }
        return null
    }

    fun getLevelProgress(score: Int): Float {
        val current = getLevel(score)
        val next = getNextLevel(score) ?: return 1f
        val range = next.scoreRequired - current.scoreRequired
        if (range <= 0) return 1f
        return ((score - current.scoreRequired).toFloat() / range).coerceIn(0f, 1f)
    }

    private fun getYesterday(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return dateFormat.format(cal.time)
    }

    fun getStreak(): Int {
        val savedStreak = prefs.getInt("streak", 0)
        val lastActive = prefs.getString(KEY_LAST_ACTIVE, "")
        return if (lastActive == today || lastActive == getYesterday()) savedStreak else 0
    }

    fun updateStreak() {
        val savedStreak = prefs.getInt("streak", 0)
        val lastActive = prefs.getString(KEY_LAST_ACTIVE, "")
        val newStreak = when {
            lastActive == getYesterday() -> savedStreak + 1
            lastActive == today -> savedStreak
            else -> 1
        }
        prefs.edit().putInt("streak", newStreak).putString(KEY_LAST_ACTIVE, today).apply()
    }

    fun getWeeklyStats(): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        repeat(7) {
            val date = dateFormat.format(cal.time)
            val count = FARD_PRAYERS.count {
                prefs.getString("${it}_$date", "PENDING") == "DONE"
            }
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
            val count = FARD_PRAYERS.count {
                prefs.getString("${it}_$date", "PENDING") == "DONE"
            }
            total += count * 20
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return total
    }

    fun resetDaily() {
        val editor = prefs.edit()
        for (prayer in FARD_PRAYERS) {
            editor.remove("${prayer}_$today")
        }
        editor.remove("subah_azkar_$today")
            .remove("sham_azkar_$today")
            .remove("tahajjud_$today")
            .remove("quran_tilawat_$today")
            .remove("$KEY_PERFECT_AWARDED$today")
            .apply()
    }

    fun getMaxDailyPoints(): Int {
        var max = 0
        for (prayer in FARD_PRAYERS) {
            max += if (prayer == "Zuhr" && isJummahToday()) 30 else 20
        }
        max += 10
        max += 5
        max += 5
        max += 20
        max += 18
        max += 5
        max += 10
        return max
    }

    fun getPrayerTimeForDisplay(prayer: String, prayerTimesMap: Map<String, String>): String {
        return if (prayer == "Jummah") prayerTimesMap["Zuhr"] ?: "--:--"
        else prayerTimesMap[prayer] ?: "--:--"
    }
}
