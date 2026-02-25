package com.carrylabs.carry.webviewshell.permission

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class PermissionHandler(activity: ComponentActivity) {

    private var onResult: ((Map<String, Boolean>) -> Unit)? = null

    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            onResult?.invoke(results)
            onResult = null
        }

    fun request(permissions: Array<String>, callback: (Map<String, Boolean>) -> Unit) {
        onResult = callback
        permissionLauncher.launch(permissions)
    }

    fun requestSingle(permission: String, callback: (Boolean) -> Unit) {
        request(arrayOf(permission)) { results ->
            callback(results[permission] ?: false)
        }
    }
}
