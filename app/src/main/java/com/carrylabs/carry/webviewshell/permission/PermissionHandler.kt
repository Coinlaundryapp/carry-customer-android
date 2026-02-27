package com.carrylabs.carry.webviewshell.permission

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.util.LinkedList

class PermissionHandler(activity: ComponentActivity) {

    private data class PendingRequest(
        val permissions: Array<String>,
        val callback: (Map<String, Boolean>) -> Unit
    )

    private val pendingQueue = LinkedList<PendingRequest>()
    private var isProcessing = false

    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val current = pendingQueue.poll()
            current?.callback?.invoke(results)
            isProcessing = false
            processNext()
        }

    fun request(permissions: Array<String>, callback: (Map<String, Boolean>) -> Unit) {
        pendingQueue.add(PendingRequest(permissions, callback))
        processNext()
    }

    fun requestSingle(permission: String, callback: (Boolean) -> Unit) {
        request(arrayOf(permission)) { results ->
            callback(results[permission] ?: false)
        }
    }

    private fun processNext() {
        if (isProcessing) return
        val next = pendingQueue.peek() ?: return
        isProcessing = true
        permissionLauncher.launch(next.permissions)
    }
}
