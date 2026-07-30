package islamic.duas.haidh

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import islamic.duas.R
import islamic.duas.calendar.ContinuousTimelineBuilder
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class HaidhTrackerActivity : ComponentActivity() {

    private val db by lazy { CycleDatabase.getInstance(this) }
    private val dao get() = db.cycleDao()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private lateinit var timelineGrid: LinearLayout
    private lateinit var timelineBuilder: ContinuousTimelineBuilder

    private var selectedDay: Int? = null
    private var selectedYear: Int? = null
    private var selectedMonth: Int? = null

    private val symptoms = listOf(
        "درد", "سر درد", "متلی", "تھکاوٹ", "چکر",
        "کمر درد", "پیٹ میں درد", "موڈ تبدیل", "بھوک میں تبدیلی"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_haidh_tracker)

        timelineGrid = findViewById(R.id.continuousCalendarGrid)

        timelineBuilder = ContinuousTimelineBuilder(
            context = this,
            daoProvider = { dao },
            onDayClick = { year, month, day ->
                selectedYear = year
                selectedMonth = month
                selectedDay = day
                showQuickEditDialog(year, month, day)
            },
            onDayLongClick = { year, month, day ->
                showDayDetailDialog(year, month, day)
            }
        )

        refreshTimeline()
    }

    private fun refreshTimeline() {
        lifecycleScope.launch {
            timelineBuilder.build(timelineGrid, selectedYear, selectedMonth, selectedDay)
        }
    }

    private fun showQuickEditDialog(year: Int, month: Int, day: Int) {
        val dateStr = String.format("%04d-%02d-%02d", year, month, day)
        lifecycleScope.launch {
            val entry = dao.getDayStatus(dateStr)

            runOnUiThread {
                val dateLabel = LocalDate.of(year, month, day)
                    .format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.Builder().setLanguage("ur").build()))

                val dialogLayout = LinearLayout(this@HaidhTrackerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24, 16, 24, 8)
                }

                var selectedStatus = when {
                    entry == null || (entry.status == MenstrualStatus.TUHR && entry.istihadaType == IstihadaType.NONE) -> 0
                    entry?.status == MenstrualStatus.HAIDH -> 1
                    else -> 2
                }

                lateinit var flowSection: LinearLayout
                lateinit var istihadaSection: LinearLayout

                // ---- FLOW (shown when Haidh) ----
                flowSection = LinearLayout(this@HaidhTrackerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = if (selectedStatus == 1) View.VISIBLE else View.GONE
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 6 }
                }
                flowSection.addView(TextView(this@HaidhTrackerActivity).apply {
                    text = "خون کی مقدار"
                    textSize = 14f
                    setTextColor(0xFFFF6666.toInt())
                })
                val flowGroup = RadioGroup(this@HaidhTrackerActivity).apply {
                    orientation = RadioGroup.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                val flowLabels = arrayOf("ہلکا", "معتدل", "بھاری")
                val flowValues = arrayOf(1, 2, 3)
                var selFlow = entry?.flowIntensity ?: 1
                if (selFlow !in 1..3) selFlow = 1
                for (i in flowLabels.indices) {
                    flowGroup.addView(RadioButton(this@HaidhTrackerActivity).apply {
                        text = flowLabels[i]
                        id = View.generateViewId()
                        isChecked = selFlow == flowValues[i]
                        layoutParams = RadioGroup.LayoutParams(0, 40, 1f)
                        setTextColor(0xFFFF6666.toInt())
                        textSize = 13f
                    })
                }
                flowSection.addView(flowGroup)
                dialogLayout.addView(flowSection)

                // ---- STATUS (clickable chips) ----
                val statusChips = arrayOf("⬜ طہارت", "🔴 حیض", "🟡 استحاضہ")
                val chipColors = intArrayOf(0xFFAAAAAA.toInt(), 0xFFFF4444.toInt(), 0xFFD4AF37.toInt())
                val statusRow = LinearLayout(this@HaidhTrackerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 10; topMargin = dp(4) }
                }
                val statusChipViews = mutableListOf<TextView>()
                for (i in statusChips.indices) {
                    val chipShape = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        setCornerRadius(dp(4).toFloat())
                        setColor(0xFF1E293B.toInt())
                        setStroke(1, 0xFF334155.toInt())
                    }
                    val chip = TextView(this@HaidhTrackerActivity).apply {
                        text = statusChips[i]
                        gravity = Gravity.CENTER
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(0, 44, 1f).apply {
                            leftMargin = if (i > 0) dp(4) else 0
                        }
                        setTextColor(chipColors[i])
                        background = chipShape
                        setOnClickListener {
                            selectedStatus = i
                            for (j in statusChips.indices) {
                                val bg = statusChipViews[j].background as android.graphics.drawable.GradientDrawable
                                bg.setColor(if (j == i) 0xFF2A303A.toInt() else 0xFF1E293B.toInt())
                            }
                            flowSection.visibility = if (i == 1) View.VISIBLE else View.GONE
                            istihadaSection.visibility = if (i == 2) View.VISIBLE else View.GONE
                        }
                    }
                    statusChipViews.add(chip)
                    statusRow.addView(chip)
                }
                // Set initial selection
                for (j in statusChips.indices) {
                    val bg = statusChipViews[j].background as android.graphics.drawable.GradientDrawable
                    bg.setColor(if (j == selectedStatus) 0xFF2A303A.toInt() else 0xFF1E293B.toInt())
                }
                dialogLayout.addView(statusRow)

                // ---- ISTIHADA TYPE (shown when Istihada) ----
                istihadaSection = LinearLayout(this@HaidhTrackerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = if (selectedStatus == 2) View.VISIBLE else View.GONE
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 6 }
                }
                istihadaSection.addView(TextView(this@HaidhTrackerActivity).apply {
                    text = "استحاضہ کی قسم"
                    textSize = 14f
                    setTextColor(0xFFD4AF37.toInt())
                })
                istihadaSection.addView(TextView(this@HaidhTrackerActivity).apply {
                    text = "استحاضہ غیر حیض خون — نماز، روزہ، قرآن سب جائز، ہر نماز پر نیا وضو"
                    textSize = 11f
                    setTextColor(0xFFAAAAAA.toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })
                val istihadaGroup = RadioGroup(this@HaidhTrackerActivity).apply {
                    orientation = RadioGroup.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                val istihadaTypes = listOf(
                    Pair("قلیلہ (کم)", IstihadaType.QALILA),
                    Pair("متوسطہ (معتدل)", IstihadaType.MUTAWASITA),
                    Pair("کثیرہ (زیادہ)", IstihadaType.KATHIRA)
                )
                var selIst = entry?.istihadaType ?: IstihadaType.QALILA
                if (selIst == IstihadaType.NONE) selIst = IstihadaType.QALILA
                for ((label, type) in istihadaTypes) {
                    istihadaGroup.addView(RadioButton(this@HaidhTrackerActivity).apply {
                        text = label
                        id = View.generateViewId()
                        isChecked = selIst == type
                        layoutParams = RadioGroup.LayoutParams(
                            RadioGroup.LayoutParams.WRAP_CONTENT, 36
                        )
                        setTextColor(0xFFFFD700.toInt())
                        textSize = 13f
                    })
                }
                istihadaSection.addView(istihadaGroup)
                dialogLayout.addView(istihadaSection)

                // ---- SYMPTOMS ----
                val symptomSection = LinearLayout(this@HaidhTrackerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 6 }
                }
                symptomSection.addView(TextView(this@HaidhTrackerActivity).apply {
                    text = "علامات"
                    textSize = 14f
                    setTextColor(0xFFD4AF37.toInt())
                })
                val savedSymptoms = (entry?.symptoms ?: "").split(",").filter { it.isNotBlank() }
                val symptomCheckboxes = mutableListOf<CheckBox>()
                for (symptom in symptoms) {
                    val cb = CheckBox(this@HaidhTrackerActivity).apply {
                        text = symptom
                        id = View.generateViewId()
                        isChecked = savedSymptoms.contains(symptom)
                        setTextColor(0xFFE8E6E1.toInt())
                        layoutParams = RadioGroup.LayoutParams(
                            RadioGroup.LayoutParams.WRAP_CONTENT, 32
                        )
                    }
                    symptomCheckboxes.add(cb)
                    symptomSection.addView(cb)
                }
                dialogLayout.addView(symptomSection)

                // ---- NOTES ----
                val notesInput = EditText(this@HaidhTrackerActivity).apply {
                    setText(entry?.notes ?: "")
                    hint = "نوٹس لکھیں..."
                    setTextColor(0xFFE8E6E1.toInt())
                    setHintTextColor(0xFF666666.toInt())
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 60
                    ).apply { bottomMargin = 4 }
                    setBackgroundColor(0xFF1E293B.toInt())
                    setPadding(8, 4, 8, 4)
                }
                dialogLayout.addView(notesInput)

                AlertDialog.Builder(this@HaidhTrackerActivity)
                    .setTitle("$dateLabel")
                    .setView(dialogLayout)
                    .setPositiveButton("محفوظ کریں") { _, _ ->
                        val newStatus: MenstrualStatus
                        val newFlow: Int
                        val newIstihada: IstihadaType

                        if (selectedStatus == 0) {
                            newStatus = MenstrualStatus.TUHR
                            newFlow = 0
                            newIstihada = IstihadaType.NONE
                        } else if (selectedStatus == 1) {
                            newStatus = MenstrualStatus.HAIDH
                            var fv = 1
                            for (i in 0 until flowGroup.childCount) {
                                val rb = flowGroup.getChildAt(i) as? RadioButton
                                if (rb != null && rb.isChecked) { fv = flowValues[i]; break }
                            }
                            newFlow = fv
                            newIstihada = IstihadaType.NONE
                        } else {
                            newStatus = MenstrualStatus.TUHR
                            newFlow = 0
                            var iv = IstihadaType.QALILA
                            for (i in 0 until istihadaGroup.childCount) {
                                val rb = istihadaGroup.getChildAt(i) as? RadioButton
                                if (rb != null && rb.isChecked) { iv = istihadaTypes[i].second; break }
                            }
                            newIstihada = iv
                        }

                        val checkedSymptoms = symptomCheckboxes.filter { it.isChecked }
                            .joinToString(",") { it.text.toString() }

                        val newEntry = CycleEntity(
                            date = dateStr,
                            status = newStatus,
                            symptoms = checkedSymptoms,
                            flowIntensity = newFlow,
                            notes = notesInput.text.toString(),
                            istihadaType = newIstihada
                        )
                        lifecycleScope.launch {
                            dao.upsertDayStatus(newEntry)
                            if (newStatus == MenstrualStatus.HAIDH) {
                                updatePhaseTracking(dateStr, newStatus)
                            }
                            selectedYear = year
                            selectedMonth = month
                            selectedDay = day
                            refreshTimeline()
                            Toast.makeText(this@HaidhTrackerActivity, "محفوظ ہو گیا", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("منسوخ") { _, _ ->
                        selectedYear = year
                        selectedMonth = month
                        selectedDay = day
                        refreshTimeline()
                    }
                    .create().show()
            }
        }
    }

    private fun showDayDetailDialog(year: Int, month: Int, day: Int) {
        val dateStr = String.format("%04d-%02d-%02d", year, month, day)
        lifecycleScope.launch {
            val entry = dao.getDayStatus(dateStr)
            val phase = dao.getPhaseForDate(dateStr)
            val status = entry?.status ?: MenstrualStatus.TUHR
            val flowIntensity = entry?.flowIntensity ?: 0
            val istihadaType = entry?.istihadaType ?: IstihadaType.NONE
            val symptoms = entry?.symptoms ?: ""
            val notes = entry?.notes ?: ""

            val flowNames = arrayOf("کوئی نہیں", "ہلکا", "معتدل", "بھاری")
            val istihadaNames = arrayOf("لا استحاضہ", "قلیلہ", "متوسطہ", "کثیرہ")

            runOnUiThread {
                val dateStrFormatted = LocalDate.of(year, month, day)
                    .format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.Builder().setLanguage("ur").build()))

                val message = buildString {
                    append("📅 $dateStrFormatted\n\n")
                    when {
                        status == MenstrualStatus.HAIDH -> {
                            append("🔴 حالت: حیض\n")
                            if (flowIntensity in 1..3) {
                                append("💧 مقدار: ${flowNames[flowIntensity]}\n")
                            }
                        }
                        istihadaType != IstihadaType.NONE -> {
                            append("🟡 حالت: استحاضہ\n")
                            append("⚠️ ${istihadaNames[istihadaType.ordinal]}\n")
                        }
                        else -> {
                            append("⬜ حالت: طہارت\n")
                        }
                    }
                    if (phase != null) {
                        append("📋 سائیکل کا دن: ${phase.cycleDay}\n")
                    }
                    if (symptoms.isNotBlank()) {
                        append("🌸 علامات: $symptoms\n")
                    }
                    if (notes.isNotBlank()) {
                        append("📝 نوٹس: $notes")
                    }
                }

                AlertDialog.Builder(this@HaidhTrackerActivity)
                    .setTitle("دن کی تفصیل")
                    .setMessage(message)
                    .setPositiveButton("ترمیم کریں") { _, _ ->
                        selectedYear = year
                        selectedMonth = month
                        selectedDay = day
                        showQuickEditDialog(year, month, day)
                    }
                    .setNegativeButton("بند کریں", null)
                    .show()
            }
        }
    }

    // ── Phase tracking (needed for Haidh saves) ──

    private suspend fun updatePhaseTracking(dateStr: String, status: MenstrualStatus) {
        val blockStart = findHaidhBlockStart(dateStr)
        val blockEnd = findHaidhBlockEnd(dateStr)

        dao.deletePhasesInRange(blockStart, blockEnd, MenstrualStatus.HAIDH)

        var scanDate = LocalDate.parse(blockStart, dateFormatter)
        val endDate = LocalDate.parse(blockEnd, dateFormatter)
        var cycleDay = 1
        while (!scanDate.isAfter(endDate)) {
            val scanDateStr = scanDate.format(dateFormatter)
            val entry = dao.getDayStatus(scanDateStr)
            if (entry?.status == MenstrualStatus.HAIDH) {
                dao.upsertPhase(CyclePhaseEntity(
                    startDate = scanDateStr,
                    endDate = scanDateStr,
                    status = MenstrualStatus.HAIDH,
                    cycleDay = cycleDay
                ))
                cycleDay++
            }
            scanDate = scanDate.plusDays(1)
        }
    }

    private suspend fun findHaidhBlockStart(fromDate: String): String {
        var date = LocalDate.parse(fromDate, dateFormatter)
        while (true) {
            val prevDate = date.minusDays(1)
            val prevDateStr = prevDate.format(dateFormatter)
            val prevEntry = dao.getDayStatus(prevDateStr)
            if (prevEntry?.status != MenstrualStatus.HAIDH) {
                return date.format(dateFormatter)
            }
            date = prevDate
        }
    }

    private fun dp(dp: Int): Int = (dp * resources.displayMetrics.density + 0.5f).toInt()

    private suspend fun findHaidhBlockEnd(fromDate: String): String {
        var date = LocalDate.parse(fromDate, dateFormatter)
        val todayStr = LocalDate.now().format(dateFormatter)
        while (true) {
            val nextDate = date.plusDays(1)
            val nextDateStr = nextDate.format(dateFormatter)
            if (nextDateStr > todayStr) return date.format(dateFormatter)
            val nextEntry = dao.getDayStatus(nextDateStr)
            if (nextEntry?.status != MenstrualStatus.HAIDH) {
                return date.format(dateFormatter)
            }
            date = nextDate
        }
    }
}
