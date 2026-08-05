package islamic.duas.haidh

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

data class Medication(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dosage: String,
    val frequency: Int,
    val times: List<String>,
    val notes: String = "",
    val isActive: Boolean = true,
    val refillDate: String? = null
)

data class MedicationLog(
    val medicationId: String,
    val date: String,
    val time: String,
    val taken: Boolean,
    val notes: String = ""
)

class HealthEngine(private val context: Context) {

    private val prefs: SharedPreferences by lazy { context.getSharedPreferences("health_prefs", Context.MODE_PRIVATE) }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        private const val KEY_EXERCISE_COUNT = "exercise_count_"
        private const val KEY_STEP_GOAL = "step_goal"
        private const val KEY_TODAY_STEPS = "today_steps_"
        private const val KEY_STEP_SENSOR_BASELINE = "step_sensor_baseline"
        private const val KEY_HISTORICAL_STEPS = "steps_"
        private const val KEY_MEDITATIONS_PREFIX = "medications_"
        private const val KEY_MED_LOG_PREFIX = "med_log_"
        private const val KEY_CONSECUTIVE_EXERCISE = "consecutive_exercise_days"
        private const val KEY_MED_REMINDER_MUTED = "med_reminder_muted"

        fun isMedReminderMuted(context: Context): Boolean {
            return try { context.getSharedPreferences("health_prefs", Context.MODE_PRIVATE)
                .getBoolean(KEY_MED_REMINDER_MUTED, false) } catch (_: ClassCastException) { false }
        }

        fun setMedReminderMuted(context: Context, muted: Boolean) {
            context.getSharedPreferences("health_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_MED_REMINDER_MUTED, muted).apply()
        }

        val EXERCISE_TYPES = listOf(
            "چہل قدمی (Walking)",
            "دوڑ (Jogging)",
            "یوگا (Yoga)",
            "تیراکی (Swimming)",
            "وزن اٹھانا (Strength)",
            "گھریلو ورزش (Home Workout)",
            "سائیکلنگ (Cycling)"
        )

        val EXERCISE_FACTS = listOf(
            "ورزش سے دماغ میں endorphins خارج ہوتے ہیں جو آپ کو خوش رکھتے ہیں — یہ اللہ کی رحمت ہے",
            "باقاعدہ ورزش سے نماز میں خشوع بڑھتا ہے — جسمانی سکون ذہنی سکون لاتا ہے",
            "ورزش سے یادداشت بہتر ہوتی ہے — جو قرآن حفظ میں مدد دیتی ہے",
            "ورزش سے نیند بہتر آتی ہے — جیسا کہ نبی ﷺ نے رات کے قیام کا حکم دیا",
            "ورزش سے نماز کے لیے ضروری جسمانی طاقت ملتی ہے",
            "نبی ﷺ نے فرمایا: 'تم میں بہتر وہ ہے جو اپنی صحت کا خیال رکھے'",
            "ورزش سے دل مضبوط ہوتا ہے — اللہ نے دل کو ایمان کی جگہ بنایا",
            "ورزش سے خون کی گردش بہتر ہوتی ہے — جو عبادت میں مدد دیتی ہے",
            "ورزش سے ذہنی دباؤ کم ہوتا ہے — اللہ نے ورزش کو شفا بنایا",
            "ورزش سے جسم لچکدار رہتا ہے — جو سجدے اور رکوع میں مدد دیتی ہے",
        )
    }

    val today: String get() = dateFormat.format(Date())

    // ========== Exercise ==========

    fun getExerciseTargetMinutes(): Int = 45

    fun getWeeklyExerciseCount(): Int {
        var count = 0
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        repeat(7) {
            val date = dateFormat.format(cal.time)
            count += prefs.getInt("$KEY_EXERCISE_COUNT$date", 0)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return count
    }

    fun getTodayExerciseMinutes(): Int = prefs.getInt("$KEY_EXERCISE_COUNT$today", 0)

    fun recordExercise(minutes: Int) {
        prefs.edit().putInt("$KEY_EXERCISE_COUNT$today", minutes).apply()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayStr = dateFormat.format(yesterday.time)
        val yesterdayHad = prefs.getInt("$KEY_EXERCISE_COUNT$yesterdayStr", 0) >= 30
        val currentStreak = prefs.getInt(KEY_CONSECUTIVE_EXERCISE, 0)
        if (yesterdayHad) {
            prefs.edit().putInt(KEY_CONSECUTIVE_EXERCISE, currentStreak + 1).apply()
        } else {
            prefs.edit().putInt(KEY_CONSECUTIVE_EXERCISE, 1).apply()
        }
    }

    fun getExerciseStreak(): Int = prefs.getInt(KEY_CONSECUTIVE_EXERCISE, 0)

    fun getExerciseMinutesForDate(date: String): Int = prefs.getInt("$KEY_EXERCISE_COUNT$date", 0)

    fun getWeeklyExerciseDays(): Int {
        var count = 0
        for (i in 0 until 7) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val date = dateFormat.format(cal.time)
            if (prefs.getInt("$KEY_EXERCISE_COUNT$date", 0) > 0) count++
        }
        return count
    }

    fun getLast30DaysExercise(): List<Pair<String, Boolean>> {
        val data = mutableListOf<Pair<String, Boolean>>()
        val cal = Calendar.getInstance()
        repeat(30) {
            val date = dateFormat.format(cal.time)
            data.add(date to (prefs.getInt("$KEY_EXERCISE_COUNT$date", 0) > 0))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return data.reversed()
    }

    fun isTodaysExerciseDone(): Boolean = getTodayExerciseMinutes() >= getExerciseTargetMinutes()

    fun getWeeklyExerciseData(): List<Pair<String, Int>> {
        val data = mutableListOf<Pair<String, Int>>()
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val dayNames = listOf("پ", "م", "ب", "ج", "ج", "ہ", "ات")
        repeat(7) {
            val date = dateFormat.format(cal.time)
            val mins = prefs.getInt("$KEY_EXERCISE_COUNT$date", 0)
            data.add(dayNames[it] to mins)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return data
    }

    fun getRandomExerciseFact(): String {
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        return EXERCISE_FACTS[dayOfYear % EXERCISE_FACTS.size]
    }

    fun getMonthExerciseData(year: Int, month: Int): Map<Int, Boolean> {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val result = mutableMapOf<Int, Boolean>()
        for (day in 1..daysInMonth) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            val dateStr = dateFormat.format(cal.time)
            result[day] = prefs.getInt("$KEY_EXERCISE_COUNT$dateStr", 0) > 0
        }
        return result
    }

    fun getMonthExerciseMinutes(year: Int, month: Int): Map<Int, Int> {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val result = mutableMapOf<Int, Int>()
        for (day in 1..daysInMonth) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            val dateStr = dateFormat.format(cal.time)
            result[day] = prefs.getInt("$KEY_EXERCISE_COUNT$dateStr", 0)
        }
        return result
    }

    val EXERCISE_TEACHINGS = listOf(
        Pair("🌙 رسول اللہ ﷺ کی تعلیم",
             "نبی کریم ﷺ نے فرمایا: 'مومن قوی اور مضبوط اللہ کے نزدیک کمزور مومن سے بہتر ہے' (مسلم)۔ ورزش سے جسم مضبوط ہوتا ہے جو عبادت میں مدد دیتا ہے۔"),
        Pair("❤️ دل کی صحت",
             "روزانہ 20 منٹ کی چہل قدمی دل کی بیماریوں کا خطرہ 30% تک کم کرتی ہے۔ اللہ نے ہمارے جسم کو امانت دیا ہے — اس کی حفاظت ہماری ذمہ داری ہے۔"),
        Pair("🧠 ذہنی سکون",
             "ورزش سے اینڈورفنز خارج ہوتے ہیں جو ڈپریشن اور پریشانی کو کم کرتے ہیں۔ جیسے نماز ذہنی سکون دیتی ہے، ویسے ہی ورزش دماغ کو تروتازہ رکھتی ہے۔"),
        Pair("📖 قرآن حفظ میں مدد",
             "باقاعدہ ورزش سے یادداشت اور توجہ بہتر ہوتی ہے۔ جو قرآن حفظ کرنے اور سمجھنے میں مدد دیتی ہے۔ اللہ نے فرمایا: 'اور ہم نے قرآن کو آسان کر دیا ہے یاد کرنے کے لیے' (القمر)"),
        Pair("💪 نماز کے لیے طاقت",
             "نماز میں قیام، رکوع اور سجدہ جسمانی طاقت مانگتے ہیں۔ ورزش سے پٹھے مضبوط ہوتے ہیں، جس سے نماز بہتر طریقے سے ادا کی جا سکتی ہے۔"),
        Pair("😴 بہتر نیند",
             "روزانہ ورزش کرنے والوں کی نیند بہتر ہوتی ہے۔ نبی ﷺ نے فرمایا: 'رات کو سوؤ کیونکہ تمہاری آنکھوں کا تم پر حق ہے' (بخاری)۔"),
        Pair("🌡 وزن کنٹرول",
             "ورزش میٹابولزم کو تیز کرتی ہے اور وزن کو کنٹرول میں رکھتی ہے۔ اسلامی تعلیمات میں میانہ روی اور اعتدال پر زور دیا گیا ہے — نہ زیادہ کھاؤ نہ اپنے جسم کو نقصان پہنچاؤ۔"),
        Pair("🤝 اللہ کی رحمت",
             "اللہ نے ہمارے جسم کو بہترین بنایا ہے (احسن تقویم)۔ ورزش اس نعمت کا شکر ادا کرنے کا ایک طریقہ ہے۔ نبی ﷺ نے فرمایا: 'تمہارے جسم کا تم پر حق ہے' (بخاری)"),
        Pair("🦴 ہڈیاں اور جوڑ",
             "باقاعدہ ورزش سے ہڈیاں مضبوط رہتی ہیں اور جوڑوں میں لچک برقرار رہتی ہے۔ یہ بڑھاپے میں بھی عبادت آسانی سے کرنے میں مدد دیتی ہے۔"),
        Pair("⚡ توانائی میں اضافہ",
             "ورزش سے جسم میں توانائی کی سطح بڑھتی ہے اور تھکن کم ہوتی ہے۔ اس سے روزمرہ کے کاموں میں بہتری آتی ہے اور عبادت کے لیے بھی توانائی ملتی ہے۔")
    )

    fun getExerciseTeaching(index: Int): Pair<String, String> {
        return EXERCISE_TEACHINGS[index % EXERCISE_TEACHINGS.size]
    }

    // ========== Step Counter ==========

    fun getStepGoal(): Int = prefs.getInt(KEY_STEP_GOAL, 8000)
    fun setStepGoal(goal: Int) { prefs.edit().putInt(KEY_STEP_GOAL, goal).apply() }

    fun getTodaySteps(): Int = prefs.getInt("$KEY_TODAY_STEPS$today", 0)

    fun updateSteps(steps: Int) {
        val current = getTodaySteps()
        if (steps > current) {
            prefs.edit().putInt("$KEY_TODAY_STEPS$today", steps).apply()
        }
    }

    fun getWeeklySteps(): List<Int> {
        val data = mutableListOf<Int>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        repeat(7) {
            val date = dateFormat.format(cal.time)
            data.add(prefs.getInt("$KEY_TODAY_STEPS$date", 0))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return data
    }

    // ── Step persistence for calendar history ──

    fun setTodaySteps(steps: Int) {
        prefs.edit().putInt("$KEY_TODAY_STEPS$today", steps).apply()
    }

    fun getStepsForDate(date: String): Int = prefs.getInt("$KEY_HISTORICAL_STEPS$date", 0)

    fun persistStepsForDate(date: String, steps: Int) {
        prefs.edit().putInt("$KEY_HISTORICAL_STEPS$date", steps).apply()
    }

    fun getMonthStepData(year: Int, month: Int): Map<Int, Int> {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val result = mutableMapOf<Int, Int>()
        for (day in 1..daysInMonth) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            val dateStr = dateFormat.format(cal.time)
            result[day] = prefs.getInt("$KEY_HISTORICAL_STEPS$dateStr", 0)
        }
        return result
    }

    fun getSensorBaseline(): Int {
        return try { prefs.getInt(KEY_STEP_SENSOR_BASELINE, -1) } catch (_: Exception) { -1 }
    }

    fun setSensorBaseline(value: Int) {
        prefs.edit().putInt(KEY_STEP_SENSOR_BASELINE, value).apply()
    }

    // ========== Medication ==========

    fun getMedications(): List<Medication> {
        val json = prefs.getString(KEY_MEDITATIONS_PREFIX + "list", "[]") ?: "[]"
        return parseMedications(json)
    }

    fun saveMedication(med: Medication) {
        val all = getMedications().toMutableList()
        val existing = all.indexOfFirst { it.id == med.id }
        if (existing >= 0) all[existing] = med
        else all.add(med)
        saveMedicationList(all)
    }

    fun deleteMedication(medId: String) {
        val all = getMedications().toMutableList()
        all.removeAll { it.id == medId }
        saveMedicationList(all)
    }

    fun getTodayMedicationLog(): List<MedicationLog> {
        val all = getMedications().filter { it.isActive }
        val logs = mutableListOf<MedicationLog>()
        for (med in all) {
            for (time in med.times) {
                val key = "$KEY_MED_LOG_PREFIX${med.id}_${today}_$time"
                val taken = try { prefs.getBoolean(key, false) } catch (_: ClassCastException) { false }
                val notes = prefs.getString("${key}_notes", "") ?: ""
                logs.add(MedicationLog(med.id, today, time, taken, notes))
            }
        }
        return logs
    }

    fun logMedicationDose(medId: String, time: String, taken: Boolean, notes: String = "") {
        val key = "$KEY_MED_LOG_PREFIX${medId}_${today}_$time"
        prefs.edit().putBoolean(key, taken).apply()
        if (notes.isNotBlank()) {
            prefs.edit().putString("${key}_notes", notes).apply()
        }
    }

    fun doseMinuteOfDay(time: String): Int? {
        val trimmed = time.trim()
        if (trimmed == "صبح") return 8 * 60
        if (trimmed == "دوپہر") return 14 * 60
        if (trimmed == "شام") return 20 * 60
        val ampm = when {
            trimmed.endsWith("AM", ignoreCase = true) -> "AM"
            trimmed.endsWith("PM", ignoreCase = true) -> "PM"
            else -> null
        }
        val numeric = if (ampm != null) trimmed.dropLast(2).trim() else trimmed
        val parts = numeric.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (m !in 0..59) return null
        val h24 = when (ampm) {
            "AM" -> if (h == 12) 0 else h
            "PM" -> if (h == 12) 12 else h + 12
            null -> h
            else -> return null
        }
        if (h24 !in 0..23) return null
        return h24 * 60 + m
    }

    fun getPendingMedications(): List<String> {
        val pending = mutableListOf<String>()
        val now = Calendar.getInstance()
        val currentMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        for (med in getMedications().filter { it.isActive }) {
            for (time in med.times) {
                val totalMedMin = doseMinuteOfDay(time) ?: continue
                if (totalMedMin <= currentMin) {
                    val logKey = "$KEY_MED_LOG_PREFIX${med.id}_${today}_$time"
                    val logTaken = try { prefs.getBoolean(logKey, false) } catch (_: ClassCastException) { false }
                    if (!logTaken) {
                        pending.add(med.name)
                    }
                }
            }
        }
        return pending.distinct()
    }

    // ========== Private Helpers ==========

    private fun parseMedications(json: String): List<Medication> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i -> parseMedication(arr.getJSONObject(i)) }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseMedication(obj: JSONObject): Medication {
        return Medication(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", ""),
            dosage = obj.optString("dosage", ""),
            frequency = obj.optInt("frequency", 1),
            times = obj.optString("times", "").split(";").filter { it.isNotBlank() },
            notes = obj.optString("notes", ""),
            isActive = obj.optBoolean("isActive", true),
            refillDate = obj.optString("refillDate", "").ifEmpty { null }
        )
    }

    private fun saveMedicationList(meds: List<Medication>) {
        val arr = JSONArray()
        for (med in meds) {
            arr.put(JSONObject().apply {
                put("id", med.id)
                put("name", med.name)
                put("dosage", med.dosage)
                put("frequency", med.frequency)
                put("times", med.times.joinToString(";"))
                put("notes", med.notes)
                put("isActive", med.isActive)
                put("refillDate", med.refillDate ?: "")
            })
        }
        prefs.edit().putString(KEY_MEDITATIONS_PREFIX + "list", arr.toString()).apply()
    }
}
