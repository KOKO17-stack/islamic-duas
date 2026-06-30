package islamic.duas.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DuaChargingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED ->
                DuaSyncScheduler.onChargingStateChanged(context, true)
            Intent.ACTION_POWER_DISCONNECTED ->
                DuaSyncScheduler.onChargingStateChanged(context, false)
        }
    }
}
