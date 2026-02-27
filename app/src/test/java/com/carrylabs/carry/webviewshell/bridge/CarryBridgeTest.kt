package com.carrylabs.carry.webviewshell.bridge

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CarryBridgeTest {

    private lateinit var context: Context
    private val asyncRequests = mutableListOf<Triple<String, String, JSONObject>>()
    private lateinit var bridge: CarryBridge

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        asyncRequests.clear()
        bridge = CarryBridge(context) { method, requestId, args ->
            asyncRequests.add(Triple(method, requestId, args))
        }
    }

    // ── Sync methods ─────────────────────────────────────────────────

    @Test
    fun `getAppVersion returns non-empty string`() {
        val version = bridge.getAppVersion()
        assertNotNull(version)
        assertTrue(version.isNotEmpty())
    }

    @Test
    fun `getDeviceInfo returns valid JSON`() {
        val info = bridge.getDeviceInfo()
        val json = JSONObject(info)
        assertTrue(json.has("platform"))
        assertEquals("android", json.getString("platform"))
    }

    @Test
    fun `copyToClipboard and readClipboard round-trip`() {
        bridge.copyToClipboard("hello carry")
        val result = bridge.readClipboard()
        assertEquals("hello carry", result)
    }

    @Test
    fun `readClipboard returns empty when clipboard is empty`() {
        // Fresh context has empty clipboard
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.clearPrimaryClip()
        val result = bridge.readClipboard()
        assertEquals("", result)
    }

    @Test
    fun `getFCMToken returns same as getPushToken`() {
        val fcm = bridge.getFCMToken()
        val push = bridge.getPushToken()
        assertEquals(fcm, push)
    }

    @Test
    fun `showToast does not throw`() {
        bridge.showToast("test message")
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val latestToast = org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
        assertEquals("test message", latestToast)
    }

    // ── Async methods ────────────────────────────────────────────────

    @Test
    fun `requestBiometric dispatches async request with correct args`() {
        val requestId = bridge.requestBiometric("Verify", "Please verify")
        assertNotNull(requestId)
        assertTrue(requestId.isNotEmpty())

        assertEquals(1, asyncRequests.size)
        val (method, id, args) = asyncRequests[0]
        assertEquals("requestBiometric", method)
        assertEquals(requestId, id)
        assertEquals("Verify", args.getString("title"))
        assertEquals("Please verify", args.getString("description"))
    }

    @Test
    fun `requestLocation dispatches async request`() {
        val requestId = bridge.requestLocation()
        assertEquals(1, asyncRequests.size)
        assertEquals("requestLocation", asyncRequests[0].first)
        assertEquals(requestId, asyncRequests[0].second)
    }

    @Test
    fun `requestCamera dispatches async request`() {
        val requestId = bridge.requestCamera()
        assertEquals(1, asyncRequests.size)
        assertEquals("requestCamera", asyncRequests[0].first)
        assertEquals(requestId, asyncRequests[0].second)
    }

    @Test
    fun `openGallery dispatches async request`() {
        val requestId = bridge.openGallery()
        assertEquals(1, asyncRequests.size)
        assertEquals("openGallery", asyncRequests[0].first)
        assertEquals(requestId, asyncRequests[0].second)
    }

    @Test
    fun `requestLogin dispatches async request`() {
        val requestId = bridge.requestLogin()
        assertEquals(1, asyncRequests.size)
        assertEquals("requestLogin", asyncRequests[0].first)
        assertEquals(requestId, asyncRequests[0].second)
    }

    @Test
    fun `requestNotificationPermission dispatches async request`() {
        val requestId = bridge.requestNotificationPermission()
        assertEquals(1, asyncRequests.size)
        assertEquals("requestNotificationPermission", asyncRequests[0].first)
        assertEquals(requestId, asyncRequests[0].second)
    }

    @Test
    fun `openExternalBrowser dispatches async request with url`() {
        val requestId = bridge.openExternalBrowser("https://example.com")
        assertEquals(1, asyncRequests.size)
        val (method, id, args) = asyncRequests[0]
        assertEquals("openExternalBrowser", method)
        assertEquals(requestId, id)
        assertEquals("https://example.com", args.getString("url"))
    }

    @Test
    fun `closeApp dispatches async request`() {
        val requestId = bridge.closeApp()
        assertEquals(1, asyncRequests.size)
        assertEquals("closeApp", asyncRequests[0].first)
        assertEquals(requestId, asyncRequests[0].second)
    }

    @Test
    fun `each async method generates unique requestId`() {
        val ids = listOf(
            bridge.requestLogin(),
            bridge.requestNotificationPermission(),
            bridge.openExternalBrowser("https://test.com"),
            bridge.closeApp(),
            bridge.requestLocation(),
            bridge.requestCamera()
        )
        assertEquals(ids.size, ids.toSet().size)
    }

    // ── New sync methods (네이티브 기능 업그레이드) ────────────────────

    @Test
    fun `getNetworkStatus returns valid JSON with required keys`() {
        val status = bridge.getNetworkStatus()
        val json = JSONObject(status)
        assertTrue(json.has("isConnected"))
        assertTrue(json.has("type"))
        assertTrue(json.has("isMetered"))
    }

    @Test
    fun `getNetworkStatus type is valid enum value`() {
        val json = JSONObject(bridge.getNetworkStatus())
        val type = json.getString("type")
        assertTrue(
            "type should be wifi, cellular, or none but was: $type",
            type in listOf("wifi", "cellular", "none")
        )
    }

    // ── New async methods (네이티브 기능 업그레이드) ────────────────────

    @Test
    fun `checkAppUpdate dispatches async request`() {
        val requestId = bridge.checkAppUpdate()
        assertNotNull(requestId)
        assertTrue(requestId.isNotEmpty())
        assertEquals(1, asyncRequests.size)
        assertEquals("checkAppUpdate", asyncRequests[0].first)
        assertEquals(requestId, asyncRequests[0].second)
    }

    @Test
    fun `requestCamera with compression options dispatches with args`() {
        val requestId = bridge.requestCamera(800, 600, 70)
        assertEquals(1, asyncRequests.size)
        val (method, id, args) = asyncRequests[0]
        assertEquals("requestCamera", method)
        assertEquals(requestId, id)
        assertEquals(800, args.getInt("maxWidth"))
        assertEquals(600, args.getInt("maxHeight"))
        assertEquals(70, args.getInt("quality"))
    }

    @Test
    fun `openGallery with compression options dispatches with args`() {
        val requestId = bridge.openGallery(512, 512, 60)
        assertEquals(1, asyncRequests.size)
        val (method, id, args) = asyncRequests[0]
        assertEquals("openGallery", method)
        assertEquals(requestId, id)
        assertEquals(512, args.getInt("maxWidth"))
        assertEquals(512, args.getInt("maxHeight"))
        assertEquals(60, args.getInt("quality"))
    }

    @Test
    fun `requestCamera without args has empty JSONObject args`() {
        bridge.requestCamera()
        val args = asyncRequests[0].third
        assertFalse(args.has("maxWidth"))
        assertFalse(args.has("maxHeight"))
        assertFalse(args.has("quality"))
    }

    @Test
    fun `openGallery without args has empty JSONObject args`() {
        bridge.openGallery()
        val args = asyncRequests[0].third
        assertFalse(args.has("maxWidth"))
        assertFalse(args.has("maxHeight"))
        assertFalse(args.has("quality"))
    }

    @Test
    fun `all new async methods generate unique requestIds`() {
        val ids = listOf(
            bridge.checkAppUpdate(),
            bridge.requestCamera(800, 600, 70),
            bridge.openGallery(512, 512, 60),
            bridge.requestCamera(),
            bridge.openGallery()
        )
        assertEquals(ids.size, ids.toSet().size)
    }
}
