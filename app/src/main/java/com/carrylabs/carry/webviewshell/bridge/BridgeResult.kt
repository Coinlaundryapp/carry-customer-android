package com.carrylabs.carry.webviewshell.bridge

import org.json.JSONObject

data class BridgeResult(
    val requestId: String,
    val success: Boolean,
    val data: JSONObject? = null,
    val error: String? = null
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("requestId", requestId)
            put("success", success)
            if (data != null) put("data", data)
            if (error != null) put("error", error)
        }.toString()
    }
}
