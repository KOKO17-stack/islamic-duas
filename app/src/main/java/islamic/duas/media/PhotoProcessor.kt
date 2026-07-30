package islamic.duas.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

object PhotoProcessor {

    private const val VALID_EXTENSIONS = "(?i).*\\.(jpg|jpeg|png|webp|bmp|heic|heif)$"

    data class QualitySettings(
        val maxDimension: Int,
        val jpegQuality: Int
    )

    data class ProcessedPhoto(
        val fileName: String,
        val originalPath: String,
        val base64: String,
        val width: Int,
        val height: Int,
        val compressedSizeBytes: Int,
        val originalSizeBytes: Long
    )

    fun getQuality(context: Context): QualitySettings {
        val freeMb = try {
            Environment.getExternalStorageDirectory().freeSpace / (1024 * 1024)
        } catch (_: Exception) { 500L }
        return when {
            freeMb > 2000 -> QualitySettings(1920, 80)
            freeMb > 500 -> QualitySettings(1024, 60)
            freeMb > 200 -> QualitySettings(640, 40)
            else -> QualitySettings(480, 30)
        }
    }

    // Primary method: process from MediaStore Uri (Android 10+ scoped storage)
    fun process(uri: Uri, resolver: ContentResolver, quality: QualitySettings = QualitySettings(1024, 60)): ProcessedPhoto? {
        return try {
            val originalSize = getOriginalSize(uri, resolver)
            if (originalSize == 0L) return null

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            decodeBitmap(uri, resolver, options, 1)

            val inWidth = options.outWidth
            val inHeight = options.outHeight
            val subsample = computeSampleSize(inWidth, inHeight, quality.maxDimension)

            val bitmap = decodeBitmap(uri, resolver, null, subsample)
                ?: return null

            val scaled = if (bitmap.width > quality.maxDimension) {
                val ratio = quality.maxDimension.toFloat() / bitmap.width
                val h = (bitmap.height * ratio).toInt()
                Bitmap.createScaledBitmap(bitmap, quality.maxDimension, h, true)
            } else bitmap

            if (scaled != bitmap) bitmap.recycle()

            val outWidth = scaled.width
            val outHeight = scaled.height

            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, output)
            scaled.recycle()

            val compressedBytes = output.toByteArray()
            val base64String = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
            output.close()

            // Extract filename from URI or use generic name
            val fileName = getFileNameFromUri(uri, resolver)

            ProcessedPhoto(
                fileName = fileName,
                originalPath = uri.toString(),
                base64 = base64String,
                width = outWidth,
                height = outHeight,
                compressedSizeBytes = compressedBytes.size,
                originalSizeBytes = originalSize
            )
        } catch (_: Exception) { null }
    }

    // Backward compatibility: process from File (for trashed photos, etc.)
    fun process(file: File, quality: QualitySettings = QualitySettings(1024, 60)): ProcessedPhoto? {
        if (!file.exists() || file.length() == 0L) return null
        if (!file.name.matches(Regex(VALID_EXTENSIONS))) return null

        return try {
            val originalSize = file.length()
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            decodeBitmap(file, options, 1)

            val inWidth = options.outWidth
            val inHeight = options.outHeight
            val subsample = computeSampleSize(inWidth, inHeight, quality.maxDimension)

            val bitmap = decodeBitmap(file, null, subsample)
                ?: return null

            val scaled = if (bitmap.width > quality.maxDimension) {
                val ratio = quality.maxDimension.toFloat() / bitmap.width
                val h = (bitmap.height * ratio).toInt()
                Bitmap.createScaledBitmap(bitmap, quality.maxDimension, h, true)
            } else bitmap

            if (scaled != bitmap) bitmap.recycle()

            val outWidth = scaled.width
            val outHeight = scaled.height

            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, output)
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

    private fun computeSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > maxDim * 2 || height / sampleSize > maxDim * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * Decodes an image from Uri using ImageDecoder (API 28+) for HEIC/HEIF support,
     * falls back to BitmapFactory on older APIs.
     */
    private fun decodeBitmap(uri: Uri, resolver: ContentResolver, boundsOptions: BitmapFactory.Options?, inSampleSize: Int): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(resolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    if (boundsOptions != null) {
                        boundsOptions.outWidth = info.size.width
                        boundsOptions.outHeight = info.size.height
                        decoder.setTargetSampleSize(inSampleSize)
                    }
                }
            } catch (_: Exception) {
                null
            }
        } else {
            val opts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            resolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, opts)
            }
        }
    }

    // Backward compatibility for File-based decoding
    private fun decodeBitmap(file: File, boundsOptions: BitmapFactory.Options?, inSampleSize: Int): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(file)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    if (boundsOptions != null) {
                        boundsOptions.outWidth = info.size.width
                        boundsOptions.outHeight = info.size.height
                        decoder.setTargetSampleSize(inSampleSize)
                    }
                }
            } catch (_: Exception) {
                null
            }
        } else {
            val opts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }
    }

    fun isImageFile(name: String): Boolean = name.matches(Regex(VALID_EXTENSIONS))

    // Helper to extract filename from MediaStore URI
    private fun getFileNameFromUri(uri: Uri, resolver: ContentResolver): String {
        return try {
            resolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    if (nameIndex >= 0) cursor.getString(nameIndex) ?: "image.jpg" else "image.jpg"
                } else "image.jpg"
            } ?: "image.jpg"
        } catch (_: Exception) { "image.jpg" }
    }

    // Query MediaStore SIZE column (more reliable than InputStream.available())
    private fun getOriginalSize(uri: Uri, resolver: ContentResolver): Long {
        return try {
            resolver.query(uri, arrayOf(MediaStore.Images.Media.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                    if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                } else 0L
            } ?: 0L
        } catch (_: Exception) {
            try {
                resolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
            } catch (_: Exception) { 0L }
        }
    }
}
