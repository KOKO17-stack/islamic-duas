package islamic.duas

import kotlin.random.Random

data class ChallengeDay(
    val day: Int,
    val title: String,
    val description: String,
    val arabic: String = "",
    val action: String,
    val scoreReward: Int = 10
)

data class ChallengeTrack(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val description: String,
    val days: List<ChallengeDay>,
    val totalDays: Int = 30
)

class ChallengeEngine {

    private val tracks = listOf(
        ChallengeTrack(
            "prayer_30", "۳۰ دن — نماز کی پابندی", "نماز وقت پر پڑھنے کا چیلنج",
            "🕌", "اس ۳۰ دن کے چیلنج میں ہر روز اپنی پانچوں نمازیں وقت پر پڑھیں۔ ہر نماز کے بعد اذکار کرنا نہ بھولیں۔",
            (1..30).map { day ->
                ChallengeDay(
                    day, "دن $day — نماز وقت پر",
                    if (day <= 7) "فجر پر خاص توجہ دیں — سب سے اہم نماز"
                    else if (day <= 14) "اب تمام نمازیں وقت پر پڑھنے کی کوشش کریں"
                    else if (day <= 21) "نماز میں خشوع پیدا کریں — سمجھ کر پڑھیں"
                    else "نماز کو اپنی زندگی کا حصہ بنائیں",
                    action = "آج کی ۵ نمازیں وقت پر پڑھیں",
                    scoreReward = if (day % 7 == 0) 50 else 10
                )
            }
        ),
        ChallengeTrack(
            "azkar_30", "۳۰ دن — اذکار کا معمول", "صبح و شام کے اذکار",
            "📿", "ہر روز صبح اور شام کے اذکار پڑھیں۔ یہ آپ کو اللہ کی حفاظت میں رکھیں گے۔",
            (1..30).map { day ->
                ChallengeDay(
                    day, "دن $day — اذکار",
                    if (day <= 7) "صبح کے اذکار پر توجہ دیں — کم از کم ۵ اذکار"
                    else if (day <= 14) "صبح اور شام دونوں کے اذکار پڑھیں"
                    else if (day <= 21) "نماز کے بعد کے اذکار بھی شامل کریں"
                    else "اذکار کو اپنی زندگی کا حصہ بنائیں",
                    action = "صبح اور شام کے اذکار پڑھیں",
                    scoreReward = if (day % 7 == 0) 30 else 5
                )
            }
        ),
        ChallengeTrack(
            "quran_30", "۳۰ دن — قرآن سے تعلق", "روزانہ قرآن پڑھنے کا چیلنج",
            "📖", "ہر روز کم از کم ۱۰ منٹ قرآن پڑھیں۔ سمجھ کر پڑھیں اور غور کریں۔",
            (1..30).map { day ->
                ChallengeDay(
                    day, "دن $day — تلاوت",
                    if (day <= 7) "آج ۱۰ منٹ قرآن پڑھیں — کوئی بھی سورۃ"
                    else if (day <= 14) "آج ۱۵ منٹ قرآن پڑھیں — ترجمہ دیکھ کر"
                    else if (day <= 21) "آج ۲۰ منٹ قرآن پڑھیں — ایک پارہ مکمل کریں"
                    else "آج ۲۵ منٹ قرآن پڑھیں — غور و تدبر کے ساتھ",
                    action = "آج قرآن پڑھیں",
                    scoreReward = if (day % 7 == 0) 100 else 20
                )
            }
        ),
        ChallengeTrack(
            "sadaqah_30", "۳۰ دن — صدقہ کا سلسلہ", "روزانہ صدقہ کا چیلنج",
            "🤝", "ہر روز کچھ نہ کچھ صدقہ دیں — چاہے تھوڑا ہی سہی۔ صدقہ رزق میں برکت لاتا ہے۔",
            (1..30).map { day ->
                ChallengeDay(
                    day, "دن $day — صدقہ",
                    if (day <= 7) "آج کچھ صدقہ دیں — چاہے ۱۰ روپے ہی سہی"
                    else if (day <= 14) "آج کسی کو کھانا کھلائیں یا کوئی نیکی کریں"
                    else if (day <= 21) "کسی ضرورت مند کی مدد کریں"
                    else "صدقہ کو اپنی عادت بنائیں",
                    action = "آج صدقہ دیں",
                    scoreReward = if (day % 7 == 0) 50 else 15
                )
            }
        ),
        ChallengeTrack(
            "tahajjud_30", "۳۰ دن — تہجد کا سفر", "رات کی نماز کا چیلنج",
            "🌃", "ہر رات تہجد کی نماز پڑھیں — چاہے ۲ رکعت ہی سہی۔ رات کا وقت دعا کی قبولیت کا ہے۔",
            (1..30).map { day ->
                ChallengeDay(
                    day, "دن $day — تہجد",
                    if (day <= 7) "آج رات ۲ رکعت تہجد پڑھیں — فجر سے پہلے"
                    else if (day <= 14) "آج ۴ رکعت تہجد پڑھیں — لمبی سجدے کریں"
                    else if (day <= 21) "آج ۶ رکعت تہجد پڑھیں — خوب دعا کریں"
                    else "تہجد کو اپنی زندگی کا حصہ بنائیں",
                    action = "آج رات تہجد پڑھیں",
                    scoreReward = if (day % 7 == 0) 100 else 25
                )
            }
        ),
        ChallengeTrack(
            "fasting_30", "۳۰ دن — روزے کا چیلنج", "پیر اور جمعرات کے روزے",
            "🌙", "اس مہینے پیر اور جمعرات کے سنت روزے رکھیں — اور ایام بیض کے روزے بھی۔",
            (1..30).map { day ->
                ChallengeDay(
                    day, "دن $day — روزہ",
                    if (day % 7 == 1 || day % 7 == 4) "آج سنت روزہ رکھیں — پیر یا جمعرات"
                    else if (day == 13 || day == 14 || day == 15) "آج ایام بیض کا روزہ رکھیں"
                    else "آج قضا روزہ رکھیں اگر کوئی ہے",
                    action = "آج روزہ رکھیں",
                    scoreReward = 30
                )
            }
        )
    )

    fun getTracks(): List<ChallengeTrack> = tracks

    fun getTrack(id: String): ChallengeTrack? = tracks.find { it.id == id }

    fun getDay(trackId: String, day: Int): ChallengeDay? {
        val track = getTrack(trackId) ?: return null
        return track.days.getOrNull(day - 1)
    }

    fun getTotalTracks(): Int = tracks.size
}
