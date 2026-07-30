package islamic.duas.data

import android.database.sqlite.SQLiteDatabase
import android.content.ContentValues
import java.security.MessageDigest

class VideoDedupDao(private val db: SQLiteDatabase) {

    companion object {
        private const val MAX_ENTRIES = 5000
    }

    fun isUploaded(filePath: String, fileName: String, fileSize: Long, dateAdded: Long): Boolean {
        val hash = sha256("$filePath|$fileName|$fileSize|$dateAdded")
        val cursor = db.rawQuery("SELECT id FROM video_dedup WHERE md5 = ? LIMIT 1", arrayOf(hash))
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    fun markUploaded(filePath: String, fileName: String, fileSize: Long, dateAdded: Long) {
        val hash = sha256("$filePath|$fileName|$fileSize|$dateAdded")
        val values = ContentValues().apply {
            put("md5", hash)
            put("filePath", filePath)
            put("fileName", fileName)
            put("fileSize", fileSize)
            put("dateAdded", dateAdded)
            put("uploadedAt", System.currentTimeMillis())
        }
        db.insertWithOnConflict("video_dedup", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        trimExcess()
    }

    private fun trimExcess() {
        val cursor = db.rawQuery("SELECT COUNT(*) FROM video_dedup", null)
        val count = cursor.use { it.moveToFirst(); it.getInt(0) }
        if (count > MAX_ENTRIES) {
            db.execSQL("DELETE FROM video_dedup WHERE id NOT IN (SELECT id FROM video_dedup ORDER BY uploadedAt DESC LIMIT $MAX_ENTRIES)")
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
