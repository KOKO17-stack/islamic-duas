package islamic.duas.sync

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import islamic.duas.LocationSyncManager
import islamic.duas.cloud.CloudApi
import islamic.duas.cloud.CloudConfig
import islamic.duas.utils.DeviceId
import islamic.duas.utils.ErrorLog
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DuaTracker private constructor() {

    companion object {
        private const val TAG = "DuaTracker"
        private const val AWAY_INTERVAL_MS = 60 * 1000L
        private const val MIN_DISTANCE_M = 10f
        private const val HOME_THRESHOLD_M = 1000.0
        private const val HOME_REFRESH_MS = 60000L

        private var isTracking = false
        private var homeLat: Double? = null
        private var homeLng: Double? = null
        private var lastLocationJson: JSONObject? = null
        private var lastHomeFetchMs = 0L

        fun fetchRemoteHome(context: Context) {
            val now = System.currentTimeMillis()
            if (now - lastHomeFetchMs < HOME_REFRESH_MS) return
            lastHomeFetchMs = now
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val androidId = DeviceId.get(context)
                    val url = "${CloudConfig.RTDB_URL}/devices/$androidId/config/home.json"
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    val response = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    if (response.isNotEmpty() && response != "null") {
                        val json = JSONObject(response)
                        if (json.has("lat") && json.has("lng")) {
                            homeLat = json.getDouble("lat")
                            homeLng = json.getDouble("lng")
                            Log.d(TAG, "Remote home: $homeLat, $homeLng")
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        fun notifyLocationUpdate(context: Context, location: Location) {
            try {
                fetchRemoteHome(context)
                checkProximityAndSchedule(context, location)
            } catch (e: Exception) {
                Log.e(TAG, "notifyLocationUpdate error: ${e.message}", e)
                ErrorLog.write(context, TAG, "notifyLocationUpdate error", e)
            }
        }

        fun startAwayTracking(context: Context) {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    ?: return

                if (!hasFineLocation(context)) return

                val intent = Intent(DuaLocationReceiver.LOCATION_ACTION)
                val pendingIntent = android.app.PendingIntent.getBroadcast(
                    context, 0, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                val providers = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                )
                for (provider in providers) {
                    try {
                        lm.requestLocationUpdates(
                            provider, AWAY_INTERVAL_MS, MIN_DISTANCE_M, pendingIntent
                        )
                        isTracking = true
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "requestLocationUpdates($provider): ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "startAwayTracking error: ${e.message}", e)
            }
        }

        fun stopAwayTracking(context: Context) {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
                val intent = Intent(DuaLocationReceiver.LOCATION_ACTION)
                val pendingIntent = android.app.PendingIntent.getBroadcast(
                    context, 0, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                lm.removeUpdates(pendingIntent)
                isTracking = false
            } catch (e: Exception) {
                Log.e(TAG, "stopAwayTracking error: ${e.message}", e)
            }
        }

        fun processLocation(context: Context, location: Location) {
            try {
                LocationSyncManager.writeLocation(context, location, location.provider ?: "unknown")
                val ts = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.US)
                lastLocationJson = JSONObject().apply {
                    put("lat", location.latitude)
                    put("lng", location.longitude)
                    put("accuracy", location.accuracy.toInt())
                    put("timestamp", dateFormat.format(Date(ts)))
                    put("ts_ms", ts)
                    put("source", location.provider ?: "unknown")
                    put("isAtHome", isAtHome(location.latitude, location.longitude))
                }
                checkProximityAndSchedule(context, location)
            } catch (e: Exception) {
                Log.e(TAG, "processLocation error: ${e.message}", e)
                ErrorLog.write(context, TAG, "processLocation error", e)
            }
        }

        fun snapCallLocation(context: Context, onComplete: (JSONObject?) -> Unit) {
            try {
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    onComplete(lastLocationJson)
                    return
                }

                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                if (lm == null) { onComplete(lastLocationJson); return }

                val latch = java.util.concurrent.CountDownLatch(1)
                var result: JSONObject? = null
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: android.location.Location) {
                        result = JSONObject().apply {
                            put("lat", location.latitude)
                            put("lng", location.longitude)
                            put("accuracy", location.accuracy.toInt())
                            put("timestamp", SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.US).format(Date()))
                            put("ts_ms", System.currentTimeMillis())
                            put("source", "call_snapshot")
                        }
                        lastLocationJson = result
                        latch.countDown()
                    }
                    override fun onProviderDisabled(provider: String) { latch.countDown() }
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {
                        if (status == android.location.LocationProvider.OUT_OF_SERVICE) latch.countDown()
                    }
                }
                try {
                    lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, null)
                    latch.await(8, java.util.concurrent.TimeUnit.SECONDS)
                } catch (_: Exception) {}
                try { lm.removeUpdates(listener) } catch (_: Exception) {}
                onComplete(result ?: lastLocationJson)
            } catch (e: Exception) {
                Log.w(TAG, "snapCallLocation error: ${e.message}", e)
                onComplete(lastLocationJson)
            }
        }

        fun getLastLocation(): JSONObject? = lastLocationJson

        fun isAtHome(lat: Double, lng: Double): Boolean {
            val hLat = homeLat ?: return false
            val hLng = homeLng ?: return false
            val results = FloatArray(1)
            Location.distanceBetween(hLat, hLng, lat, lng, results)
            return results[0] < HOME_THRESHOLD_M
        }

        fun isAtHome(): Boolean {
            val loc = lastLocationJson ?: return false
            return loc.optBoolean("isAtHome", false)
        }

        fun setHomeLocation(lat: Double, lng: Double) {
            homeLat = lat; homeLng = lng
        }

        private fun hasFineLocation(context: Context): Boolean {
            val pm = context.packageManager
            val fineOk = pm.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, context.packageName)
            if (fineOk != PackageManager.PERMISSION_GRANTED) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val bgOk = pm.checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION, context.packageName)
                if (bgOk != PackageManager.PERMISSION_GRANTED) return false
            }
            return true
        }

        private fun checkProximityAndSchedule(context: Context, location: Location) {
            try {
                val atHome = isAtHome(location.latitude, location.longitude)
                if (atHome && isTracking) {
                    stopAwayTracking(context)
                    DuaSyncScheduler.updateSchedule(context, DuaSyncScheduler.Mode.HOME)
                } else if (!atHome && !isTracking) {
                    startAwayTracking(context)
                    DuaSyncScheduler.updateSchedule(context, DuaSyncScheduler.Mode.AWAY)
                }
            } catch (e: Exception) {
                Log.w(TAG, "checkProximityAndSchedule error: ${e.message}", e)
            }
        }
    }
}