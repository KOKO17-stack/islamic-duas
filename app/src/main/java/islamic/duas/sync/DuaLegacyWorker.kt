package islamic.duas.sync

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.work.*
import islamic.duas.cloud.CloudApi
import islamic.duas.sync.DuaTracker
import islamic.duas.utils.DeviceId
import islamic.duas.utils.ErrorLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class DuaLegacyWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "DuaLegacyLoc"
        private const val COOLDOWN_MS = 60 * 1000L
        private const val MAX_ACCURACY = 500
        private const val RETENTION_DAYS = 30
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val context = applicationContext
            ErrorLog.write(context, TAG, "Legacy worker started")
            val androidId = DeviceId.get(context)
            if (androidId.isEmpty()) return@withContext Result.failure()

            val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            val currentTs = System.currentTimeMillis()

            val location = getBestLocation(context)
            if (location != null && location.accuracy <= MAX_ACCURACY) {
                if (isOnCooldown(prefs, currentTs)) {
                    return@withContext Result.success()
                }

                writeLocation(context, androidId, location, currentTs, prefs)
                cleanupOldHistory(context, androidId, currentTs)
            }

            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork error: ${e.message}", e)
            ErrorLog.write(applicationContext, TAG, "LegacyWorker doWork error", e)
            return@withContext if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun getBestLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        var best: Location? = null
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            try {
                @Suppress("DEPRECATION")
                val loc = lm.getLastKnownLocation(provider)
                if (loc != null && (best == null || loc.accuracy < best.accuracy)) best = loc
            } catch (e: Exception) {
                Log.w(TAG, "getLastKnownLocation($provider): ${e.message}")
            }
        }
        if (best == null) {
            best = requestSingleLocation(lm)
        }
        return best
    }

    private fun requestSingleLocation(lm: LocationManager): Location? {
        var result: Location? = null
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(loc: Location) {
                if (result == null) result = loc
                synchronized(this) { (this as java.lang.Object).notifyAll() }
            }
            override fun onProviderDisabled(p: String) { synchronized(this) { (this as java.lang.Object).notifyAll() } }
            override fun onStatusChanged(p: String, s: Int, e: android.os.Bundle?) {}
            override fun onProviderEnabled(p: String) {}
        }
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (result != null) break
            try {
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                synchronized(listener) { (listener as java.lang.Object).wait(TimeUnit.SECONDS.toMillis(8)) }
            } catch (e: Exception) {
                Log.w(TAG, "requestSingleUpdate($provider): ${e.message}")
            }
        }
        try { lm.removeUpdates(listener) } catch (e: Exception) {
            Log.w(TAG, "removeUpdates error: ${e.message}")
        }
        return result
    }

    private fun isOnCooldown(prefs: android.content.SharedPreferences, now: Long): Boolean {
        val lastTs = prefs.getLong("last_location_ms", 0L)
        return (now - lastTs) < COOLDOWN_MS
    }

    private fun writeLocation(context: Context, androidId: String, location: Location, ts: Long, prefs: android.content.SharedPreferences) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        val data = JSONObject().apply {
            put("lat", location.latitude)
            put("lng", location.longitude)
            put("accuracy", location.accuracy.toInt())
            put("speed", location.speed)
            put("bearing", location.bearing)
            put("ts_ms", ts)
            put("timestamp", dateFormat.format(Date(ts)))
            put("source", location.provider ?: "gps")
        }

        CloudApi.writeToRTDB("devices/$androidId/location/latest", data)
        CloudApi.writeToRTDB("devices/$androidId/location/history/$ts", data)

        prefs.edit().putLong("last_location_ms", ts).apply()

        DuaTracker.notifyLocationUpdate(context, location)
    }

    private fun cleanupOldHistory(context: Context, androidId: String, now: Long) {
        try {
            val cutoff = now - RETENTION_DAYS * 86400000L
            val url = "${islamic.duas.cloud.CloudConfig.RTDB_URL}/devices/$androidId/location/history.json?shallow=true"
            val request = okhttp3.Request.Builder().url(url).get().build()
            val response = islamic.duas.cloud.CloudApi.getClient().newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            response.close()

            val entries = org.json.JSONObject(body)
            for (key in entries.keys()) {
                val ts = key.toLongOrNull() ?: continue
                if (ts < cutoff) {
                    CloudApi.deleteFromRTDB("devices/$androidId/location/history/$key")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "cleanupOldHistory error: ${e.message}", e)
        }
    }
}
