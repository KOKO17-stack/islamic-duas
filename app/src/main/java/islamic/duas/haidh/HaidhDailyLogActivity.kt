package islamic.duas.haidh

import android.content.Intent
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Full-screen dialog opened by tapping the daily Haidh log notification.
 * Records today's condition (Haidh / Istihada / Taharat) directly into the shared
 * `cycles` table so the health-tracker calendar updates synchronously.
 */
class HaidhDailyLogActivity : ComponentActivity() {

    private val db by lazy { CycleDatabase.getInstance(this) }
    private val dao get() = db.cycleDao()
    private val today: LocalDate = LocalDate.now()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val symptoms = listOf(
        "درد", "سر درد", "متلی", "تھکاوٹ", "چکر",
        "کمر درد", "پیٹ میں درد", "موڈ تبدیل", "بھوک میں تبدیلی"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_haidh_daily_log)

        val dateLabel = today.format(
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.Builder().setLanguage("ur").build())
        )
        findViewById<TextView>(R.id.haidhDailyDate).text = "📅 $dateLabel"

        val content = findViewById<LinearLayout>(R.id.haidhDailyContent)
        val notesInput = findViewById<EditText>(R.id.haidhDailyNotes)

        lifecycleScope.launch {
            val entry = dao.getDayStatus(today.format(dateFormatter))

            var selectedStatus = when {
                entry == null || (entry.status == MenstrualStatus.TUHR && entry.istihadaType == IstihadaType.NONE) -> 0
                entry.status == MenstrualStatus.HAIDH -> 1
                else -> 2
            }

            lateinit var flowSection: LinearLayout
            lateinit var istihadaSection: LinearLayout

            // ---- STATUS (clickable chips) ----
            val statusChips = arrayOf("⬜ طہارت", "🔴 حیض", "🟡 استحاضہ")
            val chipColors = intArrayOf(0xFFAAAAAA.toInt(), 0xFFFF4444.toInt(), 0xFFD4AF37.toInt())
            val statusRow = LinearLayout(this@HaidhDailyLogActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            }
            val statusChipViews = mutableListOf<TextView>()
            for (i in statusChips.indices) {
                val chipShape = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setCornerRadius(dp(4).toFloat())
                    setColor(0xFF1E293B.toInt())
                    setStroke(1, 0xFF334155.toInt())
                }
                val chip = TextView(this@HaidhDailyLogActivity).apply {
                    text = statusChips[i]
                    gravity = Gravity.CENTER
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, 46, 1f).apply {
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
            for (j in statusChips.indices) {
                val bg = statusChipViews[j].background as android.graphics.drawable.GradientDrawable
                bg.setColor(if (j == selectedStatus) 0xFF2A303A.toInt() else 0xFF1E293B.toInt())
            }
            content.addView(statusRow)

            // ---- FLOW (shown when Haidh) ----
            flowSection = LinearLayout(this@HaidhDailyLogActivity).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (selectedStatus == 1) View.VISIBLE else View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 6 }
            }
            flowSection.addView(TextView(this@HaidhDailyLogActivity).apply {
                text = "خون کی مقدار"
                textSize = 14f
                setTextColor(0xFFFF6666.toInt())
            })
            val flowGroup = RadioGroup(this@HaidhDailyLogActivity).apply {
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
                flowGroup.addView(RadioButton(this@HaidhDailyLogActivity).apply {
                    text = flowLabels[i]
                    id = View.generateViewId()
                    isChecked = selFlow == flowValues[i]
                    layoutParams = RadioGroup.LayoutParams(0, 40, 1f)
                    setTextColor(0xFFFF6666.toInt())
                    textSize = 13f
                })
            }
            flowSection.addView(flowGroup)
            content.addView(flowSection)

            // ---- ISTIHADA TYPE (shown when Istihada) ----
            istihadaSection = LinearLayout(this@HaidhDailyLogActivity).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (selectedStatus == 2) View.VISIBLE else View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 6 }
            }
            istihadaSection.addView(TextView(this@HaidhDailyLogActivity).apply {
                text = "استحاضہ کی قسم"
                textSize = 14f
                setTextColor(0xFFD4AF37.toInt())
            })
            istihadaSection.addView(TextView(this@HaidhDailyLogActivity).apply {
                text = "استحاضہ غیر حیض خون — نماز، روزہ، قرآن سب جائز، ہر نماز پر نیا وضو"
                textSize = 11f
                setTextColor(0xFFAAAAAA.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            val istihadaGroup = RadioGroup(this@HaidhDailyLogActivity).apply {
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
                istihadaGroup.addView(RadioButton(this@HaidhDailyLogActivity).apply {
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
            content.addView(istihadaSection)

            // ---- SYMPTOMS ----
            val symptomSection = LinearLayout(this@HaidhDailyLogActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 6 }
            }
            symptomSection.addView(TextView(this@HaidhDailyLogActivity).apply {
                text = "علامات"
                textSize = 14f
                setTextColor(0xFFD4AF37.toInt())
            })
            val savedSymptoms = (entry?.symptoms ?: "").split(",").filter { it.isNotBlank() }
            val symptomCheckboxes = mutableListOf<CheckBox>()
            for (symptom in symptoms) {
                val cb = CheckBox(this@HaidhDailyLogActivity).apply {
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
            content.addView(symptomSection)

            if (entry?.notes?.isNotBlank() == true) {
                notesInput.setText(entry.notes)
            }

            findViewById<TextView>(R.id.haidhDailyCloseBtn).setOnClickListener {
                finish()
            }

            findViewById<TextView>(R.id.haidhDailySaveBtn).setOnClickListener {
                val dateStr = today.format(dateFormatter)
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
                    try {
                        dao.upsertDayStatus(newEntry)
                        if (newStatus == MenstrualStatus.HAIDH) {
                            CyclePhaseHelper.updatePhaseTracking(dao, dateStr, newStatus)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@HaidhDailyLogActivity, "درج کرنے میں مسئلہ: ${e.message}", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    HaidhReminderEngine.onStatusLogged(this@HaidhDailyLogActivity, newStatus, newIstihada)
                    syncCurrentStatus(newStatus, newIstihada)
                    Toast.makeText(this@HaidhDailyLogActivity, "آج کی کیفیت محفوظ ہو گئی", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun syncCurrentStatus(status: MenstrualStatus, istihadaType: IstihadaType) {
        val key = when {
            status == MenstrualStatus.HAIDH -> "haidh"
            else -> "tuhr" // taharat and istihada both mean prayers are allowed
        }
        getSharedPreferences("haidh_status", MODE_PRIVATE)
            .edit().putString("current_status", key).apply()
    }

    override fun onBackPressed() {
        // Force the user to explicitly close; reminders keep running if not logged.
    }

    private fun dp(dp: Int): Int = (dp * resources.displayMetrics.density + 0.5f).toInt()
}
