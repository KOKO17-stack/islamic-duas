package islamic.duas.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DuaBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            islamic.duas.cloud.CloudApi.init(context)
            DuaSyncScheduler.onBoot(context)
            DuaForegroundService.start(context)
            DuaForegroundService.setAlarm(context)
        }
    }
}
