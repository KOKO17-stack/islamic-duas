package islamic.duas.media

import android.content.ContentResolver
import android.net.Uri
import android.util.Log

class VideoProcessor {

    companion object {
        private const val TAG = "VideoProcessor"
        private const val MAX_SIZE_BYTES = 10L * 1024 * 1024
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
