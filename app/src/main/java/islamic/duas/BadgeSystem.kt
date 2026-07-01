package islamic.duas

import android.content.Context
import android.content.SharedPreferences

data class LevelDef(val level: Int, val title: String, val translation: String, val scoreRequired: Int)
data class BadgeDef(val id: String, val title: String, val description: String, val icon: String, val scoreReward: Int)

class BadgeSystem(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("badges", Context.MODE_PRIVATE)

    companion object {
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

        val BADGES = listOf(
            BadgeDef("streak_7", "🔥 ۷ دن کا اسٹریک", "لگاتار ۷ دن تمام ۵ نمازیں پڑھیں", "🔥", 50),
            BadgeDef("streak_30", "🔥🔥 ۳۰ دن کا اسٹریک", "لگاتار ۳۰ دن تمام نمازیں پڑھیں", "🔥🔥", 200),
            BadgeDef("dhikr_1000", "📿 ۱۰۰۰ ذکر", "ایک دن میں ۱۰۰۰ ذکر کریں", "📿", 30),
            BadgeDef("perfect_week", "🕌 پرفیکٹ ویک", "ایک ہفتے میں تمام ۳۵ فرض پڑھیں", "🕌", 100),
            BadgeDef("qada_10", "💪 قدا واریر", "۱۰ قضا نمازیں مکمل کریں", "💪", 50),
            BadgeDef("cycle_30", "🌸 سائیکل ٹریکر", "۳۰ دن حیض کا ڈیٹا ریکارڈ کریں", "🌸", 20),
            BadgeDef("focus_10h", "🎯 فوکس ماسٹر", "۱۰ گھنٹے فوکس سیشن مکمل کریں", "🎯", 100),
            BadgeDef("sadaqah_7", "🤝 صدقہ", "لگاتار ۷ دن صدقہ دیں", "🤝", 50),
            BadgeDef("morning_azkar", "📖 صبح کے اذکار", "ایک دن میں تمام صبح کے اذکار مکمل کریں", "📖", 30),
            BadgeDef("evening_azkar", "🌙 شام کے اذکار", "ایک دن میں تمام شام کے اذکار مکمل کریں", "🌙", 30)
        )
    }

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
        val progress = (score - current.scoreRequired).toFloat() / range
        return progress.coerceIn(0f, 1f)
    }

    fun isBadgeEarned(badgeId: String): Boolean {
        return prefs.getBoolean("badge_$badgeId", false)
    }

    fun earnBadge(badgeId: String): Boolean {
        if (isBadgeEarned(badgeId)) return false
        prefs.edit().putBoolean("badge_$badgeId", true).apply()
        return true
    }

    fun getEarnedBadgeCount(): Int {
        return BADGES.count { isBadgeEarned(it.id) }
    }

    fun getTotalBadges(): Int = BADGES.size

    fun getBadge(badgeId: String): BadgeDef? = BADGES.find { it.id == badgeId }
}
