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
import org.json.JSONObject

class LocationRequestHandler(
    private val activity: AppCompatActivity,
    private val permissionHandler: PermissionHandler
) {

    private var pendingGeolocationOrigin: String? = null
    private var pendingGeolocationCallback: GeolocationPermissions.Callback? = null

    fun handle(requestId: String, dispatcher: NativeCallDispatcher) {
        if (!hasLocationPermission()) {
            permissionHandler.requestSingle(Manifest.permission.ACCESS_FINE_LOCATION) { granted ->
                if (granted) {
                    fetchLocation(requestId, dispatcher)
                } else {
                    dispatcher.sendCallback(
                        BridgeResult(requestId, false, error = "Location permission denied")
                    )
                }
            }
            return
        }
        fetchLocation(requestId, dispatcher)
    }

    fun handleGeolocationPermission(origin: String, callback: GeolocationPermissions.Callback) {
        if (hasLocationPermission()) {
            callback.invoke(origin, true, false)
            return
        }
        pendingGeolocationOrigin = origin
        pendingGeolocationCallback = callback
        permissionHandler.requestSingle(Manifest.permission.ACCESS_FINE_LOCATION) { granted ->
            pendingGeolocationCallback?.invoke(pendingGeolocationOrigin ?: origin, granted, false)
            pendingGeolocationCallback = null
            pendingGeolocationOrigin = null
        }
    }

    private fun fetchLocation(requestId: String, dispatcher: NativeCallDispatcher) {
        try {
            if (!hasLocationPermission()) {
                dispatcher.sendCallback(
                    BridgeResult(requestId, false, error = "Location permission not granted")
                )
                return
            }

            val fusedClient = LocationServices.getFusedLocationProviderClient(activity)
            val cancellationToken = CancellationTokenSource()

            fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    val data = JSONObject().apply {
                        put("latitude", location.latitude)
                        put("longitude", location.longitude)
                        put("accuracy", location.accuracy.toDouble())
                    }
                    dispatcher.sendCallback(BridgeResult(requestId, true, data))
                } else {
                    dispatcher.sendCallback(
                        BridgeResult(requestId, false, error = "Location unavailable")
                    )
                }
            }.addOnFailureListener { e ->
                dispatcher.sendCallback(
                    BridgeResult(requestId, false, error = "Location error: ${e.message}")
                )
            }
        } catch (e: Exception) {
            dispatcher.sendCallback(
                BridgeResult(requestId, false, error = "Location error: ${e.message}")
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
