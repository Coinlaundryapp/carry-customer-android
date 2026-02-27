package com.carrylabs.carry.webviewshell.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
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

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 브릿지 메서드명 상수. CarryBridge와 MainActivity 라우터가 공유하여 오타를 방지한다.
     */
    companion object Methods {
        const val REQUEST_BIOMETRIC = "requestBiometric"
        const val REQUEST_LOCATION = "requestLocation"
        const val REQUEST_CAMERA = "requestCamera"
        const val OPEN_GALLERY = "openGallery"
        const val REQUEST_LOGIN = "requestLogin"
        const val REQUEST_NOTIFICATION_PERMISSION = "requestNotificationPermission"
        const val OPEN_EXTERNAL_BROWSER = "openExternalBrowser"
        const val CLOSE_APP = "closeApp"
    }

    // ── Synchronous methods ──────────────────────────────────────────

    @JavascriptInterface
    fun getDeviceInfo(): String = DeviceInfo.collect(context).toString()

    @JavascriptInterface
    fun getAppVersion(): String = BuildConfig.VERSION_NAME

    @JavascriptInterface
    fun getPushToken(): String = PushTokenManager.getToken(context)

    @JavascriptInterface
    fun getFCMToken(): String = PushTokenManager.getToken(context)

    @JavascriptInterface
    fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun hapticFeedback(type: String) {
        mainHandler.post {
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
    fun shareUrl(title: String, url: String) = shareText(title, url)

    @JavascriptInterface
    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("carry", text)
        clipboard.setPrimaryClip(clip)
    }

    @JavascriptInterface
    fun readClipboard(): String {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (!clipboard.hasPrimaryClip()) return ""
            val clip = clipboard.primaryClip ?: return ""
            if (clip.itemCount == 0) return ""
            clip.getItemAt(0).text?.toString() ?: ""
        } catch (_: SecurityException) {
            // Android 10+ 제한: 포그라운드 앱만 클립보드 접근 가능
            ""
        }
    }

    // ── Asynchronous methods (return requestId) ──────────────────────

    @JavascriptInterface
    fun requestBiometric(title: String, description: String): String {
        return dispatchAsync(REQUEST_BIOMETRIC, JSONObject().apply {
            put("title", title)
            put("description", description)
        })
    }

    @JavascriptInterface
    fun requestLocation(): String = dispatchAsync(REQUEST_LOCATION)

    @JavascriptInterface
    fun requestCamera(): String = dispatchAsync(REQUEST_CAMERA)

    @JavascriptInterface
    fun openGallery(): String = dispatchAsync(OPEN_GALLERY)

    @JavascriptInterface
    fun requestLogin(): String = dispatchAsync(REQUEST_LOGIN)

    @JavascriptInterface
    fun requestNotificationPermission(): String = dispatchAsync(REQUEST_NOTIFICATION_PERMISSION)

    @JavascriptInterface
    fun openExternalBrowser(url: String): String {
        return dispatchAsync(OPEN_EXTERNAL_BROWSER, JSONObject().put("url", url))
    }

    @JavascriptInterface
    fun closeApp(): String = dispatchAsync(CLOSE_APP)

    // ── Helper ───────────────────────────────────────────────────────

    private fun dispatchAsync(method: String, args: JSONObject = JSONObject()): String {
        val requestId = BridgeCallbackManager.generateRequestId()
        onAsyncRequest(method, requestId, args)
        return requestId
    }
}
