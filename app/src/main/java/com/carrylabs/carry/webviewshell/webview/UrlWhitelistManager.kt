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

    fun isAllowed(url: String): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme ?: return false
        if (scheme in specialSchemes) return false
        val host = uri.host ?: return false
        return allowedHosts.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    fun isSpecialScheme(url: String): Boolean {
        val scheme = Uri.parse(url).scheme ?: return false
        return scheme in specialSchemes
    }
}
