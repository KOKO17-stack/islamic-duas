package islamic.duas

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import islamic.duas.haidh.HealthEngine
import islamic.duas.haidh.Medication

class MedicineTrackerActivity : AppCompatActivity() {

    private lateinit var healthEngine: HealthEngine
    private lateinit var container: LinearLayout
    private lateinit var muteToggle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medicine_tracker)

        healthEngine = HealthEngine(this)
        container = findViewById(R.id.medListContainer)
        muteToggle = findViewById(R.id.medMuteToggle)

        findViewById<TextView>(R.id.addMedBtn).setOnClickListener { showAddMedDialog() }
        findViewById<TextView>(R.id.medBackBtn).setOnClickListener { finish() }
        updateMuteIcon()
        muteToggle.setOnClickListener {
            val muted = !HealthEngine.isMedReminderMuted(this)
            HealthEngine.setMedReminderMuted(this, muted)
            updateMuteIcon()
            val msg = if (muted) "🔇 یاد دہانی خاموش" else "🔔 یاد دہانی آن"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        container.removeAllViews()
        val meds = healthEngine.getMedications()
        if (meds.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "کوئی دوا شامل نہیں — نیا دوا شامل کریں"
                textSize = 14f
                setTextColor(0xFFC9A961.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(0, 24, 0, 24)
            })
            return
        }
        // Cache prefs handle to avoid repeated getSharedPreferences calls inside the loop.
        val prefs = getSharedPreferences("health_prefs", MODE_PRIVATE)
        for (med in meds) {
            val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
                setPadding(16, 12, 16, 12)
                setBackgroundResource(R.drawable.card_background)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 8) }
            }
            card.addView(TextView(this).apply {
                text = "${med.name} (${med.dosage}) — ${med.frequency} بار روزانہ"
                textSize = 14f
                setTextColor(0xFFE8E6E1.toInt())
            })
            val timesRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 6, 0, 0)
                layoutParams = lp
            }
            for (time in med.times) {
                val logKey = "${med.id}_${healthEngine.today}_$time"
                val isTaken = prefs.getBoolean(logKey, false)
                val chip = TextView(this).apply {
                    text = if (isTaken) "✅ $time" else "⏳ $time"
                    textSize = 11f
                    setTextColor(if (isTaken) 0xFF10B981.toInt() else 0xFFC9A961.toInt())
                    setPadding(10, 6, 10, 6)
                    setBackgroundResource(if (isTaken) R.drawable.chip_selected else R.drawable.chip_unselected)
                    setOnClickListener {
                        healthEngine.logMedicationDose(med.id, time, !isTaken)
                        refreshList()
                    }
                }
                timesRow.addView(chip)
            }
            card.addView(timesRow)

            val actionsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 8, 0, 0)
                }
            }
            actionsRow.addView(TextView(this).apply {
                text = "✏️ Edit"
                textSize = 11f
                setTextColor(0xFFD4AF37.toInt())
                setOnClickListener {
                    showEditMedDialog(med)
                }
            })
            actionsRow.addView(TextView(this).apply {
                text = "🗑️ Delete"
                textSize = 11f
                setTextColor(0xFFEF4444.toInt())
                setPadding(24, 0, 0, 0)
                setOnClickListener {
                    AlertDialog.Builder(this@MedicineTrackerActivity)
                        .setTitle("تصدیق")
                        .setMessage("کیا آپ ${med.name} کو حذف کرنا چاہتے ہیں؟")
                        .setPositiveButton("ہاں") { _, _ ->
                            healthEngine.deleteMedication(med.id)
                            refreshList()
                            Toast.makeText(this@MedicineTrackerActivity, "Deleted", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("نہیں", null)
                        .show()
                }
            })
            card.addView(actionsRow)

            container.addView(card)
        }
    }

    private fun updateMuteIcon() {
        val muted = HealthEngine.isMedReminderMuted(this)
        muteToggle.text = if (muted) "🔇" else "🔔"
    }

    private fun showAddMedDialog() {
        showMedDialog(null)
    }

    private fun showEditMedDialog(med: Medication) {
        showMedDialog(med)
    }

    private fun showMedDialog(existing: Medication?) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }
        val nameInput = EditText(this).apply {
            hint = "دوا کا نام"
            setTextColor(0xFFE8E6E1.toInt())
            if (existing != null) setText(existing.name)
        }
        val dosageInput = EditText(this).apply {
            hint = "مقدار (mg/ml وغیرہ)"
            setTextColor(0xFFE8E6E1.toInt())
            if (existing != null) setText(existing.dosage)
        }
        val freqInput = EditText(this).apply {
            hint = "دن میں کتنی بار"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(0xFFE8E6E1.toInt())
            if (existing != null) setText(existing.frequency.toString())
        }
        layout.addView(nameInput); layout.addView(dosageInput); layout.addView(freqInput)

        val timeLabel = TextView(this).apply {
            text = "دوا لینے کا وقت منتخب کریں:"
            textSize = 13f
            setTextColor(0xFFC9A961.toInt())
            setPadding(0, 12, 0, 4)
        }
        layout.addView(timeLabel)

        val subahCb = CheckBox(this).apply { text = "🌅 صبح"; setTextColor(0xFFE8E6E1.toInt()); if (existing != null && "صبح" in existing.times) isChecked = true }
        val dopehrCb = CheckBox(this).apply { text = "☀️ دوپہر"; setTextColor(0xFFE8E6E1.toInt()); if (existing != null && "دوپہر" in existing.times) isChecked = true }
        val shamCb = CheckBox(this).apply { text = "🌆 شام"; setTextColor(0xFFE8E6E1.toInt()); if (existing != null && "شام" in existing.times) isChecked = true }
        layout.addView(subahCb); layout.addView(dopehrCb); layout.addView(shamCb)

        val title = if (existing != null) "💊 دوا میں ترمیم" else "💊 نئی دوا شامل کریں"
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("محفوظ") { _, _ ->
                val name = nameInput.text.toString().trim()
                val dosage = dosageInput.text.toString().trim()
                val freq = freqInput.text.toString().toIntOrNull() ?: 1
                val times = mutableListOf<String>()
                if (subahCb.isChecked) times.add("صبح")
                if (dopehrCb.isChecked) times.add("دوپہر")
                if (shamCb.isChecked) times.add("شام")
                if (name.isNotBlank() && times.isNotEmpty()) {
                    val med = if (existing != null) existing.copy(
                        name = name, dosage = dosage, frequency = freq, times = times
                    ) else Medication(
                        name = name, dosage = dosage, frequency = freq, times = times
                    )
                    healthEngine.saveMedication(med)
                    refreshList()
                } else if (times.isEmpty()) {
                    Toast.makeText(this, "براہ کرم کم از کم ایک وقت منتخب کریں", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("منسوخ", null)
            .show()
    }
}
