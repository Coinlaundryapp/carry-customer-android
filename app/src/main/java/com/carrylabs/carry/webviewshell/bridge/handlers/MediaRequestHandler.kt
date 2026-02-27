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

    private var dispatcher: NativeCallDispatcher? = null

    private var pendingRequestId: String? = null
    private var cameraImageUri: Uri? = null
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var isGalleryPick = false

    // Image compression options (set per-request, reset to defaults after use)
    private var compressMaxWidth: Int = DEFAULT_MAX_WIDTH
    private var compressMaxHeight: Int = DEFAULT_MAX_HEIGHT
    private var compressQuality: Int = DEFAULT_QUALITY

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
        compressMaxWidth = maxWidth
        compressMaxHeight = maxHeight
        compressQuality = quality
    }

    private fun resetCompressionOptions() {
        compressMaxWidth = DEFAULT_MAX_WIDTH
        compressMaxHeight = DEFAULT_MAX_HEIGHT
        compressQuality = DEFAULT_QUALITY
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
        val d = dispatcher ?: return
        val reqId = pendingRequestId ?: return
        if (success && cameraImageUri != null) {
            val compressed = imageCompressor.compress(
                activity, cameraImageUri!!, compressMaxWidth, compressMaxHeight, compressQuality
            )
            if (compressed != null) {
                val data = JSONObject().apply {
                    put("uri", compressed.uri.toString())
                    put("originalUri", compressed.originalUri.toString())
                    put("width", compressed.width)
                    put("height", compressed.height)
                    put("fileSize", compressed.fileSize)
                }
                d.sendCallback(BridgeResult(reqId, true, data))
            } else {
                // Compression failed — return original URI as fallback
                val data = JSONObject().put("uri", cameraImageUri.toString())
                d.sendCallback(BridgeResult(reqId, true, data))
            }
        } else {
            d.sendCallback(BridgeResult(reqId, false, error = "Camera cancelled"))
        }
        pendingRequestId = null
        cameraImageUri = null
        resetCompressionOptions()
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
        val d = dispatcher ?: return
        val reqId = pendingRequestId ?: return
        if (uri != null) {
            val compressed = imageCompressor.compress(
                activity, uri, compressMaxWidth, compressMaxHeight, compressQuality
            )
            if (compressed != null) {
                val data = JSONObject().apply {
                    put("uri", compressed.uri.toString())
                    put("originalUri", compressed.originalUri.toString())
                    put("width", compressed.width)
                    put("height", compressed.height)
                    put("fileSize", compressed.fileSize)
                }
                d.sendCallback(BridgeResult(reqId, true, data))
            } else {
                // Compression failed — return original URI as fallback
                val data = JSONObject().put("uri", uri.toString())
                d.sendCallback(BridgeResult(reqId, true, data))
            }
        } else {
            d.sendCallback(BridgeResult(reqId, false, error = "Gallery cancelled"))
        }
        pendingRequestId = null
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
