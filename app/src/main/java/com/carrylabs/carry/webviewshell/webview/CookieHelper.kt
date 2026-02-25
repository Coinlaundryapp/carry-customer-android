package com.carrylabs.carry.webviewshell.webview

import android.webkit.CookieManager

object CookieHelper {

    fun setup() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(null, true)
    }

    fun flush() {
        CookieManager.getInstance().flush()
    }

    fun clearAll() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}
