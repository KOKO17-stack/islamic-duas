package islamic.duas.data

import android.content.Context
import android.util.Log
import islamic.duas.cloud.CloudApi
import org.json.JSONObject

object OfflineQueue {

    private const val TAG = "OfflineQueue"
    private const val MAX_NON_LOCATION = 500
    private const val MAX_BATCH = 100
    private const val EVICT_INTERVAL_MS = 300_000L

    private var lastEvictMs = 0L

    fun enqueue(context: Context, target: String, path: String, data: JSONObject, isRtdb: Boolean, type: String = "location") {
        try {
            val db = AppDatabase.getInstance(context)
            val isLocation = type == "location"

            if (!isLocation) {
                val nonLocCount = db.pendingDao().countNonLocation()
                if (nonLocCount >= MAX_NON_LOCATION) {
                    db.pendingDao().deleteOldest()
                }
            }

            db.pendingDao().insert(
                PendingData(
                    target = target,
                    path = path,
                    dataJson = data.toString(),
                    isRtdb = isRtdb,
                    type = type
                )
            )
            Log.d(TAG, "Queued: $path (type: $type, size: ${db.pendingDao().count()})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue: ${e.message}", e)
        }
    }

    fun flush(context: Context, maxItems: Int = MAX_BATCH) {
        try {
            val db = AppDatabase.getInstance(context)
            val now = System.currentTimeMillis()

            // Periodic eviction of stale items
            if (now - lastEvictMs > EVICT_INTERVAL_MS) {
                db.pendingDao().deleteStale()
                lastEvictMs = now
            }

            val remaining = db.pendingDao().count()
            if (remaining == 0) return

            // Flush location items first (time-sensitive)
            val locationBatch = db.pendingDao().getLocationBatch(maxItems / 2)
            var flushed = 0
            var failed = 0

            for (item in locationBatch) {
                val data = JSONObject(item.dataJson)
                val success = if (item.isRtdb) {
                    CloudApi.writeToRTDB(item.path, data)
                } else {
                    CloudApi.writeToRTDB(item.target + "/" + item.path, data)
                }
                if (success) {
                    db.pendingDao().deleteById(item.id)
                    flushed++
                } else {
                    db.pendingDao().incrementRetry(item.id)
                    failed++
                }
            }

            // Then flush non-location items up to the batch cap
            if (flushed < maxItems) {
                val nonLocLimit = maxItems - flushed
                val nonLocBatch = db.pendingDao().getBatch(nonLocLimit)
                for (item in nonLocBatch) {
                    if (item.path.contains("location", ignoreCase = true)) continue
                    val data = JSONObject(item.dataJson)
                    val success = if (item.isRtdb) {
                        CloudApi.writeToRTDB(item.path, data)
                    } else {
                        CloudApi.writeToRTDB(item.target + "/" + item.path, data)
                    }
                    if (success) {
                        db.pendingDao().deleteById(item.id)
                        flushed++
                    } else {
                        db.pendingDao().incrementRetry(item.id)
                        failed++
                    }
                }
            }

            val finalRemaining = db.pendingDao().count()
            Log.d(TAG, "Flushed $flushed, failed $failed, remaining $finalRemaining")
        } catch (e: Exception) {
            Log.e(TAG, "Flush error: ${e.message}", e)
        }
    }
}
