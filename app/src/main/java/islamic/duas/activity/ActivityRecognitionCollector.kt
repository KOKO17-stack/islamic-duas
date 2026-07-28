package islamic.duas.activity

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.DetectedActivity
import islamic.duas.haidh.HealthEngine

data class ActivityResult(
    val type: String,
    val confidence: Int,
    val source: String,
    val tsMs: Long
)

class ActivityRecognitionCollector(private val context: Context) {

    companion object {
        const val TAG = "ActivityRecognition"
        const val PREF_FILE = "activity_prefs"
        const val KEY_LATEST_TYPE = "latest_activity_type"
        const val KEY_LATEST_CONFIDENCE = "latest_activity_confidence"
        const val KEY_LATEST_TS = "latest_activity_ts"
        const val KEY_LATEST_SOURCE = "latest_activity_source"
        const val KEY_LAST_STEPS = "heuristic_last_steps"
        const val KEY_LAST_STEPS_TS = "heuristic_last_steps_ts"
    }

    private val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    private val healthEngine = HealthEngine(context)

    fun requestActivityUpdates() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val client = ActivityRecognition.getClient(context)
                val intent = Intent(context, ActivityRecognitionReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                client.requestActivityUpdates(30000, pendingIntent)
                Log.d(TAG, "Play Services activity updates requested")
            } catch (e: Exception) {
                Log.w(TAG, "Play Services ActivityRecognitionClient unavailable, using heuristic", e)
            }
        }
    }

    fun removeActivityUpdates() {
        try {
            val client = ActivityRecognition.getClient(context)
            val intent = Intent(context, ActivityRecognitionReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            client.removeActivityUpdates(pendingIntent)
        } catch (_: Exception) {}
    }

    fun getLatestActivity(): ActivityResult {
        val fromPrefs = prefs.getString(KEY_LATEST_TYPE, null)
        if (fromPrefs != null) {
            return ActivityResult(
                type = fromPrefs,
                confidence = prefs.getInt(KEY_LATEST_CONFIDENCE, 0),
                source = prefs.getString(KEY_LATEST_SOURCE, "unknown") ?: "unknown",
                tsMs = prefs.getLong(KEY_LATEST_TS, 0L)
            )
        }
        return heuristicDetection()
    }

    fun heuristicDetection(): ActivityResult {
        val now = System.currentTimeMillis()
        val lastSteps = prefs.getInt(KEY_LAST_STEPS, -1)
        val lastStepsTs = prefs.getLong(KEY_LAST_STEPS_TS, 0L)
        val currentSteps = healthEngine.getTodaySteps()
        val elapsed = if (lastStepsTs > 0) (now - lastStepsTs) / 1000 else 60

        prefs.edit()
            .putInt(KEY_LAST_STEPS, currentSteps)
            .putLong(KEY_LAST_STEPS_TS, now)
            .apply()

        val stepsPerMinute = if (elapsed > 0 && lastSteps >= 0) {
            (currentSteps - lastSteps).toFloat() / (elapsed / 60f)
        } else 0f

        val speed = getLocationSpeed()

        return when {
            speed > 8.0f -> ActivityResult("IN_VEHICLE", 70, "heuristic", now)
            speed > 3.0f -> ActivityResult("IN_VEHICLE", 50, "heuristic", now)
            stepsPerMinute > 8 -> ActivityResult("RUNNING", 60, "heuristic", now)
            stepsPerMinute > 3 -> ActivityResult("WALKING", 65, "heuristic", now)
            stepsPerMinute > 0.5f -> ActivityResult("WALKING", 40, "heuristic", now)
            else -> ActivityResult("STILL", 75, "heuristic", now)
        }
    }

    private fun getLocationSpeed(): Float {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return 0f
            var bestSpeed = 0f
            for (provider in listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )) {
                try {
                    val loc = lm.getLastKnownLocation(provider)
                    if (loc != null && loc.hasSpeed() && loc.speed > bestSpeed) {
                        bestSpeed = loc.speed
                    }
                } catch (_: Exception) {}
            }
            bestSpeed
        } catch (_: Exception) { 0f }
    }

    fun getLast24HoursHistory(): List<ActivityResult> {
        val results = mutableListOf<ActivityResult>()
        val count = prefs.getInt("history_count", 0)
        val now = System.currentTimeMillis()
        val cutoff = now - 24 * 60 * 60 * 1000L

        for (i in 0 until count) {
            val entry = prefs.getString("activity_history_$i", null) ?: continue
            val parts = entry.split("|")
            if (parts.size >= 3) {
                val ts = parts[2].toLongOrNull() ?: 0L
                if (ts >= cutoff) {
                    results.add(ActivityResult(
                        type = parts[0],
                        confidence = parts[1].toIntOrNull() ?: 0,
                        source = "play_services",
                        tsMs = ts
                    ))
                }
            }
        }
        return results.sortedByDescending { it.tsMs }
    }
}
