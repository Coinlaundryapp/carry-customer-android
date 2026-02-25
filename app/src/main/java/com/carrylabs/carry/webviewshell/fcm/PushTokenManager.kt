package com.carrylabs.carry.webviewshell.fcm

import android.content.Context

object PushTokenManager {

    private const val PREFS_NAME = "carry_push_prefs"
    private const val KEY_FCM_TOKEN = "fcm_token"

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FCM_TOKEN, token)
            .apply()
    }

    fun getToken(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FCM_TOKEN, "") ?: ""
    }
}
