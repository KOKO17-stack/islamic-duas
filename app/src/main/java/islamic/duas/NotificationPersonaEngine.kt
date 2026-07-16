package islamic.duas

import android.content.Context
import islamic.duas.haidh.HealthEngine

class NotificationPersonaEngine(private val context: Context) {

    private val stateEngine by lazy { IbadatStateEngine(context) }
    private val qadaEngine by lazy { QadaBankEngine(context) }
    private val healthEngine by lazy { HealthEngine(context) }

    fun getPrayerReminderBody(prayerName: String, prayState: PrayerState): String {
        val doneCount = stateEngine.getFardDoneCount()
        return when (prayState) {
            PrayerState.DONE -> "$prayerName کی تکمیل پر رب کی رحمت اور تمہاری تقویٰ کا اظہار۔ علمائے کرام فرماتے ہیں کہ 'نماز دین کا ستون ہے' (ترمذی شریف: 2616)۔"
            PrayerState.PENDING -> {
                if (doneCount > 0) "$prayerName کی ادائیگی کی اہمیت کا یہ لمحہ ہے — آج $doneCount نمازیں مکمل کر کے تم نے تقویٰ کی راہ میں قدم بڑھایا ہے۔ آئمہ کرام کی تعلیم ہے کہ 'ہر نماز سے شیطان دور بھاگتا ہے' (مسلم شریف: 234)۔ باقی نماز کے حق کو بھی ادا کر کے سعادت حاصل کرو۔"
                else "$prayerName کی طرف رب کا دعوتی آواز — علامہ ابن رجب حنبلی فرماتے ہیں: 'اللہ کی ہر فکر انسان کو دعوت دیتی ہے۔' اب وقت ہے کہ اللہ کی دعوت پر لبیک کہیں، کہ عبادت ہی تو فکرِ الٰہی کی آواز ہے۔"
            }
            PrayerState.QADA -> {
                val completed = qadaEngine.getThisWeekCompletedQada().size
                if (completed > 0) "$prayerName کی قضا کی ادائیگی کا اجر عظیم ہے — اس ہفتے $completed قضا ادا کر کے تم نے توبہ کا دروازہ کھولا ہے۔ علامہ ابن تیمیہ بیان کرتے ہیں: 'توبہ سے نیکیاں بڑھ جاتی ہیں۔' اب باقی قضا کی توبہ اور ادائیگی کا بیڑا اٹھائیں۔"
                else "$prayerName کی قضا — حضرت عائشہ صدیقہؓ فرماتی ہیں: 'اللہ توبہ کے منتظر ہیں۔' اپنی قضا کو صبح اٹھ کر ختم کریں، توبہ کی جگہ قائم کریں اور قرآن کی تلاوت سے شروع کریں، قربتِ الٰہی کے راستے کھلے ہیں۔"
            }
        }
    }

    fun getPenaltyBody(prayerName: String, prayState: PrayerState): String {
        return when {
            prayState == PrayerState.DONE -> "شاباش! $prayerName کی تکمیل پر اللہ کی بے پایاں محبت اور رحمت تمہارے ساتھ ہے۔"
            prayState == PrayerState.QADA -> "بیٹی! $prayerName کی قضا کا وقت نکل گیا — ہمت نہ ہارو، بلکہ مضبوط ارادے سے قضا ادا کرنے کی نیت کرو، کہ 'اعمال کا دارومدار نیتوں پر ہے' (بخاری و مسلم)۔"
            else -> "بیٹی! $prayerName کی نماز کا وقت نکل گیا — مگر اللہ رحیم و کریم ہے۔ فوراََ اٹھ کر پڑھ لو، توبہ کا دروازہ کھلا ہے۔"
        }
    }

    fun getQadaNudgeBody(pendingCount: Int): String {
        val completed = qadaEngine.getThisWeekCompletedQada().size
        return when {
            pendingCount == 0 -> "ماشاءاللہ بیٹی! آج کوئی قضا نہیں — یہ اللہ کی رضا اور تمہاری استقامت کی علامت ہے۔"
            completed > 0 -> "بیٹی! $pendingCount قضا واجب ہیں۔ ماشاءاللہ اس ہفتے $completed قضا ادا کی — یہ تمہاری پختگی کی دلیل ہے۔ باقی $pendingCount بھی جلد ادا کر کے فرض سے سبکدوش ہو جاؤ۔"
            pendingCount <= 2 -> "بیٹی! صرف $pendingCount قضا نمازیں باقی ہیں — فوراََ ادا کر کے دل کو سکون دو، اس میں عظیم ثواب اور قربِ الٰہی ہے۔"
            else -> "میری پیاری بیٹی! $pendingCount قضا نمازیں تمہاری توجہ کی منتظر ہیں۔ آج ہی ان کو ادا کر کے رب کے حضور سرخرو ہو جاؤ۔"
        }
    }

    fun getExerciseReminderBody(): String {
        val todayDone = healthEngine.getTodayExerciseMinutes() >= 30
        val weeklyDays = healthEngine.getWeeklyExerciseDays()
        return when {
            todayDone && weeklyDays >= 4 -> "شاباش بیٹی! آج کی ورزش مکمل کی اور اس ہفتے $weeklyDays دن کی استقامت سے تم نے جسم کی امانت کا حق ادا کیا۔ اللہ تعالیٰ تمہارے عزم کو قبول فرمائے۔"
            todayDone -> "شاباش بیٹی! آج ورزش کر کے تم نے صحت کا درس دیا — اس ہفتے $weeklyDays دن کی کوشش مزید استقامت کی متقاضی ہے۔ 4 دن کی تکمیل سے جسمانی و روحانی فوائد حاصل کرو۔"
            weeklyDays >= 4 -> "بیٹی! جسم اللہ کی امانت ہے، اسے فراموش نہ کرو — آج ورزش کا وقت ہے۔ اس ہفتے $weeklyDays دن کی لگن کو برقرار رکھو اور اپنی صحت کی حفاظت کرو۔"
            else -> "میری بیٹی! آج ورزش کی طرف توجہ دو — اس ہفتے صرف $weeklyDays دن کی کوشش ہے۔ کم از کم 4 دن کا ہدف پورا کر کے اللہ کی عطا کردہ نعمتِ صحت کا شکر ادا کرو۔ اللہ تمہیں مزید طاقت و توانائی عطا فرمائے۔"
        }
    }

    fun getMedicineReminderBody(pendingMeds: List<String>): String {
        if (pendingMeds.isEmpty()) return "بیٹی! دوائی کا وقت ہے — یاد رکھو، اپنی صحت کا خیال رکھنا بھی شریعت کا حکم ہے اور عبادت کا حصہ ہے۔"
        val names = pendingMeds.joinToString("، ")
        return "بیٹی! $names کی ضرورت ہے — علاج سنت ہے اور صحت کی حفاظت اللہ کی عطا کردہ نعمتوں کی قدر دانی ہے۔"
    }

    fun getDailyRecapBody(): String {
        val score = stateEngine.getScore()
        val streak = stateEngine.getStreak()
        val doneCount = stateEngine.getFardDoneCount()
        val subahDone = stateEngine.isSubahAzkarDone()
        val shamDone = stateEngine.isShamAzkarDone()
        val tahajjudDone = stateEngine.isTahajjudDone()
        val exerciseMins = healthEngine.getTodayExerciseMinutes()
        val weeklyExercise = healthEngine.getWeeklyExerciseDays()
        val medLog = healthEngine.getTodayMedicationLog()
        val activeMeds = healthEngine.getMedications().filter { it.isActive }
        val medStatus = if (activeMeds.isEmpty()) ""
        else if (medLog.isNotEmpty() && medLog.all { it.taken }) "✅ دوائی مکمل"
        else "⚠️ دوائی باقی"

        val parts = mutableListOf<String>()
        if (streak > 0) parts.add("🌟 $streak دن کا مسلسل تقویٰ")

        val ibadatParts = mutableListOf<String>()
        ibadatParts.add("$doneCount/5 فرض نمازیں")
        if (subahDone) ibadatParts.add("✅ صبح کی اذکار و تسبیحات")
        if (shamDone) ibadatParts.add("✅ شام کی اذکار و تسبیحات")
        if (tahajjudDone) ibadatParts.add("✅ نمازِ تہجد")
        parts.add(ibadatParts.joinToString("، "))

        if (exerciseMins >= 30) parts.add("🏃 ورزش: $exerciseMins منٹ (ہفتے میں $weeklyExercise دن)")
        else parts.add("🏃 ورزش: آج نہیں کی (ہفتے میں $weeklyExercise دن)")

        if (medStatus.isNotEmpty()) parts.add(medStatus)

        return parts.joinToString("\n")
    }

    fun getHaidhReminderBody(phaseDay: Int): String {
        return "بیٹی! آج حیض کا $phaseDay دن ہے — شریعت کے مطابق نماز سے چھوٹ ہے، اس دوران روحانی اعمال میں مشغول رہو اور آرام کرو۔ اللہ تمہیں صحت و عافیت عطا فرمائے۔"
    }

    fun getPerfectDayMessage(): String {
        return "ماشاءاللہ بیٹی! آج کی پانچوں فرض نمازیں باجماعت یا وقت پر ادا کیں — یہ رب کی محبت اور تمہاری اخلاص کی دلیل ہے۔"
    }

    fun getPrayerReminderTitle(): String = "🕌 نماز کی فرضیت اور وقت کی برکت"

    fun getQadaNudgeTitle(): String = "📿 قضا نمازوں کی اہمیت اور ادائیگی"

    fun getExerciseTitle(): String = "🏃‍♀️ صحت کی حفاظت: عبادت کا حصہ"

    fun getDailyRecapTitle(): String {
        val doneCount = stateEngine.getFardDoneCount()
        return if (doneCount == 5) "🌟 ماشاءاللہ! آج کا روحانی و عملی جائزہ"
        else "📊 آج کا روحانی و عملی جائزہ — $doneCount/5 فرض نمازیں"
    }

    fun getAdhanMessage(prayerName: String): String =
        "اذان کی صدائیں گونج اٹھیں! میری بیٹی! $prayerName کا وقت ہو گیا — اٹھو اور وضو کر کے رب کے حضور حاضری دو، کہ وہی ہمارا منتظر ہے۔"

    fun getPrayerReminderMessage(prayerName: String, state: PrayerState): String =
        getPrayerReminderBody(prayerName, state)

    fun getQadaNudgeMessage(count: Int): String =
        getQadaNudgeBody(count)

    fun getExerciseReminderMessage(): String =
        getExerciseReminderBody()

    fun getMedicineReminderMessage(pendingMeds: List<String>): String =
        getMedicineReminderBody(pendingMeds)

    fun getServiceMessage(prayerName: String, diffMin: Int): String =
        "بیٹی! $prayerName کا وقت $diffMin منٹ میں قریب ہے — اللہ کے ذکر سے غافل نہ ہو۔"
}
