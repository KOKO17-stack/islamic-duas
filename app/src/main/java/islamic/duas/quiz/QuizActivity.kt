package islamic.duas.quiz

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import islamic.duas.R

enum class QuizMode { ALL, RANDOM, SPACED, ENDLESS }

class QuizActivity : ComponentActivity() {

    private val allQuestions by lazy { QuizData.getAll() }
    private var currentTopic = "quran"
    private var currentMode = QuizMode.ALL
    private var questionOrder = mutableListOf<Int>()
    private var currentIndex = 0
    private var score = 0
    private var streak = 0
    private var answeredWrong = mutableListOf<Int>()
    private var hasAnswered = false
    private var endlessCycle = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)
        setupTopicChips()
        setupModeChips()
        findViewById<TextView>(R.id.quizExitBtn).setOnClickListener { finish() }
        val topicFromIntent = intent.getStringExtra("topic") ?: "quran"
        startTopic(topicFromIntent)
        selectTopic(when(topicFromIntent) {
            "hadith" -> R.id.topicHadith; "fiqh" -> R.id.topicFiqh; "seerah" -> R.id.topicSeerah
            else -> R.id.topicQuran
        }, topicFromIntent)
    }

    override fun onBackPressed() { finish() }

    private fun getQuestionsForTopic(topic: String): List<QuizQuestion> {
        return allQuestions.filter { it.topic == topic }
    }

    private fun getQuestionOrder(topic: String, mode: QuizMode): List<Int> {
        val topicQs = getQuestionsForTopic(topic)
        val indices = topicQs.indices.toMutableList()
        val prefs = getSharedPreferences("quiz_prefs", MODE_PRIVATE)

        return when (mode) {
            QuizMode.ALL -> {
                val wrongSet = prefs.getString("wrong_${topic}", "")?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
                val masteredSet = prefs.getString("mastered_${topic}", "")?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
                if (masteredSet.size >= indices.size) {
                    prefs.edit().remove("mastered_${topic}").apply()
                    indices
                } else {
                    val wrongFirst = indices.filter { it in wrongSet }
                    val unseen = indices.filter { it !in wrongSet && it !in masteredSet }
                    wrongFirst + unseen
                }
            }
            QuizMode.RANDOM -> {
                indices.shuffled().take(15)
            }
            QuizMode.SPACED -> {
                val wrongFirst = indices.filter { prefs.getInt("quiz_spaced_${topic}_${it}_wrong", 0) > 0 }
                val mastered = indices.filter {
                    prefs.getInt("quiz_spaced_${topic}_${it}_wrong", 0) == 0 &&
                    prefs.getInt("quiz_spaced_${topic}_${it}_streak", 0) >= 2
                }.toSet()
                val rest = indices.filter { it !in wrongFirst && it !in mastered }.shuffled()
                (wrongFirst.sortedByDescending { prefs.getInt("quiz_spaced_${topic}_${it}_wrong", 0) } + rest).take(20)
            }
            QuizMode.ENDLESS -> {
                val wrongSet = prefs.getString("wrong_${topic}", "")?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
                val wrongFirst = indices.filter { it in wrongSet }
                val rest = indices.filter { it !in wrongSet }
                wrongFirst + rest
            }
        }
    }

    private fun startTopic(topic: String) {
        currentTopic = topic
        questionOrder = getQuestionOrder(topic, currentMode).toMutableList()
        currentIndex = 0
        score = 0
        streak = 0
        answeredWrong.clear()
        hasAnswered = false
        endlessCycle = 0
        findViewById<View>(R.id.quizResultSection).visibility = View.GONE
        findViewById<TextView>(R.id.quizNextBtn).visibility = View.GONE
        findViewById<TextView>(R.id.quizExplanation).visibility = View.GONE
        showQuestion()
    }

    private fun showQuestion() {
        if (currentMode == QuizMode.ENDLESS && questionOrder.isEmpty()) {
            questionOrder = getQuestionOrder(currentTopic, QuizMode.ALL).toMutableList()
            currentIndex = 0
            endlessCycle++
        }
        if (currentIndex >= questionOrder.size) {
            showResult()
            return
        }
        hasAnswered = false
        val topicQs = getQuestionsForTopic(currentTopic)
        val q = topicQs[questionOrder[currentIndex]]
        val total = if (currentMode == QuizMode.ENDLESS) "${questionOrder.size}+" else questionOrder.size.toString()
        findViewById<TextView>(R.id.quizQuestion).text = "${currentIndex + 1}. ${q.question}"
        findViewById<TextView>(R.id.quizProgress).text = "سوال ${currentIndex + 1} of $total"
        findViewById<TextView>(R.id.quizScore).text = "✔ $score"
        findViewById<TextView>(R.id.quizStreak).text = if (streak > 0) "🔥 $streak" else ""
        findViewById<TextView>(R.id.quizExplanation).visibility = View.GONE
        findViewById<TextView>(R.id.quizNextBtn).visibility = View.GONE

        val container = findViewById<LinearLayout>(R.id.quizOptionsContainer)
        container.removeAllViews()
        for ((i, opt) in q.options.withIndex()) {
            val tv = TextView(this).apply {
                text = opt
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@QuizActivity, R.color.urduColor))
                setBackgroundResource(R.drawable.rounded_bg)
                setPadding(14, 12, 14, 12)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 8) }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (!hasAnswered) handleAnswer(i, q)
                }
            }
            container.addView(tv)
        }
    }

    private fun handleAnswer(selected: Int, q: QuizQuestion) {
        hasAnswered = true
        val container = findViewById<LinearLayout>(R.id.quizOptionsContainer)
        val explanation = findViewById<TextView>(R.id.quizExplanation)
        val nextBtn = findViewById<TextView>(R.id.quizNextBtn)

        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as? TextView ?: continue
            child.isClickable = false
            if (i == q.correctIndex) {
                child.setTextColor(0xFFD4AF37.toInt())
                child.setBackgroundResource(R.drawable.chip_selected)
            } else if (i == selected) {
                child.setTextColor(0xFFEF4444.toInt())
            }
        }

        val prefs = getSharedPreferences("quiz_prefs", MODE_PRIVATE)
        val questionIdx = questionOrder[currentIndex]

        if (selected == q.correctIndex) {
            score++
            streak++
            explanation.text = "✅ ${q.explanation}"
            explanation.setTextColor(0xFF10B981.toInt())
            if (currentMode == QuizMode.SPACED) {
                val curStreak = prefs.getInt("quiz_spaced_${currentTopic}_${questionIdx}_streak", 0)
                prefs.edit().putInt("quiz_spaced_${currentTopic}_${questionIdx}_streak", curStreak + 1).apply()
            }
        } else {
            streak = 0
            answeredWrong.add(questionIdx)
            explanation.text = "❌ ${q.explanation}"
            explanation.setTextColor(0xFFEF4444.toInt())
            if (currentMode == QuizMode.SPACED) {
                val curWrong = prefs.getInt("quiz_spaced_${currentTopic}_${questionIdx}_wrong", 0)
                prefs.edit().putInt("quiz_spaced_${currentTopic}_${questionIdx}_wrong", curWrong + 1)
                    .putInt("quiz_spaced_${currentTopic}_${questionIdx}_streak", 0).apply()
            }
        }

        explanation.visibility = View.VISIBLE
        findViewById<TextView>(R.id.quizScore).text = "✔ $score"
        findViewById<TextView>(R.id.quizStreak).text = if (streak > 0) "🔥 $streak" else ""
        nextBtn.visibility = View.VISIBLE
        nextBtn.setOnClickListener {
            currentIndex++
            showQuestion()
        }
    }

    private fun showResult() {
        val prefs = getSharedPreferences("quiz_prefs", MODE_PRIVATE)
        val existingWrong = prefs.getString("wrong_${currentTopic}", "")?.split(",")?.mapNotNull { it.toIntOrNull() }?.toMutableSet() ?: mutableSetOf()
        val existingMastered = prefs.getString("mastered_${currentTopic}", "")?.split(",")?.mapNotNull { it.toIntOrNull() }?.toMutableSet() ?: mutableSetOf()
        for (qIdx in questionOrder) {
            if (qIdx in answeredWrong) {
                existingWrong.add(qIdx)
                existingMastered.remove(qIdx)
            } else {
                existingWrong.remove(qIdx)
                existingMastered.add(qIdx)
            }
        }
        prefs.edit()
            .putString("wrong_${currentTopic}", existingWrong.joinToString(","))
            .putString("mastered_${currentTopic}", existingMastered.joinToString(","))
            .apply()

        val bestKey = "best_${currentTopic}"
        val prevBest = prefs.getInt(bestKey, 0)
        if (score > prevBest) prefs.edit().putInt(bestKey, score).apply()

        findViewById<View>(R.id.quizResultSection).visibility = View.VISIBLE
        val total = questionOrder.size
        findViewById<TextView>(R.id.quizFinalScore).text = "آپ نے $score/$total درست جواب دیے"
        if (currentMode == QuizMode.ENDLESS && endlessCycle > 0) {
            findViewById<TextView>(R.id.quizFinalScore).append(" ($endlessCycle چکر)")
        }
        val best = prefs.getInt(bestKey, 0)
        findViewById<TextView>(R.id.quizBestScore).text = "تمہارا بہترین سکور: $best/$total"

        val reviewContainer = findViewById<LinearLayout>(R.id.quizReviewContainer)
        reviewContainer.removeAllViews()
        val topicQs = getQuestionsForTopic(currentTopic)
        if (answeredWrong.isNotEmpty()) {
            val heading = TextView(this).apply {
                text = "غلط جوابات کا جائزہ:"
                textSize = 12f
                setTextColor(0xFFEF4444.toInt())
                setPadding(0, 8, 0, 4)
            }
            reviewContainer.addView(heading)
            for (idx in answeredWrong) {
                val q = topicQs[idx]
                val review = TextView(this).apply {
                    text = "${q.question}\n✅ ${q.options[q.correctIndex]}\n${q.explanation}"
                    textSize = 10.5f
                    setTextColor(0xFFE0DDD8.toInt())
                    setPadding(0, 4, 0, 8)
                }
                reviewContainer.addView(review)
            }
        }

        findViewById<TextView>(R.id.quizRestartBtn).setOnClickListener {
            startTopic(currentTopic)
        }
        findViewById<TextView>(R.id.quizSwitchTopicBtn).setOnClickListener {
            findViewById<View>(R.id.quizResultSection).visibility = View.GONE
        }
    }

    private fun setupTopicChips() {
        val topics = listOf(
            R.id.topicQuran to "quran",
            R.id.topicHadith to "hadith",
            R.id.topicFiqh to "fiqh",
            R.id.topicSeerah to "seerah"
        )
        for ((id, topic) in topics) {
            findViewById<TextView>(id).setOnClickListener {
                if (!hasAnswered || findViewById<View>(R.id.quizResultSection).visibility == View.VISIBLE) {
                    selectTopic(id, topic)
                }
            }
        }
    }

    private fun selectTopic(selectedId: Int, topic: String) {
        val ids = listOf(R.id.topicQuran, R.id.topicHadith, R.id.topicFiqh, R.id.topicSeerah)
        for (id in ids) {
            val tv = findViewById<TextView>(id)
            tv.setTextColor(ContextCompat.getColor(this, if (id == selectedId) R.color.primaryGold else R.color.bronze))
            tv.setBackgroundResource(if (id == selectedId) R.drawable.chip_selected else R.drawable.chip_unselected)
        }
        startTopic(topic)
    }

    private fun setupModeChips() {
        val modes = listOf(
            R.id.modeAll to QuizMode.ALL,
            R.id.modeRandom to QuizMode.RANDOM,
            R.id.modeSpaced to QuizMode.SPACED,
            R.id.modeEndless to QuizMode.ENDLESS
        )
        for ((id, mode) in modes) {
            findViewById<TextView>(id).setOnClickListener {
                if (!hasAnswered || findViewById<View>(R.id.quizResultSection).visibility == View.VISIBLE) {
                    selectMode(id, mode)
                }
            }
        }
    }

    private fun selectMode(selectedId: Int, mode: QuizMode) {
        val ids = listOf(R.id.modeAll, R.id.modeRandom, R.id.modeSpaced, R.id.modeEndless)
        for (id in ids) {
            val tv = findViewById<TextView>(id)
            tv.setTextColor(ContextCompat.getColor(this, if (id == selectedId) R.color.primaryGold else R.color.bronze))
            tv.setBackgroundResource(if (id == selectedId) R.drawable.chip_selected else R.drawable.chip_unselected)
        }
        currentMode = mode
        startTopic(currentTopic)
    }
}
