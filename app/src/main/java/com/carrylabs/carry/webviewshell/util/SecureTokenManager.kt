package com.carrylabs.carry.webviewshell.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SecureTokenManager {

    private const val PREFS_NAME = "carry_secure_prefs"

    @Volatile
    private var prefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return prefs ?: synchronized(this) {
            prefs ?: createEncryptedPrefs(context.applicationContext).also { prefs = it }
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveToken(context: Context, key: String, value: String) {
        getPrefs(context).edit { putString(key, value) }
    }

    fun getToken(context: Context, key: String): String {
        return getPrefs(context).getString(key, "") ?: ""
    }

    fun removeToken(context: Context, key: String) {
        getPrefs(context).edit { remove(key) }
    }

    fun clearAll(context: Context) {
        getPrefs(context).edit { clear() }
    }
}
