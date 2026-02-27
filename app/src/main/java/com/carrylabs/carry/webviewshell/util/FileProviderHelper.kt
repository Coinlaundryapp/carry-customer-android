package com.carrylabs.carry.webviewshell.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileProviderHelper {

    fun createImageUri(context: Context): Uri? {
        val cacheDir = context.externalCacheDir ?: return null
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val imageFile = File(cacheDir, "IMG_${timeStamp}.jpg")
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
        } catch (_: Exception) {
            null
        }
    }
}
