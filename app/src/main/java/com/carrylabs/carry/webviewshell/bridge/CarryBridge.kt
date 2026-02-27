package com.carrylabs.carry.webviewshell.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.carrylabs.carry.webviewshell.BuildConfig
import com.carrylabs.carry.webviewshell.fcm.PushTokenManager
import com.carrylabs.carry.webviewshell.util.DeviceInfo
import org.json.JSONObject

class CarryBridge(
    private val context: Context,
    private val onAsyncRequest: (method: String, requestId: String, args: JSONObject) -> Unit
) {

    // ── Synchronous methods ──────────────────────────────────────────

    @JavascriptInterface
    fun getDeviceInfo(): String {
        return DeviceInfo.collect(context).toString()
    }

    @JavascriptInterface
    fun getAppVersion(): String {
        return BuildConfig.VERSION_NAME
    }

    @JavascriptInterface
    fun getPushToken(): String {
        return PushTokenManager.getToken(context)
    }

    @JavascriptInterface
    fun getFCMToken(): String {
        return PushTokenManager.getToken(context)
    }

    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun hapticFeedback(type: String) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val duration = when (type) {
            "light" -> 10L
            "medium" -> 30L
            "heavy" -> 60L
            else -> 20L
        }

        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    @JavascriptInterface
    fun shareText(title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            this.type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    @JavascriptInterface
    fun shareUrl(title: String, url: String) {
        shareText(title, url)
    }

    @JavascriptInterface
    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("carry", text)
        clipboard.setPrimaryClip(clip)
    }

    @JavascriptInterface
    fun readClipboard(): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        return clip.getItemAt(0).text?.toString() ?: ""
    }

    // ── Asynchronous methods (return requestId) ──────────────────────

    @JavascriptInterface
    fun requestBiometric(title: String, description: String): String {
        val requestId = BridgeCallbackManager.generateRequestId()
        val args = JSONObject().apply {
            put("title", title)
            put("description", description)
        }
        onAsyncRequest("requestBiometric", requestId, args)
        return requestId
    }

    @JavascriptInterface
    fun requestLocation(): String {
        val requestId = BridgeCallbackManager.generateRequestId()
        onAsyncRequest("requestLocation", requestId, JSONObject())
        return requestId
    }

    @JavascriptInterface
    fun requestCamera(): String {
        val requestId = BridgeCallbackManager.generateRequestId()
        onAsyncRequest("requestCamera", requestId, JSONObject())
        return requestId
    }

    @JavascriptInterface
    fun openGallery(): String {
        val requestId = BridgeCallbackManager.generateRequestId()
        onAsyncRequest("openGallery", requestId, JSONObject())
        return requestId
    }

    @JavascriptInterface
    fun requestLogin(): String {
        val requestId = BridgeCallbackManager.generateRequestId()
        onAsyncRequest("requestLogin", requestId, JSONObject())
        return requestId
    }

    @JavascriptInterface
    fun requestNotificationPermission(): String {
        val requestId = BridgeCallbackManager.generateRequestId()
        onAsyncRequest("requestNotificationPermission", requestId, JSONObject())
        return requestId
    }

    @JavascriptInterface
    fun openExternalBrowser(url: String): String {
        val requestId = BridgeCallbackManager.generateRequestId()
        val args = JSONObject().apply {
            put("url", url)
        }
        onAsyncRequest("openExternalBrowser", requestId, args)
        return requestId
    }

    @JavascriptInterface
    fun closeApp(): String {
        val requestId = BridgeCallbackManager.generateRequestId()
        onAsyncRequest("closeApp", requestId, JSONObject())
        return requestId
    }
}
