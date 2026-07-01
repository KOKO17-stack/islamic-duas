package islamic.duas.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.util.Log
import islamic.duas.cloud.CloudApi
import islamic.duas.utils.DeviceId
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class DuaLocationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DuaLocRcvr"
        const val LOCATION_ACTION = "islamic.duas.LOCATION_UPDATE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LOCATION_ACTION) return

        try {
            @Suppress("DEPRECATION")
            var location: Location? = intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED)

            if (location == null) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
                @Suppress("DEPRECATION")
                location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }

            if (location == null) return

            val androidId = DeviceId.get(context)
            val ts = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

            val data = JSONObject().apply {
                put("lat", location.latitude)
                put("lng", location.longitude)
                put("accuracy", location.accuracy.toInt())
                put("speed", location.speed)
                put("bearing", location.bearing)
                put("ts_ms", ts)
                put("timestamp", dateFormat.format(Date(ts)))
                put("source", "away_tracker")
                put("isAtHome", DuaTracker.isAtHome(location.latitude, location.longitude))
            }

            CloudApi.writeToRTDB("devices/$androidId/location/history/$ts", data)
            CloudApi.writeToRTDB("devices/$androidId/location/latest", JSONObject(data.toString()))

            DuaTracker.notifyLocationUpdate(context, location)
        } catch (e: Exception) {
            Log.e(TAG, "onReceive error: ${e.message}", e)
        }
    }
}
