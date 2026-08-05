package islamic.duas.sync

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import islamic.duas.cloud.CloudApi
import islamic.duas.data.OfflineQueue
import islamic.duas.utils.DeviceId
import islamic.duas.utils.ErrorLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class FcmSyncService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FcmSync"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val androidId = DeviceId.get(this@FcmSyncService)
                val doc = JSONObject().apply {
                    put("fcmToken", token)
                    put("ts_ms", System.currentTimeMillis())
                }
                CloudApi.writeToRTDB("devices/$androidId/fcm/token", doc)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register FCM token", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received: ${message.data}")

        // Silent data push to trigger immediate sync
        if (message.data["sync"] == "true" || message.data["type"] == "sync") {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // High-priority FCM grants the Android 12+ background FGS-start exemption:
                    // restart the foreground service so the snapshot coroutine keeps running.
                    try {
                        DuaForegroundService.start(applicationContext)
                    } catch (e: Exception) {
                        Log.w(TAG, "FCM FGS restart failed: ${e.message}")
                        ErrorLog.write(applicationContext, TAG, "FCM FGS restart failed", e)
                    }
                    // Capture a snapshot even if the FGS is dead, so recents stay fresh on push
                    try {
                        DuaSyncWorker.captureAppSnapshot(applicationContext)
                    } catch (e: Exception) {
                        Log.w(TAG, "FCM snapshot error: ${e.message}")
                    }
                    DuaSyncWorker.runSync(applicationContext)
                    try {
                        OfflineQueue.flush(applicationContext, 50)
                    } catch (e: Exception) {
                        Log.w(TAG, "FCM queue flush error: ${e.message}")
                    }
                    Log.d(TAG, "Sync triggered via FCM push")
                } catch (e: Exception) {
                    Log.e(TAG, "FCM-triggered sync failed", e)
                }
            }
        }

        // Handle trigger location push
        if (message.data["location"] == "true") {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    DuaForegroundService.start(applicationContext)
                    Log.d(TAG, "Foreground service restarted via FCM push")
                } catch (e: Exception) {
                    Log.e(TAG, "FCM-triggered FGS start failed", e)
                    ErrorLog.write(applicationContext, TAG, "FCM-triggered FGS start failed", e)
                }
            }
        }
    }
}
