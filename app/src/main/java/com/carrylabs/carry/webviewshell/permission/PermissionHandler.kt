package com.carrylabs.carry.webviewshell.permission

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.LinkedList
import kotlin.coroutines.resume

class PermissionHandler(activity: ComponentActivity) {

    private class PendingRequest(
        val permissions: Array<String>,
        val continuation: CancellableContinuation<Map<String, Boolean>>
    )

    private val pendingQueue = LinkedList<PendingRequest>()
    private var isProcessing = false

    private val permissionLauncher =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val current = pendingQueue.poll()
            isProcessing = false
            current?.continuation?.resume(results)
            processNext()
        }

    suspend fun request(permissions: Array<String>): Map<String, Boolean> {
        require(permissions.isNotEmpty()) { "permissions must not be empty" }
        return suspendCancellableCoroutine { cont ->
            pendingQueue.add(PendingRequest(permissions, cont))
            cont.invokeOnCancellation {
                pendingQueue.removeAll { it.continuation === cont }
            }
            processNext()
        }
    }

    suspend fun requestSingle(permission: String): Boolean {
        val results = request(arrayOf(permission))
        return results[permission] ?: false
    }

    private fun processNext() {
        if (isProcessing) return
        val next = pendingQueue.peek() ?: return
        isProcessing = true
        permissionLauncher.launch(next.permissions)
    }
}
