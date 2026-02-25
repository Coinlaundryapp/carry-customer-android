package com.carrylabs.carry.webviewshell.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import org.json.JSONObject

object DeviceInfo {

    fun collect(context: Context): JSONObject {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return JSONObject().apply {
            put("platform", "android")
            put("osVersion", Build.VERSION.RELEASE)
            put("sdkVersion", Build.VERSION.SDK_INT)
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("brand", Build.BRAND)
            put("appVersion", packageInfo.versionName ?: "unknown")
            put("appVersionCode", packageInfo.longVersionCode)
            put("packageName", context.packageName)
            put("deviceId", getDeviceId(context))
        }
    }

    private fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
    }
}
