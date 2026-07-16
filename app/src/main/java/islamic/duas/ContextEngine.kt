package islamic.duas

import java.util.Calendar

data class AppContext(
    val timeOfDay: TimeBlock = TimeBlock.MORNING,
    val dayOfWeek: Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK),
    val isFriday: Boolean = false,
    val isRamadan: Boolean = false,
    val nextPrayer: String = "",
    val currentMood: CompanionMood? = null,
    val dailyStreak: Int = 0,
    val score: Int = 0,
    val level: String = "",
    val lastActiveMinutes: Int = 0,
    val completedPrayers: Int = 0,
    val totalPrayers: Int = 5,
    val isHaidh: Boolean = false,
    val isIstihadah: Boolean = false
)

enum class TimeBlock {
    FAJR_TIME, MORNING, NOON, AFTERNOON, MAGHRIB_TIME, EVENING, NIGHT, LATE_NIGHT
}

class ContextEngine {

    fun getCurrentTimeBlock(): TimeBlock {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val totalMinutes = hour * 60 + minute
        return when (totalMinutes) {
            in 0..4 * 60 + 30 -> TimeBlock.LATE_NIGHT
            in 4 * 60 + 31..6 * 60 + 30 -> TimeBlock.FAJR_TIME
            in 6 * 60 + 31..11 * 60 + 59 -> TimeBlock.MORNING
            in 12 * 60..15 * 60 + 30 -> TimeBlock.NOON
            in 15 * 60 + 31..17 * 60 + 59 -> TimeBlock.AFTERNOON
            in 18 * 60..18 * 60 + 45 -> TimeBlock.MAGHRIB_TIME
            in 18 * 60 + 46..20 * 60 + 59 -> TimeBlock.EVENING
            else -> TimeBlock.NIGHT
        }
    }

    fun buildContext(
        mood: CompanionMood? = null,
        streak: Int = 0,
        score: Int = 0,
        level: String = "",
        lastActive: Long = System.currentTimeMillis(),
        completedPrayers: Int = 0,
        isHaidh: Boolean = false,
        isIstihadah: Boolean = false
    ): AppContext {
        val cal = Calendar.getInstance()
        val nextPrayer = ""

        return AppContext(
            timeOfDay = getCurrentTimeBlock(),
            dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
            isFriday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY,
            isRamadan = HijriCalendar.toHijri().second == 9,
            nextPrayer = nextPrayer,
            currentMood = mood,
            dailyStreak = streak,
            score = score,
            level = level,
            lastActiveMinutes = ((System.currentTimeMillis() - lastActive) / 60000).toInt(),
            completedPrayers = completedPrayers,
            isHaidh = isHaidh,
            isIstihadah = isIstihadah
        )
    }

    fun getSuggestion(context: AppContext): String {
        return when {
            context.nextPrayer == "Fajr" && context.timeOfDay != TimeBlock.FAJR_TIME ->
                "فجر کا وقت قریب ہے — سو جاؤ تاکہ فجر کے لیے اٹھ سکو"
            context.timeOfDay == TimeBlock.FAJR_TIME ->
                "فجر کا وقت ہے — اللہ کے حضور حاضر ہو جاؤ"
            !context.isHaidh && context.completedPrayers < context.totalPrayers -> {
                val remaining = context.totalPrayers - context.completedPrayers
                "آج ${context.totalPrayers} میں سے ${context.completedPrayers} پڑھ چکی ہو — باقی $remaining رہ گئیں 🤍"
            }
            context.timeOfDay == TimeBlock.MAGHRIB_TIME ->
                "مغرب کا وقت ہے — روزہ کھولنے کا وقت، دعا نہ بھولنا"
            context.timeOfDay == TimeBlock.EVENING ->
                "شام کے اذکار پڑھ لو — رات کی حفاظت میں رہو گی"
            context.timeOfDay == TimeBlock.LATE_NIGHT ->
                "رات بہت ہو گئی — سونے سے پہلے سورہ الملک پڑھ لو"
            context.isFriday ->
                "جمعہ مبارک! آج درود کی کثرت کرو اور سورہ الکہف پڑھو"
            context.isRamadan ->
                "رمضان مبارک! آج کا روزہ یاد رکھو اور قرآن کی تلاوت کرو"
            context.dailyStreak > 0 && context.dailyStreak % 7 == 0 ->
                "مبارک ہو! ${context.dailyStreak} دن کا اسٹریک — اللہ تمہیں ثابت قدم رکھے"
            else -> "اللہ کی یاد میں سکون ہے — آج کا ذکر کرو"
        }
    }

    fun getGreeting(context: AppContext): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "صبح بخیر"
            in 12..17 -> "السلام علیکم"
            else -> "السلام علیکم"
        }
    }
}
