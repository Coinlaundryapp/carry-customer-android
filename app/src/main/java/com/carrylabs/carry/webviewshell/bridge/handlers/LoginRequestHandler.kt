package com.carrylabs.carry.webviewshell.bridge.handlers

import android.content.Context
import com.carrylabs.carry.webviewshell.bridge.BridgeResult
import com.carrylabs.carry.webviewshell.bridge.NativeCallDispatcher
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

class LoginRequestHandler(
    private val context: Context
) {

    suspend fun handle(requestId: String, dispatcher: NativeCallDispatcher) {
        try {
            val token = loginWithKakao()
            if (token != null) {
                val data = JSONObject().put("accessToken", token.accessToken)
                dispatcher.sendCallback(BridgeResult(requestId, true, data))
                dispatcher.dispatchLoginComplete(token.accessToken)
            } else {
                dispatcher.sendCallback(
                    BridgeResult(requestId, false, error = "Login cancelled")
                )
            }
        } catch (e: Exception) {
            dispatcher.sendCallback(
                BridgeResult(requestId, false, error = "Login failed: ${e.message}")
            )
        }
    }

    private suspend fun loginWithKakao(): OAuthToken? {
        // Try KakaoTalk app login first
        if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
            try {
                val token = loginWithKakaoTalk()
                if (token != null) return token
            } catch (e: Exception) {
                // If user intentionally cancelled KakaoTalk login, don't fall back
                if (e is ClientError && e.reason == ClientErrorCause.Cancelled) return null
                // Otherwise fall through to web login
            }
        }
        // Fallback to web login
        return loginWithKakaoAccount()
    }

    private suspend fun loginWithKakaoTalk(): OAuthToken? = suspendCancellableCoroutine { cont ->
        UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
            if (error != null) {
                cont.resume(null)
            } else {
                cont.resume(token)
            }
        }
    }

    private suspend fun loginWithKakaoAccount(): OAuthToken? = suspendCancellableCoroutine { cont ->
        UserApiClient.instance.loginWithKakaoAccount(context) { token, error ->
            if (error != null) {
                cont.resume(null)
            } else {
                cont.resume(token)
            }
        }
    }

    fun cleanup() {
        // No-op: SDK manages its own state
    }
}
