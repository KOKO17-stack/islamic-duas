package islamic.duas

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

enum class SpiritualStruggle(val label: String) {
    CONSISTENCY("استقامت — نماز وقت پر پڑھنا"),
    FOCUS("توجہ — نماز میں خشوع"),
    KNOWLEDGE("علم — دین سیکھنا"),
    EMOTIONAL("جذبات — اللہ سے تعلق")
}

enum class LoveLanguage(val label: String) {
    PRAYER("نماز"),
    QURAN("قرآن"),
    DHIKR("ذکر"),
    SADAQAH("صدقہ")
}

enum class DifficultyTime(val label: String) {
    FAJR("فجر"),
    WORK("دن کے اوقات"),
    EVENING("شام"),
    NIGHT("رات")
}

enum class ArabicLevel(val label: String) {
    NONE("نہیں آتی"),
    BASIC("تھوڑی بہت"),
    INTERMEDIATE("اچھی خاصی"),
    FLUENT("پڑھ لکھ سکتی ہوں")
}

enum  class SpiritualGoal(val label: String) {
    HABIT("عادت بنانا"),
    LEARN("سیکھنا"),
    CONNECTION("اللہ سے تعلق"),
    ALL("سب کچھ")
}

data class Persona(
    val struggle: SpiritualStruggle = SpiritualStruggle.CONSISTENCY,
    val loveLanguage: LoveLanguage = LoveLanguage.PRAYER,
    val difficultyTime: DifficultyTime = DifficultyTime.FAJR,
    val arabicLevel: ArabicLevel = ArabicLevel.NONE,
    val goal: SpiritualGoal = SpiritualGoal.ALL
)

class UserProfile(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_NAME = "user_name"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_PERSONA_STRUGGLE = "persona_struggle"
        private const val KEY_PERSONA_LOVE = "persona_love"
        private const val KEY_PERSONA_TIME = "persona_time"
        private const val KEY_PERSONA_ARABIC = "persona_arabic"
        private const val KEY_PERSONA_GOAL = "persona_goal"
    }

    fun isOnboarded(): Boolean = prefs.getBoolean(KEY_ONBOARDED, false)

    fun markOnboarded() {
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    fun getName(): String = prefs.getString(KEY_NAME, "") ?: ""

    fun setName(name: String) {
        prefs.edit().putString(KEY_NAME, name).apply()
    }

    fun getGreeting(): String {
        val name = getName()
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when (hour) {
            in 5..11 -> "صبح بخیر"
            in 12..17 -> "السلام علیکم"
            else -> "السلام علیکم"
        }
        return if (name.isNotEmpty()) "$timeGreeting، $name"
        else "$timeGreeting"
    }

    fun getPersona(): Persona {
        return Persona(
            struggle = SpiritualStruggle.values().getOrElse(prefs.getInt(KEY_PERSONA_STRUGGLE, 0)) { SpiritualStruggle.CONSISTENCY },
            loveLanguage = LoveLanguage.values().getOrElse(prefs.getInt(KEY_PERSONA_LOVE, 0)) { LoveLanguage.PRAYER },
            difficultyTime = DifficultyTime.values().getOrElse(prefs.getInt(KEY_PERSONA_TIME, 0)) { DifficultyTime.FAJR },
            arabicLevel = ArabicLevel.values().getOrElse(prefs.getInt(KEY_PERSONA_ARABIC, 0)) { ArabicLevel.NONE },
            goal = SpiritualGoal.values().getOrElse(prefs.getInt(KEY_PERSONA_GOAL, 0)) { SpiritualGoal.ALL }
        )
    }

    fun savePersona(persona: Persona) {
        prefs.edit().apply {
            putInt(KEY_PERSONA_STRUGGLE, persona.struggle.ordinal)
            putInt(KEY_PERSONA_LOVE, persona.loveLanguage.ordinal)
            putInt(KEY_PERSONA_TIME, persona.difficultyTime.ordinal)
            putInt(KEY_PERSONA_ARABIC, persona.arabicLevel.ordinal)
            putInt(KEY_PERSONA_GOAL, persona.goal.ordinal)
            apply()
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
