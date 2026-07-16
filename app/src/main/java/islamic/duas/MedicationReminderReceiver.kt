package islamic.duas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import islamic.duas.haidh.HealthEngine

class MedicationReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val muted = HealthEngine.isMedReminderMuted(context)
        ensureChannel(context, muted)
        val healthEngine = HealthEngine(context)
        val pending = healthEngine.getPendingMedications()
        if (pending.isEmpty()) return
        val msg = pending.joinToString("، ") + " — وقت ہو گیا ہے!"
        try {
            val builder = android.app.Notification.Builder(context, CHANNEL_MED)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("💊 دوا کا وقت")
                .setContentText(msg)
                .setStyle(android.app.Notification.BigTextStyle().bigText(msg))
                .setAutoCancel(true)
            val n = builder.build()
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.notify(9001, n)
        } catch (_: Exception) {}
        scheduleNext(context)
    }

    private fun scheduleNext(context: Context) {
        val alarmMgr = context.getSystemService(android.app.AlarmManager::class.java)
        val pi = android.app.PendingIntent.getBroadcast(context, 9002,
            Intent(context, MedicationReminderReceiver::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        alarmMgr.set(android.app.AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 30 * 60 * 1000, pi)
    }

    companion object {
        const val CHANNEL_MED = "medication_reminders"
        fun ensureChannel(context: Context, muted: Boolean = false) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val importance = if (muted) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(
                    CHANNEL_MED,
                    "دوا کی یاد دہانیاں",
                    importance
                ).apply {
                    description = "جب دوا کا وقت ہو تو اطلاع"
                    setSound(null, null)
                }
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(channel)
            }
        }
    }
}
