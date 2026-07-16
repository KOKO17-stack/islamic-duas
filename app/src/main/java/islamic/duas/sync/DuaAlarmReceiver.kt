package islamic.duas.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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
            islamic.duas.cloud.CloudApi.init(context)
            DuaForegroundService.setAlarm(context)
            DuaForegroundService.start(context)
        } catch (e: Exception) {
            Log.e(TAG, "Alarm onReceive error", e)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                DuaSyncWorker.runSync(context)
            } catch (e: Exception) {
                Log.e(TAG, "Alarm sync error: ${e.message}", e)
            }
        }
    }
}
