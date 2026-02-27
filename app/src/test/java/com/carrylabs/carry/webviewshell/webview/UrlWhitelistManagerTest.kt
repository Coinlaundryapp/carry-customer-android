package com.carrylabs.carry.webviewshell.webview

import org.junit.Assert.assertEquals
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

    // ── classify() ─────────────────────────────────────────────────

    @Test
    fun `classify - whitelisted URL returns isAllowed true only`() {
        val result = UrlWhitelistManager.classify("https://app.carry.com/home")
        assertTrue(result.isAllowed)
        assertFalse(result.isSpecialScheme)
        assertFalse(result.isAppLinkScheme)
    }

    @Test
    fun `classify - subdomain of whitelisted host returns isAllowed true`() {
        val result = UrlWhitelistManager.classify("https://api.carry.com/v1/users")
        assertTrue(result.isAllowed)
        assertFalse(result.isSpecialScheme)
        assertFalse(result.isAppLinkScheme)
    }

    @Test
    fun `classify - special scheme returns isSpecialScheme true only`() {
        val tel = UrlWhitelistManager.classify("tel:01012345678")
        assertTrue(tel.isSpecialScheme)
        assertFalse(tel.isAppLinkScheme)
        assertFalse(tel.isAllowed)

        val mailto = UrlWhitelistManager.classify("mailto:test@carry.com")
        assertTrue(mailto.isSpecialScheme)
        assertFalse(mailto.isAllowed)

        val sms = UrlWhitelistManager.classify("sms:01012345678")
        assertTrue(sms.isSpecialScheme)
        assertFalse(sms.isAllowed)
    }

    @Test
    fun `classify - app link scheme returns isAppLinkScheme true only`() {
        val kakao = UrlWhitelistManager.classify("kakaotalk://link")
        assertTrue(kakao.isAppLinkScheme)
        assertFalse(kakao.isSpecialScheme)
        assertFalse(kakao.isAllowed)

        val toss = UrlWhitelistManager.classify("tosspayments://pay")
        assertTrue(toss.isAppLinkScheme)
        assertFalse(toss.isSpecialScheme)
        assertFalse(toss.isAllowed)
    }

    @Test
    fun `classify - external URL returns all flags false`() {
        val result = UrlWhitelistManager.classify("https://google.com")
        assertFalse(result.isAllowed)
        assertFalse(result.isSpecialScheme)
        assertFalse(result.isAppLinkScheme)
    }

    @Test
    fun `classify - flags are mutually exclusive`() {
        // Each URL should only have at most one flag true
        val urls = listOf(
            "https://app.carry.com",    // isAllowed
            "tel:010",                   // isSpecialScheme
            "kakaotalk://link",          // isAppLinkScheme
            "https://external.com"       // none
        )
        for (url in urls) {
            val c = UrlWhitelistManager.classify(url)
            val trueCount = listOf(c.isAllowed, c.isSpecialScheme, c.isAppLinkScheme).count { it }
            assertTrue(
                "URL '$url' should have at most 1 flag true, but had $trueCount",
                trueCount <= 1
            )
        }
    }

    @Test
    fun `classify - empty URL returns all flags false`() {
        val result = UrlWhitelistManager.classify("")
        assertFalse(result.isAllowed)
        assertFalse(result.isSpecialScheme)
        assertFalse(result.isAppLinkScheme)
    }

    @Test
    fun `classify - URL without scheme returns all flags false`() {
        val result = UrlWhitelistManager.classify("just-some-text")
        assertFalse(result.isAllowed)
        assertFalse(result.isSpecialScheme)
        assertFalse(result.isAppLinkScheme)
    }

    @Test
    fun `classify consistent with convenience methods`() {
        val urls = listOf(
            "https://app.carry.com/path",
            "tel:01012345678",
            "kakaotalk://link",
            "https://google.com",
            ""
        )
        for (url in urls) {
            val c = UrlWhitelistManager.classify(url)
            assertEquals("isAllowed mismatch for '$url'",
                c.isAllowed, UrlWhitelistManager.isAllowed(url))
            assertEquals("isSpecialScheme mismatch for '$url'",
                c.isSpecialScheme, UrlWhitelistManager.isSpecialScheme(url))
            assertEquals("isAppLinkScheme mismatch for '$url'",
                c.isAppLinkScheme, UrlWhitelistManager.isAppLinkScheme(url))
        }
    }
}
