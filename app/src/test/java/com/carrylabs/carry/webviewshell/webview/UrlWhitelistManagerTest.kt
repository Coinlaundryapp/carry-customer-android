package com.carrylabs.carry.webviewshell.webview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UrlWhitelistManagerTest {

    // ── isAllowed ────────────────────────────────────────────────────

    @Test
    fun `isAllowed - whitelisted host returns true`() {
        assertTrue(UrlWhitelistManager.isAllowed("https://app.carry.com/home"))
        assertTrue(UrlWhitelistManager.isAllowed("https://staging.carry.com/login"))
        assertTrue(UrlWhitelistManager.isAllowed("https://carry.com"))
    }

    @Test
    fun `isAllowed - subdomain of whitelisted host returns true`() {
        assertTrue(UrlWhitelistManager.isAllowed("https://api.app.carry.com/v1"))
        assertTrue(UrlWhitelistManager.isAllowed("https://www.carry.com/about"))
    }

    @Test
    fun `isAllowed - localhost and emulator returns true`() {
        assertTrue(UrlWhitelistManager.isAllowed("http://localhost:3000/test"))
        assertTrue(UrlWhitelistManager.isAllowed("http://10.0.2.2:3000/test"))
    }

    @Test
    fun `isAllowed - external URL returns false`() {
        assertFalse(UrlWhitelistManager.isAllowed("https://google.com"))
        assertFalse(UrlWhitelistManager.isAllowed("https://naver.com"))
        assertFalse(UrlWhitelistManager.isAllowed("https://evil-carry.com"))
    }

    @Test
    fun `isAllowed - special schemes return false`() {
        assertFalse(UrlWhitelistManager.isAllowed("tel:01012345678"))
        assertFalse(UrlWhitelistManager.isAllowed("mailto:test@carry.com"))
        assertFalse(UrlWhitelistManager.isAllowed("sms:01012345678"))
        assertFalse(UrlWhitelistManager.isAllowed("intent://scan"))
    }

    @Test
    fun `isAllowed - payment app schemes return false`() {
        assertFalse(UrlWhitelistManager.isAllowed("kakaotalk://link"))
        assertFalse(UrlWhitelistManager.isAllowed("ispmobile://pay"))
        assertFalse(UrlWhitelistManager.isAllowed("supertoss://send"))
    }

    // ── isSpecialScheme ──────────────────────────────────────────────

    @Test
    fun `isSpecialScheme - tel mailto sms return true`() {
        assertTrue(UrlWhitelistManager.isSpecialScheme("tel:01012345678"))
        assertTrue(UrlWhitelistManager.isSpecialScheme("mailto:test@carry.com"))
        assertTrue(UrlWhitelistManager.isSpecialScheme("sms:01012345678"))
    }

    @Test
    fun `isSpecialScheme - intent is not special scheme`() {
        assertFalse(UrlWhitelistManager.isSpecialScheme("intent://something"))
    }

    @Test
    fun `isSpecialScheme - http and payment schemes return false`() {
        assertFalse(UrlWhitelistManager.isSpecialScheme("https://carry.com"))
        assertFalse(UrlWhitelistManager.isSpecialScheme("kakaotalk://link"))
        assertFalse(UrlWhitelistManager.isSpecialScheme("ispmobile://pay"))
    }

    // ── isAppLinkScheme ──────────────────────────────────────────────

    @Test
    fun `isAppLinkScheme - payment schemes return true`() {
        assertTrue(UrlWhitelistManager.isAppLinkScheme("kakaotalk://link"))
        assertTrue(UrlWhitelistManager.isAppLinkScheme("kakaolink://send"))
        assertTrue(UrlWhitelistManager.isAppLinkScheme("naversearchapp://search"))
        assertTrue(UrlWhitelistManager.isAppLinkScheme("ispmobile://pay"))
        assertTrue(UrlWhitelistManager.isAppLinkScheme("supertoss://send"))
        assertTrue(UrlWhitelistManager.isAppLinkScheme("tosspayments://pay"))
        assertTrue(UrlWhitelistManager.isAppLinkScheme("shinhan-sr-ansimclick://start"))
        assertTrue(UrlWhitelistManager.isAppLinkScheme("kb-acp://start"))
        assertTrue(UrlWhitelistManager.isAppLinkScheme("payco://order"))
        assertTrue(UrlWhitelistManager.isAppLinkScheme("samsungpay://pay"))
        assertTrue(UrlWhitelistManager.isAppLinkScheme("lotteappcard://pay"))
        assertTrue(UrlWhitelistManager.isAppLinkScheme("wooripay://pay"))
        assertTrue(UrlWhitelistManager.isAppLinkScheme("hanawalletmembers://pay"))
    }

    @Test
    fun `isAppLinkScheme - non-payment schemes return false`() {
        assertFalse(UrlWhitelistManager.isAppLinkScheme("https://carry.com"))
        assertFalse(UrlWhitelistManager.isAppLinkScheme("http://localhost:3000"))
        assertFalse(UrlWhitelistManager.isAppLinkScheme("tel:01012345678"))
        assertFalse(UrlWhitelistManager.isAppLinkScheme("mailto:test@test.com"))
    }

    @Test
    fun `isAppLinkScheme - empty or malformed URL returns false`() {
        assertFalse(UrlWhitelistManager.isAppLinkScheme(""))
        assertFalse(UrlWhitelistManager.isAppLinkScheme("not-a-url"))
    }
}
