package com.mgeni.autologin.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors active network interfaces (Wi-Fi, Cellular) to warn users when
 * mobile data is active alongside captive Wi-Fi.
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isCellularActive = MutableStateFlow(false)
    val isCellularActive: StateFlow<Boolean> = _isCellularActive.asStateFlow()

    private val _isWifiActive = MutableStateFlow(false)
    val isWifiActive: StateFlow<Boolean> = _isWifiActive.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkStates()
        }

        override fun onLost(network: Network) {
            updateNetworkStates()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            updateNetworkStates()
        }
    }

    init {
        updateNetworkStates()
        register()
    }

    private fun register() {
        try {
            val request = NetworkRequest.Builder().build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (_: Exception) {
            // Ignore if restricted or unavailable
        }
    }

    fun unregister() {
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
            // Ignore unregister errors on destroy
        }
    }

    fun updateNetworkStates() {
        val cm = connectivityManager ?: return
        var hasCellular = false
        var hasWifi = false

        try {
            val activeNetworks = cm.allNetworks
            for (network in activeNetworks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    hasCellular = true
                }
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    hasWifi = true
                }
            }
        } catch (_: Exception) {
            // Fallback to active network info
            val activeCap = cm.getNetworkCapabilities(cm.activeNetwork)
            if (activeCap != null) {
                hasCellular = activeCap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                hasWifi = activeCap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
        }

        _isCellularActive.value = hasCellular
        _isWifiActive.value = hasWifi
    }
}
