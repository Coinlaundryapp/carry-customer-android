package com.carrylabs.carry.webviewshell.bridge.handlers

import com.carrylabs.carry.webviewshell.bridge.BridgeResult
import com.carrylabs.carry.webviewshell.bridge.NativeCallDispatcher
import com.carrylabs.carry.webviewshell.util.BiometricHelper
import org.json.JSONObject

class BiometricRequestHandler(
    private val biometricHelper: BiometricHelper
) {

    fun handle(requestId: String, args: JSONObject, dispatcher: NativeCallDispatcher) {
        val title = args.optString("title", "Authentication")
        val description = args.optString("description", "")

        if (!biometricHelper.canAuthenticate()) {
            dispatcher.sendCallback(
                BridgeResult(requestId, false, error = "Biometric not available")
            )
            return
        }

        biometricHelper.authenticate(
            title = title,
            description = description,
            onSuccess = {
                dispatcher.sendCallback(
                    BridgeResult(requestId, true, JSONObject().put("authenticated", true))
                )
            },
            onError = { code, message ->
                dispatcher.sendCallback(
                    BridgeResult(requestId, false, error = "Biometric error ($code): $message")
                )
            }
        )
    }
}
