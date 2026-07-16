package islamic.duas

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import islamic.duas.haidh.HealthEngine
import java.util.Calendar

class StepCounterService : Service(), SensorEventListener {

    companion object {
        const val NOTIF_ID = 2001
        const val CHANNEL_ID = "step_counter"
        private const val MIDNIGHT_ALARM_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, StepCounterService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun scheduleMidnight(context: Context) {
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, StepMidnightReceiver::class.java).apply {
                action = "islamic.duas.STEP_MIDNIGHT"
            }
            val pi = PendingIntent.getBroadcast(
                context, MIDNIGHT_ALARM_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            try {
                alarmMgr.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi
                )
            } catch (_: Exception) {}
        }
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var healthEngine: HealthEngine
    private var stepSensor: Sensor? = null

    override fun onCreate() {
        super.onCreate()
        healthEngine = HealthEngine(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIF_ID, buildNotification(healthEngine.getTodaySteps()))
        } catch (_: Exception) {
            // Foreground not supported on this device — run without it
        }
        registerSensor()
        scheduleMidnight(this)
        return START_REDELIVER_INTENT
    }

    private fun registerSensor() {
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
        val reading = event.values[0].toInt()
        val baseline = healthEngine.getSensorBaseline()
        if (baseline < 0) {
            healthEngine.setSensorBaseline(reading)
            return
        }
        val delta = reading - baseline
        if (delta <= 0) return
        val current = healthEngine.getTodaySteps()
        val newTotal = current + delta
        healthEngine.setTodaySteps(newTotal)
        healthEngine.setSensorBaseline(reading)
        updateNotification(newTotal)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try { sensorManager.unregisterListener(this) } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "شمارندہ قدم", NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "روزانہ قدموں کی گنتی"
                setShowBadge(false)
                setSound(null, null)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(steps: Int): Notification {
        val openIntent = Intent(this, ExerciseLogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("\uD83D\uDC5F قدم شمار: $steps")
            .setContentText("آج کے قدم گنے جا رہے ہیں")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingOpen)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(steps: Int) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotification(steps))
        } catch (_: Exception) {}
    }
}
