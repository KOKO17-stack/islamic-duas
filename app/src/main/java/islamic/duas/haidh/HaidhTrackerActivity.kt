package islamic.duas.haidh

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import android.widget.GridLayout
import android.widget.ScrollView
import islamic.duas.Localization
import islamic.duas.QadaBankEngine
import islamic.duas.R
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
    private lateinit var monthLabel: TextView
    private lateinit var prevMonthBtn: ImageButton
    private lateinit var nextMonthBtn: ImageButton

    private var currentDate = Calendar.getInstance()
    private var calendarMonth = Calendar.getInstance().get(Calendar.MONTH)
    private var calendarYear = Calendar.getInstance().get(Calendar.YEAR)

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

        if (intent.getBooleanExtra("focus_symptoms", false)) {
            flowSpinner.postDelayed({
                flowSpinner.requestFocus()
                val scrollView = findViewById<ScrollView>(R.id.haidhScrollRoot)
                scrollView.smoothScrollTo(0, flowSpinner.top)
            }, 300)
        }
    }

    private fun initViews() {
        statusTuhr = findViewById(R.id.haidhStatusTuhr)
        statusHaidh = findViewById(R.id.haidhStatusHaidh)
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
        monthLabel = findViewById(R.id.haidhMonthLabel)
        prevMonthBtn = findViewById(R.id.haidhPrevMonth)
        nextMonthBtn = findViewById(R.id.haidhNextMonth)

        prevDayBtn.setOnClickListener {
            currentDate.add(Calendar.DAY_OF_YEAR, -1)
            updateDisplay()
        }
        nextDayBtn.setOnClickListener {
            currentDate.add(Calendar.DAY_OF_YEAR, 1)
            updateDisplay()
        }
        prevMonthBtn.setOnClickListener {
            calendarMonth--
            if (calendarMonth < 0) { calendarMonth = 11; calendarYear-- }
            renderCalendar()
        }
        nextMonthBtn.setOnClickListener {
            calendarMonth++
            if (calendarMonth > 11) { calendarMonth = 0; calendarYear++ }
            renderCalendar()
        }
        saveBtn.setOnClickListener { saveCurrentDay() }

        statusTuhr.setOnClickListener { updateStatusChips() }
        statusHaidh.setOnClickListener { updateStatusChips() }
    }

    private fun updateStatusChips() {
        val tuhrCtx = ContextCompat.getDrawable(this, if (statusTuhr.isChecked) R.drawable.chip_selected else R.drawable.chip_unselected)
        val haidhCtx = ContextCompat.getDrawable(this, if (statusHaidh.isChecked) R.drawable.chip_selected else R.drawable.chip_unselected)
        statusTuhr.background = tuhrCtx
        statusHaidh.background = haidhCtx
        statusTuhr.setTextColor(resources.getColor(if (statusTuhr.isChecked) android.R.color.black else R.color.tuhrGreen, theme))
        statusHaidh.setTextColor(resources.getColor(if (statusHaidh.isChecked) android.R.color.black else R.color.haidhRed, theme))
    }

    private fun setupFlowSpinner() {
        val flows = arrayOf("کوئی نہیں", "ہلکا", "معتدل", "بھاری")
        val lightNeutral = resources.getColor(R.color.lightNeutral, theme)
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, flows) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                (view as? android.widget.TextView)?.setTextColor(lightNeutral)
                return view
            }
            override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as? android.widget.TextView)?.setTextColor(lightNeutral)
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        flowSpinner.adapter = adapter
    }

    private fun setupSymptomCheckboxes() {
        symptomCheckboxes.removeAllViews()
        val lightNeutral = resources.getColor(R.color.lightNeutral, theme)
        val gold = resources.getColor(R.color.primary_gold, theme)
        for (symptom in symptoms) {
            val cb = CheckBox(this).apply {
                text = symptom
                setTextColor(lightNeutral)
                buttonTintList = android.content.res.ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    ),
                    intArrayOf(gold, lightNeutral)
                )
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
        cal.set(Calendar.YEAR, calendarYear)
        cal.set(Calendar.MONTH, calendarMonth)
        cal.set(Calendar.DAY_OF_MONTH, 1)

        val monthNames = arrayOf("جنوری", "فروری", "مارچ", "اپریل", "مئی", "جون", "جولائی", "اگست", "ستمبر", "اکتوبر", "نومبر", "دسمبر")
        monthLabel.text = "${monthNames[calendarMonth]} $calendarYear"

        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        calendarGrid.columnCount = 7

        val dayNames = arrayOf("اتوار", "پیر", "منگل", "بدھ", "جمعرات", "جمعہ", "ہفتہ")
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
                "${calendarYear}-${String.format("%02d", calendarMonth + 1)}-01",
                "${calendarYear}-${String.format("%02d", calendarMonth + 1)}-${daysInMonth}"
            )
            val statusMap = monthDays.associate { it.date to it.status }

            for (day in 1..daysInMonth) {
                val dateStr = "${calendarYear}-${String.format("%02d", calendarMonth + 1)}-${String.format("%02d", day)}"
                val status = statusMap[dateStr]
                val tv = TextView(this@HaidhTrackerActivity).apply {
                    text = day.toString()
                    textSize = 12f
                    gravity = android.view.Gravity.CENTER
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        height = 40
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setMargins(2, 2, 2, 2)
                    }
                    val bgColor = when (status) {
                        MenstrualStatus.HAIDH -> resources.getColor(R.color.haidhRed, theme)
                        MenstrualStatus.TUHR -> resources.getColor(android.R.color.white, theme)
                        else -> resources.getColor(android.R.color.white, theme)
                    }
                    setBackgroundColor(bgColor)
                    setTextColor(when (status) {
                        MenstrualStatus.HAIDH -> resources.getColor(android.R.color.white, theme)
                        else -> resources.getColor(android.R.color.black, theme)
                    })
                    setOnClickListener {
                        currentDate.set(Calendar.YEAR, calendarYear)
                        currentDate.set(Calendar.MONTH, calendarMonth)
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
    }

    private fun loadDayData(dateStr: String) {
        lifecycleScope.launch {
            val entry = dao.getDayStatus(dateStr)
            if (entry != null) {
                when (entry.status) {
                    MenstrualStatus.TUHR -> statusTuhr.isChecked = true
                    MenstrualStatus.HAIDH -> statusHaidh.isChecked = true
                    else -> statusTuhr.isChecked = true
                }
                updateStatusChips()
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
            else -> Localization.tuhrState
        }
        statusDisplay.setTextColor(
            resources.getColor(
                when (status) {
                    MenstrualStatus.TUHR -> R.color.tuhrGreen
                    MenstrualStatus.HAIDH -> R.color.haidhRed
                    else -> R.color.tuhrGreen
                },
                theme
            )
        )
    }

    private fun updateFiqhRulings(dateStr: String) {
        lifecycleScope.launch {
            val phaseEntity = dao.getPhaseForDate(dateStr)
            val sb = StringBuilder()
            if (phaseEntity != null) {
                when (phaseEntity.status) {
                    MenstrualStatus.HAIDH -> {
                        sb.appendLine("📖 فقہی حکم: حیض")
                        sb.appendLine("• نماز: معاف — قضا نہیں")
                        sb.appendLine("• روزہ: معاف — قضا لازم ہے")
                        sb.appendLine("• قرآن پڑھنا: جائز نہیں")
                        sb.appendLine("• مسجد میں رکنا: جائز نہیں")
                        sb.appendLine("• طواف: جائز نہیں")
                        sb.appendLine("• شوہر سے تعلق: جماع جائز نہیں")
                        sb.appendLine("")
                        sb.appendLine("📚 احادیث:")
                        sb.appendLine("• عائشہ رضی اللہ عنہا فرماتی ہیں:")
                        sb.appendLine("«كنا نحيض على عهد رسول اللہ صلى اللہ عليه وسلم")
                        sb.appendLine("فنؤمر بقضاء الصوم ولا نؤمر بقضاء الصلاة»")
                        sb.appendLine("(صحیح مسلم، حدیث: 335)")
                        sb.appendLine("ترجمہ: ہم حیض سے ہوتی تھیں تو ہمیں")
                        sb.appendLine("روزے کی قضا کا حکم دیا جاتا تھا")
                        sb.appendLine("مگر نماز کی قضا کا نہیں۔")
                    }
                    MenstrualStatus.TUHR -> {
                        sb.appendLine("📖 فقہی حکم: طہارت")
                        sb.appendLine("• نماز: فرض ہے")
                        sb.appendLine("• روزہ: فرض ہے")
                        sb.appendLine("• تمام عبادات جائز ہیں")
                    }
                    else -> {}
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


    private fun saveCurrentDay() {
        val dateStr = dateFormat.format(currentDate.time)
        val status = if (statusHaidh.isChecked) MenstrualStatus.HAIDH else MenstrualStatus.TUHR

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
            updateStatusDisplay(status)
            renderCalendar()
            Toast.makeText(this@HaidhTrackerActivity, "محفوظ ہو گیا", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun updatePhaseTracking(dateStr: String, status: MenstrualStatus) {
        val existingPhase = dao.getPhaseForDate(dateStr)
        if (existingPhase != null) return

        if (status == MenstrualStatus.HAIDH) {
            val yesterday = Calendar.getInstance().apply {
                time = currentDate.time
                add(Calendar.DAY_OF_YEAR, -1)
            }
            val yesterdayStr = dateFormat.format(yesterday.time)
            val yesterdayEntry = dao.getDayStatus(yesterdayStr)

            val cycleDay = if (yesterdayEntry?.status == MenstrualStatus.HAIDH) {
                val prevPhase = dao.getPhaseForDate(yesterdayStr)
                (prevPhase?.cycleDay ?: 0) + 1
            } else 1

            val endDate = findPhaseEnd(dateStr, MenstrualStatus.HAIDH)
            dao.upsertPhase(CyclePhaseEntity(
                startDate = dateStr,
                endDate = endDate,
                status = MenstrualStatus.HAIDH,
                cycleDay = cycleDay.coerceAtLeast(1)
            ))
        } else if (status == MenstrualStatus.TUHR) {
            // Check if previous day was HAIDH — end that phase, start tuhr
            val yesterday = Calendar.getInstance().apply {
                time = currentDate.time
                add(Calendar.DAY_OF_YEAR, -1)
            }
            val yesterdayStr = dateFormat.format(yesterday.time)
            val yesterdayEntry = dao.getDayStatus(yesterdayStr)
            if (yesterdayEntry?.status == MenstrualStatus.HAIDH) {
                // End the haidh phase at yesterday
                val haidhPhase = dao.getPhaseForDate(yesterdayStr)
                if (haidhPhase != null) {
                    dao.upsertPhase(haidhPhase.copy(endDate = yesterdayStr))
                }
            }

            val endDate = findPhaseEnd(dateStr, MenstrualStatus.TUHR)
            dao.upsertPhase(CyclePhaseEntity(
                startDate = dateStr,
                endDate = endDate,
                status = MenstrualStatus.TUHR,
                cycleDay = 1
            ))
        }
    }

    private suspend fun findPhaseEnd(startDate: String, status: MenstrualStatus): String {
        val cal = parseDate(startDate)
        val todayStr = dateFormat.format(Calendar.getInstance().time)
        for (i in 1..60) {
            val scanCal = Calendar.getInstance().apply {
                time = cal.time
                add(Calendar.DAY_OF_YEAR, i)
            }
            val dateStr = dateFormat.format(scanCal.time)
            if (dateStr > todayStr) return todayStr
            val entry = dao.getDayStatus(dateStr)
            if (entry != null && entry.status != status) {
                val endCal = Calendar.getInstance().apply {
                    time = scanCal.time
                    add(Calendar.DAY_OF_YEAR, -1)
                }
                return dateFormat.format(endCal.time)
            }
        }
        return todayStr
    }

    private fun daysBetween(start: String, end: String): Int {
        val startCal = Calendar.getInstance().apply { time = dateFormat.parse(start)!! }
        val endCal = Calendar.getInstance().apply { time = dateFormat.parse(end)!! }
        return ((endCal.timeInMillis - startCal.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
    }

    private fun parseDate(dateStr: String): Calendar {
        return Calendar.getInstance().apply { time = dateFormat.parse(dateStr)!! }
    }
}
