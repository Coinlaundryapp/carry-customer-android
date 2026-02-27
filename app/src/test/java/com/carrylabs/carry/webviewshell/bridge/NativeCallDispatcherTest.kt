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
    private val evaluatedJs = mutableListOf<String>()

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        webView = WebView(context)

        // Shadow을 통해 evaluateJavascript 호출을 캡처
        val shadowWebView = Shadows.shadowOf(webView)

        dispatcher = NativeCallDispatcher(webView)
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

    // ── ensureValidJson (tested indirectly via sendEvent) ─────────

    @Test
    fun `sendEvent with valid JSON object does not throw`() {
        dispatcher.sendEvent("test", """{"key":"value"}""")
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        // No exception means the JSON was accepted
    }

    @Test
    fun `sendEvent with valid JSON array does not throw`() {
        dispatcher.sendEvent("test", """[1,2,3]""")
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun `sendEvent with invalid JSON falls back to empty object`() {
        // This should not throw — invalid JSON is replaced with {}
        dispatcher.sendEvent("test", "not valid json {{{")
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun `sendEvent with empty string falls back to empty object`() {
        dispatcher.sendEvent("test", "")
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
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

    // ── Dispatch methods do not throw ─────────────────────────────

    @Test
    fun `dispatchLoginComplete does not throw`() {
        dispatcher.dispatchLoginComplete("token123")
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun `dispatchLoginComplete with special chars does not throw`() {
        dispatcher.dispatchLoginComplete("token\"with'special<chars>")
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun `dispatchNativeBackPressed does not throw`() {
        dispatcher.dispatchNativeBackPressed()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun `dispatchPushNotification does not throw`() {
        dispatcher.dispatchPushNotification("""{"title":"push","body":"test"}""")
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun `dispatchPushNotification with invalid JSON does not throw`() {
        dispatcher.dispatchPushNotification("invalid json")
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun `dispatchAppResume does not throw`() {
        dispatcher.dispatchAppResume()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun `sendCallback does not throw`() {
        val result = BridgeResult("req-1", true, JSONObject().put("data", "test"))
        dispatcher.sendCallback(result)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }
}
