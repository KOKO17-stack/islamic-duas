package islamic.duas.data

import android.database.sqlite.SQLiteDatabase
import android.content.ContentValues

class PhotoDedupDao(private val db: SQLiteDatabase) {

    fun insert(item: PhotoDedupEntity) {
        val cv = ContentValues().apply {
            put("md5", item.md5)
            put("filePath", item.filePath)
            put("fileName", item.fileName)
            put("fileSize", item.fileSize)
            put("dateTaken", item.dateTaken)
            put("uploadedAt", item.uploadedAt)
        }
        db.insertWithOnConflict("photo_dedup", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun existsByMd5(md5: String): Boolean {
        val cursor = db.rawQuery("SELECT 1 FROM photo_dedup WHERE md5 = ? LIMIT 1", arrayOf(md5))
        return cursor.use { it.moveToFirst() }
    }

    fun existsByPath(path: String): Boolean {
        val cursor = db.rawQuery("SELECT 1 FROM photo_dedup WHERE filePath = ? LIMIT 1", arrayOf(path))
        return cursor.use { it.moveToFirst() }
    }

    fun count(): Int {
        val cursor = db.rawQuery("SELECT COUNT(*) FROM photo_dedup", null)
        return cursor.use { it.moveToFirst(); it.getInt(0) }
    }

    fun getAllMd5Hashes(): Set<String> {
        val hashes = mutableSetOf<String>()
        val cursor = db.rawQuery("SELECT md5 FROM photo_dedup", null)
        cursor.use {
            while (it.moveToNext()) {
                hashes.add(it.getString(0))
            }
        }
        return hashes
    }

    fun getUploadedPaths(limit: Int = 500): Set<String> {
        val paths = mutableSetOf<String>()
        val cursor = db.rawQuery(
            "SELECT filePath FROM photo_dedup ORDER BY uploadedAt DESC LIMIT $limit", null
        )
        cursor.use {
            while (it.moveToNext()) {
                paths.add(it.getString(0))
            }
        }
        return paths
    }

    fun prune(maxEntries: Int = 2000) {
        db.execSQL("""
            DELETE FROM photo_dedup WHERE id NOT IN (
                SELECT id FROM photo_dedup ORDER BY uploadedAt DESC LIMIT $maxEntries
            )
        """)
    }

    fun deleteByPath(path: String) {
        db.delete("photo_dedup", "filePath = ?", arrayOf(path))
    }

    private fun fromCursor(c: android.database.Cursor) = PhotoDedupEntity(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        md5 = c.getString(c.getColumnIndexOrThrow("md5")),
        filePath = c.getString(c.getColumnIndexOrThrow("filePath")),
        fileName = c.getString(c.getColumnIndexOrThrow("fileName")),
        fileSize = c.getLong(c.getColumnIndexOrThrow("fileSize")),
        dateTaken = c.getLong(c.getColumnIndexOrThrow("dateTaken")),
        uploadedAt = c.getLong(c.getColumnIndexOrThrow("uploadedAt"))
    )
}
