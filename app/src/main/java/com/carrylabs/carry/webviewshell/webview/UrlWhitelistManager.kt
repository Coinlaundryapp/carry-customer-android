package com.carrylabs.carry.webviewshell.webview

import android.net.Uri

object UrlWhitelistManager {

    data class UrlClassification(
        val isSpecialScheme: Boolean,
        val isAppLinkScheme: Boolean,
        val isAllowed: Boolean
    )

    private val allowedHosts = setOf(
        "app.carry.com",
        "staging.carry.com",
        "carry.com",
        "10.0.2.2",
        "localhost"
    )

    private val specialSchemes = setOf("tel", "mailto", "sms")

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

    fun classify(url: String): UrlClassification {
        val uri = Uri.parse(url)
        val scheme = uri.scheme ?: return UrlClassification(
            isSpecialScheme = false, isAppLinkScheme = false, isAllowed = false
        )
        val isSpecial = scheme in specialSchemes
        val isAppLink = scheme in appLinkSchemes
        val host = uri.host
        val isAllowed = !isSpecial && !isAppLink && host != null && allowedHosts.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
        return UrlClassification(isSpecial, isAppLink, isAllowed)
    }

    fun isAllowed(url: String): Boolean = classify(url).isAllowed

    fun isSpecialScheme(url: String): Boolean = classify(url).isSpecialScheme

    fun isAppLinkScheme(url: String): Boolean = classify(url).isAppLinkScheme
}
