package com.carrylabs.carry.webviewshell.bridge

import android.os.Handler
import android.os.Looper
import android.webkit.WebView

class NativeCallDispatcher(private val webView: WebView) {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun sendCallback(result: BridgeResult) {
        val js = "javascript:window.CarryBridge && window.CarryBridge.__onNativeCallback && " +
                "window.CarryBridge.__onNativeCallback(${result.toJson()})"
        evaluateOnMainThread(js)
    }

    fun sendEvent(eventName: String, dataJson: String) {
        val js = "javascript:window.CarryBridge && window.CarryBridge.__onNativeEvent && " +
                "window.CarryBridge.__onNativeEvent('$eventName', $dataJson)"
        evaluateOnMainThread(js)
    }

    // ── 명세서 콜백 ────────────────────────────────────────────────

    fun dispatchLoginComplete(token: String) {
        val escaped = token.replace("\\", "\\\\").replace("'", "\\'")
        val js = "javascript:typeof window.onLoginComplete === 'function' && window.onLoginComplete('$escaped')"
        evaluateOnMainThread(js)
    }

    /**
     * Returns true if `window.onNativeBackPressed` exists and was invoked.
     * The caller should check the JS-side existence first via [checkAndDispatchBackPressed].
     */
    fun dispatchNativeBackPressed() {
        val js = "javascript:typeof window.onNativeBackPressed === 'function' && window.onNativeBackPressed()"
        evaluateOnMainThread(js)
    }

    /**
     * Evaluates whether `window.onNativeBackPressed` exists, then invokes callback with the result.
     */
    fun checkAndDispatchBackPressed(onResult: (Boolean) -> Unit) {
        val checkJs = "typeof window.onNativeBackPressed === 'function'"
        if (Looper.myLooper() == Looper.getMainLooper()) {
            webView.evaluateJavascript(checkJs) { result ->
                val exists = result?.trim() == "true"
                if (exists) {
                    dispatchNativeBackPressed()
                }
                onResult(exists)
            }
        } else {
            mainHandler.post {
                webView.evaluateJavascript(checkJs) { result ->
                    val exists = result?.trim() == "true"
                    if (exists) {
                        dispatchNativeBackPressed()
                    }
                    onResult(exists)
                }
            }
        }
    }

    fun dispatchPushNotification(dataJson: String) {
        val js = "javascript:typeof window.onPushNotification === 'function' && window.onPushNotification($dataJson)"
        evaluateOnMainThread(js)
    }

    fun dispatchAppResume() {
        val js = "javascript:typeof window.onAppResume === 'function' && window.onAppResume()"
        evaluateOnMainThread(js)
    }

    private fun evaluateOnMainThread(js: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            webView.evaluateJavascript(js, null)
        } else {
            mainHandler.post {
                webView.evaluateJavascript(js, null)
            }
        }
    }
}
