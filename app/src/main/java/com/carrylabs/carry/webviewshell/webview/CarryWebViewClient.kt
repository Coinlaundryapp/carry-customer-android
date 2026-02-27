package com.carrylabs.carry.webviewshell.webview

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.carrylabs.carry.webviewshell.BuildConfig

class CarryWebViewClient(
    private val onPageStarted: () -> Unit,
    private val onPageFinished: () -> Unit,
    private val onError: (errorType: ErrorType) -> Unit,
    private val onRendererCrash: () -> Unit = {}
) : WebViewClient() {

    enum class ErrorType {
        NETWORK, SERVER, SSL, SAFE_BROWSING
    }

    private companion object {
        private const val TAG = "CarryWebViewClient"
        private const val SCHEME_INTENT_PREFIX = "intent://"
        private const val KEY_BROWSER_FALLBACK_URL = "browser_fallback_url"
        private const val MARKET_DETAILS_PREFIX = "market://details?id="
        private const val MARKET_SEARCH_PREFIX = "market://search?q="
    }

    private var hasError = false

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()

        // Handle intent:// scheme
        if (url.startsWith(SCHEME_INTENT_PREFIX)) {
            return handleIntentScheme(view, url)
        }

        val classification = UrlWhitelistManager.classify(url)

        // Handle payment app schemes
        if (classification.isAppLinkScheme) {
            return handleAppLinkScheme(view, url)
        }

        // Handle special schemes (tel:, mailto:, sms:)
        if (classification.isSpecialScheme) {
            try {
                view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No app found for scheme: ${request.url.scheme}", e)
            }
            return true
        }

        // Handle whitelisted URLs - load in WebView
        if (classification.isAllowed) {
            return false
        }

        // External URLs - open in system browser
        try {
            view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(view.context, "No app found to open this link", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun handleIntentScheme(view: WebView, url: String): Boolean {
        try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            // Try to launch the app
            val resolveInfo = view.context.packageManager.resolveActivity(intent, 0)
            if (resolveInfo != null) {
                view.context.startActivity(intent)
                return true
            }

            // Try browser_fallback_url
            val fallbackUrl = intent.getStringExtra(KEY_BROWSER_FALLBACK_URL)
            if (!fallbackUrl.isNullOrEmpty()) {
                view.loadUrl(fallbackUrl)
                return true
            }

            // Fallback to Play Store
            val packageName = intent.`package`
            if (!packageName.isNullOrEmpty()) {
                try {
                    val marketIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("${MARKET_DETAILS_PREFIX}$packageName")
                    )
                    view.context.startActivity(marketIntent)
                } catch (e: ActivityNotFoundException) {
                    Log.w(TAG, "Play Store not available for package: $packageName", e)
                }
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle intent scheme: $url", e)
        }
        return true
    }

    private fun handleAppLinkScheme(view: WebView, url: String): Boolean {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            view.context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // App not installed — try to extract package name and open Play Store
            val uri = Uri.parse(url)
            val scheme = uri.scheme
            if (!scheme.isNullOrEmpty()) {
                try {
                    val marketIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("${MARKET_SEARCH_PREFIX}$scheme")
                    )
                    view.context.startActivity(marketIntent)
                } catch (_: Exception) {
                }
            }
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

    override fun onSafeBrowsingHit(
        view: WebView,
        request: WebResourceRequest,
        threatType: Int,
        callback: SafeBrowsingResponse
    ) {
        Log.w(TAG, "Safe Browsing threat detected (type=$threatType): ${request.url}")
        callback.backToSafety(true)
        if (request.isForMainFrame) {
            hasError = true
            onError(ErrorType.SAFE_BROWSING)
        }
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        Log.e(TAG, "Renderer process gone (crashed=${detail.didCrash()}, priority=${detail.rendererPriorityAtExit()})")
        onRendererCrash()
        return true // true = 이 WebView가 제거될 것임을 시스템에 알림
    }
}
