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
