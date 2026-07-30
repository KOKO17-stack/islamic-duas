package islamic.duas

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import androidx.activity.ComponentActivity

class GuidedSessionActivity : ComponentActivity() {

    private lateinit var sessionTitle: TextView
    private lateinit var sessionTimer: TextView
    private lateinit var sessionArabic: TextView
    private lateinit var sessionMeaning: TextView
    private lateinit var sessionReflection: TextView
    private lateinit var sessionStopBtn: TextView
    private lateinit var sessionNextBtn: TextView
    private lateinit var sessionStepProgress: TextView

    private var currentStep = 0
    private var timer: CountDownTimer? = null
    private var sessionTotalDuration = 0
    private var elapsedSeconds = 0
    private var selectedSession: GuidedSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.guided_session)

        sessionTitle = findViewById(R.id.sessionTitle)
        sessionTimer = findViewById(R.id.sessionTimer)
        sessionArabic = findViewById(R.id.sessionArabic)
        sessionMeaning = findViewById(R.id.sessionMeaning)
        sessionReflection = findViewById(R.id.sessionReflection)
        sessionStopBtn = findViewById(R.id.sessionStopBtn)
        sessionNextBtn = findViewById(R.id.sessionNextBtn)
        sessionStepProgress = findViewById(R.id.sessionStepProgress)

        val typeName = intent.getStringExtra("session_type") ?: SessionType.TFAKKUR.name
        val type = try { SessionType.valueOf(typeName) } catch (_: Exception) { SessionType.TFAKKUR }
        val engine = GuidedSessionsEngine()
        selectedSession = engine.getSession(type)

        selectedSession?.let { session ->
            sessionTitle.text = session.title
            sessionTotalDuration = session.totalDurationMinutes * 60
            showStep(0)
        }

        sessionNextBtn.setOnClickListener {
            selectedSession?.let { session ->
                if (currentStep < session.steps.size - 1) {
                    currentStep++
                    showStep(currentStep)
                } else {
                    finish()
                }
            }
        }

        sessionStopBtn.setOnClickListener {
            timer?.cancel()
            finish()
        }
    }

    private fun showStep(index: Int) {
        selectedSession?.let { session ->
            val step = session.steps[index]
            sessionArabic.text = step.arabic
            sessionMeaning.text = step.meaning
            sessionReflection.text = step.reflection
            sessionStepProgress.text = "مرحلہ ${index + 1}/${session.steps.size}"

            timer?.cancel()
            elapsedSeconds = 0
            timer = object : CountDownTimer((step.durationSeconds * 1000).toLong(), 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    elapsedSeconds++
                    val totalSec = session.steps.take(index).sumOf { it.durationSeconds } + elapsedSeconds
                    val remaining = sessionTotalDuration - totalSec
                    sessionTimer.text = formatTime(remaining.coerceAtLeast(0))
                }

                override fun onFinish() {
                    sessionTimer.text = "00:00"
                    if (currentStep < session.steps.size - 1) {
                        currentStep++
                        showStep(currentStep)
                    }
                }
            }.start()
        }
    }

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
