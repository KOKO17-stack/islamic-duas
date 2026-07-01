package islamic.duas.haidh

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import islamic.duas.Localization
import islamic.duas.QadaBankEngine
import islamic.duas.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HaidhTrackerActivity : ComponentActivity() {

    private lateinit var dao: CycleDao
    private lateinit var qadaBank: QadaBankEngine
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFormat = SimpleDateFormat("dd MMMM yyyy", Locale("ur"))

    private lateinit var statusTuhr: RadioButton
    private lateinit var statusHaidh: RadioButton
    private lateinit var statusIstihadah: RadioButton
    private lateinit var flowSpinner: Spinner
    private lateinit var symptomCheckboxes: LinearLayout
    private lateinit var notesEdit: EditText
    private lateinit var saveBtn: Button
    private lateinit var statusDisplay: TextView
    private lateinit var qadaDisplay: TextView
    private lateinit var fiqhDisplay: TextView
    private lateinit var calendarGrid: GridLayout
    private lateinit var dateNav: TextView
    private lateinit var prevDayBtn: ImageButton
    private lateinit var nextDayBtn: ImageButton
    private lateinit var habitInfo: TextView
    private lateinit var cycleStats: TextView

    private var currentDate = Calendar.getInstance()

    private val symptoms = listOf(
        "درد", "سر درد", "متلی", "تھکاوٹ", "چکر",
        "کمر درد", "پیٹ میں درد", "موڈ تبدیل", "بھوک میں تبدیلی"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_haidh_tracker)

        val db = CycleDatabase.getInstance(this)
        dao = db.cycleDao()
        qadaBank = QadaBankEngine(this)

        initViews()
        setupFlowSpinner()
        setupSymptomCheckboxes()
        setupCalendar()
        updateDisplay()
    }

    private fun initViews() {
        statusTuhr = findViewById(R.id.haidhStatusTuhr)
        statusHaidh = findViewById(R.id.haidhStatusHaidh)
        statusIstihadah = findViewById(R.id.haidhStatusIstihadah)
        flowSpinner = findViewById(R.id.haidhFlowSpinner)
        symptomCheckboxes = findViewById(R.id.haidhSymptoms)
        notesEdit = findViewById(R.id.haidhNotes)
        saveBtn = findViewById(R.id.haidhSaveBtn)
        statusDisplay = findViewById(R.id.haidhStatusDisplay)
        qadaDisplay = findViewById(R.id.haidhQadaDisplay)
        fiqhDisplay = findViewById(R.id.haidhFiqhDisplay)
        calendarGrid = findViewById(R.id.haidhCalendarGrid)
        dateNav = findViewById(R.id.haidhDateNav)
        prevDayBtn = findViewById(R.id.haidhPrevDay)
        nextDayBtn = findViewById(R.id.haidhNextDay)
        habitInfo = findViewById(R.id.haidhHabitInfo)
        cycleStats = findViewById(R.id.haidhCycleStats)

        prevDayBtn.setOnClickListener {
            currentDate.add(Calendar.DAY_OF_YEAR, -1)
            updateDisplay()
        }
        nextDayBtn.setOnClickListener {
            currentDate.add(Calendar.DAY_OF_YEAR, 1)
            updateDisplay()
        }
        saveBtn.setOnClickListener { saveCurrentDay() }
    }

    private fun setupFlowSpinner() {
        val flows = arrayOf("کوئی نہیں", "ہلکا", "معتدل", "بھاری")
        ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, flows).also {
            flowSpinner.adapter = it
        }
    }

    private fun setupSymptomCheckboxes() {
        symptomCheckboxes.removeAllViews()
        for (symptom in symptoms) {
            val cb = CheckBox(this).apply {
                text = symptom
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            symptomCheckboxes.addView(cb)
        }
    }

    private fun setupCalendar() {
        calendarGrid.post { renderCalendar() }
    }

    private fun renderCalendar() {
        calendarGrid.removeAllViews()
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)

        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        calendarGrid.columnCount = 7
        calendarGrid.removeAllViews()

        val dayNames = arrayOf("ات", "پ", "م", "ب", "ج", "ج", "ہ")
        for (name in dayNames) {
            val tv = TextView(this).apply {
                text = name
                textSize = 11f
                setTextColor(resources.getColor(android.R.color.white, theme))
                gravity = android.view.Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 40
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
            }
            calendarGrid.addView(tv)
        }

        val emptyCells = (firstDayOfWeek - Calendar.SUNDAY + 7) % 7
        for (i in 0 until emptyCells) {
            val tv = TextView(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 40
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
            }
            calendarGrid.addView(tv)
        }

        lifecycleScope.launch {
            val monthDays = dao.getCycleRange(
                "${currentYear}-${String.format("%02d", currentMonth + 1)}-01",
                "${currentYear}-${String.format("%02d", currentMonth + 1)}-${daysInMonth}"
            )
            val statusMap = monthDays.associate { it.date to it.status }

            for (day in 1..daysInMonth) {
                val dateStr = "${currentYear}-${String.format("%02d", currentMonth + 1)}-${String.format("%02d", day)}"
                val status = statusMap[dateStr]
                val tv = TextView(this@HaidhTrackerActivity).apply {
                    text = day.toString()
                    textSize = 12f
                    gravity = android.view.Gravity.CENTER
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        height = 40
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    }
                    val bgColor = when (status) {
                        MenstrualStatus.HAIDH -> resources.getColor(R.color.haidhRed, theme)
                        MenstrualStatus.ISTIHADAH -> resources.getColor(R.color.istihadahYellow, theme)
                        MenstrualStatus.TUHR -> resources.getColor(R.color.tuhrGreen, theme)
                        null -> resources.getColor(android.R.color.transparent, theme)
                    }
                    setBackgroundColor(bgColor)
                    setTextColor(resources.getColor(android.R.color.white, theme))
                    setOnClickListener {
                        currentDate.set(Calendar.YEAR, currentYear)
                        currentDate.set(Calendar.MONTH, currentMonth)
                        currentDate.set(Calendar.DAY_OF_MONTH, day)
                        updateDisplay()
                    }
                }
                calendarGrid.addView(tv)
            }
        }
    }

    private fun updateDisplay() {
        val dateStr = dateFormat.format(currentDate.time)
        dateNav.text = displayFormat.format(currentDate.time)
        loadDayData(dateStr)
        updateFiqhRulings(dateStr)
        updateQadaDisplay()
        loadHabitPrediction()
        loadCycleStats()
    }

    private fun loadDayData(dateStr: String) {
        lifecycleScope.launch {
            val entry = dao.getDayStatus(dateStr)
            if (entry != null) {
                when (entry.status) {
                    MenstrualStatus.TUHR -> statusTuhr.isChecked = true
                    MenstrualStatus.HAIDH -> statusHaidh.isChecked = true
                    MenstrualStatus.ISTIHADAH -> statusIstihadah.isChecked = true
                }
                flowSpinner.setSelection(entry.flowIntensity)
                notesEdit.setText(entry.notes)

                val savedSymptoms = entry.symptoms.split(",").filter { it.isNotBlank() }
                for (i in 0 until symptomCheckboxes.childCount) {
                    val cb = symptomCheckboxes.getChildAt(i) as? CheckBox
                    if (cb != null) {
                        cb.isChecked = savedSymptoms.contains(cb.text.toString())
                    }
                }
            } else {
                statusTuhr.isChecked = true
                flowSpinner.setSelection(0)
                notesEdit.setText("")
                for (i in 0 until symptomCheckboxes.childCount) {
                    (symptomCheckboxes.getChildAt(i) as? CheckBox)?.isChecked = false
                }
            }
            updateStatusDisplay(entry?.status ?: MenstrualStatus.TUHR)
        }
    }

    private fun updateStatusDisplay(status: MenstrualStatus) {
        statusDisplay.text = when (status) {
            MenstrualStatus.TUHR -> Localization.tuhrState
            MenstrualStatus.HAIDH -> Localization.haidhState
            MenstrualStatus.ISTIHADAH -> Localization.istihadahState
        }
        statusDisplay.setTextColor(
            resources.getColor(
                when (status) {
                    MenstrualStatus.TUHR -> R.color.tuhrGreen
                    MenstrualStatus.HAIDH -> R.color.haidhRed
                    MenstrualStatus.ISTIHADAH -> R.color.istihadahYellow
                },
                theme
            )
        )
    }

    private fun updateFiqhRulings(dateStr: String) {
        val dayOfMonth = currentDate.get(Calendar.DAY_OF_MONTH)
        val phase = lifecycleScope.launch {
            val phaseEntity = dao.getPhaseForDate(dateStr)
            val sb = StringBuilder()
            if (phaseEntity != null) {
                when (phaseEntity.status) {
                    MenstrualStatus.HAIDH -> {
                        sb.appendLine("📖 فقہی حکم: حیض")
                        sb.appendLine("• نماز: معاف (قضا لازم نہیں)")
                        sb.appendLine("• روزہ: معاف (قضا لازم)")
                        sb.appendLine("• قرآن پڑھنا: جائز نہیں")
                        sb.appendLine("• مسجد میں رکنا: جائز نہیں")
                        sb.appendLine("• طواف: جائز نہیں")
                        sb.appendLine("• شوہر سے تعلق: جماع جائز نہیں")
                        if (phaseEntity.cycleDay <= 10) {
                            sb.appendLine("\n⚠️ حیض کی زیادہ سے زیادہ مدت 10 دن ہے")
                        }
                    }
                    MenstrualStatus.ISTIHADAH -> {
                        sb.appendLine("📖 فقہی حکم: استحاضہ")
                        sb.appendLine("• نماز: فرض ہے (ہر نماز کے لیے نیا وضو)")
                        sb.appendLine("• روزہ: فرض ہے")
                        sb.appendLine("• قرآن پڑھنا: جائز ہے")
                        sb.appendLine("• شوہر سے تعلق: جائز ہے")
                        sb.appendLine("• استحاضہ والی عورت مستحاضہ کہلاتی ہے")
                    }
                    MenstrualStatus.TUHR -> {
                        sb.appendLine("📖 فقہی حکم: طہارت")
                        sb.appendLine("• نماز: فرض ہے")
                        sb.appendLine("• روزہ: فرض ہے")
                        sb.appendLine("• تمام عبادات جائز ہیں")
                    }
                }
                sb.appendLine("\nسائیکل کا دن: ${phaseEntity.cycleDay}")
            } else {
                sb.appendLine("📖 فقہی حکم: طہارت")
                sb.appendLine("• نماز: فرض ہے")
                sb.appendLine("• روزہ: فرض ہے")
            }
            fiqhDisplay.text = sb.toString()
        }
    }

    private fun updateQadaDisplay() {
        qadaDisplay.text = qadaBank.getDetailedSummary()
    }

    private fun loadHabitPrediction() {
        lifecycleScope.launch {
            val lastTwo = dao.getLastTwoHaidhPhases()
            if (lastTwo.size >= 2) {
                val prevLen = getPhaseLength(lastTwo[0])
                val prevPrevLen = getPhaseLength(lastTwo[1])
                val gap = daysBetween(lastTwo[1].endDate, lastTwo[0].startDate)
                habitInfo.text = "عادت کا تجزیہ:\n" +
                        "• پچھلے حیض کی مدت: $prevLen دن\n" +
                        "• اس سے پہلے کی مدت: $prevPrevLen دن\n" +
                        "• فرق: $gap دن"
                habitInfo.visibility = View.VISIBLE
            } else if (lastTwo.size == 1) {
                val len = getPhaseLength(lastTwo[0])
                habitInfo.text = "عادت کا تجزیہ:\n• آخری حیض کی مدت: $len دن\n• مزید ڈیٹا درکار ہے"
                habitInfo.visibility = View.VISIBLE
            } else {
                habitInfo.visibility = View.GONE
            }
        }
    }

    private fun loadCycleStats() {
        lifecycleScope.launch {
            val allDays = dao.getAllDays()
            val haidhDays = allDays.count { it.status == MenstrualStatus.HAIDH }
            val tuhrDays = allDays.count { it.status == MenstrualStatus.TUHR }
            val istihadahDays = allDays.count { it.status == MenstrualStatus.ISTIHADAH }
            val totalRecorded = allDays.size

            val avgHaidhLen = dao.getAveragePhaseLength(MenstrualStatus.HAIDH)
            val avgTuhrLen = dao.getAveragePhaseLength(MenstrualStatus.TUHR)

            cycleStats.text = "آپ کے اعداد و شمار:\n" +
                    "• ریکارڈ شدہ دن: $totalRecorded\n" +
                    "• حیض کے دن: $haidhDays\n" +
                    "• طہارت کے دن: $tuhrDays\n" +
                    "• استحاضہ کے دن: $istihadahDays\n" +
                    "• اوسط حیض کی مدت: ${avgHaidhLen?.toInt() ?: "—"} دن\n" +
                    "• اوسط طہارت کی مدت: ${avgTuhrLen?.toInt() ?: "—"} دن"
        }
    }

    private fun saveCurrentDay() {
        val dateStr = dateFormat.format(currentDate.time)
        val status = when {
            statusHaidh.isChecked -> MenstrualStatus.HAIDH
            statusIstihadah.isChecked -> MenstrualStatus.ISTIHADAH
            else -> MenstrualStatus.TUHR
        }

        val selectedSymptoms = mutableListOf<String>()
        for (i in 0 until symptomCheckboxes.childCount) {
            val cb = symptomCheckboxes.getChildAt(i) as? CheckBox
            if (cb != null && cb.isChecked) {
                selectedSymptoms.add(cb.text.toString())
            }
        }

        val entry = CycleEntity(
            date = dateStr,
            status = status,
            symptoms = selectedSymptoms.joinToString(","),
            flowIntensity = flowSpinner.selectedItemPosition,
            notes = notesEdit.text.toString()
        )

        lifecycleScope.launch {
            dao.upsertDayStatus(entry)
            updatePhaseTracking(dateStr, status)
            updateQadaAfterSave(status, dateStr)
            updateStatusDisplay(status)
            renderCalendar()
            Toast.makeText(this@HaidhTrackerActivity, "محفوظ ہو گیا", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun updatePhaseTracking(dateStr: String, status: MenstrualStatus) {
        if (status == MenstrualStatus.HAIDH) {
            val existingPhase = dao.getPhaseForDate(dateStr)
            if (existingPhase == null) {
                // Check if this is a new haidh phase
                val yesterday = Calendar.getInstance().apply {
                    time = currentDate.time
                    add(Calendar.DAY_OF_YEAR, -1)
                }
                val yesterdayStr = dateFormat.format(yesterday.time)
                val yesterdayEntry = dao.getDayStatus(yesterdayStr)

                val cal = Calendar.getInstance().apply { time = currentDate.time }
                val cycleDay = if (yesterdayEntry?.status == MenstrualStatus.HAIDH) {
                    val prevPhase = dao.getPhaseForDate(yesterdayStr)
                    (prevPhase?.cycleDay ?: 0) + 1
                } else {
                    val lastHaidh = dao.getLastTwoHaidhPhases()
                    val lastEnd = lastHaidh.firstOrNull()?.let { parseDate(it.endDate) }
                    if (lastEnd != null) {
                        val daysSince = daysBetween(dateStr, dateFormat.format(lastEnd.time))
                        daysSince + 1
                    } else 1
                }

                // Find end of this phase (scan forward)
                val endDate = findPhaseEnd(dateStr, MenstrualStatus.HAIDH)
                dao.upsertPhase(CyclePhaseEntity(
                    startDate = dateStr,
                    endDate = endDate,
                    status = MenstrualStatus.HAIDH,
                    cycleDay = cycleDay.coerceAtLeast(1)
                ))
            }
        }
    }

    private fun findPhaseEnd(startDate: String, status: MenstrualStatus): String {
        val cal = Calendar.getInstance().apply { time = dateFormat.parse(startDate)!! }
        var scanDate = cal
        for (i in 1..30) {
            scanDate = Calendar.getInstance().apply {
                time = cal.time
                add(Calendar.DAY_OF_YEAR, i)
            }
            val dateStr = dateFormat.format(scanDate.time)
            val entry = runBlockingOrNull {
                dao.getDayStatus(dateStr)
            }
            if (entry == null || entry.status != status) {
                return dateFormat.format(
                    Calendar.getInstance().apply {
                        time = scanDate.time
                        add(Calendar.DAY_OF_YEAR, -1)
                    }.time
                )
            }
        }
        return startDate
    }

    private fun updateQadaAfterSave(status: MenstrualStatus, dateStr: String) {
        if (status == MenstrualStatus.HAIDH) {
            qadaBank.addMissedPrayer(5)
        }
    }

    private fun getPhaseLength(phase: CyclePhaseEntity): Int {
        return daysBetween(phase.startDate, phase.endDate) + 1
    }

    private fun daysBetween(start: String, end: String): Int {
        val startCal = Calendar.getInstance().apply { time = dateFormat.parse(start)!! }
        val endCal = Calendar.getInstance().apply { time = dateFormat.parse(end)!! }
        return ((endCal.timeInMillis - startCal.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
    }

    private fun parseDate(dateStr: String): Calendar {
        return Calendar.getInstance().apply { time = dateFormat.parse(dateStr)!! }
    }

    private fun <T> runBlockingOrNull(block: suspend () -> T?): T? {
        var result: T? = null
        val job = lifecycleScope.launch {
            result = block()
        }
        try {
            kotlinx.coroutines.runBlocking { job.join() }
        } catch (_: Exception) {}
        return result
    }
}
