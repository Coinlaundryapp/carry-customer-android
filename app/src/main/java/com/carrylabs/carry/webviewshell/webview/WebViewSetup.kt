package com.carrylabs.carry.webviewshell.webview

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import com.carrylabs.carry.webviewshell.BuildConfig

object WebViewSetup {

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true

            // Cache
            cacheMode = if (BuildConfig.DEBUG) {
                WebSettings.LOAD_NO_CACHE
            } else {
                WebSettings.LOAD_DEFAULT
            }

            // Display
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false

            // Content access
            allowFileAccess = false
            allowContentAccess = true

            // Mixed content
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            // User agent
            val defaultUA = userAgentString
            userAgentString = "$defaultUA Carry-Android/${BuildConfig.VERSION_NAME}"

            // Media
            mediaPlaybackRequiresUserGesture = false

            // Text
            textZoom = 100
        }

        // Cookies
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // Debugging
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
