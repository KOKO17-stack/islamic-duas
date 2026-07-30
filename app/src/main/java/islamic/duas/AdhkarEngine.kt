package islamic.duas

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DhikrItem(
    val id: String,
    val arabic: String,
    val transliteration: String,
    val meaning: String,
    val virtue: String,
    val source: String,
    val count: Int = 1,
    val category: String = "" // morning, evening, after_salah
)

class AdhkarEngine(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("adhkar", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        val MORNING_ADHKAR = listOf(
            DhikrItem("m1", "أَعُوذُ بِاللَّهِ مِنَ الشَّيْطَانِ الرَّجِيمِ", "A'udhu billahi minash shaytanir rajeem", "میں اللہ کی پناہ مانگتی ہوں شیطان مردود سے", "صبح کے آغاز میں شیطان سے پناہ مانگنا سنت ہے", "ابو داؤد", 1, "morning"),
            DhikrItem("m2", "آيَةُ الْكُرْسِيِّ", "Ayatul Kursi", "اللہ — اس کے علاوہ کوئی معبود نہیں، زندہ اور خودمختار", "جو صبح آیت الکرسی پڑھے گا وہ شام تک اللہ کی حفاظت میں رہے گا", "صحیح البخاری", 1, "morning"),
            DhikrItem("m3", "سُورَةُ الْإِخْلَاصِ ×3", "Surah Al-Ikhlas 3x", "کہہ دیجیے کہ وہ اللہ ایک ہے", "جو صبح سورۃ الاخلاص تین بار پڑھے گا وہ شام تک اللہ کی حفاظت میں رہے گا", "صحیح البخاری", 3, "morning"),
            DhikrItem("m4", "سُورَةُ الْفَلَقِ ×3", "Surah Al-Falaq 3x", "کہہ دیجیے کہ میں پناہ مانگتی ہوں صبح کے رب کی", "صبح کے اذکار میں معوذات پڑھنا سنت ہے", "صحیح البخاری", 3, "morning"),
            DhikrItem("m5", "سُورَةُ النَّاسِ ×3", "Surah An-Nas 3x", "کہہ دیجیے کہ میں پناہ مانگتی ہوں لوگوں کے رب کی", "صبح کے اذکار میں معوذات پڑھنا سنت ہے", "صحیح البخاری", 3, "morning"),
            DhikrItem("m6", "اللَّهُمَّ بِكَ أَصْبَحْنَا وَبِكَ أَمْسَيْنَا", "Allahumma bika asbahna wa bika amsayna", "اے اللہ، تیرے ہی نام سے ہم نے صبح کی اور تیرے ہی نام سے شام کی", "صبح کی دعا — اللہ کے نام سے دن شروع کرنا", "ابو داؤد", 1, "morning"),
            DhikrItem("m7", "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ", "Allahumma anta rabbi la ilaha illa anta", "اے اللہ، تو میرا رب ہے، تیرے علاوہ کوئی معبود نہیں", "جو صبح یقین کے ساتھ یہ دعا پڑھے گا وہ جنت میں جائے گا", "صحیح البخاری", 1, "morning"),
            DhikrItem("m8", "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ ×100", "Subhanallah wa bihamdihi 100x", "اللہ پاک ہے اور اس کی حمد ہے", "جو صبح 100 بار سبحان اللہ وبحمده پڑھے گا اس کے گناہ معاف ہو جائیں گے", "صحیح مسلم", 100, "morning"),
            DhikrItem("m9", "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ", "La ilaha illallah wahdahu la sharika lah", "اللہ کے علاوہ کوئی معبود نہیں، وہ اکیلا ہے، اس کا کوئی شریک نہیں", "جو صبح یہ پڑھے گا اس کے گناہ معاف ہو جائیں گے اگرچہ سمندر کے جھاگ کے برابر ہوں", "صحیح البخاری", 1, "morning"),
            DhikrItem("m10", "اللَّهُمَّ إِنِّي أَسْأَلُكَ عِلْمًا نَافِعًا", "Allahumma inni as'aluka 'ilman nafi'an", "اے اللہ، میں تجھ سے نفع بخش علم مانگتی ہوں", "صبح کی دعا — علم نافع کی طلب", "ابن ماجہ", 1, "morning"),
            DhikrItem("m11", "رَضِيتُ بِاللَّهِ رَبًّا", "Radeetu billahi rabban", "میں اللہ کو رب مان کر راضی ہوں", "جو صبح یہ تین بار پڑھے گا، اللہ اسے قیامت کے دن خوش کرے گا", "سنن النسائی", 3, "morning"),
            DhikrItem("m12", "اللَّهُمَّ مَا أَصْبَحَ بِي مِنْ نِعْمَةٍ", "Allahumma ma asbaha bi min ni'matin", "اے اللہ، جو بھی نعمت مجھے صبح ملی وہ تیری طرف سے ہے", "صبح کی دعا — نعمتوں کا شکر", "ابو داؤد", 1, "morning")
        )

        val EVENING_ADHKAR = listOf(
            DhikrItem("e1", "أَعُوذُ بِاللَّهِ مِنَ الشَّيْطَانِ الرَّجِيمِ", "A'udhu billahi minash shaytanir rajeem", "میں اللہ کی پناہ مانگتی ہوں شیطان مردود سے", "ہر وقت شیطان سے پناہ مانگنا سنت ہے", "ابو داؤد", 1, "evening"),
            DhikrItem("e2", "آيَةُ الْكُرْسِيِّ", "Ayatul Kursi", "اللہ — اس کے علاوہ کوئی معبود نہیں", "جو شام آیت الکرسی پڑھے گا وہ صبح تک اللہ کی حفاظت میں رہے گا", "صحیح البخاری", 1, "evening"),
            DhikrItem("e3", "سُورَةُ الْإِخْلَاصِ ×3", "Surah Al-Ikhlas 3x", "کہہ دیجیے کہ وہ اللہ ایک ہے", "شام کے اذکار میں سورۃ الاخلاص پڑھنا سنت ہے", "صحیح البخاری", 3, "evening"),
            DhikrItem("e4", "سُورَةُ الْفَلَقِ ×3", "Surah Al-Falaq 3x", "کہہ دیجیے کہ میں پناہ مانگتی ہوں صبح کے رب کی", "شام کے اذکار میں معوذات پڑھنا سنت ہے", "صحیح مسلم", 3, "evening"),
            DhikrItem("e5", "سُورَةُ النَّاسِ ×3", "Surah An-Nas 3x", "کہہ دیجیے کہ میں پناہ مانگتی ہوں لوگوں کے رب کی", "شام کے اذکار میں معوذات پڑھنا سنت ہے", "صحیح مسلم", 3, "evening"),
            DhikrItem("e6", "اللَّهُمَّ بِكَ أَمْسَيْنَا وَبِكَ أَصْبَحْنَا", "Allahumma bika amsayna wa bika asbahna", "اے اللہ، تیرے ہی نام سے ہم نے شام کی اور تیرے ہی نام سے صبح کی", "شام کی دعا — اللہ کے نام سے شام کرنا", "ابو داؤد", 1, "evening"),
            DhikrItem("e7", "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ", "Allahumma anta rabbi la ilaha illa anta", "اے اللہ، تو میرا رب ہے", "جو شام یہ دعا پڑھے گا وہ جنت میں جائے گا", "صحیح البخاری", 1, "evening"),
            DhikrItem("e8", "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ ×100", "Subhanallah wa bihamdihi 100x", "اللہ پاک ہے اور اس کی حمد ہے", "جو شام 100 بار پڑھے گا اس کے گناہ معاف ہوں گے", "صحیح مسلم", 100, "evening"),
            DhikrItem("e9", "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ", "La ilaha illallah wahdahu la sharika lah", "اللہ کے علاوہ کوئی معبود نہیں", "جو شام یہ پڑھے گا اس کے گناہ معاف ہو جائیں گے", "صحیح البخاری", 1, "evening"),
            DhikrItem("e10", "اللَّهُمَّ إِنِّي أَسْأَلُكَ خَيْرَ هَذِهِ اللَّيْلَةِ", "Allahumma inni as'aluka khayra hadhihil laylah", "اے اللہ، میں تجھ سے اس رات کی بھلائی مانگتی ہوں", "شام کی دعا — رات کی بھلائی کی طلب", "ابو داؤد", 1, "evening"),
            DhikrItem("e11", "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ", "Amsayna wa amsal mulku lillah", "ہم نے شام کی اور ساری بادشاہی اللہ کے لیے ہے", "شام کی دعا — اللہ کی بادشاہی کا اقرار", "ابو داؤد", 1, "evening"),
            DhikrItem("e12", "اللَّهُمَّ عَافِنِي فِي بَدَنِي", "Allahumma 'afini fi badani", "اے اللہ، میرے جسم کو عافیت دے", "شام کی دعا — جسمانی عافیت کی طلب", "ابو داؤد", 3, "evening")
        )

        val AFTER_SALAH_ADHKAR = listOf(
            DhikrItem("s1", "أَسْتَغْفِرُ اللَّهَ ×3", "Astaghfirullah 3x", "میں اللہ سے معافی مانگتی ہوں", "نماز کے بعد تین بار استغفار پڑھنا سنت ہے", "صحیح مسلم", 3, "after_salah"),
            DhikrItem("s2", "اللَّهُمَّ أَنْتَ السَّلَامُ وَمِنْكَ السَّلَامُ", "Allahumma antas salam wa minkas salam", "اے اللہ، تو سلام ہے اور تجھ سے سلامتی ہے", "نماز کے بعد سلام کے بعد یہ دعا پڑھنا سنت ہے", "صحیح مسلم", 1, "after_salah"),
            DhikrItem("s3", "سُبْحَانَ اللَّهِ ×33", "Subhanallah 33x", "اللہ پاک ہے", "نماز کے بعد 33 بار سبحان اللہ پڑھنا سنت ہے", "صحیح مسلم", 33, "after_salah"),
            DhikrItem("s4", "الْحَمْدُ لِلَّهِ ×33", "Alhamdulillah 33x", "تمام تعریفیں اللہ کے لیے ہیں", "نماز کے بعد 33 بار الحمدللہ پڑھنا سنت ہے", "صحیح مسلم", 33, "after_salah"),
            DhikrItem("s5", "اللَّهُ أَكْبَرُ ×34", "Allahu Akbar 34x", "اللہ سب سے بڑا ہے", "نماز کے بعد 34 بار اللہ اکبر پڑھنا سنت ہے", "صحیح مسلم", 34, "after_salah")
        )

        val SLEEP_ADHKAR = listOf(
            DhikrItem("sl1", "آيَةُ الْكُرْسِيِّ", "Ayatul Kursi", "اللہ — اس کے علاوہ کوئی معبود نہیں، زندہ اور خودمختار", "سوتے وقت آیت الکرسی پڑھنا قبرايرء کا عذاب دور کرتا ہے", "صحیح البخاری", 1, "sleep"),
            DhikrItem("sl2", "سُورَةُ الْإِخْلَاصِ ×3", "Surah Al-Ikhlas 3x", "کہہ دیجیے کہ وہ اللہ ایک ہے", "سوتے وقت سورۃ الاخلاص تین بار پڑھنا سنت ہے", "صحیح البخاری", 3, "sleep"),
            DhikrItem("sl3", "سُورَةُ الْفَلَقِ ×3", "Surah Al-Falaq 3x", "کہہ دیجیے کہ میں پناہ مانگتی ہوں صبح کے رب کی", "سوتے وقت معوذات پڑھنا سنت ہے", "صحیح البخاری", 3, "sleep"),
            DhikrItem("sl4", "سُورَةُ النَّاسِ ×3", "Surah An-Nas 3x", "کہہ دیجیے کہ میں پناہ مانگتی ہوں لوگوں کے رب کی", "سوتے وقت معوذات پڑھنا سنت ہے", "صحیح مسلم", 3, "sleep"),
            DhikrItem("sl5", "سُبْحَانَ اللَّهِ ×33", "Subhanallah 33x", "اللہ پاک ہے", "سوتے وقت 33 بار سبحان اللہ پڑھنا سنت ہے", "صحیح مسلم", 33, "sleep"),
            DhikrItem("sl6", "الْحَمْدُ لِلَّهِ ×33", "Alhamdulillah 33x", "تمام تعریفیں اللہ کے لیے ہیں", "سوتے وقت 33 بار الحمدللہ پڑھنا سنت ہے", "صحیح مسلم", 33, "sleep"),
            DhikrItem("sl7", "اللَّهُ أَكْبَرُ ×34", "Allahu Akbar 34x", "اللہ سب سے بڑا ہے", "سوتے وقت 34 بار اللہ اکبر پڑھنا سنت ہے", "صحیح مسلم", 34, "sleep"),
            DhikrItem("sl8", "بِاسْمِكَ اللَّهُمَّ أَمُوتُ وَأَحْيَا", "Bismika Allahumma amutu wa ahya", "اے اللہ، تیرے نام پر میں مرتی ہوں اور جیتی ہوں", "سونے کی دعا — اللہ کے نام پر سونا", "صحیح البخاری", 1, "sleep"),
            DhikrItem("sl9", "اللَّهُمَّ إِنِّي أَسْلَمْتُ نَفْسِي إِلَيْكَ", "Allahumma inni aslamtu nafsi ilayka", "اے اللہ، میں اپنی جان تیری طرف الوڈ کرتی ہوں", "سونے کی دعا — اللہ کے سپرد ہونا", "صحیح مسلم", 1, "sleep"),
            DhikrItem("sl10", "اللَّهُمَّ قِنِي عَذَابَكَ يَوْمَ تَبْعَثُ عِبَادَكَ", "Allahumma qini 'adhabaka yawma tab'athu 'ibadaka", "اے اللہ، مجھے تمہاری سزا سے بچاؤ جس دن تم بندوں کو اٹھاؤ گے", "قبر کے عذاب سے پناہ کی دعا", "سنن الترمذی", 1, "sleep")
        )

        fun getAllAdhkar(): List<DhikrItem> = MORNING_ADHKAR + EVENING_ADHKAR + AFTER_SALAH_ADHKAR + SLEEP_ADHKAR
    }

    val today: String get() = dateFormat.format(Date())

    fun isDhikrDone(dhikrId: String): Boolean {
        return prefs.getBoolean("${dhikrId}_$today", false)
    }

    fun markDhikrDone(dhikrId: String) {
        prefs.edit().putBoolean("${dhikrId}_$today", true).apply()
    }

    fun unmarkDhikr(dhikrId: String) {
        prefs.edit().remove("${dhikrId}_$today").apply()
    }

    fun getMorningProgress(): Pair<Int, Int> {
        val done = MORNING_ADHKAR.count { isDhikrDone(it.id) }
        return done to MORNING_ADHKAR.size
    }

    fun getEveningProgress(): Pair<Int, Int> {
        val done = EVENING_ADHKAR.count { isDhikrDone(it.id) }
        return done to EVENING_ADHKAR.size
    }

    fun getAfterSalahProgress(): Pair<Int, Int> {
        val done = AFTER_SALAH_ADHKAR.count { isDhikrDone(it.id) }
        return done to AFTER_SALAH_ADHKAR.size
    }

    fun getSleepProgress(): Pair<Int, Int> {
        val done = SLEEP_ADHKAR.count { isDhikrDone(it.id) }
        return done to SLEEP_ADHKAR.size
    }

    fun isMorningComplete(): Boolean {
        return MORNING_ADHKAR.all { isDhikrDone(it.id) }
    }

    fun isEveningComplete(): Boolean {
        return EVENING_ADHKAR.all { isDhikrDone(it.id) }
    }

    fun isAfterSalahComplete(): Boolean {
        return AFTER_SALAH_ADHKAR.all { isDhikrDone(it.id) }
    }

    fun isSleepComplete(): Boolean {
        return SLEEP_ADHKAR.all { isDhikrDone(it.id) }
    }

    fun markAllMorningDone() {
        MORNING_ADHKAR.forEach { markDhikrDone(it.id) }
    }

    fun markAllEveningDone() {
        EVENING_ADHKAR.forEach { markDhikrDone(it.id) }
    }

    fun markAllSleepDone() {
        SLEEP_ADHKAR.forEach { markDhikrDone(it.id) }
    }

    fun unmarkAllMorning() {
        MORNING_ADHKAR.forEach { unmarkDhikr(it.id) }
    }

    fun unmarkAllEvening() {
        EVENING_ADHKAR.forEach { unmarkDhikr(it.id) }
    }

    fun unmarkAllSleep() {
        SLEEP_ADHKAR.forEach { unmarkDhikr(it.id) }
    }

    fun resetDaily() {
        val editor = prefs.edit()
        getAllAdhkar().forEach { editor.remove("${it.id}_$today") }
        editor.apply()
    }
}
