package com.carrylabs.carry.webviewshell.fcm

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PushTokenManagerTest {

    @Test
    fun `getToken returns empty string when no token saved`() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals("", PushTokenManager.getToken(context))
    }

    @Test
    fun `saveToken then getToken returns saved token`() {
        val context = RuntimeEnvironment.getApplication()
        PushTokenManager.saveToken(context, "test-token-123")
        assertEquals("test-token-123", PushTokenManager.getToken(context))
    }

    @Test
    fun `saveToken overwrites previous token`() {
        val context = RuntimeEnvironment.getApplication()
        PushTokenManager.saveToken(context, "old-token")
        PushTokenManager.saveToken(context, "new-token")
        assertEquals("new-token", PushTokenManager.getToken(context))
    }

    @Test
    fun `saveToken handles empty token`() {
        val context = RuntimeEnvironment.getApplication()
        PushTokenManager.saveToken(context, "")
        assertEquals("", PushTokenManager.getToken(context))
    }
}
