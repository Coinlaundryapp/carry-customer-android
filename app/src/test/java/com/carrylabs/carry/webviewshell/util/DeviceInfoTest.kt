package com.carrylabs.carry.webviewshell.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DeviceInfoTest {

    @Test
    fun `collect returns JSON with all required fields`() {
        val context = RuntimeEnvironment.getApplication()
        val json = DeviceInfo.collect(context)

        assertEquals("android", json.getString("platform"))
        assertNotNull(json.getString("osVersion"))
        assertTrue(json.getInt("sdkVersion") > 0)
        assertNotNull(json.getString("manufacturer"))
        assertNotNull(json.getString("model"))
        assertNotNull(json.getString("brand"))
        assertNotNull(json.getString("appVersion"))
        assertTrue(json.getLong("appVersionCode") >= 0)
        assertNotNull(json.getString("packageName"))
        assertNotNull(json.getString("deviceId"))
    }

    @Test
    fun `collect returns valid packageName`() {
        val context = RuntimeEnvironment.getApplication()
        val json = DeviceInfo.collect(context)

        assertEquals(context.packageName, json.getString("packageName"))
    }

    @Test
    fun `collect output is valid JSON string`() {
        val context = RuntimeEnvironment.getApplication()
        val json = DeviceInfo.collect(context)

        val parsed = org.json.JSONObject(json.toString())
        assertEquals(json.getString("platform"), parsed.getString("platform"))
    }
}
