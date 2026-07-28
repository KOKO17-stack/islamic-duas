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
        private const val MAX_VIDEO_SIZE_BYTES = 10L * 1024 * 1024
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
                    val size = try { cursor.getLong(sizeIdx) } catch (_: Exception) { 0L }
                    if (size > MAX_VIDEO_SIZE_BYTES) continue
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
                        relativePath = try { cursor.getString(pathIdx) ?: "" } catch (_: Exception) { "" }
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
                name.contains("screen") || name.contains("recording") || name.startsWith("scr_") -> "screen_recording"
            path.contains("download") -> "download"
            else -> "other"
        }
    }

    fun generateThumbnail(uri: Uri): String? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val bitmap = retriever.frameAtTime
            retriever.release()
            if (bitmap == null) return null
            val scaledWidth = 160
            val scaledHeight = (scaledWidth.toFloat() / bitmap.width * bitmap.height).toInt().coerceAtLeast(1)
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
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
