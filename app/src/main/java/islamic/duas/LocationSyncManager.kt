package islamic.duas

import android.content.Context
import android.location.Location
import islamic.duas.cloud.CloudApi
import islamic.duas.sync.DuaTracker
import islamic.duas.utils.DeviceId
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object LocationSyncManager {

    private const val COOLDOWN_MS = 15_000L
    private const val MAX_ACCURACY = 500
    private const val HOME_HISTORY_COOLDOWN_MS = 120_000L

    private const val HIGH_ACC_COOLDOWN_MS = 60_000L
    private const val HIGH_ACC_MAX_ACCURACY = 10

    fun writeLocation(context: Context, location: Location, source: String) {
        if (location.accuracy > MAX_ACCURACY) return
        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastMs = prefs.getLong("location_cooldown", 0L)
        if (now - lastMs < COOLDOWN_MS) return
        prefs.edit().putLong("location_cooldown", now).apply()

        val androidId = DeviceId.get(context)
        val cal = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.US)
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val monthStr = "%02d".format(month)
        val dayStr = "%02d".format(day)

        val isAtHome = try { DuaTracker.isAtHome(location.latitude, location.longitude) } catch (_: Exception) { false }

        val data = JSONObject().apply {
            put("lat", location.latitude)
            put("lng", location.longitude)
            put("accuracy", location.accuracy.toInt())
            put("speed", location.speed)
            put("bearing", location.bearing)
            put("ts_ms", now)
            put("timestamp", dateFormat.format(Date(now)))
            put("source", source)
            put("isAtHome", isAtHome)
            put("isHighAccuracy", false)
            put("satellites", 0)
        }

        // Always write to latest
        CloudApi.writeToRTDB("devices/$androidId/location/latest", data)

        // History: always when away (every 15s), every 15min when home
        val homeHistoryOk = if (isAtHome) {
            val lastHomeHistory = prefs.getLong("home_history_last_ms", 0L)
            now - lastHomeHistory >= HOME_HISTORY_COOLDOWN_MS
        } else true

        if (homeHistoryOk) {
            CloudApi.writeToRTDB("devices/$androidId/location/history/$year/$monthStr/$dayStr/$now", data)
            if (isAtHome) prefs.edit().putLong("home_history_last_ms", now).apply()
        }

        // Save last position for dedup
        prefs.edit().putFloat("last_loc_lat", location.latitude.toFloat())
            .putFloat("last_loc_lng", location.longitude.toFloat()).apply()

        // Notify tracker for proximity analysis
        DuaTracker.notifyLocationUpdate(context, location)
    }

    fun writeHighAccuracyLocation(context: Context, location: Location, source: String) {
        if (location.accuracy > HIGH_ACC_MAX_ACCURACY) return
        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastMs = prefs.getLong("high_accuracy_cooldown", 0L)
        if (now - lastMs < HIGH_ACC_COOLDOWN_MS) return
        prefs.edit().putLong("high_accuracy_cooldown", now).apply()

        val androidId = DeviceId.get(context)
        val cal = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.US)
        val year = cal.get(Calendar.YEAR)
        val month = "%02d".format(cal.get(Calendar.MONTH) + 1)
        val day = "%02d".format(cal.get(Calendar.DAY_OF_MONTH))

        val satellites = try { location.extras?.getInt("satellites") } catch (_: Exception) { null }

        val data = JSONObject().apply {
            put("lat", location.latitude)
            put("lng", location.longitude)
            put("accuracy", location.accuracy.toInt())
            put("speed", location.speed)
            put("bearing", location.bearing)
            put("ts_ms", now)
            put("timestamp", dateFormat.format(Date(now)))
            put("source", source)
            put("isHighAccuracy", true)
            if (satellites != null) put("satellites", satellites)
        }

        CloudApi.writeToRTDB("devices/$androidId/location/latest", data)
        CloudApi.writeToRTDB("devices/$androidId/location/history/$year/$month/$day/$now", data)
        prefs.edit().putLong("last_high_acc_ms", now).apply()

        DuaTracker.notifyLocationUpdate(context, location)
    }
}