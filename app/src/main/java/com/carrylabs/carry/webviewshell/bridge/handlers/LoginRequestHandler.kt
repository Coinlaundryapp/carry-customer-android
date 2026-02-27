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
import kotlin.coroutines.resumeWithException

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
        if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
            try {
                return loginWithKakaoTalk()
            } catch (e: Exception) {
                if (e is ClientError && e.reason == ClientErrorCause.Cancelled) return null
            }
        }
        return loginWithKakaoAccount()
    }

    private suspend fun loginWithKakaoTalk(): OAuthToken? = suspendCancellableCoroutine { cont ->
        UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
            when {
                error != null -> cont.resumeWithException(error)
                token != null -> cont.resume(token)
                else -> cont.resume(null)
            }
        }
    }

    private suspend fun loginWithKakaoAccount(): OAuthToken? = suspendCancellableCoroutine { cont ->
        UserApiClient.instance.loginWithKakaoAccount(context) { token, error ->
            when {
                error != null -> cont.resumeWithException(error)
                token != null -> cont.resume(token)
                else -> cont.resume(null)
            }
        }
    }

    fun cleanup() {
        // No-op: SDK manages its own state
    }
}
