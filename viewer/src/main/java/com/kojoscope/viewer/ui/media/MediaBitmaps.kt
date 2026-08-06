package com.kojoscope.viewer.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.File

/** Bitmap helpers that downsample before decoding to avoid OOM. */
object MediaBitmaps {

    /** Decodes a file at most [reqSize] pixels on its longest side. */
    fun decodeSampledFile(file: File, reqSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqSize ||
            bounds.outHeight / (sample * 2) >= reqSize
        ) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun decodeSampledBase64(b64: String, reqSize: Int): Bitmap? {
        val bytes = try { Base64.decode(b64, Base64.DEFAULT) } catch (_: Exception) { return null }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqSize ||
            bounds.outHeight / (sample * 2) >= reqSize
        ) {
            sample *= 2
        }
        return BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }
}
