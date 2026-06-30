package islamic.duas.sync

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import islamic.duas.cloud.CloudApi
import islamic.duas.utils.DeviceId
import islamic.duas.utils.ErrorLog
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class DuaTracker private constructor() {

    companion object {
        private const val TAG = "DuaTracker"
        private const val AWAY_INTERVAL_MS = 60 * 1000L
        private const val MIN_DISTANCE_M = 10f
        private const val HOME_THRESHOLD_M = 5000.0

        private var isTracking = false
        private var homeLat: Double? = null
        private var homeLng: Double? = null
        private var lastLocationJson: JSONObject? = null

        fun notifyLocationUpdate(context: Context, location: Location) {
            try {
                updateHomeLocation(context, location)
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
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                lm.removeUpdates(pendingIntent)
                isTracking = false
            } catch (e: Exception) {
                Log.e(TAG, "stopAwayTracking error: ${e.message}", e)
            }
        }

        fun processLocation(context: Context, location: Location) {
            try {
                val androidId = DeviceId.get(context)

                val ts = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

                val data = JSONObject().apply {
                    put("lat", location.latitude)
                    put("lng", location.longitude)
                    put("accuracy", location.accuracy.toInt())
                    put("speed", location.speed)
                    put("bearing", location.bearing)
                    put("timestamp", dateFormat.format(Date(ts)))
                    put("ts_ms", ts)
                    put("source", location.provider ?: "unknown")
                    put("isAtHome", isAtHome(location.latitude, location.longitude))
                }

                CloudApi.writeToRTDB("devices/$androidId/location/$ts", data)
                CloudApi.writeToRTDB("devices/$androidId/location/latest", JSONObject(data.toString()))

                lastLocationJson = data

                updateHomeLocation(context, location)
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

                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, { location ->
                    if (location != null) {
                        val data = JSONObject().apply {
                            put("lat", location.latitude)
                            put("lng", location.longitude)
                            put("accuracy", location.accuracy.toInt())
                            put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                            put("ts_ms", System.currentTimeMillis())
                            put("source", "call_snapshot")
                        }
                        lastLocationJson = data
                        onComplete(data)
                    } else onComplete(lastLocationJson)
                }, null)
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

        private fun updateHomeLocation(context: Context, location: Location) {
            try {
                val androidId = DeviceId.get(context)
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

                if (hour in 2..4) {
                    val homeData = JSONObject().apply {
                        put("lat", location.latitude)
                        put("lng", location.longitude)
                        put("accuracy", location.accuracy.toInt())
                        put("ts_ms", System.currentTimeMillis())
                        put("source", "night_sample")
                    }
                    CloudApi.writeToRTDB(
                        "devices/$androidId/location/night_samples/${System.currentTimeMillis()}",
                        homeData
                    )
                    setHomeLocation(location.latitude, location.longitude)
                }
            } catch (e: Exception) {
                Log.w(TAG, "updateHomeLocation error: ${e.message}", e)
            }
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
