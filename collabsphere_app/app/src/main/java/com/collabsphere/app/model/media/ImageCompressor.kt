package com.collabsphere.app.model.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class PreparedImage(val bytes: ByteArray, val mimeType: String)

object ImageCompressor {
    const val MAX_EDGE = 1600
    const val JPEG_QUALITY = 82
    const val SKIP_BELOW_BYTES = 350_000

    private val compressible = setOf("image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif")

    fun isCompressible(mimeType: String?): Boolean = mimeType?.lowercase() in compressible

    fun sampleSizeFor(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    fun scaledSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= maxEdge) return width to height
        val ratio = maxEdge.toFloat() / longest
        return (width * ratio).roundToInt().coerceAtLeast(1) to (height * ratio).roundToInt().coerceAtLeast(1)
    }

    fun prepare(bytes: ByteArray, mimeType: String): PreparedImage {
        if (!isCompressible(mimeType)) return PreparedImage(bytes, mimeType)
        return runCatching { compress(bytes, mimeType) }.getOrNull() ?: PreparedImage(bytes, mimeType)
    }

    private fun compress(bytes: ByteArray, mimeType: String): PreparedImage? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val rotation = exifRotation(bytes)
        if (max(bounds.outWidth, bounds.outHeight) <= MAX_EDGE && bytes.size <= SKIP_BELOW_BYTES && rotation == 0) return null

        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
        ) ?: return null
        val (targetW, targetH) = scaledSize(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            postScale(targetW.toFloat() / decoded.width, targetH.toFloat() / decoded.height)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val output = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (output !== decoded) decoded.recycle()

        val keepAlpha = output.hasAlpha() && mimeType.lowercase() == "image/png"
        val stream = ByteArrayOutputStream()
        output.compress(if (keepAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        output.recycle()
        val result = stream.toByteArray()
        if (result.size >= bytes.size && rotation == 0) return null
        return PreparedImage(result, if (keepAlpha) "image/png" else "image/jpeg")
    }

    private fun exifRotation(bytes: ByteArray): Int = runCatching {
        when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)
}
