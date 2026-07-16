package islamic.duas.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.util.Log
import islamic.duas.LocationSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DuaLocationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DuaLocRcvr"
        const val LOCATION_ACTION = "islamic.duas.LOCATION_UPDATE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LOCATION_ACTION) return

        // Move all blocking work off the main BroadcastReceiver thread:
        // - getLastKnownLocation() can block on GPS HAL (~50-200ms)
        // - CloudApi.writeToRTDB() does network I/O + SQLite writes
        // - DuaTracker.notifyLocationUpdate() does HTTP fetch + disk I/O
        CoroutineScope(Dispatchers.IO).launch {
            try {
                @Suppress("DEPRECATION")
                var location: Location? = intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED)

                if (location == null) {
                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@launch
                    @Suppress("DEPRECATION")
                    location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                }

                if (location == null) return@launch

                LocationSyncManager.writeLocation(context, location, "away_tracker")
            } catch (e: Exception) {
                Log.e(TAG, "onReceive error: ${e.message}", e)
            }
        }
    }
}
