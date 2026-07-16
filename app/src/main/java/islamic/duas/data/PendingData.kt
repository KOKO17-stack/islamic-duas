package islamic.duas.data

data class PendingData(
    val id: Long = 0,
    val target: String,
    val path: String,
    val dataJson: String,
    val isRtdb: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)
