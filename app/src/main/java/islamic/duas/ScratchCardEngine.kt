package islamic.duas

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

data class SunnahItem(
    val title: String,
    val description: String,
    val source: String,
    val arabic: String = ""
)

class ScratchCardEngine(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("scratch_card", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        private const val KEY_REVEALED = "revealed_"
        private const val KEY_SUNNAH_INDEX = "sunnah_index_"
    }

    val today: String get() = dateFormat.format(Date())

    private val sunnahList = listOf(
        SunnahItem("مسواک", "نبی کریم ﷺ نے فرمایا: 'مسواک منہ کی صفائی اور رب کی رضامندی کا باعث ہے'", "صحیح البخاری", "السِّوَاكُ مَطْهَرَةٌ لِلْفَمِ مَرْضَاةٌ لِلرَّبِّ"),
        SunnahItem("سوتے وقت وضو", "جب تم اپنے بستر پر آؤ تو وضو کرو جیسے نماز کے لیے وضو کرتے ہو", "صحیح البخاری: ۲۴۷", ""),
        SunnahItem("تین بار چھینک", "جب تم میں سے کوئی چھینکے تو 'الحمدللہ' کہے", "صحیح البخاری: ۶۲۲۴", ""),
        SunnahItem("دائیں طرف سونا", "نبی ﷺ اپنے دائیں ہاتھ پر سوتے تھے اور قبلہ رخ", "صحیح البخاری: ۶۳۱۳", ""),
        SunnahItem("کھانے سے پہلے بسم اللہ", "جب تم میں سے کوئی کھانا کھائے تو 'بسم اللہ' پڑھے", "صحیح مسلم: ۲۰۲۲", ""),
        SunnahItem("آیۃ الکرسی", "جو شخص رات کو آیۃ الکرسی پڑھے، اللہ اس پر ایک نگہبان مقرر کرتا ہے", "صحیح البخاری: ۳۲۷۵", ""),
        SunnahItem("تین بار سلام", "نبی ﷺ تین بار سلام کرتے تھے", "صحیح البخاری: ۶۲۴۶", ""),
        SunnahItem("پانی تین گھونٹ میں پینا", "نبی ﷺ تین سانسوں میں پانی پیتے تھے", "صحیح مسلم: ۲۰۲۷", ""),
        SunnahItem("جوتے داہنے پہننا", "جب جوتا پہنو تو داہنے سے شروع کرو، اور جب اتارو تو بائیں سے", "صحیح البخاری: ۵۸۵۶", ""),
        SunnahItem("چھت کی طرف دیکھ کر دعا", "نبی ﷺ جب بارش دیکھتے تو فرماتے: 'اللهم صيبًا نافعًا'", "صحیح البخاری: ۱۰۳۲", ""),
        SunnahItem("سفر کی دعا", "نبی ﷺ جب سفر پر نکلتے تو 'اللہ اکبر' کہتے", "صحیح مسلم: ۱۳۴۲", ""),
        SunnahItem("سلام کرنا", "نبی ﷺ نے فرمایا: 'تم اس وقت تک جنت میں داخل نہیں ہو سکتے جب تک ایمان نہ لاؤ، اور تم اس وقت تک مومن نہیں ہو سکتے جب تک آپس میں محبت نہ کرو۔ کیا میں تمہیں ایسی چیز نہ بتاؤں جس سے تم آپس میں محبت کرو؟ آپس میں سلام کو عام کرو'", "صحیح مسلم: ۵۴", ""),
        SunnahItem("سورۃ الملک", "نبی ﷺ سونے سے پہلے سورۃ الملک پڑھتے تھے", "سنن الترمذی: ۲۸۹۲", ""),
        SunnahItem("کھڑے ہو کر پانی نہ پینا", "نبی ﷺ نے کھڑے ہو کر پانی پینے سے منع فرمایا", "صحیح مسلم: ۲۰۲۴", ""),
        SunnahItem("داہنے ہاتھ سے کھانا", "نبی ﷺ نے فرمایا: 'بسم اللہ پڑھو اور داہنے ہاتھ سے کھاؤ'", "صحیح البخاری: ۵۳۷۶", ""),
        SunnahItem("عطریہ استعمال", "نبی ﷺ خوشبو پسند فرماتے تھے", "صحیح البخاری: ۵۹۲۳", ""),
        SunnahItem("مسجد میں داہنے پیر داخل ہونا", "نبی ﷺ مسجد میں داہنے پیر سے داخل ہوتے تھے", "سنن النسائی: ۷۰۱", ""),
        SunnahItem("تین بار پانی منہ میں ڈالنا", "نبی ﷺ وضو میں تین بار کلی کرتے تھے", "صحیح البخاری: ۱۶۱", ""),
        SunnahItem("سلام کا جواب بہتر طریقے سے دینا", "اللہ تعالیٰ فرماتا ہے: 'جب تمہیں سلام کیا جائے تو اس سے بہتر جواب دو'", "النساء: ۸۶", ""),
        SunnahItem("مریض کی عیادت", "نبی ﷺ نے فرمایا: 'مسلمان کے مسلمان پر چھ حق ہیں' — ان میں سے ایک مریض کی عیادت ہے", "صحیح مسلم: ۲۱۶۲", ""),
        SunnahItem("نئے کپڑے پہننے کی دعا", "نبی ﷺ جب نیا کپڑا پہنتے تو فرماتے: 'اللهم لك الحمد'", "سنن الترمذی: ۱۷۶۰", ""),
        SunnahItem("رات کو سورۃ الاخلاص پڑھنا", "نبی ﷺ نے فرمایا: 'کیا تم میں سے کوئی ہر رات ایک تہائی قرآن نہیں پڑھ سکتا؟' — سورۃ الاخلاص ایک تہائی قرآن کے برابر ہے", "صحیح مسلم: ۸۱۱", ""),
        SunnahItem("تین بار جمائی روکنا", "نبی ﷺ نے فرمایا: 'جب تم میں سے کسی کو جمائی آئے تو اسے روکے'", "صحیح مسلم: ۲۹۹۴", ""),
        SunnahItem("بائیں ہاتھ سے استنجا", "نبی ﷺ اپنے بائیں ہاتھ سے استنجا کرتے تھے", "صحیح البخاری: ۱۵۳", ""),
        SunnahItem("رات کو سورۂ السجدہ پڑھنا", "نبی ﷺ جمعہ کے دن فجر میں سورۂ السجدہ پڑھتے تھے", "صحیح البخاری: ۱۰۶۸", ""),
        SunnahItem("جمعہ کا دن غسل کرنا", "نبی ﷺ نے فرمایا: 'جمعہ کے دن غسل کرنا ہر بالغ پر واجب ہے'", "صحیح البخاری: ۸۷۷", ""),
        SunnahItem("عید کے دن مختلف راستے", "نبی ﷺ عید کے دن ایک راستے سے جاتے اور دوسرے سے واپس آتے", "صحیح البخاری: ۹۸۶", ""),
        SunnahItem("آئینہ دیکھ کر دعا", "نبی ﷺ جب آئینہ دیکھتے تو فرماتے: 'اللهم أنت حسنت خلقي'", "صحیح مسلم: ۲۶۲۲", ""),
        SunnahItem("سوتے وقت دعا", "نبی ﷺ سوتے وقت اپنے دونوں ہاتھ ملا کر معوذات پڑھتے اور جسم پر پھیرتے", "صحیح البخاری: ۵۰۱۷", ""),
        SunnahItem("بچوں کو سلام کرنا", "نبی ﷺ بچوں کو سلام کرتے تھے", "صحیح مسلم: ۲۱۶۸", "")
    )

    fun getTodaysSunnah(): SunnahItem {
        val storedIndex = prefs.getInt("$KEY_SUNNAH_INDEX$today", -1)
        if (storedIndex in sunnahList.indices) {
            return sunnahList[storedIndex]
        }
        val newIndex = Random.nextInt(sunnahList.size)
        prefs.edit().putInt("$KEY_SUNNAH_INDEX$today", newIndex).apply()
        return sunnahList[newIndex]
    }

    fun isRevealed(): Boolean {
        return prefs.getBoolean("$KEY_REVEALED$today", false)
    }

    fun reveal() {
        prefs.edit().putBoolean("$KEY_REVEALED$today", true).apply()
    }

    fun resetForNewDay() {
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayStr = dateFormat.format(yesterday.time)
        prefs.edit()
            .remove("$KEY_REVEALED$yesterdayStr")
            .remove("$KEY_SUNNAH_INDEX$yesterdayStr")
            .apply()
    }
}
