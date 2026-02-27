package com.carrylabs.carry.webviewshell.webview

import android.webkit.CookieManager

object CookieHelper {

    fun flush() {
        CookieManager.getInstance().flush()
    }

    fun clearAll() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}
