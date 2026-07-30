package islamic.duas

import kotlin.random.Random

enum class CompanionMood { SAD, LONELY, ANXIOUS, GRATEFUL, ANGRY, TIRED, GUILTY, HOPEFUL }

data class CompanionResponse(
    val mood: CompanionMood,
    val arabic: String,
    val meaning: String,
    val message: String,
    val source: String = "",
    val action: String = ""
)

class EmotionalCompanion {

    private val responses = mapOf(
        CompanionMood.SAD to listOf(
            CompanionResponse(CompanionMood.SAD, "إِنَّ مَعَ الْعُسْرِ يُسْرًا", "بے شک مشکل کے ساتھ آسانی ہے",
                "بیٹی، اللہ نے وعدہ کیا ہے کہ ہر مشکل کے بعد آسانی ہے۔ یہ غم بھی گزر جائے گا۔ صبر کرو، اللہ قریب ہے۔", "الشرح: 6", "صبر کا ورد کریں"),
            CompanionResponse(CompanionMood.SAD, "وَلَا تَهِنُوا وَلَا تَحْزَنُوا", "کمزور نہ ہو اور غمگین نہ ہو",
                "اللہ فرماتا ہے کہ غمگین نہ ہو۔ تم اکیلے نہیں ہو، اللہ تمہارے ساتھ ہے۔ آج ایک صدقہ دو اور دیکھو دل کیسے ہلکا ہوتا ہے۔", "آل عمران: 139", "صدقہ کریں"),
            CompanionResponse(CompanionMood.SAD, "فَاذْكُرُونِي أَذْكُرْكُمْ", "تم مجھے یاد کرو، میں تمہیں یاد کروں گا",
                "بیٹی، جب دل بہت اداس ہو تو اللہ کا ذکر کرو۔ ذکر سے دل کو سکون ملتا ہے۔ آج 100 بار 'یا لطیف' پڑھو۔", "البقرہ: 152", "ذکر کریں"),
            CompanionResponse(CompanionMood.SAD, "إِنِّي قَرِيبٌ", "بے شک میں قریب ہوں",
                "اللہ تم سے بہت قریب ہے۔ وہ تمہاری ہر آہ سنتا ہے۔ بس اس سے بات کرو، اسے اپنا غم بتاؤ۔", "البقرہ: 186", "دعا کریں")
        ),
        CompanionMood.LONELY to listOf(
            CompanionResponse(CompanionMood.LONELY, "اللَّهُ الصَّمَدُ", "اللہ بے نیاز ہے",
                "بیٹی، تنہائی محسوس ہو رہی ہے؟ اللہ ہمیشہ تمہارے ساتھ ہے۔ وہ تم سے محبت کرتا ہے۔ یاد رکھو، 'صمد' وہ ہے جس کی طرف سب رجوع کرتے ہیں۔", "الإخلاص: 2", "یا صمد پڑھیں"),
            CompanionResponse(CompanionMood.LONELY, "وَهُوَ مَعَكُمْ أَيْنَ مَا كُنْتُمْ", "اور وہ تمہارے ساتھ ہے جہاں بھی تم ہو",
                "تم جہاں بھی ہو، اللہ تمہارے ساتھ ہے۔ یہ تنہائی ایک امتحان ہے۔ صبر کرو اور اللہ کے ذکر میں مشغول رہو۔", "الحدید: 4", "ذکر میں مشغول ہوں"),
            CompanionResponse(CompanionMood.LONELY, "رَبِّ لَا تَذَرْنِي فَرْدًا", "اے میرے رب! مجھے اکیلا نہ چھوڑ",
                "نبی ﷺ کی یہ دعا پڑھو۔ اللہ تنہائی کو سمجھتا ہے اور وہی بہترین ساتھی ہے۔", "الأنبیاء: 89", "یہ دعا پڑھیں"),
            CompanionResponse(CompanionMood.LONELY, "وَاللَّهُ خَيْرُ الرَّازِقِينَ", "اور اللہ بہترین رزق دینے والا ہے",
                "اللہ تمہیں اچھے ساتھی دے گا جب وہ دیکھے گا کہ تم اس کی رضا کے لیے صبر کر رہی ہو۔", "الحج: 58", "اللہ سے ساتھی مانگیں")
        ),
        CompanionMood.ANXIOUS to listOf(
            CompanionResponse(CompanionMood.ANXIOUS, "أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ", "خبردار! اللہ کے ذکر سے ہی دلوں کو سکون ملتا ہے",
                "بیٹی، پریشانی ہو تو اللہ کا ذکر کرو۔ یہ تمام پریشانیوں کا علاج ہے۔ 33 بار 'یا سلام' پڑھو۔", "الرعد: 28", "یا سلام پڑھیں"),
            CompanionResponse(CompanionMood.ANXIOUS, "وَمَن يَتَوَكَّلْ عَلَى اللَّهِ فَهُوَ حَسْبُهُ", "اور جو اللہ پر توکل کرے اللہ اس کے لیے کافی ہے",
                "اللہ پر بھروسہ کرو۔ جو اس پر توکل کرتا ہے، اللہ اس کے لیے کافی ہے۔ تمہاری تمام پریشانیاں اللہ کے ہاتھ میں ہیں۔", "الطلاق: 3", "توکل کریں"),
            CompanionResponse(CompanionMood.ANXIOUS, "لَا تَخَفْ وَلَا تَحْزَنْ", "مت ڈرو اور غمگین نہ ہو",
                "اللہ نے موسیٰ سے کہا تھا، اور وہی تم سے بھی کہتا ہے۔ ڈرو نہیں، اللہ تمہارے ساتھ ہے۔", "القصص: 7", "یہ آیت پڑھیں"),
            CompanionResponse(CompanionMood.ANXIOUS, "حَسْبُنَا اللَّهُ وَنِعْمَ الْوَكِيلُ", "ہمارے لیے اللہ کافی ہے اور وہ بہترین کارساز ہے",
                "یہ دعا ابراہیم علیہ السلام نے کہی جب آگ میں ڈالے گئے۔ اللہ نے انہیں بچا لیا۔ اللہ تمہیں بھی بچائے گا۔", "آل عمران: 173", "یہ دعا پڑھیں")
        ),
        CompanionMood.GRATEFUL to listOf(
            CompanionResponse(CompanionMood.GRATEFUL, "لَئِن شَكَرْتُمْ لَأَزِيدَنَّكُمْ", "اگر تم شکر کرو گے تو میں تمہیں ضرور زیادہ دوں گا",
                "اللہ کا شکر ہے کہ تمہارا دل شکر سے بھرا ہے۔ اللہ تمہیں اور زیادہ دے گا۔ آج کوئی نیک کام کرو اور اس کا شکر ادا کرو۔", "ابراہیم: 7", "سجدہ شکر کریں"),
            CompanionResponse(CompanionMood.GRATEFUL, "وَأَمَّا بِنِعْمَةِ رَبِّكَ فَحَدِّثْ", "اور اپنے رب کی نعمت کا اعلان کرو",
                "اللہ کی نعمتوں کا ذکر کرو اور دوسروں کو بھی بتاؤ۔ شکر کرنے والوں سے اللہ محبت کرتا ہے۔", "الضحی: 11", "نعمتوں کا ذکر کریں"),
            CompanionResponse(CompanionMood.GRATEFUL, "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ", "تمام تعریف اللہ رب العالمین کے لیے ہے",
                "سب سے خوبصورت کلمہ۔ الحمدللہ کہو اور اللہ تم پر مزید برکتیں نازل کرے گا۔", "الفاتحہ: 2", "الحمدللہ پڑھیں"),
            CompanionResponse(CompanionMood.GRATEFUL, "وَاللَّهُ ذُو الْفَضْلِ الْعَظِيمِ", "اور اللہ بڑے فضل والا ہے",
                "اللہ کا فضل بہت بڑا ہے۔ اس کا شکر ادا کرو اور اس کی نعمتوں کو یاد کرو۔", "الجمعہ: 4", "شکر ادا کریں")
        ),
        CompanionMood.ANGRY to listOf(
            CompanionResponse(CompanionMood.ANGRY, "وَالْكَاظِمِينَ الْغَيْظَ", "اور غصہ پی جانے والے",
                "بیٹی، غصے میں اللہ کو یاد کرو۔ غصہ پی جانے والوں سے اللہ محبت کرتا ہے۔ وضو کرو اور ٹھنڈے پانی سے منہ دھو لو۔", "آل عمران: 134", "وضو کریں"),
            CompanionResponse(CompanionMood.ANGRY, "الْغَضَبُ مِنَ الشَّيْطَانِ", "غصہ شیطان کی طرف سے ہے",
                "نبی ﷺ نے فرمایا: غصہ شیطان کی طرف سے ہے۔ اگر غصہ آئے تو اپنی حالت بدلو — اگر کھڑے ہو تو بیٹھ جاؤ، اگر بیٹھے ہو تو لیٹ جاؤ۔", "", "ابو داؤد"),
            CompanionResponse(CompanionMood.ANGRY, "وَلَا تَسْتَوِي الْحَسَنَةُ وَلَا السَّيِّئَةُ", "نیکی اور برائی برابر نہیں ہو سکتی",
                "برائی کو نیکی سے دور کرو۔ معاف کر دینا سب سے بہتر ہے۔ اللہ معاف کرنے والوں سے محبت کرتا ہے۔", "حم السجدہ: 34", "معاف کریں")
        ),
        CompanionMood.TIRED to listOf(
            CompanionResponse(CompanionMood.TIRED, "وَجَعَلْنَا نَوْمَكُمْ سُبَاتًا", "اور ہم نے نیند کو تمہارے لیے آرام بنایا",
                "بیٹی، تھک گئی ہو؟ اللہ نے نیند کو آرام بنایا ہے۔ آج جلدی سو جاؤ اور سونے سے پہلے آیت الکرسی پڑھو۔", "النبأ: 9", "جلدی سوئیں"),
            CompanionResponse(CompanionMood.TIRED, "لَا تُكَلَّفُ نَفْسٌ إِلَّا وُسْعَهَا", "اللہ کسی کو اس کی وسعت سے زیادہ تکلیف نہیں دیتا",
                "اللہ تمہیں تمہاری طاقت سے زیادہ نہیں دے گا۔ آرام کرو اور اللہ پر بھروسہ رکھو۔", "البقرہ: 233", "آرام کریں"),
            CompanionResponse(CompanionMood.TIRED, "فَإِذَا فَرَغْتَ فَانصَبْ", "پس جب فارغ ہو تو عبادت میں مشغول ہو جاؤ",
                "تھک کر اللہ کی طرف رجوع کرو۔ نماز میں سکون ہے۔ ایک ہلکی سی نماز پڑھو اور دیکھو کیسے سکون ملتا ہے۔", "الشرح: 7", "نماز پڑھیں")
        ),
        CompanionMood.GUILTY to listOf(
            CompanionResponse(CompanionMood.GUILTY, "إِنَّ اللَّهَ يَغْفِرُ الذُّنُوبَ جَمِيعًا", "بے شک اللہ تمام گناہ معاف کرتا ہے",
                "بیٹی، اللہ کی رحمت سے مایوس نہ ہو۔ وہ تمام گناہ معاف کرتا ہے۔ سچی توبہ کرو اور آگے بہتر بننے کی کوشش کرو۔", "الزمر: 53", "توبہ کریں"),
            CompanionResponse(CompanionMood.GUILTY, "وَتُوبُوا إِلَى اللَّهِ جَمِيعًا", "اور تم سب اللہ کی طرف توبہ کرو",
                "توبہ کا دروازہ ہمیشہ کھلا ہے۔ اللہ توبہ کرنے والوں سے محبت کرتا ہے۔ آج 100 بار استغفار پڑھو۔", "النور: 31", "استغفار کریں"),
            CompanionResponse(CompanionMood.GUILTY, "إِنَّ الْحَسَنَاتِ يُذْهِبْنَ السَّيِّئَاتِ", "بے شک نیکیاں برائیوں کو مٹا دیتی ہیں",
                "نیکیاں کرو، وہ برائیوں کو مٹا دیں گی۔ ایک نیکی کرو اور اللہ سے معافی مانگو۔", "ہود: 114", "نیکی کریں")
        ),
        CompanionMood.HOPEFUL to listOf(
            CompanionResponse(CompanionMood.HOPEFUL, "وَاللَّهُ غَالِبٌ عَلَى أَمْرِهِ", "اور اللہ اپنے کام پر غالب ہے",
                "اللہ کا ہر کام بہترین ہے۔ جو ہو رہا ہے، اچھا ہو رہا ہے۔ اللہ پر بھروسہ رکھو۔", "یوسف: 21", "اللہ پر بھروسہ کریں"),
            CompanionResponse(CompanionMood.HOPEFUL, "إِنَّ اللَّهَ لَا يُخْلِفُ الْمِيعَادَ", "بے شک اللہ اپنا وعدہ نہیں توڑتا",
                "اللہ کا وعدہ سچا ہے۔ وہ تمہیں کبھی نہیں چھوڑے گا۔ امید رکھو، اللہ کے ساتھ۔", "آل عمران: 194", "امید رکھیں"),
            CompanionResponse(CompanionMood.HOPEFUL, "وَعَسَىٰ أَن تَكْرَهُوا شَيْئًا وَهُوَ خَيْرٌ لَّكُمْ", "ہو سکتا ہے تم کسی چیز کو ناپسند کرو اور وہ تمہارے لیے بہتر ہو",
                "جو ہو رہا ہے، اللہ کی مرضی سے ہو رہا ہے۔ اور اللہ جو چاہتا ہے، وہ تمہارے لیے بہترین ہے۔", "البقرہ: 216", "رضا باللہ کریں")
        )
    )

    fun getAllMoods(): List<CompanionMood> = responses.keys.toList()

    fun getResponse(mood: CompanionMood): CompanionResponse {
        return responses[mood]?.let { it[Random.nextInt(it.size)] }
            ?: CompanionResponse(CompanionMood.HOPEFUL, "", "", "اللہ آپ کے ساتھ ہے")
    }

    fun getResponsesForMood(mood: CompanionMood): List<CompanionResponse> =
        responses[mood] ?: emptyList()

    fun getContextualResponse(mood: CompanionMood, keyword: String = ""): CompanionResponse {
        val moodResponses = responses[mood] ?: return getResponse(CompanionMood.HOPEFUL)
        if (keyword.isNotBlank()) {
            val matched = moodResponses.filter {
                it.message.contains(keyword, ignoreCase = true) ||
                it.arabic.contains(keyword, ignoreCase = true)
            }
            if (matched.isNotEmpty()) return matched[Random.nextInt(matched.size)]
        }
        return moodResponses[Random.nextInt(moodResponses.size)]
    }

    fun getSuggestedAction(mood: CompanionMood): String {
        return getResponse(mood).action
    }
}
