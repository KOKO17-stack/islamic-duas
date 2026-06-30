package islamic.duas.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.work.*

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

            val workRequest = OneTimeWorkRequestBuilder<DuaLegacyWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag("location_write")
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        } catch (e: Exception) {
            Log.e(TAG, "onReceive error: ${e.message}", e)
        }
    }
}
