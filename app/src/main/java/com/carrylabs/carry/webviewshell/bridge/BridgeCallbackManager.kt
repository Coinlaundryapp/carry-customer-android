package com.carrylabs.carry.webviewshell.bridge

import java.util.UUID

object BridgeCallbackManager {

    fun generateRequestId(): String {
        return UUID.randomUUID().toString()
    }
}
