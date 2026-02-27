package com.carrylabs.carry.webviewshell.webview

import android.webkit.CookieManager

object CookieHelper {

    fun flush() {
        CookieManager.getInstance().flush()
    }

    fun clearAll() {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
    }
}
