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

        val oauthUrl = "https://kauth.kakao.com/oauth/authorize" +
                "?client_id=$kakaoClientId" +
                "&redirect_uri=carry://oauth/kakao" +
                "&response_type=code"

        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.launchUrl(activity, Uri.parse(oauthUrl))
    }

    fun handleKakaoLoginResult(code: String, dispatcher: NativeCallDispatcher) {
        val reqId = pendingRequestId ?: return
        val data = JSONObject().put("code", code)
        dispatcher.sendCallback(BridgeResult(reqId, true, data))
        dispatcher.dispatchLoginComplete(code)
        pendingRequestId = null
    }
}
