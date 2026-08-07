package islamic.duas

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import islamic.duas.haidh.HealthEngine

class MedicationDosePromptActivity : AppCompatActivity() {

    private lateinit var notifManager: AppNotificationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medication_dose_prompt)
        notifManager = AppNotificationManager(this)

        val medId = intent.getStringExtra(AppNotificationManager.EXTRA_MED_ID)
        val medTime = intent.getStringExtra(AppNotificationManager.EXTRA_MED_TIME) ?: run {
            finish()
            return
        }

        val health = HealthEngine(this)
        val medName = if (medId != null) {
            health.getMedications().firstOrNull { it.id == medId }?.name
        } else {
            health.getMedications()
                .filter { it.isActive && it.times.contains(medTime) }
                .joinToString("، ") { it.name }
        } ?: "دوائی"

        findViewById<TextView>(R.id.medDialogName).text = medName
        findViewById<TextView>(R.id.medDialogTime).text = "$medTime — کیا دوا لے لی؟"

        val takenBtn = findViewById<TextView>(R.id.medDialogTakenBtn)
        val laterBtn = findViewById<TextView>(R.id.medDialogLaterBtn)

        takenBtn.setOnClickListener {
            val i = Intent(this, NotificationReceiver::class.java).apply {
                action = AppNotificationManager.ACTION_MEDICINE_TAKEN
                putExtra(AppNotificationManager.EXTRA_MED_ID, medId)
                putExtra(AppNotificationManager.EXTRA_MED_TIME, medTime)
            }
            sendBroadcast(i)
            notifManager.cancelMedicineNotification(medTime)
            finish()
        }

        laterBtn.setOnClickListener {
            val i = Intent(this, NotificationReceiver::class.java).apply {
                action = AppNotificationManager.ACTION_MEDICINE_SNOOZE
                putExtra(AppNotificationManager.EXTRA_MED_TIME, medTime)
            }
            sendBroadcast(i)
            notifManager.cancelMedicineNotification(medTime)
            finish()
        }
    }

    override fun onBackPressed() {
    }
}
