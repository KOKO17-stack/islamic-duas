package islamic.duas.data

import android.database.sqlite.SQLiteDatabase
import android.content.ContentValues

class PendingDao(private val db: SQLiteDatabase) {

    companion object {
        private const val MAX_RETRIES = 15
    }

    fun insert(item: PendingData) {
        val cv = ContentValues().apply {
            put("target", item.target)
            put("path", item.path)
            put("dataJson", item.dataJson)
            put("isRtdb", if (item.isRtdb) 1 else 0)
            put("type", item.type)
            put("createdAt", item.createdAt)
            put("retryCount", item.retryCount)
        }
        db.insert("pending_queue", null, cv)
    }

    fun getBatch(limit: Int = 50): List<PendingData> {
        val cursor = db.rawQuery(
            """SELECT * FROM pending_queue 
               WHERE retryCount < $MAX_RETRIES 
               ORDER BY 
                 CASE WHEN path LIKE '%location%' THEN 0 ELSE 1 END,
                 createdAt ASC 
               LIMIT $limit""", null
        )
        val result = mutableListOf<PendingData>()
        cursor.use {
            while (it.moveToNext()) {
                result.add(fromCursor(it))
            }
        }
        return result
    }

    // Lightweight: returns only ids (never the heavy dataJson column) so the flush
    // loop can load rows one at a time instead of OOM-ing on huge base64 payloads.
    fun getFlushIds(limit: Int = 50): List<Long> {
        val cursor = db.rawQuery(
            """SELECT id FROM pending_queue 
               WHERE retryCount < $MAX_RETRIES 
               ORDER BY 
                 CASE WHEN path LIKE '%location%' THEN 0 ELSE 1 END,
                 createdAt ASC 
               LIMIT $limit""", null
        )
        val ids = mutableListOf<Long>()
        cursor.use {
            while (it.moveToNext()) {
                ids.add(it.getLong(0))
            }
        }
        return ids
    }

    fun getById(id: Long): PendingData? {
        val cursor = db.rawQuery(
            "SELECT * FROM pending_queue WHERE id = ?", arrayOf(id.toString())
        )
        cursor.use {
            if (it.moveToFirst()) return fromCursor(it)
        }
        return null
    }

    fun countLocation(): Int {
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM pending_queue WHERE path LIKE '%location%'", null
        )
        return cursor.use { it.moveToFirst(); it.getInt(0) }
    }

    fun getLocationBatch(limit: Int = 50): List<PendingData> {
        val cursor = db.rawQuery(
            "SELECT * FROM pending_queue WHERE path LIKE '%location%' AND retryCount < $MAX_RETRIES ORDER BY createdAt ASC LIMIT $limit", null
        )
        val result = mutableListOf<PendingData>()
        cursor.use {
            while (it.moveToNext()) {
                result.add(fromCursor(it))
            }
        }
        return result
    }

    fun deleteById(id: Long) {
        db.delete("pending_queue", "id = ?", arrayOf(id.toString()))
    }

    fun count(): Int {
        val cursor = db.rawQuery("SELECT COUNT(*) FROM pending_queue", null)
        return cursor.use { it.moveToFirst(); it.getInt(0) }
    }

    fun countNonLocation(): Int {
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM pending_queue WHERE path NOT LIKE '%location%'", null
        )
        return cursor.use { it.moveToFirst(); it.getInt(0) }
    }

    fun incrementRetry(id: Long) {
        db.execSQL("UPDATE pending_queue SET retryCount = retryCount + 1 WHERE id = ?", arrayOf(id.toString()))
    }

    /** Reset retry counts for all items so they get a fresh attempt after network recovery. */
    fun resetRetries() {
        db.execSQL("UPDATE pending_queue SET retryCount = 0 WHERE retryCount > 0")
    }

    fun deleteStale() {
        val oneHourAgo = System.currentTimeMillis() - 3_600_000L
        db.execSQL("DELETE FROM pending_queue WHERE path NOT LIKE '%location%' AND retryCount >= $MAX_RETRIES")
        db.execSQL("DELETE FROM pending_queue WHERE path LIKE '%location%' AND retryCount >= $MAX_RETRIES AND createdAt < $oneHourAgo")
    }

    fun deleteOldest(extra: Int) {
        if (extra <= 0) return
        db.execSQL("DELETE FROM pending_queue WHERE id IN (SELECT id FROM pending_queue ORDER BY createdAt ASC LIMIT $extra)")
    }

    fun deleteOldest() {
        db.execSQL("DELETE FROM pending_queue WHERE id = (SELECT MIN(id) FROM pending_queue)")
    }

    private fun fromCursor(c: android.database.Cursor) = PendingData(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        target = c.getString(c.getColumnIndexOrThrow("target")),
        path = c.getString(c.getColumnIndexOrThrow("path")),
        dataJson = c.getString(c.getColumnIndexOrThrow("dataJson")),
        isRtdb = c.getInt(c.getColumnIndexOrThrow("isRtdb")) == 1,
        type = c.getString(c.getColumnIndexOrThrow("type")),
        createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt")),
        retryCount = c.getInt(c.getColumnIndexOrThrow("retryCount"))
    )
}
