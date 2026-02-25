package com.carrylabs.carry.webviewshell.webview

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.carrylabs.carry.webviewshell.BuildConfig

class CarryWebViewClient(
    private val onPageStarted: () -> Unit,
    private val onPageFinished: () -> Unit,
    private val onError: (errorType: ErrorType) -> Unit
) : WebViewClient() {

    enum class ErrorType {
        NETWORK, SERVER, SSL
    }

    private var hasError = false

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()

        // Handle special schemes (tel:, mailto:, sms:)
        if (UrlWhitelistManager.isSpecialScheme(url)) {
            try {
                view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
            } catch (_: Exception) {
            }
            return true
        }

        // Handle whitelisted URLs - load in WebView
        if (UrlWhitelistManager.isAllowed(url)) {
            return false
        }

        // External URLs - open in system browser
        try {
            view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
        } catch (_: Exception) {
        }
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        hasError = false
        onPageStarted()
    }

    override fun onPageFinished(view: WebView, url: String?) {
        if (!hasError) {
            onPageFinished()
        }
        CookieHelper.flush()
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        // Only handle main frame errors
        if (request.isForMainFrame) {
            hasError = true
            onError(ErrorType.NETWORK)
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: android.webkit.WebResourceResponse?
    ) {
        if (request.isForMainFrame) {
            val statusCode = errorResponse?.statusCode ?: 0
            if (statusCode >= 500) {
                hasError = true
                onError(ErrorType.SERVER)
            }
            // 4xx errors are handled by the web app
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        if (BuildConfig.DEBUG) {
            // In debug mode, allow SSL errors for local dev
            handler.proceed()
        } else {
            handler.cancel()
            hasError = true
            onError(ErrorType.SSL)
        }
    }
}
