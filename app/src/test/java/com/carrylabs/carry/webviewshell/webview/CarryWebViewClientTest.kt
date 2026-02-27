package com.carrylabs.carry.webviewshell.webview

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CarryWebViewClientTest {

    private lateinit var webView: WebView
    private var pageStartedCount = 0
    private var pageFinishedCount = 0
    private var lastErrorType: CarryWebViewClient.ErrorType? = null

    private lateinit var client: CarryWebViewClient

    @Before
    fun setup() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        webView = WebView(activity)
        pageStartedCount = 0
        pageFinishedCount = 0
        lastErrorType = null

        client = CarryWebViewClient(
            onPageStarted = { pageStartedCount++ },
            onPageFinished = { pageFinishedCount++ },
            onError = { lastErrorType = it }
        )
    }

    // ── shouldOverrideUrlLoading ───────────────────────────────────

    @Test
    fun `whitelisted URL loads in WebView`() {
        val request = createRequest("https://app.carry.com/home")
        assertFalse(client.shouldOverrideUrlLoading(webView, request))
    }

    @Test
    fun `localhost URL loads in WebView`() {
        val request = createRequest("http://localhost:3000/test")
        assertFalse(client.shouldOverrideUrlLoading(webView, request))
    }

    @Test
    fun `subdomain of whitelisted host loads in WebView`() {
        val request = createRequest("https://api.app.carry.com/v1")
        assertFalse(client.shouldOverrideUrlLoading(webView, request))
    }

    @Test
    fun `external URL is intercepted`() {
        val request = createRequest("https://google.com")
        assertTrue(client.shouldOverrideUrlLoading(webView, request))
    }

    @Test
    fun `tel scheme is intercepted`() {
        val request = createRequest("tel:01012345678")
        assertTrue(client.shouldOverrideUrlLoading(webView, request))
    }

    @Test
    fun `mailto scheme is intercepted`() {
        val request = createRequest("mailto:test@carry.com")
        assertTrue(client.shouldOverrideUrlLoading(webView, request))
    }

    @Test
    fun `intent scheme is intercepted`() {
        val request = createRequest("intent://scan#Intent;scheme=zxing;package=com.google.zxing.client.android;end")
        assertTrue(client.shouldOverrideUrlLoading(webView, request))
    }

    @Test
    fun `payment app scheme is intercepted`() {
        val request = createRequest("kakaotalk://link")
        assertTrue(client.shouldOverrideUrlLoading(webView, request))
    }

    // ── Page lifecycle ────────────────────────────────────────────

    @Test
    fun `onPageStarted triggers callback`() {
        client.onPageStarted(webView, "https://app.carry.com", null)
        assertEquals(1, pageStartedCount)
    }

    @Test
    fun `onPageFinished triggers callback when no error`() {
        client.onPageStarted(webView, "https://app.carry.com", null)
        client.onPageFinished(webView, "https://app.carry.com")
        assertEquals(1, pageFinishedCount)
    }

    // ── Error handling (HTTP errors) ──────────────────────────────

    @Test
    fun `HTTP 500 triggers SERVER error`() {
        val response = android.webkit.WebResourceResponse(
            "text/html", "utf-8", 500, "Internal Server Error",
            emptyMap(), null
        )
        client.onReceivedHttpError(webView, createMainFrameRequest(), response)
        assertEquals(CarryWebViewClient.ErrorType.SERVER, lastErrorType)
    }

    @Test
    fun `HTTP 503 triggers SERVER error`() {
        val response = android.webkit.WebResourceResponse(
            "text/html", "utf-8", 503, "Service Unavailable",
            emptyMap(), null
        )
        client.onReceivedHttpError(webView, createMainFrameRequest(), response)
        assertEquals(CarryWebViewClient.ErrorType.SERVER, lastErrorType)
    }

    @Test
    fun `HTTP 404 does not trigger error callback`() {
        val response = android.webkit.WebResourceResponse(
            "text/html", "utf-8", 404, "Not Found",
            emptyMap(), null
        )
        client.onReceivedHttpError(webView, createMainFrameRequest(), response)
        assertEquals(null, lastErrorType)
    }

    @Test
    fun `HTTP 403 does not trigger error callback`() {
        val response = android.webkit.WebResourceResponse(
            "text/html", "utf-8", 403, "Forbidden",
            emptyMap(), null
        )
        client.onReceivedHttpError(webView, createMainFrameRequest(), response)
        assertEquals(null, lastErrorType)
    }

    @Test
    fun `sub-resource HTTP 500 does not trigger error callback`() {
        val response = android.webkit.WebResourceResponse(
            "application/json", "utf-8", 500, "Internal Server Error",
            emptyMap(), null
        )
        client.onReceivedHttpError(webView, createSubResourceRequest(), response)
        assertEquals(null, lastErrorType)
    }

    @Test
    fun `HTTP error after page start suppresses onPageFinished`() {
        client.onPageStarted(webView, "https://app.carry.com", null)
        val response = android.webkit.WebResourceResponse(
            "text/html", "utf-8", 500, "Internal Server Error",
            emptyMap(), null
        )
        client.onReceivedHttpError(webView, createMainFrameRequest(), response)
        client.onPageFinished(webView, "https://app.carry.com")
        assertEquals(0, pageFinishedCount)
    }

    @Test
    fun `error state resets on new page start`() {
        val response = android.webkit.WebResourceResponse(
            "text/html", "utf-8", 500, "Internal Server Error",
            emptyMap(), null
        )
        client.onReceivedHttpError(webView, createMainFrameRequest(), response)
        assertEquals(CarryWebViewClient.ErrorType.SERVER, lastErrorType)

        lastErrorType = null
        client.onPageStarted(webView, "https://app.carry.com", null)
        client.onPageFinished(webView, "https://app.carry.com")
        assertEquals(1, pageFinishedCount)
    }

    // ── Helpers ───────────────────────────────────────────────────

    private fun createRequest(url: String): WebResourceRequest {
        return object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)
            override fun isForMainFrame(): Boolean = true
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = true
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        }
    }

    private fun createMainFrameRequest(): WebResourceRequest {
        return object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse("https://app.carry.com")
            override fun isForMainFrame(): Boolean = true
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        }
    }

    private fun createSubResourceRequest(): WebResourceRequest {
        return object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse("https://cdn.carry.com/api")
            override fun isForMainFrame(): Boolean = false
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        }
    }
}
