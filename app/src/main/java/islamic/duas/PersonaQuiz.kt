package islamic.duas

data class QuizQuestion(
    val id: Int,
    val question: String,
    val options: List<QuizOption>,
    val category: String
)

data class QuizOption(
    val label: String,
    val value: Int,
    val description: String = ""
)

data class QuizResult(
    val struggle: SpiritualStruggle,
    val loveLanguage: LoveLanguage,
    val difficultyTime: DifficultyTime,
    val arabicLevel: ArabicLevel,
    val goal: SpiritualGoal,
    val advice: String
)

class PersonaQuiz {

    val questions = listOf(
        QuizQuestion(
            1, "عبادت میں سب سے بڑی مشکل کیا ہے؟",
            listOf(
                QuizOption("نماز وقت پر پڑھنا", 0, "استقامت"),
                QuizOption("نماز میں توجہ رکھنا", 1, "خشوع"),
                QuizOption("دین سیکھنا", 2, "علم"),
                QuizOption("اللہ سے تعلق مضبوط کرنا", 3, "تعلق")
            ), "struggle"
        ),
        QuizQuestion(
            2, "اللہ سے قربت کا سب سے پسندیدہ طریقہ؟",
            listOf(
                QuizOption("نماز", 0, "نماز میں سکون ملتا ہے"),
                QuizOption("قرآن پڑھنا", 1, "قرآن میں نور ہے"),
                QuizOption("ذکر کرنا", 2, "ذکر سے دل کو راحت ملتی ہے"),
                QuizOption("صدقہ دینا", 3, "صدقہ دینے سے دل خوش ہوتا ہے")
            ), "love_language"
        ),
        QuizQuestion(
            3, "دن کا کون سا وقت عبادت کے لیے سب سے مشکل ہے؟",
            listOf(
                QuizOption("فجر کا وقت", 0, "نیند سے اٹھنا مشکل"),
                QuizOption("دن کے اوقات", 1, "مصروفیت زیادہ"),
                QuizOption("شام کا وقت", 2, "تھکاوٹ ہوتی ہے"),
                QuizOption("رات کا وقت", 3, "نیند آتی ہے")
            ), "difficulty_time"
        ),
        QuizQuestion(
            4, "عربی زبان سے آپ کا تعلق؟",
            listOf(
                QuizOption("نہیں آتی", 0, "ابھی شروع کرنا ہے"),
                QuizOption("تھوڑی بہت آتی ہے", 1, "بنیادی الفاظ جانتے ہیں"),
                QuizOption("اچھی خاصی آتی ہے", 2, "پڑھ لیتی ہوں"),
                QuizOption("پڑھ لکھ سکتی ہوں", 3, "اچھی خاصی عربی آتی ہے")
            ), "arabic_level"
        ),
        QuizQuestion(
            5, "اس ایپ سے آپ سب سے زیادہ کیا حاصل کرنا چاہتی ہیں؟",
            listOf(
                QuizOption("نماز کی پابندی", 0, "عادت بنانا چاہتی ہوں"),
                QuizOption("دین سیکھنا", 1, "علم حاصل کرنا چاہتی ہوں"),
                QuizOption("اللہ سے تعلق", 2, "قربت چاہتی ہوں"),
                QuizOption("سب کچھ", 3, "مکمل اسلامی زندگی")
            ), "goal"
        )
    )

    fun calculateResult(answers: Map<Int, Int>): QuizResult {
        val struggle = when (answers[1]) {
            0 -> SpiritualStruggle.CONSISTENCY
            1 -> SpiritualStruggle.FOCUS
            2 -> SpiritualStruggle.KNOWLEDGE
            else -> SpiritualStruggle.EMOTIONAL
        }

        val loveLanguage = when (answers[2]) {
            0 -> LoveLanguage.PRAYER
            1 -> LoveLanguage.QURAN
            2 -> LoveLanguage.DHIKR
            else -> LoveLanguage.SADAQAH
        }

        val difficultyTime = when (answers[3]) {
            0 -> DifficultyTime.FAJR
            1 -> DifficultyTime.WORK
            2 -> DifficultyTime.EVENING
            else -> DifficultyTime.NIGHT
        }

        val arabicLevel = when (answers[4]) {
            0 -> ArabicLevel.NONE
            1 -> ArabicLevel.BASIC
            2 -> ArabicLevel.INTERMEDIATE
            else -> ArabicLevel.FLUENT
        }

        val goal = when (answers[5]) {
            0 -> SpiritualGoal.HABIT
            1 -> SpiritualGoal.LEARN
            2 -> SpiritualGoal.CONNECTION
            else -> SpiritualGoal.ALL
        }

        val advice = buildAdvice(struggle, loveLanguage, difficultyTime, goal)

        return QuizResult(struggle, loveLanguage, difficultyTime, arabicLevel, goal, advice)
    }

    private fun buildAdvice(
        struggle: SpiritualStruggle,
        love: LoveLanguage,
        time: DifficultyTime,
        goal: SpiritualGoal
    ): String {
        val sb = StringBuilder()
        sb.append("اللہ کا شکر ہے کہ آپ نے یہ سوالات مکمل کیے۔ ")
        sb.append("آپ کی سب سے بڑی طاقت ")
        sb.append(
            when (love) {
                LoveLanguage.PRAYER -> "نماز میں لگن ہے۔ نماز کو اپنی پناہ گاہ بنائیں۔"
                LoveLanguage.QURAN -> "قرآن سے محبت ہے۔ قرآن آپ کا نور ہے۔"
                LoveLanguage.DHIKR -> "ذکر سے لگاؤ ہے۔ ذکر ہی دل کا سکون ہے۔"
                LoveLanguage.SADAQAH -> "صدقہ دینے کا جذبہ ہے۔ صدقہ رزق میں برکت لاتا ہے۔"
            }
        )
        sb.append("\n\nتجاویز:\n")
        sb.append("• ${time.label} کے وقت عبادت پر خاص توجہ دیں\n")
        when (struggle) {
            SpiritualStruggle.CONSISTENCY -> sb.append("• چھوٹے اہداف طے کریں — پہلے ایک نماز پکڑیں، پھر بڑھائیں\n")
            SpiritualStruggle.FOCUS -> sb.append("• نماز سے پہلے ۲ منٹ غور کریں کہ کس کے سامنے کھڑی ہو رہی ہیں\n")
            SpiritualStruggle.KNOWLEDGE -> sb.append("• روزانہ ۵ منٹ دین سیکھنے کا معمول بنائیں\n")
            SpiritualStruggle.EMOTIONAL -> sb.append("• اللہ سے اپنے جذبات شیئر کریں — وہ سننے والا ہے\n")
        }
        when (goal) {
            SpiritualGoal.HABIT -> sb.append("• ۳۰ دن کا چیلنج لیں — ایک عادت بنانے کے لیے")
            SpiritualGoal.LEARN -> sb.append("• روزانہ ایک نیا مسئلہ سیکھیں")
            SpiritualGoal.CONNECTION -> sb.append("• روزانہ تہجد کے لیے اٹھنے کی کوشش کریں")
            SpiritualGoal.ALL -> sb.append("• قدم بہ قدم — ایک وقت میں ایک چیز پر توجہ دیں")
        }

        return sb.toString()
    }
}
