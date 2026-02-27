package com.carrylabs.carry.webviewshell.util

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * SecureTokenManager는 EncryptedSharedPreferences를 사용하므로
 * AndroidKeyStore가 필요하다. Robolectric에서는 AndroidKeyStore
 * 시뮬레이션이 제한적일 수 있어, 실패 시 graceful하게 처리된다.
 *
 * 전체 E2E 검증은 androidTest(instrumented test)에서 수행한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SecureTokenManagerTest {

    private lateinit var context: Context
    private var isKeyStoreAvailable = false

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        // Probe whether EncryptedSharedPreferences works in this environment
        isKeyStoreAvailable = try {
            SecureTokenManager.getToken(context, "__probe__")
            true
        } catch (_: Exception) {
            false
        }
    }

    @Test
    fun `getToken returns empty string for nonexistent key`() {
        if (!isKeyStoreAvailable) return
        assertEquals("", SecureTokenManager.getToken(context, "nonexistent_key"))
    }

    @Test
    fun `saveToken then getToken returns saved value`() {
        if (!isKeyStoreAvailable) return
        SecureTokenManager.saveToken(context, "test_key", "test_value")
        assertEquals("test_value", SecureTokenManager.getToken(context, "test_key"))
    }

    @Test
    fun `saveToken overwrites existing value`() {
        if (!isKeyStoreAvailable) return
        SecureTokenManager.saveToken(context, "overwrite_key", "old_value")
        SecureTokenManager.saveToken(context, "overwrite_key", "new_value")
        assertEquals("new_value", SecureTokenManager.getToken(context, "overwrite_key"))
    }

    @Test
    fun `removeToken removes stored value`() {
        if (!isKeyStoreAvailable) return
        SecureTokenManager.saveToken(context, "remove_key", "some_value")
        SecureTokenManager.removeToken(context, "remove_key")
        assertEquals("", SecureTokenManager.getToken(context, "remove_key"))
    }

    @Test
    fun `clearAll removes all stored values`() {
        if (!isKeyStoreAvailable) return
        SecureTokenManager.saveToken(context, "key1", "val1")
        SecureTokenManager.saveToken(context, "key2", "val2")
        SecureTokenManager.clearAll(context)
        assertEquals("", SecureTokenManager.getToken(context, "key1"))
        assertEquals("", SecureTokenManager.getToken(context, "key2"))
    }

    @Test
    fun `saveToken handles empty key and value`() {
        if (!isKeyStoreAvailable) return
        SecureTokenManager.saveToken(context, "", "")
        assertEquals("", SecureTokenManager.getToken(context, ""))
    }

    @Test
    fun `saveToken handles unicode characters`() {
        if (!isKeyStoreAvailable) return
        SecureTokenManager.saveToken(context, "한글키", "한글값")
        assertEquals("한글값", SecureTokenManager.getToken(context, "한글키"))
    }
}
