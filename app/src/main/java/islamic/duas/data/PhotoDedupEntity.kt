package islamic.duas.data

data class PhotoDedupEntity(
    val id: Long = 0,
    val md5: String,
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val dateTaken: Long,
    val uploadedAt: Long = System.currentTimeMillis()
)
