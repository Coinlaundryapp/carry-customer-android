package com.carrylabs.carry.webviewshell.bridge.handlers

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.carrylabs.carry.webviewshell.bridge.BridgeResult
import com.carrylabs.carry.webviewshell.bridge.NativeCallDispatcher
import com.carrylabs.carry.webviewshell.permission.PermissionHandler
import com.carrylabs.carry.webviewshell.util.FileProviderHelper
import com.carrylabs.carry.webviewshell.util.ImageCompressor
import org.json.JSONObject

class MediaRequestHandler(
    private val activity: ComponentActivity,
    private val permissionHandler: PermissionHandler,
    private val imageCompressor: ImageCompressor = ImageCompressor
) {

    private data class CompressionOptions(
        val maxWidth: Int = DEFAULT_MAX_WIDTH,
        val maxHeight: Int = DEFAULT_MAX_HEIGHT,
        val quality: Int = DEFAULT_QUALITY
    )

    private var dispatcher: NativeCallDispatcher? = null

    private var pendingRequestId: String? = null
    private var cameraImageUri: Uri? = null
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var isGalleryPick = false
    private var compressionOptions = CompressionOptions()

    companion object {
        const val DEFAULT_MAX_WIDTH = 1024
        const val DEFAULT_MAX_HEIGHT = 1024
        const val DEFAULT_QUALITY = 80
    }

    // ── Activity Result Launchers (등록은 Activity STARTED 전에 완료) ──

    private val contentPickerLauncher = activity.registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (isGalleryPick) {
            handleGalleryResult(uri)
        } else {
            fileUploadCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
            fileUploadCallback = null
        }
        isGalleryPick = false
    }

    private val cameraLauncher = activity.registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        handleCameraResult(success)
    }

    fun initialize(dispatcher: NativeCallDispatcher) {
        this.dispatcher = dispatcher
    }

    fun cleanup() {
        fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = null
        pendingRequestId = null
        cameraImageUri = null
        dispatcher = null
    }

    fun setCompressionOptions(maxWidth: Int, maxHeight: Int, quality: Int) {
        compressionOptions = CompressionOptions(maxWidth, maxHeight, quality)
    }

    private fun resetCompressionOptions() {
        compressionOptions = CompressionOptions()
    }

    // ── Bridge: Camera ───────────────────────────────────────────────

    suspend fun handleCamera(requestId: String) {
        val d = dispatcher ?: return
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            val granted = permissionHandler.requestSingle(Manifest.permission.CAMERA)
            if (!granted) {
                d.sendCallback(
                    BridgeResult(requestId, false, error = "Camera permission denied")
                )
                resetCompressionOptions()
                return
            }
        }
        launchCamera(requestId)
    }

    private fun launchCamera(requestId: String) {
        val uri = FileProviderHelper.createImageUri(activity)
        if (uri == null) {
            dispatcher?.sendCallback(
                BridgeResult(requestId, false, error = "Failed to create image file")
            )
            resetCompressionOptions()
            return
        }
        pendingRequestId = requestId
        cameraImageUri = uri
        cameraLauncher.launch(uri)
    }

    private fun handleCameraResult(success: Boolean) {
        val imageUri = if (success) cameraImageUri else null
        handleMediaResult(imageUri, "Camera cancelled")
    }

    // ── Bridge: Gallery ──────────────────────────────────────────────

    fun handleGallery(requestId: String) {
        fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = null
        pendingRequestId = requestId
        isGalleryPick = true
        contentPickerLauncher.launch("image/*")
    }

    private fun handleGalleryResult(uri: Uri?) {
        handleMediaResult(uri, "Gallery cancelled")
    }

    // ── Shared media result handler ──────────────────────────────────

    private fun handleMediaResult(uri: Uri?, cancelMessage: String) {
        val d = dispatcher ?: return
        val reqId = pendingRequestId ?: return
        if (uri != null) {
            val opts = compressionOptions
            val compressed = imageCompressor.compress(
                activity, uri, opts.maxWidth, opts.maxHeight, opts.quality
            )
            val data = compressed?.toJson()
                ?: JSONObject().put("uri", uri.toString())
            d.sendCallback(BridgeResult(reqId, true, data))
        } else {
            d.sendCallback(BridgeResult(reqId, false, error = cancelMessage))
        }
        pendingRequestId = null
        cameraImageUri = null
        resetCompressionOptions()
    }

    // ── WebChromeClient: File Chooser ────────────────────────────────

    fun handleFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ): Boolean {
        fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = callback
        return try {
            val intent = params.createIntent()
            isGalleryPick = false
            contentPickerLauncher.launch(intent.type ?: "*/*")
            true
        } catch (_: Exception) {
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = null
            false
        }
    }
}
