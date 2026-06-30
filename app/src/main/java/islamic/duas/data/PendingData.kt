package islamic.duas.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_queue")
data class PendingData(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val target: String,
    val path: String,
    val dataJson: String,
    val isRtdb: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)
