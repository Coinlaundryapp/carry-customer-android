package com.carrylabs.carry.webviewshell.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import org.json.JSONObject

class NetworkUtils(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun getNetworkStatus(): JSONObject {
        val network = connectivityManager.activeNetwork
        val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }

        val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
        val type = when {
            caps == null -> "none"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            else -> "none"
        }
        val isMetered = connectivityManager.isActiveNetworkMetered

        return JSONObject().apply {
            put("isConnected", isConnected)
            put("type", type)
            put("isMetered", isMetered)
        }
    }

    fun registerNetworkCallback(onChanged: (JSONObject) -> Unit): ConnectivityManager.NetworkCallback {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onChanged(getNetworkStatus())
            }

            override fun onLost(network: Network) {
                onChanged(JSONObject().apply {
                    put("isConnected", false)
                    put("type", "none")
                    put("isMetered", false)
                })
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                onChanged(getNetworkStatus())
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        return callback
    }

    fun unregisterNetworkCallback(callback: ConnectivityManager.NetworkCallback) {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }
}
