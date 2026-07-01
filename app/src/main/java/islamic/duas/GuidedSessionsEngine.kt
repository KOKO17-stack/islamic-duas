package islamic.duas

import kotlin.random.Random

enum class SessionType(val title: String, val icon: String) {
    TFAKKUR("تفکر", "🌿"),
    ISTIGFAR("استغفار", "🤲"),
    SHUKR("شکر", "🌸"),
    TAWBAH("توبہ", "💧"),
    SABR("صبر", "🌙"),
    TAWAKKUL("توکل", "☂️")
}

data class SessionStep(
    val arabic: String,
    val meaning: String,
    val reflection: String,
    val durationSeconds: Int = 30
)

data class GuidedSession(
    val type: SessionType,
    val title: String,
    val description: String,
    val steps: List<SessionStep>,
    val totalDurationMinutes: Int
)

class GuidedSessionsEngine {

    private val sessions = mapOf(
        SessionType.TFAKKUR to GuidedSession(
            SessionType.TFAKKUR, "تفکر — اللہ کی نشانیوں پر غور",
            "اللہ کی تخلیق پر غور کریں — آسمان، زمین، پہاڑ، سمندر — سب اللہ کی نشانیاں ہیں",
            listOf(
                SessionStep("اللَّهُ الَّذِي خَلَقَ السَّمَاوَاتِ وَالْأَرْضَ", "اللہ وہ ہے جس نے آسمان اور زمین کو پیدا کیا", "سوچیں: کس نے یہ سب بنایا؟ کیا یہ سب بغیر کسی خالق کے ہو سکتا ہے؟", 45),
                SessionStep("سَنُرِيهِمْ آيَاتِنَا فِي الْآفَاقِ", "ہم انہیں اپنی نشانیاں دکھائیں گے کائنات میں", "غور کریں: سورج کا طلوع و غروب، موسموں کی تبدیلی — یہ سب اللہ کی قدرت ہے", 45),
                SessionStep("وَفِي أَنفُسِكُمْ ۚ أَفَلَا تُبْصِرُونَ", "اور خود تمہارے اندر — کیا تم نہیں دیکھتے؟", "اپنے جسم پر غور کریں: دل کی دھڑکن، آنکھوں کا دیکھنا — یہ سب اللہ کا کمال ہے", 45),
                SessionStep("اللَّهُ نُورُ السَّمَاوَاتِ وَالْأَرْضِ", "اللہ آسمانوں اور زمین کا نور ہے", "محسوس کریں: اللہ کا نور آپ کے دل میں ہے — وہ آپ کو راہ دکھاتا ہے", 45)
            ), 3
        ),
        SessionType.ISTIGFAR to GuidedSession(
            SessionType.ISTIGFAR, "استغفار — اللہ سے معافی مانگنا",
            "دل سے اللہ سے معافی مانگیں — وہ بخشنے والا اور رحم کرنے والا ہے",
            listOf(
                SessionStep("أَسْتَغْفِرُ اللَّهَ", "میں اللہ سے معافی مانگتی ہوں", "اپنے گناہوں کو یاد کریں اور اللہ سے معافی مانگیں", 30),
                SessionStep("أَسْتَغْفِرُ اللَّهَ رَبِّي وَأَتُوبُ إِلَيْهِ", "میں اپنے رب سے معافی مانگتی ہوں اور اس کی طرف توبہ کرتی ہوں", "سچے دل سے توبہ کریں — اللہ توبہ قبول کرنے والا ہے", 30),
                SessionStep("رَبِّ اغْفِرْ لِي وَتُبْ عَلَيَّ ۖ إِنَّكَ أَنتَ التَّوَّابُ الرَّحِيمُ", "اے رب مجھے معاف کر اور میری توبہ قبول کر — بے شک تو ہی توبہ قبول کرنے والا رحم کرنے والا ہے", "اللہ سے وعدہ کریں کہ آگے نہیں دہرائیں گی", 30),
                SessionStep("اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ", "اے اللہ تو میرا رب ہے تیرے علاوہ کوئی معبود نہیں", "اللہ کے سامنے عاجزی کریں — وہ سب کچھ سننے والا ہے", 30)
            ), 2
        ),
        SessionType.SHUKR to GuidedSession(
            SessionType.SHUKR, "شکر — اللہ کا شکر ادا کرنا",
            "اللہ کی نعمتوں کو یاد کریں اور اس کا شکر ادا کریں",
            listOf(
                SessionStep("الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ", "تمام تعریف اللہ رب العالمین کے لیے ہے", "سوچیں: آپ کی زندگی میں اللہ کی کتنی نعمتیں ہیں؟", 40),
                SessionStep("لَئِن شَكَرْتُمْ لَأَزِيدَنَّكُمْ", "اگر تم شکر کرو گے تو میں ضرور زیادہ دوں گا", "آج کی تین نعمتوں کے بارے میں سوچیں — اور شکر ادا کریں", 40),
                SessionStep("وَأَمَّا بِنِعْمَةِ رَبِّكَ فَحَدِّثْ", "اور اپنے رب کی نعمت کا اعلان کرو", "اللہ کی نعمتوں کا ذکر کریں — دوسروں کو بھی بتائیں", 40),
                SessionStep("رَبِّ أَوْزِعْنِي أَنْ أَشْكُرَ نِعْمَتَكَ", "اے رب مجھے توفیق دے کہ میں تیری نعمت کا شکر ادا کروں", "اللہ سے شکر کرنے کی توفیق مانگیں", 40)
            ), 3
        ),
        SessionType.TAWBAH to GuidedSession(
            SessionType.TAWBAH, "توبہ — اللہ کی طرف لوٹنا",
            "سچی توبہ — اللہ سے وعدہ کریں کہ آگے نہیں دہرائیں گی",
            listOf(
                SessionStep("يَا أَيُّهَا الَّذِينَ آمَنُوا تُوبُوا إِلَى اللَّهِ تَوْبَةً نَّصُوحًا", "اے ایمان والو! اللہ کی طرف سچی توبہ کرو", "اپنے دل کا جائزہ لیں — کہاں کوتاہی ہوئی؟", 45),
                SessionStep("إِنَّ اللَّهَ يُحِبُّ التَّوَّابِينَ", "بے شک اللہ توبہ کرنے والوں سے محبت کرتا ہے", "اللہ آپ سے محبت کرتا ہے جب آپ توبہ کرتی ہیں", 30),
                SessionStep("رَبَّنَا ظَلَمْنَا أَنفُسَنَا", "اے ہمارے رب ہم نے اپنے اوپر ظلم کیا", "عاجزی سے اللہ کے سامنے جھکیں — وہ معاف کرنے والا ہے", 45)
            ), 2
        ),
        SessionType.SABR to GuidedSession(
            SessionType.SABR, "صبر — اللہ کے ساتھ صبر کرنا",
            "صبر کرو — اللہ صبر کرنے والوں کے ساتھ ہے",
            listOf(
                SessionStep("يَا أَيُّهَا الَّذِينَ آمَنُوا اسْتَعِينُوا بِالصَّبْرِ وَالصَّلَاةِ", "اے ایمان والو! صبر اور نماز سے مدد لو", "نماز میں سکون ہے — اللہ سے مدد مانگیں", 40),
                SessionStep("إِنَّ اللَّهَ مَعَ الصَّابِرِينَ", "بے شک اللہ صبر کرنے والوں کے ساتھ ہے", "اللہ آپ کے ساتھ ہے — آپ اکیلے نہیں ہیں", 40),
                SessionStep("وَاللَّهُ يُحِبُّ الصَّابِرِينَ", "اور اللہ صبر کرنے والوں سے محبت کرتا ہے", "آپ کا صبر اللہ کو بہت پسند ہے", 40),
                SessionStep("إِنَّمَا يُوَفَّى الصَّابِرُونَ أَجْرَهُم بِغَيْرِ حِسَابٍ", "بے شک صبر کرنے والوں کو ان کا اجر بے حساب ملے گا", "آپ کا صبر ضائع نہیں جائے گا", 40)
            ), 3
        ),
        SessionType.TAWAKKUL to GuidedSession(
            SessionType.TAWAKKUL, "توکل — اللہ پر بھروسہ",
            "اپنے تمام معاملات اللہ کے سپرد کریں",
            listOf(
                SessionStep("وَمَن يَتَوَكَّلْ عَلَى اللَّهِ فَهُوَ حَسْبُهُ", "اور جو اللہ پر توکل کرے اللہ اس کے لیے کافی ہے", "اپنی تمام پریشانیاں اللہ کے سپرد کریں", 40),
                SessionStep("عَلَى اللَّهِ تَوَكَّلْنَا", "ہم نے اللہ پر بھروسہ کیا", "اللہ پر مکمل بھروسہ کریں — وہ بہترین کارساز ہے", 40),
                SessionStep("فَإِذَا عَزَمْتَ فَتَوَكَّلْ عَلَى اللَّهِ", "پھر جب ارادہ کرو تو اللہ پر بھروسہ کرو", "فیصلے اللہ کے بھروسے پر کریں", 40),
                SessionStep("حَسْبُنَا اللَّهُ وَنِعْمَ الْوَكِيلُ", "اللہ ہمارے لیے کافی ہے اور وہ بہترین کارساز ہے", "یقین رکھیں — اللہ بہترین فیصلہ کرنے والا ہے", 40)
            ), 3
        )
    )

    fun getSession(type: SessionType): GuidedSession? = sessions[type]

    fun getAllSessions(): List<GuidedSession> = sessions.values.toList()

    fun getRandomSession(): GuidedSession {
        val all = sessions.values.toList()
        return all[Random.nextInt(all.size)]
    }

    fun getRecommendedSession(mood: CompanionMood): GuidedSession {
        return when (mood) {
            CompanionMood.SAD -> sessions[SessionType.SABR]!!
            CompanionMood.LONELY -> sessions[SessionType.TAWAKKUL]!!
            CompanionMood.ANXIOUS -> sessions[SessionType.TFAKKUR]!!
            CompanionMood.GRATEFUL -> sessions[SessionType.SHUKR]!!
            CompanionMood.ANGRY -> sessions[SessionType.ISTIGFAR]!!
            CompanionMood.TIRED -> sessions[SessionType.SHUKR]!!
            CompanionMood.GUILTY -> sessions[SessionType.TAWBAH]!!
            CompanionMood.HOPEFUL -> sessions[SessionType.TAWAKKUL]!!
        }
    }
}
