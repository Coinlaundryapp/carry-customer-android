package com.carrylabs.carry.webviewshell.bridge

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONObject

class NativeCallDispatcher(private val webView: WebView) {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun sendCallback(result: BridgeResult) {
        val safeJson = result.toJson() // JSONObject.toString() — 안전
        callJsFunction(
            "window.CarryBridge && window.CarryBridge.__onNativeCallback",
            "window.CarryBridge.__onNativeCallback($safeJson)"
        )
    }

    fun sendEvent(eventName: String, dataJson: String) {
        val safeName = JSONObject.quote(eventName) // "eventName" (따옴표 포함)
        val safeData = ensureValidJson(dataJson)
        callJsFunction(
            "window.CarryBridge && window.CarryBridge.__onNativeEvent",
            "window.CarryBridge.__onNativeEvent($safeName, $safeData)"
        )
    }

    // ── 명세서 콜백 ────────────────────────────────────────────────

    fun dispatchLoginComplete(token: String) {
        val safeToken = JSONObject.quote(token) // "token" (따옴표 포함)
        callJsFunctionIfExists("window.onLoginComplete", "window.onLoginComplete($safeToken)")
    }

    fun dispatchNativeBackPressed() {
        callJsFunctionIfExists("window.onNativeBackPressed", "window.onNativeBackPressed()")
    }

    fun checkAndDispatchBackPressed(onResult: (Boolean) -> Unit) {
        val checkJs = "typeof window.onNativeBackPressed === 'function'"
        evaluateOnMainThread(checkJs) { result ->
            val exists = result?.trim() == "true"
            if (exists) {
                dispatchNativeBackPressed()
            }
            onResult(exists)
        }
    }

    fun dispatchPushNotification(dataJson: String) {
        val safeData = ensureValidJson(dataJson)
        callJsFunctionIfExists("window.onPushNotification", "window.onPushNotification($safeData)")
    }

    fun dispatchAppResume() {
        callJsFunctionIfExists("window.onAppResume", "window.onAppResume()")
    }

    // ── Private helpers ──────────────────────────────────────────────

    /**
     * guard && call 패턴으로 JS 함수를 호출한다.
     */
    private fun callJsFunction(guard: String, call: String) {
        evaluateOnMainThread("$guard && $call", null)
    }

    /**
     * typeof 체크 후 JS 함수를 호출한다.
     */
    private fun callJsFunctionIfExists(funcRef: String, call: String) {
        evaluateOnMainThread("typeof $funcRef === 'function' && $call", null)
    }

    /**
     * JSON 문자열을 파싱하여 유효성을 검증한다.
     * 유효하지 않으면 빈 객체를 반환한다.
     */
    private fun ensureValidJson(dataJson: String): String {
        return try {
            // JSONObject로 파싱 → toString()으로 안전하게 재직렬화
            JSONObject(dataJson).toString()
        } catch (_: Exception) {
            try {
                // JSONArray일 수 있음
                org.json.JSONArray(dataJson).toString()
            } catch (_: Exception) {
                "{}"
            }
        }
    }

    private fun evaluateOnMainThread(js: String, callback: ((String?) -> Unit)?) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            webView.evaluateJavascript(js, callback)
        } else {
            mainHandler.post {
                webView.evaluateJavascript(js, callback)
            }
        }
    }
}
