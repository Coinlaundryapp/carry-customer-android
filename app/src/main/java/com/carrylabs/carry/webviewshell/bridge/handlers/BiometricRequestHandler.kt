package com.carrylabs.carry.webviewshell.bridge.handlers

import com.carrylabs.carry.webviewshell.bridge.BridgeResult
import com.carrylabs.carry.webviewshell.bridge.NativeCallDispatcher
import com.carrylabs.carry.webviewshell.util.BiometricHelper
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

class BiometricRequestHandler(
    private val biometricHelper: BiometricHelper
) {

    suspend fun handle(requestId: String, args: JSONObject, dispatcher: NativeCallDispatcher) {
        val title = args.optString("title", "Authentication")
        val description = args.optString("description", "")

        if (!biometricHelper.canAuthenticate()) {
            dispatcher.sendCallback(
                BridgeResult(requestId, false, error = "Biometric not available")
            )
            return
        }

        val result = suspendCancellableCoroutine { cont ->
            biometricHelper.authenticate(
                title = title,
                description = description,
                onSuccess = {
                    cont.resume(
                        BridgeResult(requestId, true, JSONObject().put("authenticated", true))
                    )
                },
                onError = { code, message ->
                    cont.resume(
                        BridgeResult(requestId, false, error = "Biometric error ($code): $message")
                    )
                }
            )
        }
        dispatcher.sendCallback(result)
    }
}
