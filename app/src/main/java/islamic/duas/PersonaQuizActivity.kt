package islamic.duas

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class PersonaQuizActivity : ComponentActivity() {

    private lateinit var quiz: PersonaQuiz
    private lateinit var questionText: TextView
    private lateinit var optionsContainer: LinearLayout
    private lateinit var nextBtn: TextView
    private lateinit var progressText: TextView
    private lateinit var resultTitle: TextView
    private lateinit var resultAdvice: TextView
    private lateinit var userProfile: UserProfile

    private var currentQuestion = 0
    private val answers = mutableMapOf<Int, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.persona_quiz)

        quiz = PersonaQuiz()
        userProfile = UserProfile(this)
        questionText = findViewById(R.id.quizQuestionText)
        optionsContainer = findViewById(R.id.quizOptionsContainer)
        nextBtn = findViewById(R.id.quizNextBtn)
        progressText = findViewById(R.id.quizProgress)
        resultTitle = findViewById(R.id.quizResultTitle)
        resultAdvice = findViewById(R.id.quizResultAdvice)

        showQuestion(0)

        nextBtn.setOnClickListener {
            if (currentQuestion < quiz.questions.size - 1) {
                currentQuestion++
                showQuestion(currentQuestion)
            } else {
                finishQuiz()
            }
        }
    }

    private fun showQuestion(index: Int) {
        val q = quiz.questions[index]
        questionText.text = q.question
        progressText.text = "سوال ${index + 1}/${quiz.questions.size}"
        optionsContainer.removeAllViews()

        q.options.forEachIndexed { i, option ->
            val tv = TextView(this).apply {
                text = option.label
                textSize = 13f
                setTextColor(0xFFE0DDD8.toInt())
                setBackgroundResource(R.drawable.rounded_bg)
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                setPadding(16, 12, 16, 12)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 8) }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    answers[q.id] = i
                    highlightSelected(i)
                }
            }
            optionsContainer.addView(tv)
        }

        nextBtn.text = if (index < quiz.questions.size - 1) "اگلا سوال" else "مکمل کریں"
        resultTitle.visibility = android.view.View.GONE
        resultAdvice.visibility = android.view.View.GONE
    }

    private fun highlightSelected(selectedIndex: Int) {
        for (i in 0 until optionsContainer.childCount) {
            val child = optionsContainer.getChildAt(i)
            if (child is TextView) {
                if (i == selectedIndex) {
                    child.setTextColor(0xFFD4AF37.toInt())
                    child.setBackgroundResource(R.drawable.chip_selected)
                } else {
                    child.setTextColor(0xFFE0DDD8.toInt())
                    child.setBackgroundResource(R.drawable.rounded_bg)
                }
            }
        }
    }

    private fun finishQuiz() {
        if (answers.size < quiz.questions.size) return

        val result = quiz.calculateResult(answers)
        userProfile.savePersona(Persona(
            struggle = result.struggle,
            loveLanguage = result.loveLanguage,
            difficultyTime = result.difficultyTime,
            arabicLevel = result.arabicLevel,
            goal = result.goal
        ))
        userProfile.markOnboarded()

        questionText.visibility = android.view.View.GONE
        optionsContainer.visibility = android.view.View.GONE
        progressText.visibility = android.view.View.GONE
        nextBtn.text = "ایپ شروع کریں"
        resultTitle.visibility = android.view.View.VISIBLE
        resultAdvice.visibility = android.view.View.VISIBLE

        resultTitle.text = "آپ کی شخصیت: ${result.struggle.label}"
        resultAdvice.text = result.advice

        nextBtn.setOnClickListener {
            val intent = android.content.Intent(this, MainActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
