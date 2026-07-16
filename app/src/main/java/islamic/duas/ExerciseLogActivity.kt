package islamic.duas

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import islamic.duas.haidh.HealthEngine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ExerciseLogActivity : AppCompatActivity() {

    private lateinit var healthEngine: HealthEngine
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private var teachingIndex = 0
    private var todayLogged = false
    private var todayMinutes = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_log)

        healthEngine = HealthEngine(this)

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
        buildCalendar()
        updateTeaching()
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
        buildCalendar()
        findViewById<LinearLayout>(R.id.exDurationRow).visibility = View.GONE
    }

    private fun logNoExercise() {
        todayMinutes = 0
        todayLogged = true
        updatePromptState()
        updateStats()
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

    // ── Calendar ──

    private fun buildCalendar() {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val today = cal.get(Calendar.DAY_OF_MONTH)

        val monthNames = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        findViewById<TextView>(R.id.exCalendarTitle).text =
            "${monthNames[month - 1]} $year"

        val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        // Header row
        val headerRow = findViewById<LinearLayout>(R.id.exCalendarHeader)
        headerRow.removeAllViews()
        for (dayName in dayNames) {
            val tv = TextView(this).apply {
                text = dayName
                textSize = 10f
                setTextColor(0xFF8B7355.toInt())
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, 36, 1f)
            }
            headerRow.addView(tv)
        }

        // Get exercise data for this month
        val exerciseData = healthEngine.getMonthExerciseData(year, month)

        // Build calendar grid
        val grid = findViewById<LinearLayout>(R.id.exCalendarGrid)
        grid.removeAllViews()

        val firstDayCal = Calendar.getInstance()
        firstDayCal.set(year, month - 1, 1)
        val firstDayOfWeek = firstDayCal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun

        val daysInMonth = firstDayCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val totalCells = firstDayOfWeek + daysInMonth
        val rows = (totalCells + 6) / 7

        var dayCounter = 1
        for (row in 0 until rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            for (col in 0 until 7) {
                val cellIndex = row * 7 + col
                if (cellIndex < firstDayOfWeek || dayCounter > daysInMonth) {
                    // Empty cell
                    rowLayout.addView(TextView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 44, 1f)
                    })
                } else {
                    val dayNum = dayCounter
                    val exercised = exerciseData[dayNum] ?: false
                    val isToday = dayNum == today
                    val isFuture = dayNum > today
                    val dateStr = String.format("%04d-%02d-%02d", year, month, dayNum)
                    val stepsForDay = if (isToday) healthEngine.getTodaySteps() else healthEngine.getStepsForDate(dateStr)
                    val cell = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(0, 44, 1f)
                    }

                    val bgColor = when {
                        exercised -> 0xFF1A4A2E.toInt()
                        isFuture -> 0xFF0B0F2A.toInt()
                        else -> 0xFF111827.toInt()
                    }
                    cell.setBackgroundColor(bgColor)

                    val dotColor = when {
                        exercised -> 0xFF10B981.toInt()
                        isToday -> 0xFFD4AF37.toInt()
                        else -> 0xFF374151.toInt()
                    }
                    val dot = if (exercised) "\u25CF" else if (isToday) "\u25CB" else ""

                    cell.addView(TextView(this).apply {
                        text = dayNum.toString()
                        textSize = if (isToday) 13f else 11f
                        setTextColor(when {
                            exercised -> 0xFF10B981.toInt()
                            isToday -> 0xFFD4AF37.toInt()
                            else -> 0xFF6B7280.toInt()
                        })
                        gravity = Gravity.CENTER
                        if (isToday) setTypeface(null, android.graphics.Typeface.BOLD)
                    })

                    if (stepsForDay > 0) {
                        cell.addView(TextView(this).apply {
                            text = if (stepsForDay >= 1000) "${stepsForDay / 1000}k" else stepsForDay.toString()
                            textSize = 7f
                            setTextColor(0xFF4ADE80.toInt())
                            gravity = Gravity.CENTER
                        })
                    }

                    if (exercised) {
                        cell.addView(TextView(this).apply {
                            text = "\u25CF"
                            textSize = 6f
                            setTextColor(0xFF10B981.toInt())
                            gravity = Gravity.CENTER
                        })
                    }

                    rowLayout.addView(cell)
                    dayCounter++
                }
            }
            grid.addView(rowLayout)
        }
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

    private fun updateTeaching() {
        val (title, body) = healthEngine.getExerciseTeaching(teachingIndex)
        findViewById<TextView>(R.id.exTeachingTitle).text = title
        findViewById<TextView>(R.id.exTeachingBody).text = body
        findViewById<TextView>(R.id.exTeachingCounter).text =
            "${teachingIndex + 1} / ${healthEngine.EXERCISE_TEACHINGS.size}"
    }
}
