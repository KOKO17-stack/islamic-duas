package islamic.duas

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import islamic.duas.calendar.ExerciseTimelineBuilder
import islamic.duas.haidh.HealthEngine
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ExerciseLogActivity : AppCompatActivity() {

    private lateinit var healthEngine: HealthEngine

    private var teachingIndex = 0
    private var todayLogged = false
    private var todayMinutes = 0

    private lateinit var timelineBuilder: ExerciseTimelineBuilder
    private lateinit var timelineContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_log)

        healthEngine = HealthEngine(this)

        timelineContainer = findViewById(R.id.exerciseTimelineContainer)

        timelineBuilder = ExerciseTimelineBuilder(
            context = this,
            healthEngine = healthEngine,
            onSaveExercise = { minutes -> logExercise(minutes) }
        )

        findViewById<TextView>(R.id.exBackBtn).setOnClickListener { finish() }

        setupPrompt()
        setupDurationButtons()
        setupTeachingNav()
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        try {
            StepCounterService.start(this)
        } catch (_: Exception) {}
        refreshAll()
    }

    private fun refreshAll() {
        todayMinutes = healthEngine.getTodayExerciseMinutes()
        todayLogged = todayMinutes > 0

        updatePromptState()
        updateStats()
        updateStepsUI()
        buildTimeline()
        updateTeaching()
        update30DaySummary()
    }

    private fun buildTimeline() {
        timelineBuilder.build(timelineContainer, null, null, null)
    }

    // ── Prompt Section ──

    private fun setupPrompt() {
        findViewById<TextView>(R.id.exYesBtn).setOnClickListener {
            findViewById<LinearLayout>(R.id.exDurationRow).visibility = View.VISIBLE
            findViewById<TextView>(R.id.exNoBtn).background = ContextCompat.getDrawable(this, R.drawable.chip_unselected)
            findViewById<TextView>(R.id.exNoBtn).setTextColor(0xFFE8E6E1.toInt())
            findViewById<TextView>(R.id.exYesBtn).background = ContextCompat.getDrawable(this, R.drawable.chip_selected)
            findViewById<TextView>(R.id.exYesBtn).setTextColor(0xFF000000.toInt())
        }
        findViewById<TextView>(R.id.exNoBtn).setOnClickListener {
            logNoExercise()
        }
    }

    private fun setupDurationButtons() {
        val durations = listOf(
            R.id.exDur20 to 20,
            R.id.exDur40 to 40,
            R.id.exDur60 to 60,
            R.id.exDur80 to 80
        )
        for ((id, mins) in durations) {
            findViewById<TextView>(id).setOnClickListener {
                logExercise(mins)
            }
        }
    }

    private fun logExercise(minutes: Int) {
        healthEngine.recordExercise(minutes)
        todayMinutes = minutes
        todayLogged = true
        updatePromptState()
        updateStats()
        buildTimeline()
        update30DaySummary()
        findViewById<LinearLayout>(R.id.exDurationRow).visibility = View.GONE
    }

    private fun logNoExercise() {
        healthEngine.recordExercise(0)
        todayMinutes = 0
        todayLogged = true
        updatePromptState()
        updateStats()
        buildTimeline()
        update30DaySummary()
        findViewById<TextView>(R.id.exYesBtn).background = ContextCompat.getDrawable(this, R.drawable.chip_unselected)
        findViewById<TextView>(R.id.exYesBtn).setTextColor(0xFFE8E6E1.toInt())
        findViewById<TextView>(R.id.exNoBtn).background = ContextCompat.getDrawable(this, R.drawable.chip_selected)
        findViewById<TextView>(R.id.exNoBtn).setTextColor(0xFF000000.toInt())
    }

    private fun updatePromptState() {
        val title = findViewById<TextView>(R.id.exPromptTitle)
        val subtitle = findViewById<TextView>(R.id.exPromptSubtitle)
        val status = findViewById<TextView>(R.id.exLoggedStatus)
        val yesNoRow = findViewById<LinearLayout>(R.id.exYesNoRow)
        val durationRow = findViewById<LinearLayout>(R.id.exDurationRow)

        if (todayLogged) {
            if (todayMinutes > 0) {
                title.text = "✅ بہت خوب!"
                subtitle.text = "آج $todayMinutes منٹ ورزش کی"
                status.text = "$todayMinutes منٹ ریکارڈ — تبدیل کرنے کے لیے ہاں پر دبائیں"
                status.visibility = View.VISIBLE
            } else {
                title.text = "کوئی بات نہیں!"
                subtitle.text = "آرام بھی ضروری ہے۔ کل کوشش کریں۔"
                status.text = "نہیں کی کے طور پر ریکارڈ (اپ ڈیٹ کے لیے ہاں دبائیں)"
                status.visibility = View.VISIBLE
            }
            durationRow.visibility = View.GONE
        } else {
            title.text = "آج ورزش کی؟"
            subtitle.text = "نیچے اپنا انتخاب کریں"
            status.visibility = View.GONE
            durationRow.visibility = View.GONE
            findViewById<TextView>(R.id.exYesBtn).background = ContextCompat.getDrawable(this, R.drawable.chip_selected)
            findViewById<TextView>(R.id.exYesBtn).setTextColor(0xFF000000.toInt())
            findViewById<TextView>(R.id.exNoBtn).background = ContextCompat.getDrawable(this, R.drawable.chip_unselected)
            findViewById<TextView>(R.id.exNoBtn).setTextColor(0xFFE8E6E1.toInt())
        }
    }

    // ── Stats ──

    private fun updateStats() {
        val mins = healthEngine.getTodayExerciseMinutes()
        val streak = healthEngine.getExerciseStreak()
        val weekCount = healthEngine.getWeeklyExerciseDays()

        findViewById<TextView>(R.id.exTodayMins).text = if (mins > 0) "$mins min" else "--"
        findViewById<TextView>(R.id.exStreak).text = if (streak > 0) "$streak days" else "0 days"
        findViewById<TextView>(R.id.exWeekCount).text = "$weekCount/4"
    }

    // ── Steps ──

    private fun updateStepsUI() {
        val steps = healthEngine.getTodaySteps()
        val goal = healthEngine.getStepGoal()
        findViewById<TextView>(R.id.exStepsToday).text = steps.toString()
        findViewById<TextView>(R.id.exStepsGoal).text = "/ $goal"
        val progress = findViewById<ProgressBar>(R.id.exStepsProgress)
        progress.max = goal
        progress.progress = if (steps > goal) goal else steps
    }

    // ── Teachings ──

    private fun setupTeachingNav() {
        findViewById<TextView>(R.id.exTeachingPrev).setOnClickListener {
            teachingIndex = if (teachingIndex > 0) teachingIndex - 1
            else healthEngine.EXERCISE_TEACHINGS.size - 1
            updateTeaching()
        }
        findViewById<TextView>(R.id.exTeachingNext).setOnClickListener {
            teachingIndex = (teachingIndex + 1) % healthEngine.EXERCISE_TEACHINGS.size
            updateTeaching()
        }
    }

    private fun update30DaySummary() {
        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        var totalMins = 0
        var daysExercised = 0
        var bestStreak = 0
        var currentStreak = 0
        val today = LocalDate.now()

        for (i in 0 until 30) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(dateFormatter)
            val mins = healthEngine.getExerciseMinutesForDate(dateStr)
            totalMins += mins
            if (mins > 0) {
                daysExercised++
                currentStreak++
                if (currentStreak > bestStreak) bestStreak = currentStreak
            } else {
                currentStreak = 0
            }
        }

        findViewById<TextView>(R.id.ex30TotalMins).text = totalMins.toString()
        findViewById<TextView>(R.id.ex30Days).text = daysExercised.toString()
        findViewById<TextView>(R.id.ex30Streak).text = bestStreak.toString()
    }

    private fun updateTeaching() {
        val (title, body) = healthEngine.getExerciseTeaching(teachingIndex)
        findViewById<TextView>(R.id.exTeachingTitle).text = title
        findViewById<TextView>(R.id.exTeachingBody).text = body
        findViewById<TextView>(R.id.exTeachingCounter).text =
            "${teachingIndex + 1} / ${healthEngine.EXERCISE_TEACHINGS.size}"
    }
}
