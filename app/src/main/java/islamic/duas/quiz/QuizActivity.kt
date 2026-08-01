package islamic.duas.quiz

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import islamic.duas.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class QuizMode { ALL, RANDOM, SPACED, ENDLESS }

class QuizActivity : ComponentActivity() {

    private val allQuestions by lazy { QuizData.getAll() }
    private val allTopicIds = listOf("quran", "hadith", "seerah", "fiqh", "women", "sahaba", "history")
    private val topicChipIds = listOf(
        R.id.topicQuran, R.id.topicHadith, R.id.topicFiqh, R.id.topicSeerah,
        R.id.topicWomen, R.id.topicSahaba, R.id.topicHistory
    )

    private var selectedTopics = mutableSetOf("quran")
    private var currentMode = QuizMode.ALL
    private var sessionLength = 0
    private var questionOrder = mutableListOf<Pair<Int, String>>()
    private var currentIndex = 0
    private var score = 0
    private var streak = 0
    private var answeredWrong = mutableListOf<Pair<Int, String>>()
    private var hasAnswered = false
    private var endlessCycle = 0
    private var isDailyChallenge = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)
        lifecycleScope.launch(Dispatchers.IO) { allQuestions }
        findViewById<TextView>(R.id.quizExitBtn).setOnClickListener { finish() }
        setupTopicChips()
        setupModeChips()
        setupSessionLengthChips()
        setupBookmarkButton()

        val topicFromIntent = intent.getStringExtra("topic") ?: ""
        val dailyFromIntent = intent.getBooleanExtra("daily_challenge", false)

        if (dailyFromIntent) {
            isDailyChallenge = true
            selectedTopics = allTopicIds.toMutableSet()
            startDailyChallenge()
        } else if (topicFromIntent in allTopicIds) {
            selectedTopics = mutableSetOf(topicFromIntent)
            updateTopicChips()
            startQuiz()
        } else {
            updateTopicChips()
            startQuiz()
        }
    }

    override fun onBackPressed() { finish() }

    private fun getQuestionsForTopics(topics: Set<String>): List<QuizQuestion> {
        return allQuestions.filter { it.topic in topics }
    }

    private fun buildQuestionOrder(topics: Set<String>, mode: QuizMode, length: Int): MutableList<Pair<Int, String>> {
        val topicQs = getQuestionsForTopics(topics)
        val grouped = topicQs.groupBy { it.topic }
        val prefs = getSharedPreferences("quiz_prefs", MODE_PRIVATE)
        val result = mutableListOf<Pair<Int, String>>()

        for ((topic, questions) in grouped) {
            val indices = questions.indices.toMutableList()
            val order = when (mode) {
                QuizMode.ALL -> {
                    val wrongSet = prefs.getString("wrong_${topic}", "")?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
                    val masteredSet = prefs.getString("mastered_${topic}", "")?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
                    if (masteredSet.size >= indices.size) {
                        prefs.edit().remove("mastered_${topic}").apply()
                        indices
                    } else {
                        indices.filter { it in wrongSet } + indices.filter { it !in wrongSet && it !in masteredSet }
                    }
                }
                QuizMode.RANDOM -> indices.shuffled().take(15)
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
                    indices.filter { it in wrongSet } + indices.filter { it !in wrongSet }
                }
            }
            val globalStart = allQuestions.indexOf(questions[0])
            result.addAll(order.map { (globalStart + it) to topic })
        }

        result.shuffle()
        return if (length > 0 && length < result.size) result.take(length).toMutableList() else result
    }

    private fun getTopicColor(topic: String): Int {
        return when (topic) {
            "quran" -> 0xFFD4AF37.toInt()
            "hadith" -> 0xFFD4AF37.toInt()
            "fiqh" -> 0xFFD4AF37.toInt()
            "seerah" -> 0xFFD4AF37.toInt()
            "women" -> 0xFFE8A87C.toInt()
            "sahaba" -> 0xFF5CB87A.toInt()
            "history" -> 0xFF5B8FD9.toInt()
            else -> 0xFFD4AF37.toInt()
        }
    }

    private fun startDailyChallenge() {
        isDailyChallenge = true
        val prefs = getSharedPreferences("quiz_prefs", MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val lastDaily = prefs.getString("daily_challenge_date", "") ?: ""

        if (lastDaily != today) {
            val allIndices = allQuestions.indices.toList().shuffled().take(10)
            questionOrder = allIndices.map { it to allQuestions[it].topic }.toMutableList()
            prefs.edit().putString("daily_challenge_date", today).apply()
            prefs.edit().putString("daily_challenge_order", questionOrder.joinToString(",") { "${it.first}:${it.second}" }).apply()
        } else {
            val saved = prefs.getString("daily_challenge_order", "") ?: ""
            questionOrder = if (saved.isNotEmpty()) {
                saved.split(",").mapNotNull { s ->
                    val parts = s.split(":")
                    if (parts.size == 2) parts[0].toIntOrNull()?.let { it to parts[1] } else null
                }.toMutableList()
            } else {
                allQuestions.indices.toList().shuffled().take(10).map { it to allQuestions[it].topic }.toMutableList()
            }
        }

        findViewById<TextView>(R.id.quizDailyChallengeBadge).text = "☀️ روزانہ چیلنج — آج $today"
        findViewById<TextView>(R.id.quizDailyChallengeBadge).visibility = View.VISIBLE
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

    private fun startQuiz() {
        isDailyChallenge = false
        findViewById<TextView>(R.id.quizDailyChallengeBadge).visibility = View.GONE
        questionOrder = buildQuestionOrder(selectedTopics, currentMode, sessionLength)
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
            questionOrder = buildQuestionOrder(selectedTopics, QuizMode.ALL, sessionLength)
            currentIndex = 0
            endlessCycle++
        }
        if (currentIndex >= questionOrder.size) {
            showResult()
            return
        }
        hasAnswered = false
        val (globalIdx, topic) = questionOrder[currentIndex]
        val q = allQuestions[globalIdx]
        val total = if (currentMode == QuizMode.ENDLESS) "${questionOrder.size}+" else questionOrder.size.toString()

        val topicLabels = mapOf("quran" to "قرآن", "hadith" to "حدیث", "fiqh" to "فقہ", "seerah" to "سیرت", "women" to "خواتین", "sahaba" to "صحابہ", "history" to "تاریخ")
        val topicEmojis = mapOf("quran" to "📖", "hadith" to "📜", "fiqh" to "⚖️", "seerah" to "🕋", "women" to "👩‍⚖️", "sahaba" to "🤝", "history" to "🕌")

        findViewById<TextView>(R.id.quizQuestion).text = "${topicEmojis[topic] ?: ""} ${topicLabels[topic] ?: topic}\n${currentIndex + 1}. ${q.question}"
        findViewById<TextView>(R.id.quizProgress).text = "سوال ${currentIndex + 1} of $total"
        findViewById<TextView>(R.id.quizScore).text = "✔ $score"
        findViewById<TextView>(R.id.quizStreak).text = if (streak > 0) "🔥 $streak" else ""
        findViewById<TextView>(R.id.quizExplanation).visibility = View.GONE
        findViewById<TextView>(R.id.quizNextBtn).visibility = View.GONE

        val progressBar = findViewById<View>(R.id.quizProgressBarFill)
        val progressFraction = if (questionOrder.isNotEmpty()) currentIndex.toFloat() / questionOrder.size else 0f
        progressBar.layoutParams = progressBar.layoutParams.apply { width = (findViewById<View>(R.id.quizProgressBarContainer).measuredWidth * progressFraction).toInt() }

        updateBookmarkButton(globalIdx)

        val container = findViewById<LinearLayout>(R.id.quizOptionsContainer)
        container.removeAllViews()
        for ((i, opt) in q.options.withIndex()) {
            val labels = arrayOf("أ", "ب", "ج", "د")
            val tv = TextView(this).apply {
                text = "${labels[i]}. $opt"
                textSize = 15f
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
                    if (!hasAnswered) handleAnswer(i, q, globalIdx)
                }
            }
            container.addView(tv)
        }

        // Track that this question was shown
        val prefs = getSharedPreferences("quiz_prefs", MODE_PRIVATE)
        val timesShown = prefs.getInt("quiz_stats_${topic}_${globalIdx}_shown", 0)
        prefs.edit().putInt("quiz_stats_${topic}_${globalIdx}_shown", timesShown + 1)
            .putLong("quiz_stats_${topic}_${globalIdx}_lastseen", System.currentTimeMillis()).apply()
    }

    private fun handleAnswer(selected: Int, q: QuizQuestion, globalIdx: Int) {
        hasAnswered = true
        val container = findViewById<LinearLayout>(R.id.quizOptionsContainer)
        val explanation = findViewById<TextView>(R.id.quizExplanation)
        val nextBtn = findViewById<TextView>(R.id.quizNextBtn)
        val topic = q.topic

        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as? TextView ?: continue
            child.isClickable = false
            if (i == q.correctIndex) {
                child.setTextColor(getTopicColor(topic))
                child.setBackgroundResource(R.drawable.chip_selected)
            } else if (i == selected) {
                child.setTextColor(0xFFEF4444.toInt())
            }
        }

        val prefs = getSharedPreferences("quiz_prefs", MODE_PRIVATE)

        if (selected == q.correctIndex) {
            score++
            streak++
            explanation.text = "✅ ${q.explanation}"
            explanation.setTextColor(0xFF10B981.toInt())

            val timesCorrect = prefs.getInt("quiz_stats_${topic}_${globalIdx}_correct", 0)
            prefs.edit().putInt("quiz_stats_${topic}_${globalIdx}_correct", timesCorrect + 1).apply()

            if (currentMode == QuizMode.SPACED) {
                val curStreak = prefs.getInt("quiz_spaced_${topic}_${globalIdx}_streak", 0)
                prefs.edit().putInt("quiz_spaced_${topic}_${globalIdx}_streak", curStreak + 1).apply()
            }
        } else {
            streak = 0
            answeredWrong.add(globalIdx to topic)
            explanation.text = "❌ ${q.explanation}"
            explanation.setTextColor(0xFFEF4444.toInt())

            if (currentMode == QuizMode.SPACED) {
                val curWrong = prefs.getInt("quiz_spaced_${topic}_${globalIdx}_wrong", 0)
                prefs.edit().putInt("quiz_spaced_${topic}_${globalIdx}_wrong", curWrong + 1)
                    .putInt("quiz_spaced_${topic}_${globalIdx}_streak", 0).apply()
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

        val totalQuestions = questionOrder.size
        val answersByTopic = mutableMapOf<String, Pair<Int, Int>>()
        for ((globalIdx, topic) in questionOrder) {
            val (wrong, correct) = answersByTopic.getOrDefault(topic, 0 to 0)
            if (globalIdx to topic in answeredWrong) {
                answersByTopic[topic] = wrong + 1 to correct
            } else {
                answersByTopic[topic] = wrong to correct + 1
            }
        }

        if (!isDailyChallenge) {
            val topicGroups = questionOrder.groupBy { it.second }
            for ((topic, items) in topicGroups) {
                val existingWrong = prefs.getString("wrong_${topic}", "")?.split(",")?.mapNotNull { it.toIntOrNull() }?.toMutableSet() ?: mutableSetOf()
                val existingMastered = prefs.getString("mastered_${topic}", "")?.split(",")?.mapNotNull { it.toIntOrNull() }?.toMutableSet() ?: mutableSetOf()
                val localTopicQs = getQuestionsForTopics(setOf(topic))
                val baseIdx = allQuestions.indexOf(localTopicQs.firstOrNull() ?: continue)
                for ((globalIdx, _) in items) {
                    val localIdx = globalIdx - baseIdx
                    if (globalIdx to topic in answeredWrong) {
                        existingWrong.add(localIdx)
                        existingMastered.remove(localIdx)
                    } else {
                        existingWrong.remove(localIdx)
                        existingMastered.add(localIdx)
                    }
                }
                prefs.edit()
                    .putString("wrong_${topic}", existingWrong.joinToString(","))
                    .putString("mastered_${topic}", existingMastered.joinToString(","))
                    .apply()

                val bestKey = "best_${topic}"
                val topicScore = items.count { (globalIdx, t) -> globalIdx to t !in answeredWrong }
                val prevBest = prefs.getInt(bestKey, 0)
                if (topicScore > prevBest) prefs.edit().putInt(bestKey, topicScore).apply()
            }
        }

        // Save last quiz date for streak
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        prefs.edit().putString("last_quiz_date", today).apply()

        // Update daily streak
        val lastStreakDate = prefs.getString("last_streak_date", "") ?: ""
        var currentStreak = prefs.getInt("daily_streak", 0)
        if (lastStreakDate != today) {
            val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() - 86400000))
            currentStreak = if (lastStreakDate == yesterday) currentStreak + 1 else 1
            prefs.edit().putInt("daily_streak", currentStreak).putString("last_streak_date", today).apply()
        }

        findViewById<View>(R.id.quizResultSection).visibility = View.VISIBLE
        findViewById<TextView>(R.id.quizFinalScore).text = "آپ نے $score/$totalQuestions درست جواب دیے"
        if (currentMode == QuizMode.ENDLESS && endlessCycle > 0) {
            findViewById<TextView>(R.id.quizFinalScore).append(" ($endlessCycle چکر)")
        }

        val accuracy = if (totalQuestions > 0) (score * 100 / totalQuestions) else 0
        findViewById<TextView>(R.id.quizAccuracy).text = "درستگی: $accuracy%  |  🔥 تسلسل: $currentStreak دن"

        val bestScores = selectedTopics.mapNotNull { t ->
            val b = prefs.getInt("best_${t}", 0)
            if (b > 0) t to b else null
        }
        if (bestScores.isNotEmpty()) {
            val bestText = bestScores.joinToString(" | ") { (t, s) ->
                val labels = mapOf("quran" to "قرآن", "hadith" to "حدیث", "fiqh" to "فقہ", "seerah" to "سیرت", "women" to "خواتین", "sahaba" to "صحابہ", "history" to "تاریخ")
                "${labels[t] ?: t}: $s"
            }
            findViewById<TextView>(R.id.quizBestScore).text = "🏆 بہترین سکور: $bestText"
        } else {
            findViewById<TextView>(R.id.quizBestScore).text = ""
        }

        // Check achievements
        checkAchievements(prefs, accuracy)

        // Weak area
        val topicAccuracy = answersByTopic.mapValues { (topic, pair) ->
            val total = pair.first + pair.second
            if (total > 0) (pair.second * 100 / total) else 0
        }
        val weakest = topicAccuracy.minByOrNull { it.value }
        if (weakest != null && weakest.value < 70) {
            val labels = mapOf("quran" to "قرآن", "hadith" to "حدیث", "fiqh" to "فقہ", "seerah" to "سیرت", "women" to "خواتین", "sahaba" to "صحابہ", "history" to "تاریخ")
            findViewById<TextView>(R.id.quizWeakArea).text = "⚠️ توجہ طلب: ${labels[weakest.key] ?: weakest.key} میں بہتری لائیں (${weakest.value}%)"
            findViewById<TextView>(R.id.quizWeakArea).visibility = View.VISIBLE
        } else {
            findViewById<TextView>(R.id.quizWeakArea).visibility = View.GONE
        }

        val reviewContainer = findViewById<LinearLayout>(R.id.quizReviewContainer)
        reviewContainer.removeAllViews()
        if (answeredWrong.isNotEmpty()) {
            val heading = TextView(this).apply {
                text = "غلط جوابات کا جائزہ:"
                textSize = 14f
                setTextColor(0xFFEF4444.toInt())
                setPadding(0, 8, 0, 4)
            }
            reviewContainer.addView(heading)
            for ((globalIdx, topic) in answeredWrong) {
                val q = allQuestions[globalIdx]
                val review = TextView(this).apply {
                    text = "${q.question}\n✅ ${q.options[q.correctIndex]}\n${q.explanation}"
                    textSize = 12.5f
                    setTextColor(0xFFE0DDD8.toInt())
                    setPadding(0, 4, 0, 8)
                }
                reviewContainer.addView(review)
            }
        }

        findViewById<TextView>(R.id.quizRestartBtn).setOnClickListener {
            if (isDailyChallenge) startDailyChallenge() else startQuiz()
        }
        findViewById<TextView>(R.id.quizSwitchTopicBtn).setOnClickListener {
            findViewById<View>(R.id.quizResultSection).visibility = View.GONE
        }
    }

    private fun checkAchievements(prefs: android.content.SharedPreferences, accuracy: Int) {
        val unlocked = mutableListOf<String>()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        // First quiz
        val quizCount = prefs.getInt("quiz_count", 0) + 1
        prefs.edit().putInt("quiz_count", quizCount).apply()
        if (quizCount == 1) unlocked.add("🏅 پہلا کوئز")
        if (quizCount == 10) unlocked.add("🏅 10 کوئزز")
        if (quizCount == 50) unlocked.add("🏅 50 کوئزز")

        // Perfect score
        if (accuracy == 100) unlocked.add("🌟 مکمل درستگی")

        // Streak
        val streak = prefs.getInt("daily_streak", 0)
        if (streak >= 3) unlocked.add("🔥 3 دن کا تسلسل")
        if (streak >= 7) unlocked.add("🔥 7 دن کا تسلسل")
        if (streak >= 30) unlocked.add("🔥 30 دن کا تسلسل")

        val container = findViewById<LinearLayout>(R.id.quizAchievementsContainer)
        container.removeAllViews()
        if (unlocked.isNotEmpty()) {
            for (badge in unlocked.take(4)) {
                val badgeTv = TextView(this).apply {
                    text = badge
                    textSize = 11f
                    setTextColor(0xFFD4AF37.toInt())
                    setBackgroundResource(R.drawable.chip_selected)
                    setPadding(8, 4, 8, 4)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 6, 0) }
                }
                container.addView(badgeTv)
            }
        }
    }

    private fun setupTopicChips() {
        for ((i, id) in topicChipIds.withIndex()) {
            findViewById<TextView>(id).setOnClickListener {
                val topic = allTopicIds[i]
                if (!hasAnswered || findViewById<View>(R.id.quizResultSection).visibility == View.VISIBLE) {
                    toggleTopic(id, topic)
                }
            }
        }
    }

    private fun toggleTopic(selectedId: Int, topic: String) {
        if (selectedTopics.contains(topic)) {
            if (selectedTopics.size > 1) {
                selectedTopics.remove(topic)
            }
        } else {
            selectedTopics.add(topic)
        }
        updateTopicChips()
        if (!isDailyChallenge) startQuiz()
    }

    private fun updateTopicChips() {
        for ((i, id) in topicChipIds.withIndex()) {
            val tv = findViewById<TextView>(id)
            val topic = allTopicIds[i]
            val isSelected = topic in selectedTopics
            if (isSelected) {
                tv.setTextColor(getTopicColor(topic))
                tv.setBackgroundResource(R.drawable.chip_selected)
            } else {
                tv.setTextColor(0xFF8B7355.toInt())
                tv.setBackgroundResource(R.drawable.chip_unselected)
            }
        }
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
        if (!isDailyChallenge) startQuiz()
    }

    private fun setupSessionLengthChips() {
        val lengths = listOf(
            R.id.len10 to 10, R.id.len15 to 15, R.id.len20 to 20,
            R.id.len25 to 25, R.id.lenAll to 0
        )
        for ((id, len) in lengths) {
            findViewById<TextView>(id).setOnClickListener {
                selectSessionLength(id, len)
            }
        }
        // Default: select 10
        selectSessionLength(R.id.len10, 10)
    }

    private fun selectSessionLength(selectedId: Int, length: Int) {
        val ids = listOf(R.id.len10, R.id.len15, R.id.len20, R.id.len25, R.id.lenAll)
        for (id in ids) {
            val tv = findViewById<TextView>(id)
            val isSelected = id == selectedId
            tv.setTextColor(ContextCompat.getColor(this, if (isSelected) R.color.primaryGold else R.color.bronze))
            tv.setBackgroundResource(if (isSelected) R.drawable.chip_selected else R.drawable.chip_unselected)
        }
        sessionLength = length
        if (!isDailyChallenge && selectedTopics.isNotEmpty()) startQuiz()
    }

    private fun setupBookmarkButton() {
        findViewById<TextView>(R.id.quizBookmarkBtn).setOnClickListener {
            if (currentIndex < questionOrder.size) {
                val (globalIdx, topic) = questionOrder[currentIndex]
                toggleBookmark(globalIdx, topic)
            }
        }
    }

    private fun toggleBookmark(globalIdx: Int, topic: String) {
        val prefs = getSharedPreferences("quiz_prefs", MODE_PRIVATE)
        val key = "bookmarked_${topic}_${globalIdx}"
        val isBookmarked = prefs.getBoolean(key, false)
        prefs.edit().putBoolean(key, !isBookmarked).apply()
        updateBookmarkButton(globalIdx)
    }

    private fun updateBookmarkButton(globalIdx: Int) {
        val q = allQuestions.getOrNull(globalIdx) ?: return
        val prefs = getSharedPreferences("quiz_prefs", MODE_PRIVATE)
        val key = "bookmarked_${q.topic}_${globalIdx}"
        val isBookmarked = prefs.getBoolean(key, false)
        findViewById<TextView>(R.id.quizBookmarkBtn).text = if (isBookmarked) "★" else "☆"
    }
}
