package com.carrylabs.carry.webviewshell.bridge.handlers

import android.Manifest
import android.content.pm.PackageManager
import android.webkit.GeolocationPermissions
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.carrylabs.carry.webviewshell.bridge.BridgeResult
import com.carrylabs.carry.webviewshell.bridge.NativeCallDispatcher
import com.carrylabs.carry.webviewshell.permission.PermissionHandler
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

class LocationRequestHandler(
    private val activity: AppCompatActivity,
    private val permissionHandler: PermissionHandler
) {

    private var pendingGeolocationOrigin: String? = null
    private var pendingGeolocationCallback: GeolocationPermissions.Callback? = null
    private var activeCancellationToken: CancellationTokenSource? = null

    suspend fun handle(requestId: String, dispatcher: NativeCallDispatcher) {
        if (!hasLocationPermission()) {
            val granted = permissionHandler.requestSingle(Manifest.permission.ACCESS_FINE_LOCATION)
            if (!granted) {
                dispatcher.sendCallback(
                    BridgeResult(requestId, false, error = "Location permission denied")
                )
                return
            }
        }
        val result = fetchLocation(requestId)
        dispatcher.sendCallback(result)
    }

    suspend fun handleGeolocationPermission(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        if (hasLocationPermission()) {
            callback.invoke(origin, true, false)
            return
        }
        pendingGeolocationOrigin = origin
        pendingGeolocationCallback = callback
        val granted = permissionHandler.requestSingle(Manifest.permission.ACCESS_FINE_LOCATION)
        pendingGeolocationCallback?.invoke(pendingGeolocationOrigin ?: origin, granted, false)
        pendingGeolocationCallback = null
        pendingGeolocationOrigin = null
    }

    fun invalidateGeolocationCallback() {
        pendingGeolocationCallback = null
        pendingGeolocationOrigin = null
    }

    fun cleanup() {
        activeCancellationToken?.cancel()
        activeCancellationToken = null
        invalidateGeolocationCallback()
    }

    private suspend fun fetchLocation(requestId: String): BridgeResult {
        if (!hasLocationPermission()) {
            return BridgeResult(requestId, false, error = "Location permission not granted")
        }

        return suspendCancellableCoroutine { cont ->
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(activity)
                val cancellationToken = CancellationTokenSource()
                activeCancellationToken = cancellationToken

                cont.invokeOnCancellation { cancellationToken.cancel() }

                fusedClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationToken.token
                ).addOnSuccessListener { location ->
                    activeCancellationToken = null
                    if (location != null) {
                        val data = JSONObject().apply {
                            put("latitude", location.latitude)
                            put("longitude", location.longitude)
                            put("accuracy", location.accuracy.toDouble())
                        }
                        cont.resume(BridgeResult(requestId, true, data))
                    } else {
                        cont.resume(
                            BridgeResult(requestId, false, error = "Location unavailable")
                        )
                    }
                }.addOnFailureListener { e ->
                    activeCancellationToken = null
                    cont.resume(
                        BridgeResult(requestId, false, error = "Location error: ${e.message}")
                    )
                }
            } catch (e: Exception) {
                activeCancellationToken = null
                cont.resume(
                    BridgeResult(requestId, false, error = "Location error: ${e.message}")
                )
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
