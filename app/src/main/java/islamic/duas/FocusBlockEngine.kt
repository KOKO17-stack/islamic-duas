package islamic.duas

import android.os.CountDownTimer
import java.util.Calendar

data class FocusItem(
    val text: String,
    val transliteration: String = "",
    val meaning: String = ""
)

class FocusBlockEngine {

    private var currentIndex = 0
    private var timer: CountDownTimer? = null
    private var isRunning = false
    private var elapsedSeconds = 0
    private var totalSeconds = 0
    private var onTick: ((Int, String) -> Unit)? = null
    private var onFinish: (() -> Unit)? = null

    val names = listOf(
        FocusItem("اللہ", "Allah", "ذات باری تعالیٰ"),
        FocusItem("الرَّحْمَٰن", "Ar-Rahman", "بے حد رحم کرنے والا"),
        FocusItem("الرَّحِيم", "Ar-Raheem", "بے انتہا رحم کرنے والا"),
        FocusItem("الْمَلِك", "Al-Malik", "بادشاہ"),
        FocusItem("الْقُدُّوس", "Al-Quddus", "نہایت پاک"),
        FocusItem("السَّلَام", "As-Salam", "سلامتی"),
        FocusItem("الْمُؤْمِن", "Al-Mu'min", "امن دینے والا"),
        FocusItem("الْمُهَيْمِن", "Al-Muhaymin", "نگہبان"),
        FocusItem("الْعَزِيز", "Al-Aziz", "غالب"),
        FocusItem("الْجَبَّار", "Al-Jabbar", "زبردست"),
        FocusItem("الْمُتَكَبِّر", "Al-Mutakabbir", "بڑائی والا"),
        FocusItem("الْخَالِق", "Al-Khaliq", "پیدا کرنے والا"),
        FocusItem("الْبَارِئ", "Al-Bari'", "ٹھیک بنانے والا"),
        FocusItem("الْمُصَوِّر", "Al-Musawwir", "صورت بنانے والا"),
        FocusItem("الْغَفَّار", "Al-Ghaffar", "بہت بخشنے والا"),
        FocusItem("الْقَهَّار", "Al-Qahhar", "دبانے والا"),
        FocusItem("الْوَهَّاب", "Al-Wahhab", "بہت دینے والا"),
        FocusItem("الرَّزَّاق", "Ar-Razzaq", "روزی دینے والا"),
        FocusItem("الْفَتَّاح", "Al-Fattah", "کھولنے والا"),
        FocusItem("الْعَلِيم", "Al-Aleem", "سب کچھ جاننے والا"),
        FocusItem("الْقَابِض", "Al-Qabid", "قبضہ کرنے والا"),
        FocusItem("الْبَاسِط", "Al-Basit", "کشادہ کرنے والا"),
        FocusItem("الرَّافِع", "Ar-Rafi'", "بلند کرنے والا"),
        FocusItem("الْمُعِزّ", "Al-Mu'izz", "عزت دینے والا"),
        FocusItem("الْمُذِلّ", "Al-Mudhill", "ذلیل کرنے والا"),
        FocusItem("السَّمِيع", "As-Sami'", "سننے والا"),
        FocusItem("الْبَصِير", "Al-Baseer", "دیکھنے والا"),
        FocusItem("الْحَكَم", "Al-Hakam", "فیصلہ کرنے والا"),
        FocusItem("الْعَدْل", "Al-'Adl", "انصاف کرنے والا"),
        FocusItem("اللَّطِيف", "Al-Lateef", "باریک بین"),
        FocusItem("الْخَبِير", "Al-Khabeer", "خبر رکھنے والا"),
        FocusItem("الْحَلِيم", "Al-Haleem", "بردبار"),
        FocusItem("الْعَظِيم", "Al-'Azeem", "عظیم"),
        FocusItem("الْغَفُور", "Al-Ghafoor", "بخشنے والا"),
        FocusItem("الشَّكُور", "Ash-Shakoor", "قدر کرنے والا"),
        FocusItem("الْعَلِيُّ", "Al-'Aliyy", "بلند"),
        FocusItem("الْكَبِير", "Al-Kabeer", "بڑا"),
        FocusItem("الْحَفِيظ", "Al-Hafeez", "محفوظ رکھنے والا"),
        FocusItem("الْمُقِيت", "Al-Muqeet", "پرواہ کرنے والا"),
        FocusItem("الْحَسِيب", "Al-Haseeb", "حساب لینے والا"),
        FocusItem("الْجَلِيل", "Al-Jaleel", "جلال والا"),
        FocusItem("الْكَرِيم", "Al-Kareem", "سخی"),
        FocusItem("الرَّقِيب", "Ar-Raqeeb", "نگران"),
        FocusItem("الْمُجِيب", "Al-Mujeeb", "قبول فرمانے والا"),
        FocusItem("الْوَاسِع", "Al-Wasi'", "وسیع"),
        FocusItem("الْحَكِيم", "Al-Hakeem", "حکمت والا"),
        FocusItem("الْوَدُود", "Al-Wadud", "محبت کرنے والا"),
        FocusItem("الْمَجِيد", "Al-Majeed", "عزت والا"),
        FocusItem("الْبَاعِث", "Al-Ba'ith", "اٹھانے والا"),
        FocusItem("الشَّهِيد", "Ash-Shaheed", "گواہ"),
        FocusItem("الْحَقّ", "Al-Haqq", "حق"),
        FocusItem("الْوَكِيل", "Al-Wakeel", "کارساز"),
        FocusItem("الْقَوِيُّ", "Al-Qawiyy", "طاقتور"),
        FocusItem("الْمَتِين", "Al-Mateen", "مضبوط"),
        FocusItem("الْوَلِيُّ", "Al-Waliyy", "دوست"),
        FocusItem("الْحَمِيد", "Al-Hameed", "تعریف کے لائق"),
        FocusItem("الْمُحْصِي", "Al-Muhsi", "گنتی رکھنے والا"),
        FocusItem("الْمُبْدِئ", "Al-Mubdi'", "شروع کرنے والا"),
        FocusItem("الْمُعِيد", "Al-Mu'id", "دوبارہ پیدا کرنے والا"),
        FocusItem("الْمُحْيِي", "Al-Muhyi", "زندگی دینے والا"),
        FocusItem("الْمُمِيت", "Al-Mumeet", "موت دینے والا"),
        FocusItem("الْحَيُّ", "Al-Hayy", "زندہ"),
        FocusItem("الْقَيُّوم", "Al-Qayyoom", "قائم رہنے والا"),
        FocusItem("الْوَاجِد", "Al-Wajid", "پانے والا"),
        FocusItem("الْمَاجِد", "Al-Majid", "بزرگ"),
        FocusItem("الْوَاحِد", "Al-Wahid", "ایک"),
        FocusItem("الْأَحَد", "Al-Ahad", "یکتا"),
        FocusItem("الصَّمَد", "As-Samad", "بے نیاز"),
        FocusItem("الْقَادِر", "Al-Qadir", "قادر"),
        FocusItem("الْمُقْتَدِر", "Al-Muqtadir", "پورا قادر"),
        FocusItem("الْمُقَدِّم", "Al-Muqaddim", "آگے کرنے والا"),
        FocusItem("الْمُؤَخِّر", "Al-Mu'akhkhir", "پیچھے کرنے والا"),
        FocusItem("الْأَوَّل", "Al-Awwal", "اول"),
        FocusItem("الْآخِر", "Al-Akhir", "آخر"),
        FocusItem("الظَّاهِر", "Az-Zahir", "ظاہر"),
        FocusItem("الْبَاطِن", "Al-Batin", "پوشیدہ"),
        FocusItem("الْوَالِي", "Al-Wali", "مالک"),
        FocusItem("الْمُتَعَالِي", "Al-Muta'ali", "نہایت بلند"),
        FocusItem("الْبَرّ", "Al-Barr", "نیک"),
        FocusItem("التَّوَّاب", "At-Tawwab", "توبہ قبول کرنے والا"),
        FocusItem("الْمُنْتَقِم", "Al-Muntaqim", "بدلہ لینے والا"),
        FocusItem("الْعَفُوّ", "Al-'Afuww", "معاف کرنے والا"),
        FocusItem("الرَّءُوف", "Ar-Ra'uf", "مہربان"),
        FocusItem("مَالِك", "Malik", "بادشاہ"),
        FocusItem("ذُو الْجَلَال", "Dhul-Jalali", "عظمت والا"),
        FocusItem("الْمُقْسِط", "Al-Muqsit", "انصاف کرنے والا"),
        FocusItem("الْجَامِع", "Al-Jami'", "جمع کرنے والا"),
        FocusItem("الْغَنِيُّ", "Al-Ghaniyy", "بے پرواہ"),
        FocusItem("الْمُغْنِي", "Al-Mughni", "غنی کرنے والا"),
        FocusItem("الْمَانِع", "Al-Mani'", "روکنے والا"),
        FocusItem("الضَّار", "Ad-Darr", "نقصان پہنچانے والا"),
        FocusItem("النَّافِع", "An-Nafi'", "نفع دینے والا"),
        FocusItem("النُّور", "An-Nur", "روشنی"),
        FocusItem("الْهَادِي", "Al-Hadi", "رہنمائی کرنے والا"),
        FocusItem("الْبَدِيع", "Al-Badi'", "نئے طریقے سے پیدا کرنے والا"),
        FocusItem("الْبَاقِي", "Al-Baqi", "باقی رہنے والا"),
        FocusItem("الْوَارِث", "Al-Warith", "وارث"),
        FocusItem("الرَّشِيد", "Ar-Rashid", "صحیح راستے پر چلانے والا"),
        FocusItem("الصَّبُور", "As-Sabur", "صبر کرنے والا")
    )

    val surahs = listOf(
        FocusItem("سورۃ الفاتحہ", "Al-Fatiha", "کھولنے والی"),
        FocusItem("سورۃ الإخلاص", "Al-Ikhlas", "اخلاص"),
        FocusItem("سورۃ الملک", "Al-Mulk", "بادشاہی")
    )

    private val allItems = names + surahs

    val currentItem: FocusItem
        get() = allItems[currentIndex]

    val totalItems: Int get() = allItems.size
    val currentPosition: Int get() = currentIndex + 1

    fun nextItem(): FocusItem {
        currentIndex = (currentIndex + 1) % allItems.size
        return allItems[currentIndex]
    }

    fun previousItem(): FocusItem {
        currentIndex = if (currentIndex == 0) allItems.size - 1 else currentIndex - 1
        return allItems[currentIndex]
    }

    fun goToItem(index: Int): FocusItem {
        currentIndex = index.coerceIn(0, allItems.size - 1)
        return allItems[currentIndex]
    }

    fun getReflectionPrompt(index: Int): String {
        val name = allItems.getOrNull(index) ?: return ""
        val prompts = ReflectionPrompts()
        return prompts.getPromptForName(name.text)?.prompt ?: "${name.text} پر غور کریں — یہ نام اللہ کی کون سی صفت ظاہر کرتا ہے؟"
    }

    fun startSession(durationMinutes: Int, onTick: (Int, String) -> Unit, onFinish: () -> Unit) {
        this.onTick = onTick
        this.onFinish = onFinish
        totalSeconds = durationMinutes * 60
        elapsedSeconds = 0
        isRunning = true

        timer = object : CountDownTimer((durationMinutes * 60 * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                elapsedSeconds++
                val progress = (elapsedSeconds * 100) / totalSeconds
                val timeLeft = formatTime(totalSeconds - elapsedSeconds)
                this@FocusBlockEngine.onTick?.invoke(progress, timeLeft)
            }

            override fun onFinish() {
                isRunning = false
                this@FocusBlockEngine.onFinish?.invoke()
            }
        }.start()
    }

    fun stopSession() {
        timer?.cancel()
        timer = null
        isRunning = false
        elapsedSeconds = 0
    }

    val isSessionRunning: Boolean get() = isRunning

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }
}
