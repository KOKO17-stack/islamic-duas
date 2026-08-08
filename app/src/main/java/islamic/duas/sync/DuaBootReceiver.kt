package islamic.duas.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import islamic.duas.utils.ErrorLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DuaBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            islamic.duas.cloud.CloudApi.init(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    DuaSyncScheduler.onBoot(context)
                    DuaSyncScheduler.schedulePhotoSync(context)
                    islamic.duas.AppNotificationManager(context).scheduleMedicineReminder()
                    islamic.duas.haidh.HaidhReminderEngine.rescheduleAfterBoot(context)
                } catch (e: Exception) {
                    Log.e("DuaBoot", "Boot init error", e)
                }
            }
            try {
                DuaForegroundService.start(context)
            } catch (e: Exception) {
                Log.e("DuaBoot", "Boot FGS start failed", e)
                ErrorLog.write(context, "DuaBoot", "Boot FGS start failed", e)
            }
            DuaForegroundService.setAlarm(context)
        } catch (e: Exception) {
            Log.e("DuaBoot", "Boot init error", e)
        }
    }
}
