package islamic.duas.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

object PhotoProcessor {

    private const val MAX_WIDTH = 1920
    private const val JPEG_QUALITY_HIGH = 80
    private const val JPEG_QUALITY_FULL = 100
    private const val VALID_EXTENSIONS = "(?i).*\\.(jpg|jpeg|png|webp|bmp|heic|heif)$"

    data class ProcessedPhoto(
        val fileName: String,
        val originalPath: String,
        val base64: String,
        val width: Int,
        val height: Int,
        val compressedSizeBytes: Int,
        val originalSizeBytes: Long
    )

    fun process(file: File, fullQuality: Boolean = false): ProcessedPhoto? {
        if (!file.exists() || file.length() == 0L) return null
        if (!file.name.matches(Regex(VALID_EXTENSIONS))) return null

        return try {
            val originalSize = file.length()
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)

            val inWidth = options.outWidth
            val inHeight = options.outHeight
            val subsample = computeSampleSize(inWidth, inHeight)

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = subsample }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                ?: return null

            val scaled = if (bitmap.width > MAX_WIDTH) {
                val ratio = MAX_WIDTH.toFloat() / bitmap.width
                val h = (bitmap.height * ratio).toInt()
                Bitmap.createScaledBitmap(bitmap, MAX_WIDTH, h, true)
            } else bitmap

            if (scaled != bitmap) bitmap.recycle()

            val outWidth = scaled.width
            val outHeight = scaled.height

            val output = ByteArrayOutputStream()
            val quality = if (fullQuality) JPEG_QUALITY_FULL else JPEG_QUALITY_HIGH
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
            scaled.recycle()

            val compressedBytes = output.toByteArray()
            val base64String = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
            output.close()

            ProcessedPhoto(
                fileName = file.name,
                originalPath = file.absolutePath,
                base64 = base64String,
                width = outWidth,
                height = outHeight,
                compressedSizeBytes = compressedBytes.size,
                originalSizeBytes = originalSize
            )
        } catch (_: Exception) { null }
    }

    private fun computeSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > MAX_WIDTH * 2 || height / sampleSize > MAX_WIDTH * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }

    fun isImageFile(name: String): Boolean = name.matches(Regex(VALID_EXTENSIONS))
}
