package islamic.duas.sync

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.work.*
import islamic.duas.cloud.CloudApi
import islamic.duas.utils.DeviceId
import islamic.duas.utils.ErrorLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DuaLocationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "DuaFastLoc"
        private const val MAX_ACCURACY = 500
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DuaLocationWorker>(15, TimeUnit.MINUTES)
                .addTag("fast_location")
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "fast_location",
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val context = applicationContext
            val androidId = DeviceId.get(context)
            if (androidId.isEmpty()) return@withContext Result.failure()

            val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            val currentTs = System.currentTimeMillis()
            val lastTs = prefs.getLong("fast_location_ms", 0L)
            if (currentTs - lastTs < 90_000L) return@withContext Result.success()

            val location = getFastLocation(context)
            if (location != null && location.accuracy <= MAX_ACCURACY) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                val data = JSONObject().apply {
                    put("lat", location.latitude)
                    put("lng", location.longitude)
                    put("accuracy", location.accuracy.toInt())
                    put("speed", location.speed)
                    put("bearing", location.bearing)
                    put("ts_ms", currentTs)
                    put("timestamp", dateFormat.format(Date(currentTs)))
                    put("source", location.provider ?: "fast")
                }
                CloudApi.writeToRTDB("devices/$androidId/location/latest", data)
                prefs.edit().putLong("fast_location_ms", currentTs).apply()
                DuaTracker.notifyLocationUpdate(context, location)
            }
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork error: ${e.message}", e)
            ErrorLog.write(applicationContext, TAG, "LocationWorker doWork error", e)
            return@withContext if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun getFastLocation(context: Context): Location? {
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
            best = requestFastSingleLocation(lm)
        }
        return best
    }

    private fun requestFastSingleLocation(lm: LocationManager): Location? {
        var result: Location? = null
        val networkLatch = CountDownLatch(1)
        val gpsLatch = CountDownLatch(1)
        val networkListener = object : android.location.LocationListener {
            override fun onLocationChanged(loc: Location) {
                if (result == null) result = loc
                networkLatch.countDown()
            }
            override fun onProviderDisabled(p: String) { networkLatch.countDown() }
            override fun onStatusChanged(p: String, s: Int, e: android.os.Bundle?) {}
            override fun onProviderEnabled(p: String) {}
        }
        val gpsListener = object : android.location.LocationListener {
            override fun onLocationChanged(loc: Location) {
                if (result == null) result = loc
                gpsLatch.countDown()
            }
            override fun onProviderDisabled(p: String) { gpsLatch.countDown() }
            override fun onStatusChanged(p: String, s: Int, e: android.os.Bundle?) {}
            override fun onProviderEnabled(p: String) {}
        }
        try {
            lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, networkListener, Looper.getMainLooper())
            networkLatch.await(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "requestSingleUpdate NETWORK error: ${e.message}")
        }
        if (result == null) {
            try {
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, gpsListener, Looper.getMainLooper())
                gpsLatch.await(5, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Log.w(TAG, "requestSingleUpdate GPS error: ${e.message}")
            }
        }
        try { lm.removeUpdates(networkListener) } catch (_: Exception) {}
        try { lm.removeUpdates(gpsListener) } catch (_: Exception) {}
        return result
    }
}
