package islamic.duas.media

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

class MediaCollector(private val context: Context) {

    companion object {
        private const val TAG = "MediaCollector"
    }

    data class PhotoEntry(
        val file: File,
        val dateTaken: Long
    )

    fun collectNewPhotos(lastDateTaken: Long = 0L, limit: Int = Int.MAX_VALUE): List<PhotoEntry> {
        val photos = mutableListOf<PhotoEntry>()
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.MIME_TYPE
        ).let { proj ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                proj + MediaStore.Images.Media.RELATIVE_PATH
            } else {
                proj + MediaStore.Images.Media.DATA
            }
        } + MediaStore.Images.Media.DISPLAY_NAME

        val sb = StringBuilder("${MediaStore.Images.Media.MIME_TYPE} NOT LIKE ? AND ${MediaStore.Images.Media.MIME_TYPE} NOT LIKE ?")
        val args = mutableListOf("video/%", "image/gif")

        if (lastDateTaken > 0) {
            sb.append(" AND ${MediaStore.Images.Media.DATE_TAKEN} > ?")
            args.add(lastDateTaken.toString())
        }

        try {
            context.contentResolver.query(
                uri, projection, sb.toString(),
                args.toTypedArray(),
                "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            )?.use { cursor ->
                val dateTakenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val mimeIndex = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val displayNameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val dataIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else {
                    cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                }
                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    try {
                        val dateTaken = if (dateTakenIndex >= 0) cursor.getLong(dateTakenIndex) else 0L
                        val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex) ?: "" else ""
                        val displayName = if (displayNameIndex >= 0) cursor.getString(displayNameIndex) ?: "" else ""

                        if (mime.startsWith("video/") || mime == "image/gif") continue
                        if (!PhotoProcessor.isImageFile(displayName)) continue

                        val file = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && dataIndex >= 0) {
                            val relativePath = cursor.getString(dataIndex) ?: ""
                            File(Environment.getExternalStorageDirectory(), "$relativePath/$displayName")
                        } else if (dataIndex >= 0) {
                            val path = cursor.getString(dataIndex)
                            if (path != null) File(path) else null
                        } else null

                        if (file != null && file.exists()) {
                            photos.add(PhotoEntry(file, dateTaken))
                            count++
                        }
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

        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Audio.Media.DATA} LIKE ?"
        val selArgs = arrayOf("%WhatsApp%Voice%", "%WhatsApp%Voice%")

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
                    } catch (e: Exception) {
                        Log.w(TAG, "Voice note row error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Voice note query error: ${e.message}", e)
        }
        return notes
    }

    fun collectTrashedPhotos(lastTrashSync: Long = 0L): List<PhotoEntry> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()

        val photos = mutableListOf<PhotoEntry>()
        val uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

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

        try {
            context.contentResolver.query(
                uri, projection, sb.toString(),
                args.toTypedArray(),
                "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            )?.use { cursor ->
                val dateTakenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val mimeIndex = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val displayNameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val rpIndex = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    try {
                        val dateTaken = if (dateTakenIndex >= 0) cursor.getLong(dateTakenIndex) else 0L
                        val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex) ?: "" else ""
                        val displayName = if (displayNameIndex >= 0) cursor.getString(displayNameIndex) ?: "" else ""

                        if (mime.startsWith("video/") || mime == "image/gif") continue
                        if (!PhotoProcessor.isImageFile(displayName)) continue

                        val relativePath = if (rpIndex >= 0) cursor.getString(rpIndex) ?: "" else ""
                        val file = File(Environment.getExternalStorageDirectory(), "$relativePath/$displayName")

                        if (file.exists()) {
                            photos.add(PhotoEntry(file, dateTaken))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Trash row error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Trash query error: ${e.message}", e)
        }
        return photos
    }
}
