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

    // ── Activity Result Launchers (등록은 Activity STARTED 전에 완료) ──

    private val fileChooserLauncher = activity.registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        fileUploadCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileUploadCallback = null
    }

    private val cameraLauncher = activity.registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        handleCameraResult(success)
    }

    private val galleryLauncher = activity.registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        handleGalleryResult(uri)
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

    fun handleCamera(requestId: String) {
        val d = dispatcher ?: return
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionHandler.requestSingle(Manifest.permission.CAMERA) { granted ->
                if (granted) {
                    launchCamera(requestId)
                } else {
                    d.sendCallback(
                        BridgeResult(requestId, false, error = "Camera permission denied")
                    )
                }
            }
            return
        }
        launchCamera(requestId)
    }

    private fun launchCamera(requestId: String) {
        pendingRequestId = requestId
        cameraImageUri = FileProviderHelper.createImageUri(activity)
        cameraLauncher.launch(cameraImageUri!!)
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
        pendingRequestId = requestId
        galleryLauncher.launch("image/*")
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
            fileChooserLauncher.launch(intent.type ?: "*/*")
            true
        } catch (_: Exception) {
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = null
            false
        }
    }
}
