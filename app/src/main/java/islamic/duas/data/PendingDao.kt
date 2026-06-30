package islamic.duas.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingDao {
    @Insert
    suspend fun insert(item: PendingData)

    @Query("SELECT * FROM pending_queue ORDER BY createdAt ASC LIMIT 20")
    suspend fun getNextBatch(): List<PendingData>

    @Query("DELETE FROM pending_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM pending_queue")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM pending_queue WHERE path NOT LIKE '%location%'")
    suspend fun countNonLocation(): Int

    @Query("UPDATE pending_queue SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)

    @Query("SELECT * FROM pending_queue ORDER BY createdAt ASC LIMIT 1")
    suspend fun getOldest(): PendingData?

    @Query("DELETE FROM pending_queue WHERE id = (SELECT MIN(id) FROM pending_queue)")
    suspend fun deleteOldest()
}
