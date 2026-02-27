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
import org.json.JSONObject

class MediaRequestHandler(
    private val activity: ComponentActivity,
    private val permissionHandler: PermissionHandler
) {

    private var dispatcher: NativeCallDispatcher? = null

    private var pendingRequestId: String? = null
    private var cameraImageUri: Uri? = null
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var isGalleryPick = false

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
            val data = JSONObject().put("uri", cameraImageUri.toString())
            d.sendCallback(BridgeResult(reqId, true, data))
        } else {
            d.sendCallback(BridgeResult(reqId, false, error = "Camera cancelled"))
        }
        pendingRequestId = null
        cameraImageUri = null
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
            val data = JSONObject().put("uri", uri.toString())
            d.sendCallback(BridgeResult(reqId, true, data))
        } else {
            d.sendCallback(BridgeResult(reqId, false, error = "Gallery cancelled"))
        }
        pendingRequestId = null
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
