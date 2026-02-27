package com.carrylabs.carry.webviewshell.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ImageCompressor {

    data class Result(
        val uri: Uri,
        val originalUri: Uri,
        val width: Int,
        val height: Int,
        val fileSize: Long
    )

    fun compress(
        context: Context,
        uri: Uri,
        maxWidth: Int = 1024,
        maxHeight: Int = 1024,
        quality: Int = 80
    ): Result? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            // 1) Decode bounds only
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            // 2) Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(origWidth, origHeight, maxWidth, maxHeight)
            options.inJustDecodeBounds = false

            // 3) Decode with downsampling
            val sampledStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(sampledStream, null, options)
            sampledStream.close()
            bitmap ?: return null

            // 4) Scale to exact max dimensions if still larger
            val scaled = scaleBitmap(bitmap, maxWidth, maxHeight)
            if (scaled !== bitmap) bitmap.recycle()

            // 5) Compress to JPEG in cache dir
            val cacheDir = context.externalCacheDir ?: return null
            val outFile = File(cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            }

            val compressedUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )

            val result = Result(
                uri = compressedUri,
                originalUri = uri,
                width = scaled.width,
                height = scaled.height,
                fileSize = outFile.length()
            )
            scaled.recycle()
            result
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(
        rawWidth: Int, rawHeight: Int,
        maxWidth: Int, maxHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawWidth > maxWidth || rawHeight > maxHeight) {
            val halfWidth = rawWidth / 2
            val halfHeight = rawHeight / 2
            while (halfWidth / inSampleSize >= maxWidth && halfHeight / inSampleSize >= maxHeight) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxWidth && height <= maxHeight) return bitmap

        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
