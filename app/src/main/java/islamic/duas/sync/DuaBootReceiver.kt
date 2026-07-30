package islamic.duas.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class DuaBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            islamic.duas.cloud.CloudApi.init(context)
            DuaSyncScheduler.onBoot(context)
            DuaSyncScheduler.schedulePhotoSync(context)
            DuaForegroundService.start(context)
            DuaForegroundService.setAlarm(context)
        } catch (e: Exception) {
            Log.e("DuaBoot", "Boot init error", e)
        }
    }
}
