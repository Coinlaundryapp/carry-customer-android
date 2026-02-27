package com.carrylabs.carry.webviewshell.bridge.handlers

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.carrylabs.carry.webviewshell.BuildConfig
import com.carrylabs.carry.webviewshell.bridge.BridgeResult
import com.carrylabs.carry.webviewshell.bridge.NativeCallDispatcher
import org.json.JSONObject

class LoginRequestHandler(
    private val activity: AppCompatActivity
) {

    companion object {
        const val KAKAO_AUTH_URL = "https://kauth.kakao.com/oauth/authorize"
        const val KAKAO_REDIRECT_URI = "carry://oauth/kakao"
        const val KAKAO_REDIRECT_SCHEME = "carry"
        const val KAKAO_REDIRECT_HOST = "oauth"
        const val KAKAO_REDIRECT_PATH = "/kakao"
    }

    private var pendingRequestId: String? = null

    fun handle(requestId: String, dispatcher: NativeCallDispatcher) {
        val kakaoClientId = BuildConfig.KAKAO_CLIENT_ID
        if (kakaoClientId.isEmpty()) {
            dispatcher.sendCallback(
                BridgeResult(requestId, false, error = "Kakao client ID not configured")
            )
            return
        }
        pendingRequestId = requestId

        val oauthUri = Uri.parse(KAKAO_AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", kakaoClientId)
            .appendQueryParameter("redirect_uri", KAKAO_REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .build()

        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.launchUrl(activity, oauthUri)
    }

    fun handleKakaoLoginResult(code: String, dispatcher: NativeCallDispatcher) {
        val reqId = pendingRequestId ?: return
        val data = JSONObject().put("code", code)
        dispatcher.sendCallback(BridgeResult(reqId, true, data))
        dispatcher.dispatchLoginComplete(code)
        pendingRequestId = null
    }

    fun cleanup() {
        pendingRequestId = null
    }
}
