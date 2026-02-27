package com.carrylabs.carry.webviewshell.bridge

import android.webkit.WebView
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
class NativeCallDispatcherTest {

    private lateinit var webView: WebView
    private lateinit var dispatcher: NativeCallDispatcher

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        webView = WebView(context)
        dispatcher = NativeCallDispatcher(webView)
    }

    private fun flushAndGetLastJs(): String? {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        return Shadows.shadowOf(webView).lastEvaluatedJavascript
    }

    // ── BridgeResult JSON safety ──────────────────────────────────

    @Test
    fun `BridgeResult toJson produces valid JSON`() {
        val result = BridgeResult("req-1", true, JSONObject().put("key", "value"))
        val json = JSONObject(result.toJson())
        assertEquals("req-1", json.getString("requestId"))
        assertTrue(json.getBoolean("success"))
        assertEquals("value", json.getJSONObject("data").getString("key"))
    }

    @Test
    fun `BridgeResult toJson with error produces valid JSON`() {
        val result = BridgeResult("req-2", false, error = "something failed")
        val json = JSONObject(result.toJson())
        assertEquals("req-2", json.getString("requestId"))
        assertEquals(false, json.getBoolean("success"))
        assertEquals("something failed", json.getString("error"))
    }

    @Test
    fun `BridgeResult toJson with special characters in error`() {
        val result = BridgeResult("req-3", false, error = "error with \"quotes\" and \\ backslash")
        val json = JSONObject(result.toJson())
        assertEquals("error with \"quotes\" and \\ backslash", json.getString("error"))
    }

    // ── sendEvent JS content verification ─────────────────────────

    @Test
    fun `sendEvent generates correct JS with guard pattern`() {
        dispatcher.sendEvent("testEvent", """{"key":"value"}""")
        val js = flushAndGetLastJs()
        assertNotNull(js)
        assertTrue(js!!.contains("window.CarryBridge && window.CarryBridge.__onNativeEvent"))
        assertTrue(js.contains("__onNativeEvent("))
        assertTrue(js.contains("\"testEvent\""))
    }

    @Test
    fun `sendEvent with invalid JSON falls back to empty object`() {
        dispatcher.sendEvent("test", "not valid json {{{")
        val js = flushAndGetLastJs()
        assertNotNull(js)
        assertTrue(js!!.contains("{}"))
    }

    @Test
    fun `sendEvent with valid JSON array does not throw`() {
        dispatcher.sendEvent("test", """[1,2,3]""")
        val js = flushAndGetLastJs()
        assertNotNull(js)
        assertTrue(js!!.contains("[1,2,3]"))
    }

    @Test
    fun `sendEvent with empty string falls back to empty object`() {
        dispatcher.sendEvent("test", "")
        val js = flushAndGetLastJs()
        assertNotNull(js)
        assertTrue(js!!.contains("{}"))
    }

    // ── sendCallback JS content verification ──────────────────────

    @Test
    fun `sendCallback generates correct JS with guard pattern`() {
        val result = BridgeResult("req-1", true, JSONObject().put("data", "test"))
        dispatcher.sendCallback(result)
        val js = flushAndGetLastJs()
        assertNotNull(js)
        assertTrue(js!!.contains("window.CarryBridge && window.CarryBridge.__onNativeCallback"))
        assertTrue(js.contains("\"requestId\""))
        assertTrue(js.contains("req-1"))
    }

    // ── JSONObject quote safety ───────────────────────────────────

    @Test
    fun `JSONObject quote escapes double quotes`() {
        val quoted = JSONObject.quote("hello \"world\"")
        assertNotNull(quoted)
        assertTrue(quoted.contains("\\\""))
    }

    @Test
    fun `JSONObject quote escapes backslash`() {
        val quoted = JSONObject.quote("path\\to\\file")
        assertNotNull(quoted)
        assertTrue(quoted.contains("\\\\"))
    }

    @Test
    fun `JSONObject quote escapes newlines`() {
        val quoted = JSONObject.quote("line1\nline2")
        assertNotNull(quoted)
        assertTrue(quoted.contains("\\n"))
    }

    @Test
    fun `JSONObject quote handles unicode`() {
        val quoted = JSONObject.quote("한글 테스트")
        assertNotNull(quoted)
        assertTrue(quoted.contains("한글"))
    }

    // ── Dispatch methods JS verification ──────────────────────────

    @Test
    fun `dispatchLoginComplete generates typeof check JS`() {
        dispatcher.dispatchLoginComplete("token123")
        val js = flushAndGetLastJs()
        assertNotNull(js)
        assertTrue(js!!.contains("typeof window.onLoginComplete === 'function'"))
        assertTrue(js.contains("window.onLoginComplete("))
        assertTrue(js.contains("token123"))
    }

    @Test
    fun `dispatchLoginComplete escapes special chars in token`() {
        dispatcher.dispatchLoginComplete("token\"with'special<chars>")
        val js = flushAndGetLastJs()
        assertNotNull(js)
        assertTrue(js!!.contains("\\\""))
    }

    @Test
    fun `dispatchNativeBackPressed generates typeof check JS`() {
        dispatcher.dispatchNativeBackPressed()
        val js = flushAndGetLastJs()
        assertNotNull(js)
        assertTrue(js!!.contains("typeof window.onNativeBackPressed === 'function'"))
        assertTrue(js.contains("window.onNativeBackPressed()"))
    }

    @Test
    fun `dispatchPushNotification generates typeof check JS`() {
        dispatcher.dispatchPushNotification("""{"title":"push","body":"test"}""")
        val js = flushAndGetLastJs()
        assertNotNull(js)
        assertTrue(js!!.contains("typeof window.onPushNotification === 'function'"))
        assertTrue(js.contains("window.onPushNotification("))
    }

    @Test
    fun `dispatchPushNotification with invalid JSON uses fallback`() {
        dispatcher.dispatchPushNotification("invalid json")
        val js = flushAndGetLastJs()
        assertNotNull(js)
        assertTrue(js!!.contains("{}"))
    }

    @Test
    fun `dispatchAppResume generates typeof check JS`() {
        dispatcher.dispatchAppResume()
        val js = flushAndGetLastJs()
        assertNotNull(js)
        assertTrue(js!!.contains("typeof window.onAppResume === 'function'"))
        assertTrue(js.contains("window.onAppResume()"))
    }
}
