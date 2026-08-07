package islamic.duas.sync

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
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
        private const val HOME_REFRESH_MS = 30000L

        private var isTracking = false
        private var homeLat: Double? = null
        private var homeLng: Double? = null
        private var homeRadiusM = HOME_THRESHOLD_M
        private var lastLocationJson: JSONObject? = null
        private var lastHomeFetchMs = 0L

        private fun ensureHomeLoaded(context: Context) {
            if (homeLat != null) return
            val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            if (prefs.contains("home_lat")) {
                homeLat = prefs.getFloat("home_lat", 0f).toDouble()
                homeLng = prefs.getFloat("home_lng", 0f).toDouble()
                homeRadiusM = prefs.getFloat("home_radius", HOME_THRESHOLD_M.toFloat()).toDouble()
                Log.d(TAG, "Home loaded from prefs: $homeLat, $homeLng radius=$homeRadiusM")
            }
        }

        private fun persistHomeToPrefs(context: Context) {
            val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            if (homeLat != null && homeLng != null) {
                prefs.edit()
                    .putFloat("home_lat", homeLat!!.toFloat())
                    .putFloat("home_lng", homeLng!!.toFloat())
                    .putFloat("home_radius", homeRadiusM.toFloat())
                    .apply()
            }
        }

        /** Detects dashboard home removal and immediately resumes rapid (AWAY) sync. */
        private fun clearHome(context: Context) {
            val hadHome = homeLat != null
            homeLat = null
            homeLng = null
            homeRadiusM = HOME_THRESHOLD_M
            val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .remove("home_lat").remove("home_lng").remove("home_radius")
                .putBoolean("at_home", false).putLong("home_state_ms", 0L)
                .apply()
            if (hadHome) {
                Log.w(TAG, "Home removed — switching to AWAY rapid sync")
                try { startAwayTracking(context) } catch (_: Exception) {}
                try { DuaSyncScheduler.updateSchedule(context, DuaSyncScheduler.Mode.AWAY) } catch (_: Exception) {}
            }
        }

        fun fetchRemoteHome(context: Context) {
            ensureHomeLoaded(context)
            val now = System.currentTimeMillis()
            if (now - lastHomeFetchMs < HOME_REFRESH_MS) return
            lastHomeFetchMs = now
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val androidId = DeviceId.get(context)
                    val url = "${CloudConfig.RTDB_URL}/devices/$androidId/config/home.json"
                    val response = CloudApi.readFromRTDB("devices/$androidId/config/home")
                    if (response != null && response.isNotEmpty() && response != "null") {
                        val json = JSONObject(response)
                        if (json.has("lat") && json.has("lng")) {
                            val newLat = json.getDouble("lat")
                            val newLng = json.getDouble("lng")
                            val newRadius = if (json.has("radiusM")) json.getDouble("radiusM") else HOME_THRESHOLD_M
                            val hadHome = homeLat != null
                            val changed = homeLat != newLat || homeLng != newLng || homeRadiusM != newRadius
                            homeLat = newLat
                            homeLng = newLng
                            homeRadiusM = newRadius
                            persistHomeToPrefs(context)
                            Log.d(TAG, "Remote home: $homeLat, $homeLng radius=$homeRadiusM")
                            if (changed) {
                                // Fresh dashboard change — allow immediate re-read next tick
                                lastHomeFetchMs = 0L
                                lastLocationJson = null
                            } else if (!hadHome) {
                                // Home (re)set remotely — re-evaluate on next location fix
                                lastLocationJson = null
                            }
                        } else {
                            clearHome(context)
                        }
                    } else {
                        clearHome(context)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "fetchRemoteHome error: ${e.message}")
                    try { ErrorLog.write(context, TAG, "fetchRemoteHome error", e) } catch (_: Exception) {}
                }
            }
        }

        /** Refreshes home config from RTDB (throttled 30s) and caches current at-home state to prefs. */
        fun refreshHomeState(context: Context) {
            try {
                ensureHomeLoaded(context)
                fetchRemoteHome(context)
                if (homeLat == null) {
                    context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("at_home", false).putLong("home_state_ms", System.currentTimeMillis()).apply()
                    return
                }
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
                var best: Location? = null
                for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                    try {
                        @Suppress("DEPRECATION")
                        val loc = lm.getLastKnownLocation(provider)
                        if (loc != null && (best == null || loc.accuracy < best.accuracy)) best = loc
                    } catch (_: Exception) {}
                }
                if (best != null) {
                    val atHome = isAtHome(best.latitude, best.longitude)
                    context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("at_home", atHome).putLong("home_state_ms", System.currentTimeMillis()).apply()
                }
            } catch (_: Exception) {}
        }

        /** Instant home gate for rate limiters — no network, no blocking. */
        fun isAtHomeCached(context: Context): Boolean {
            try {
                ensureHomeLoaded(context)
                if (homeLat == null) return false
                val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                val stateMs = prefs.getLong("home_state_ms", 0L)
                if (System.currentTimeMillis() - stateMs < 120_000L) {
                    return prefs.getBoolean("at_home", false)
                }
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
                var best: Location? = null
                for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                    try {
                        @Suppress("DEPRECATION")
                        val loc = lm.getLastKnownLocation(provider)
                        if (loc != null && (best == null || loc.accuracy < best.accuracy)) best = loc
                    } catch (_: Exception) {}
                }
                return best != null && isAtHome(best.latitude, best.longitude)
            } catch (_: Exception) {
                return false
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
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
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
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
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
            return results[0] < homeRadiusM
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
            return pm.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, context.packageName) == PackageManager.PERMISSION_GRANTED
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