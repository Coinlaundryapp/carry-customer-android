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
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.carrylabs.carry.webviewshell.BuildConfig
import com.carrylabs.carry.webviewshell.fcm.PushTokenManager
import com.carrylabs.carry.webviewshell.util.DeviceInfo
import org.json.JSONObject
import java.util.UUID

/**
 * WebView JavaScript 브릿지. `window.AndroidBridge` / `window.CarryNative`로 노출된다.
 *
 * - **동기 메서드**: 즉시 값을 반환한다 (예: [getDeviceInfo], [getAppVersion]).
 * - **비동기 메서드**: `requestId`를 반환하고, 결과는
 *   `window.CarryBridge.__onNativeCallback(result)` 콜백으로 전달된다.
 *
 * 모든 `@JavascriptInterface` 메서드는 WebView의 JS 스레드에서 호출되므로,
 * UI 조작이 필요한 메서드는 [mainHandler]를 통해 메인 스레드로 전환한다.
 */
class CarryBridge(
    private val context: Context,
    private val onAsyncRequest: (method: String, requestId: String, args: JSONObject) -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "CarryBridge"
        private const val MAX_TOAST_LENGTH = 200

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

    /** 기기 정보를 JSON 문자열로 반환한다 (platform, osVersion, model, appVersion 등). */
    @JavascriptInterface
    fun getDeviceInfo(): String = DeviceInfo.collect(context).toString()

    /** 앱 버전명을 반환한다 (예: "1.0"). */
    @JavascriptInterface
    fun getAppVersion(): String = BuildConfig.VERSION_NAME

    /** 저장된 FCM 푸시 토큰을 반환한다. 없으면 빈 문자열. */
    @JavascriptInterface
    fun getPushToken(): String = PushTokenManager.getToken(context)

    /** [getPushToken]의 별칭. 웹에서 `getFCMToken()`으로 호출할 수 있다. */
    @JavascriptInterface
    fun getFCMToken(): String = getPushToken()

    /** 네이티브 Toast를 표시한다. 최대 200자까지 표시된다. */
    @JavascriptInterface
    fun showToast(message: String) {
        val safeMessage = message.take(MAX_TOAST_LENGTH)
        mainHandler.post {
            Toast.makeText(context, safeMessage, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 햅틱 피드백을 실행한다.
     * @param type `"light"` (10ms), `"medium"` (30ms), `"heavy"` (60ms). 미지원 타입은 20ms.
     */
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
                else -> {
                    Log.w(TAG, "Unknown haptic type: $type, using default (20ms)")
                    20L
                }
            }

            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    /** 시스템 공유 시트를 열어 텍스트를 공유한다. */
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

    /** [shareText]의 별칭. URL을 공유한다. */
    @JavascriptInterface
    fun shareUrl(title: String, url: String) = shareText(title, url)

    /** 시스템 클립보드에 텍스트를 복사한다. */
    @JavascriptInterface
    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("carry", text)
        clipboard.setPrimaryClip(clip)
    }

    /**
     * 클립보드 텍스트를 읽는다.
     * Android 10+ 에서는 포그라운드 앱만 접근 가능하며, 실패 시 빈 문자열을 반환한다.
     */
    @JavascriptInterface
    fun readClipboard(): String {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (!clipboard.hasPrimaryClip()) return ""
            val clip = clipboard.primaryClip ?: return ""
            if (clip.itemCount == 0) return ""
            clip.getItemAt(0).text?.toString() ?: ""
        } catch (_: SecurityException) {
            ""
        }
    }

    // ── Asynchronous methods (return requestId) ──────────────────────

    /**
     * 생체 인증을 요청한다.
     * @return requestId. 결과: `{ authenticated: true }` 또는 에러.
     */
    @JavascriptInterface
    fun requestBiometric(title: String, description: String): String {
        return dispatchAsync(REQUEST_BIOMETRIC, JSONObject().apply {
            put("title", title)
            put("description", description)
        })
    }

    /** 현재 위치를 요청한다. 권한이 없으면 런타임 권한을 요청한다. */
    @JavascriptInterface
    fun requestLocation(): String = dispatchAsync(REQUEST_LOCATION)

    /** 카메라를 열어 사진을 촬영한다. 권한이 없으면 런타임 권한을 요청한다. */
    @JavascriptInterface
    fun requestCamera(): String = dispatchAsync(REQUEST_CAMERA)

    /** 갤러리를 열어 이미지를 선택한다. */
    @JavascriptInterface
    fun openGallery(): String = dispatchAsync(OPEN_GALLERY)

    /** 카카오 OAuth 로그인을 시작한다. Chrome Custom Tab으로 인증 페이지를 연다. */
    @JavascriptInterface
    fun requestLogin(): String = dispatchAsync(REQUEST_LOGIN)

    /** 알림 권한을 요청한다 (Android 13+). 이전 버전에서는 항상 granted. */
    @JavascriptInterface
    fun requestNotificationPermission(): String = dispatchAsync(REQUEST_NOTIFICATION_PERMISSION)

    /** 외부 브라우저(Chrome Custom Tab)로 URL을 연다. */
    @JavascriptInterface
    fun openExternalBrowser(url: String): String {
        return dispatchAsync(OPEN_EXTERNAL_BROWSER, JSONObject().put("url", url))
    }

    /** 앱을 종료한다. */
    @JavascriptInterface
    fun closeApp(): String = dispatchAsync(CLOSE_APP)

    // ── Helper ───────────────────────────────────────────────────────

    private fun dispatchAsync(method: String, args: JSONObject = JSONObject()): String {
        val requestId = UUID.randomUUID().toString()
        onAsyncRequest(method, requestId, args)
        return requestId
    }
}
