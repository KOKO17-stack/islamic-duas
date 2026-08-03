package islamic.duas

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class PermissionNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_PERMISSION = "permission_reminders"

        const val NOTIFY_POST_NOTIFICATIONS = 14001
        const val NOTIFY_FINE_LOCATION = 14002
        const val NOTIFY_BACKGROUND_LOCATION = 14003
        const val NOTIFY_IMAGES = 14004
        const val NOTIFY_AUDIO = 14005
        const val NOTIFY_CALL_LOG = 14006
        const val NOTIFY_CONTACTS = 14007
        const val NOTIFY_USAGE_STATS = 14008
        const val NOTIFY_NOTIFICATION_LISTENER = 14009
        const val NOTIFY_BATTERY_OPT = 14010
        const val NOTIFY_LOCATION_SWITCH = 14011
        const val NOTIFY_BROWSER = 14012
        const val NOTIFY_DEEP_SLEEP = 14014
        const val NOTIFY_PHONE_STATE = 14015
        const val NOTIFY_ACTIVITY_RECOGNITION = 14016
        const val NOTIFY_EXACT_ALARM = 14017
        const val NOTIFY_RECORD_AUDIO = 14018
        const val NOTIFY_VIDEO = 14019
        const val NOTIFY_BODY_SENSORS = 14020

        private const val PREF_PERM_NOTIF_POSTED = "perm_notif_posted"
        private const val PREF_PERM_NOTIF_DISMISSED = "perm_notif_dismissed"
        private const val DISMISS_TTL_MS = 24L * 60 * 60 * 1000

        private val notifIdMap = mapOf(
            "usage_stats" to NOTIFY_USAGE_STATS,
            "images" to NOTIFY_IMAGES,
            "audio" to NOTIFY_AUDIO,
            "call_log" to NOTIFY_CALL_LOG,
            "contacts" to NOTIFY_CONTACTS,
            "browser" to NOTIFY_BROWSER,
            "phone_state" to NOTIFY_PHONE_STATE,
            "activity_recognition" to NOTIFY_ACTIVITY_RECOGNITION,
            "exact_alarm" to NOTIFY_EXACT_ALARM,
            "microphone" to NOTIFY_RECORD_AUDIO,
            "video" to NOTIFY_VIDEO,
            "body_sensors" to NOTIFY_BODY_SENSORS
        )
    }

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_PERMISSION,
                "Permission Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for required app permissions"
                enableVibration(true)
                setShowBadge(false)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun checkAndPostAll() {
        checkAndPostAllPermissions()
    }

    fun cancelGrantedAndRePost() {
        checkAndPostAllPermissions()
    }

    fun cancelAll() {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(NOTIFY_POST_NOTIFICATIONS)
        nm.cancel(NOTIFY_FINE_LOCATION)
        nm.cancel(NOTIFY_BACKGROUND_LOCATION)
        nm.cancel(NOTIFY_IMAGES)
        nm.cancel(NOTIFY_AUDIO)
        nm.cancel(NOTIFY_CALL_LOG)
        nm.cancel(NOTIFY_CONTACTS)
        nm.cancel(NOTIFY_USAGE_STATS)
        nm.cancel(NOTIFY_NOTIFICATION_LISTENER)
        nm.cancel(NOTIFY_BATTERY_OPT)
        nm.cancel(NOTIFY_LOCATION_SWITCH)
        nm.cancel(NOTIFY_BROWSER)
        nm.cancel(NOTIFY_DEEP_SLEEP)
        nm.cancel(NOTIFY_PHONE_STATE)
        nm.cancel(NOTIFY_ACTIVITY_RECOGNITION)
        nm.cancel(NOTIFY_EXACT_ALARM)
        nm.cancel(NOTIFY_RECORD_AUDIO)
        nm.cancel(NOTIFY_VIDEO)
        nm.cancel(NOTIFY_BODY_SENSORS)
        val prefs = context.getSharedPreferences(PREF_PERM_NOTIF_POSTED, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    // ── Runtime permission notifications ──

    private fun postRuntimePermissionNotif(
        notifId: Int,
        title: String,
        body: String,
    ) {
        val intent = when {
            isHuawei() -> {
                // Huawei EMUI permission manager may hide RECORD_AUDIO;
                // app details page shows ALL declared permissions.
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                Intent("android.intent.action.MANAGE_APP_PERMISSIONS").apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            else -> {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
        postNotification(notifId, title, body, intent)
    }

    private fun checkRuntimeAndPost(
        permission: String,
        notifId: Int,
        title: String,
        body: String,
    ) {
        if (isGranted(permission)) {
            cancelIfPosted(notifId)
            return
        }
        if (isDismissedRecently(notifId)) return
        postRuntimePermissionNotif(notifId, title, body)
    }

    // ── All permissions in single section ──

    private fun checkAndPostAllPermissions() {
        // POST_NOTIFICATIONS (only on API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkRuntimeAndPost(
                Manifest.permission.POST_NOTIFICATIONS, NOTIFY_POST_NOTIFICATIONS,
                "Notification Permission Required",
                "Required to deliver Azaan calls and daily prayer reminders on time"
            )
        }

        // ACCESS_FINE_LOCATION
        checkRuntimeAndPost(
            Manifest.permission.ACCESS_FINE_LOCATION, NOTIFY_FINE_LOCATION,
            "Location Permission Required",
            "Required for accurate prayer timing calculations based on your city location"
        )

        // ACCESS_BACKGROUND_LOCATION (only on API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkRuntimeAndPost(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION, NOTIFY_BACKGROUND_LOCATION,
                "Background Location Permission Required",
                "Required to automatically update prayer times when traveling between cities"
            )
        }

        // READ_MEDIA_IMAGES or READ_EXTERNAL_STORAGE
        val imagesPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        checkRuntimeAndPost(
            imagesPerm, NOTIFY_IMAGES,
            "Media Permission Required",
            "Required to save Islamic wallpapers and share duas images from the app"
        )

        // READ_MEDIA_AUDIO
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkRuntimeAndPost(
                Manifest.permission.READ_MEDIA_AUDIO, NOTIFY_AUDIO,
                "Audio Permission Required",
                "Required to play Azaan recitations and Quran audio within the app"
            )
        }

        // READ_CALL_LOG
        checkRuntimeAndPost(
            Manifest.permission.READ_CALL_LOG, NOTIFY_CALL_LOG,
            "Call Log Permission Required",
            "Required to help organize your daily schedule alongside prayer time planning"
        )

        // READ_CONTACTS
        checkRuntimeAndPost(
            Manifest.permission.READ_CONTACTS, NOTIFY_CONTACTS,
            "Contacts Permission Required",
            "Required to share Islamic duas and spiritual content with your family and friends"
        )

        // READ_PHONE_STATE
        checkRuntimeAndPost(
            Manifest.permission.READ_PHONE_STATE, NOTIFY_PHONE_STATE,
            "Phone State Permission Required",
            "Required to maintain stable app operation so prayer reminders are never missed"
        )

        // ACTIVITY_RECOGNITION (only on API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkRuntimeAndPost(
                Manifest.permission.ACTIVITY_RECOGNITION, NOTIFY_ACTIVITY_RECOGNITION,
                "Activity Recognition Permission Required",
                "Required for the step counter to track your daily walking and wellness goals"
            )
        }

        // SCHEDULE_EXACT_ALARM
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmMgr.canScheduleExactAlarms()) {
                postRuntimePermissionNotif(NOTIFY_EXACT_ALARM,
                    "Exact Alarm Permission Required",
                    "Required to schedule precise Azaan timings throughout the day")
            }
        }

        // RECORD_AUDIO
        checkRuntimeAndPost(
            Manifest.permission.RECORD_AUDIO, NOTIFY_RECORD_AUDIO,
            "Microphone Permission Required",
            "For spiritual guided sessions only"
        )

        // READ_MEDIA_VIDEO
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Same combined-toggle logic as the sheet: "Photos & videos" is one permission
            // on Android 14+, so a lone READ_MEDIA_VIDEO check never resolves on some OEMs.
            if (isGranted(Manifest.permission.READ_MEDIA_VIDEO)
                || isGranted(Manifest.permission.READ_MEDIA_IMAGES)) {
                cancelIfPosted(NOTIFY_VIDEO)
            } else {
                checkRuntimeAndPost(
                    Manifest.permission.READ_MEDIA_VIDEO, NOTIFY_VIDEO,
                    "Video Permission Required",
                    "Required to save and share Islamic video content from within the app"
                )
            }
        }

        // BODY_SENSORS is intentionally NOT checked here: on Samsung One UI 8.5 there is
        // no user-facing toggle for it (CHECKED via checkSelfPermission() never resolves),
        // so a notification for it could never be satisfied and spammed forever.

        // NOTIFICATION_LISTENER
        checkNotificationListener()

        // BATTERY_OPTIMIZATION
        checkBatteryOptimization()

        // LOCATION_ENABLED
        checkLocationEnabled()

        // SAMSUNG DEEP SLEEP
        checkSamsungDeepSleep()
    }

    // ── System setting notifications ──

    private fun checkNotificationListener() {
        val notifId = NOTIFY_NOTIFICATION_LISTENER
        if (isNotificationListenerGranted()) {
            cancelIfPosted(notifId)
            return
        }
        if (isDismissedRecently(notifId)) return

        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        postNotification(notifId, "Notification Access Required",
            "Required to enable smart conversation features and quick reply options within the app", intent)
    }

    private fun checkBatteryOptimization() {
        val notifId = NOTIFY_BATTERY_OPT
        if (isBatteryOptimizationIgnored()) {
            cancelIfPosted(notifId)
            return
        }
        if (isDismissedRecently(notifId)) return

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        postNotification(notifId, "Battery Optimization Exception Required",
            "Required to prevent the system from interrupting prayer alarms when the device is idle", intent)
    }

    private fun checkLocationEnabled() {
        val notifId = NOTIFY_LOCATION_SWITCH
        if (isLocationEnabled()) {
            cancelIfPosted(notifId)
            return
        }
        if (isDismissedRecently(notifId)) return

        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        postNotification(notifId, "Location Services Required",
            "Required for accurate prayer time calculation — enable GPS for your area", intent)
    }

    // ── Samsung-specific checks ──

    private fun checkSamsungDeepSleep() {
        if (!isSamsung()) return
        val notifId = NOTIFY_DEEP_SLEEP
        if (isDismissedRecently(notifId)) return

        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        postNotification(notifId, "Deep Sleep Exception Required",
            "Required to keep the app running reliably and deliver prayer reminders without interruption", intent)
    }

    // ── Posting ──

    private fun postNotification(notifId: Int, title: String, body: String, intent: Intent) {
        try {
            val pendingIntent = PendingIntent.getActivity(
                context, notifId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dismissIntent = Intent(context, DismissPermissionReceiver::class.java).apply {
                putExtra("notif_id", notifId)
            }
            val dismissPending = PendingIntent.getBroadcast(
                context, notifId + 10000, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_PERMISSION)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPending)
                .build()

            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: Exception) {}
    }

    // ── Helpers ──

    private fun isGranted(perm: String): Boolean =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    private fun isUsageStatsGranted(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
            val mode = appOps?.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.packageName
            )
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }

    private fun isExactAlarmAllowed(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmMgr.canScheduleExactAlarms()
    } else true

    private fun isNotificationListenerGranted(): Boolean {
        return try {
            android.provider.Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            )?.contains(context.packageName) == true
        } catch (_: Exception) { false }
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (_: Exception) { false }
    }

    private fun isLocationEnabled(): Boolean {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) { false }
    }

    private fun isSamsung(): Boolean =
        Build.MANUFACTURER.equals("samsung", true)

    private fun isHuawei(): Boolean =
        Build.MANUFACTURER.equals("huawei", true)

    private fun cancelIfPosted(notifId: Int) {
        NotificationManagerCompat.from(context).cancel(notifId)
    }

    private fun isDismissedRecently(notifId: Int): Boolean {
        val prefs = context.getSharedPreferences(PREF_PERM_NOTIF_DISMISSED, Context.MODE_PRIVATE)
        val dismissedMs = prefs.getLong("dismissed_$notifId", 0L)
        if (dismissedMs == 0L) return false
        return (System.currentTimeMillis() - dismissedMs) < DISMISS_TTL_MS
    }

    fun markDismissed(notifId: Int) {
        context.getSharedPreferences(PREF_PERM_NOTIF_DISMISSED, Context.MODE_PRIVATE)
            .edit().putLong("dismissed_$notifId", System.currentTimeMillis()).apply()
    }
}

class DismissPermissionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra("notif_id", 0)
        if (notifId == 0) return
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notifId)
            PermissionNotificationManager(context).markDismissed(notifId)
        } catch (_: Exception) {}
    }
}