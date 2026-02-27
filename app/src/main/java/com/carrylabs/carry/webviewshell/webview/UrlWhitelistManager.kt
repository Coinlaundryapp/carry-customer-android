package com.carrylabs.carry.webviewshell.webview

import android.net.Uri

object UrlWhitelistManager {

    private val allowedHosts = setOf(
        "app.carry.com",
        "staging.carry.com",
        "carry.com",
        "10.0.2.2",
        "localhost"
    )

    private val specialSchemes = setOf("tel", "mailto", "sms", "intent")

    private val appLinkSchemes = setOf(
        "kakaotalk",
        "kakaolink",
        "kakaokompassauth",
        "storykompassauth",
        "naversearchapp",
        "naversearchthirdlogin",
        "naverblog",
        "ispmobile",
        "shinhan-sr-ansimclick",
        "kb-acp",
        "kftc-bankpay",
        "liivbank",
        "newsmartpib",
        "nhappcardansimclick",
        "lottesmartpay",
        "lotteappcard",
        "mpocket.online.ansimclick",
        "payco",
        "supertoss",
        "tosspayments",
        "vguardstart",
        "samsungpay",
        "shinsegaeeasypayment",
        "wooripay",
        "hanawalletmembers",
        "cloudpay",
        "citimobileapp",
        "com.wooricard.wcard",
        "newliiv",
        "nhallonepayansimclick"
    )

    fun isAllowed(url: String): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme ?: return false
        if (scheme in specialSchemes) return false
        if (scheme in appLinkSchemes) return false
        val host = uri.host ?: return false
        return allowedHosts.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    fun isSpecialScheme(url: String): Boolean {
        val scheme = Uri.parse(url).scheme ?: return false
        return scheme in specialSchemes
    }

    fun isAppLinkScheme(url: String): Boolean {
        val scheme = Uri.parse(url).scheme ?: return false
        return scheme in appLinkSchemes
    }
}
