package com.carrylabs.carry.webviewshell.bridge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeResultTest {

    @Test
    fun `toJson - success result with data`() {
        val data = JSONObject().apply {
            put("latitude", 37.5665)
            put("longitude", 126.9780)
        }
        val result = BridgeResult("req-123", true, data)
        val json = JSONObject(result.toJson())

        assertEquals("req-123", json.getString("requestId"))
        assertTrue(json.getBoolean("success"))
        assertEquals(37.5665, json.getJSONObject("data").getDouble("latitude"), 0.0001)
        assertEquals(126.9780, json.getJSONObject("data").getDouble("longitude"), 0.0001)
        assertFalse(json.has("error"))
    }

    @Test
    fun `toJson - failure result with error`() {
        val result = BridgeResult("req-456", false, error = "Location permission denied")
        val json = JSONObject(result.toJson())

        assertEquals("req-456", json.getString("requestId"))
        assertFalse(json.getBoolean("success"))
        assertEquals("Location permission denied", json.getString("error"))
        assertFalse(json.has("data"))
    }

    @Test
    fun `toJson - success result without data`() {
        val result = BridgeResult("req-789", true)
        val json = JSONObject(result.toJson())

        assertEquals("req-789", json.getString("requestId"))
        assertTrue(json.getBoolean("success"))
        assertFalse(json.has("data"))
        assertFalse(json.has("error"))
    }

    @Test
    fun `toJson - result with both data and error`() {
        val data = JSONObject().put("partial", true)
        val result = BridgeResult("req-mix", false, data, "Partial failure")
        val json = JSONObject(result.toJson())

        assertEquals("req-mix", json.getString("requestId"))
        assertFalse(json.getBoolean("success"))
        assertTrue(json.getJSONObject("data").getBoolean("partial"))
        assertEquals("Partial failure", json.getString("error"))
    }
}
