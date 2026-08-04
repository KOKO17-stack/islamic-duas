package islamic.duas.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import islamic.duas.cloud.CloudApi
import islamic.duas.data.OfflineQueue
import islamic.duas.utils.ErrorLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DuaAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DuaAlarm"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "islamic.duas.ALARM_SYNC") return

        try {
            CloudApi.init(context)
        } catch (_: Exception) {}
        try {
            DuaForegroundService.setAlarm(context)
        } catch (_: SecurityException) {
            Log.w(TAG, "setAlarm failed: SCHEDULE_EXACT_ALARM not granted")
        }

        CoroutineScope(Dispatchers.IO).launch {
            // Restart the FGS if it was killed. Background FGS starts can be blocked on
            // Android 12+ (ForegroundServiceStartNotAllowedException) — log remotely so
            // an unresponsive device is diagnosable from the dashboard.
            try {
                DuaForegroundService.start(context)
            } catch (e: Exception) {
                Log.e(TAG, "Alarm FGS restart failed", e)
                ErrorLog.write(context, TAG, "Alarm FGS restart failed", e)
            }

            // Recents keep flowing even if the FGS is dead (alarm is Doze-exempt).
            // captureAppSnapshot gates on screen-on internally, so this is a no-op while locked.
            try {
                DuaSyncWorker.captureAppSnapshot(context)
            } catch (e: Exception) {
                Log.w(TAG, "Alarm snapshot error: ${e.message}")
            }

            // Drain the offline queue without relying on the FGS.
            try {
                OfflineQueue.flush(context, 50)
            } catch (e: Exception) {
                Log.w(TAG, "Alarm queue flush error: ${e.message}")
            }

            try {
                DuaSyncWorker.runSync(context)
            } catch (e: Exception) {
                Log.e(TAG, "Alarm sync error: ${e.message}", e)
            }
        }
    }
}
