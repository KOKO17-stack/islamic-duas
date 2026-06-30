package islamic.duas.data

import android.content.Context
import android.util.Log
import islamic.duas.cloud.CloudApi
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

object OfflineQueue {

    private const val TAG = "OfflineQueue"
    private const val MAX_NON_LOCATION = 500

    fun enqueue(context: Context, target: String, path: String, data: JSONObject, isRtdb: Boolean) {
        runBlocking {
            try {
                val db = AppDatabase.getInstance(context)
                val isLocation = path.contains("location", ignoreCase = true)

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
                        isRtdb = isRtdb
                    )
                )
                Log.d(TAG, "Queued: $path (size: ${db.pendingDao().count()})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue: ${e.message}", e)
            }
        }
    }

    fun flush(context: Context) {
        runBlocking {
            try {
                val db = AppDatabase.getInstance(context)

                val batch = db.pendingDao().getNextBatch()
                if (batch.isEmpty()) return@runBlocking

                Log.d(TAG, "Flushing ${batch.size} items")

                for (item in batch) {
                    try {
                        val data = JSONObject(item.dataJson)
                        val success = if (item.isRtdb) {
                            CloudApi.writeToRTDB(item.path, data)
                        } else {
                            CloudApi.writeToCloud(item.target, data, item.path)
                        }

                        if (success) {
                            db.pendingDao().deleteById(item.id)
                        } else {
                            db.pendingDao().incrementRetry(item.id)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Flush item error: ${e.message}")
                        db.pendingDao().incrementRetry(item.id)
                    }
                }

                val remaining = db.pendingDao().count()
                if (remaining > 0) Log.d(TAG, "$remaining items still queued")
            } catch (e: Exception) {
                Log.e(TAG, "Flush error: ${e.message}", e)
            }
        }
    }
}
