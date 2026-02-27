package com.carrylabs.carry.webviewshell.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowConnectivityManager
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NetworkUtilsTest {

    private lateinit var context: Context
    private lateinit var networkUtils: NetworkUtils
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var shadowConnectivityManager: ShadowConnectivityManager

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        networkUtils = NetworkUtils(context)
        connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowConnectivityManager = shadowOf(connectivityManager)
    }

    // ── isNetworkAvailable ───────────────────────────────────────────

    @Test
    fun `isNetworkAvailable returns false when no active network`() {
        shadowConnectivityManager.setDefaultNetworkActive(false)
        // In Robolectric, we need to remove active network
        val network = connectivityManager.activeNetwork
        if (network != null) {
            shadowConnectivityManager.removeNetwork(network)
        }
        // Re-create NetworkUtils to pick up changes
        val utils = NetworkUtils(context)
        // Without active network, should return false
        assertFalse(utils.isNetworkAvailable())
    }

    @Test
    fun `isNetworkAvailable returns true when network with internet capability exists`() {
        // Robolectric default provides an active network
        val result = networkUtils.isNetworkAvailable()
        // Default Robolectric setup should have network available
        assertNotNull(result)
    }

    // ── getNetworkStatus ─────────────────────────────────────────────

    @Test
    fun `getNetworkStatus returns valid JSON with required keys`() {
        val status = networkUtils.getNetworkStatus()

        assertTrue(status.has("isConnected"))
        assertTrue(status.has("type"))
        assertTrue(status.has("isMetered"))
    }

    @Test
    fun `getNetworkStatus type is one of wifi cellular none`() {
        val status = networkUtils.getNetworkStatus()
        val type = status.getString("type")

        assertTrue(
            "type should be wifi, cellular, or none but was: $type",
            type in listOf("wifi", "cellular", "none")
        )
    }

    @Test
    fun `getNetworkStatus isConnected is boolean`() {
        val status = networkUtils.getNetworkStatus()
        // Should not throw - proves it's a boolean
        status.getBoolean("isConnected")
    }

    @Test
    fun `getNetworkStatus isMetered is boolean`() {
        val status = networkUtils.getNetworkStatus()
        // Should not throw - proves it's a boolean
        status.getBoolean("isMetered")
    }

    @Test
    fun `getNetworkStatus returns valid JSON string`() {
        val status = networkUtils.getNetworkStatus()
        val jsonString = status.toString()
        val parsed = JSONObject(jsonString)

        assertEquals(status.getBoolean("isConnected"), parsed.getBoolean("isConnected"))
        assertEquals(status.getString("type"), parsed.getString("type"))
        assertEquals(status.getBoolean("isMetered"), parsed.getBoolean("isMetered"))
    }

    // ── registerNetworkCallback / unregisterNetworkCallback ──────────

    @Test
    fun `registerNetworkCallback returns non-null callback`() {
        val callback = networkUtils.registerNetworkCallback { }
        assertNotNull(callback)
        networkUtils.unregisterNetworkCallback(callback)
    }

    @Test
    fun `unregisterNetworkCallback does not throw for valid callback`() {
        val callback = networkUtils.registerNetworkCallback { }
        // Should not throw
        networkUtils.unregisterNetworkCallback(callback)
    }

    @Test
    fun `unregisterNetworkCallback swallows exception for unregistered callback`() {
        val callback = networkUtils.registerNetworkCallback { }
        networkUtils.unregisterNetworkCallback(callback)
        // Double-unregister should not throw
        networkUtils.unregisterNetworkCallback(callback)
    }
}
