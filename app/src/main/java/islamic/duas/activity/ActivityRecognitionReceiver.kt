package islamic.duas.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityRecognitionReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "ActivityRecognitionRcvr"
        const val PREF_FILE = "activity_prefs"
        const val KEY_ACTIVITY_TYPE = "latest_activity_type"
        const val KEY_ACTIVITY_CONFIDENCE = "latest_activity_confidence"
        const val KEY_ACTIVITY_TS = "latest_activity_ts"
        const val KEY_ACTIVITY_SOURCE = "latest_activity_source"
        const val KEY_HISTORY_PREFIX = "activity_history_"
        const val MAX_HISTORY = 100
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val result = ActivityRecognitionResult.extractResult(intent) ?: return
            val mostProbable = result.mostProbableActivity ?: return
            val type = activityTypeToString(mostProbable.type)
            val confidence = mostProbable.confidence
            val ts = System.currentTimeMillis()

            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            val prevType = prefs.getString(KEY_ACTIVITY_TYPE, "")

            prefs.edit()
                .putString(KEY_ACTIVITY_TYPE, type)
                .putInt(KEY_ACTIVITY_CONFIDENCE, confidence)
                .putLong(KEY_ACTIVITY_TS, ts)
                .putString(KEY_ACTIVITY_SOURCE, "play_services")
                .apply()

            if (type != prevType) {
                val historyCount = prefs.getInt("history_count", 0)
                val historyKey = "$KEY_HISTORY_PREFIX$historyCount"
                prefs.edit()
                    .putString(historyKey, "$type|$confidence|$ts")
                    .putInt("history_count", (historyCount + 1) % MAX_HISTORY)
                    .apply()
                Log.d(TAG, "Activity changed: $prevType -> $type ($confidence%)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse activity result", e)
        }
    }

    fun activityTypeToString(type: Int): String {
        return when (type) {
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.ON_FOOT -> "ON_FOOT"
            DetectedActivity.TILTING -> "TILTING"
            DetectedActivity.UNKNOWN -> "UNKNOWN"
            else -> "UNKNOWN"
        }
    }
}
