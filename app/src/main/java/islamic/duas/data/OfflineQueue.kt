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
    // Media payloads (photos/voice notes) can be multi-MB base64 blobs. Queuing them
    // during an outage bloated SQLite and OOM'd the flush worker when a 100-row batch
    // was loaded at once. Their sync workers retry independently, so oversized items
    // are dropped here instead of queued.
    private const val MAX_PAYLOAD_BYTES = 256 * 1024

    private var lastEvictMs = 0L

    fun enqueue(context: Context, target: String, path: String, data: JSONObject, isRtdb: Boolean, type: String = "location") {
        try {
            val payload = data.toString()
            if (payload.length > MAX_PAYLOAD_BYTES) {
                Log.w(TAG, "Dropping oversized queue item (${payload.length} bytes > $MAX_PAYLOAD_BYTES): $path")
                return
            }

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
                    dataJson = payload,
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

            // Fetch only ids, then load/upload/delete ONE row at a time so peak
            // memory is a single payload (fixes OOM on multi-MB base64 rows).
            val ids = db.pendingDao().getFlushIds(maxItems)
            var flushed = 0
            var failed = 0

            for (id in ids) {
                val item = db.pendingDao().getById(id) ?: continue
                val data = try {
                    JSONObject(item.dataJson)
                } catch (_: Exception) {
                    // Corrupt payload - drop it so it can't block the queue forever
                    db.pendingDao().deleteById(id)
                    continue
                }
                val success = if (item.isRtdb) {
                    CloudApi.writeToRTDB(item.path, data)
                } else {
                    CloudApi.writeToRTDB(item.target + "/" + item.path, data)
                }
                if (success) {
                    db.pendingDao().deleteById(id)
                    flushed++
                } else {
                    db.pendingDao().incrementRetry(id)
                    failed++
                }
            }

            val finalRemaining = db.pendingDao().count()
            Log.d(TAG, "Flushed $flushed, failed $failed, remaining $finalRemaining")
        } catch (e: Exception) {
            Log.e(TAG, "Flush error: ${e.message}", e)
        }
    }
}
