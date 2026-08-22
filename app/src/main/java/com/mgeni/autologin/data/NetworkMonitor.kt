package com.mgeni.autologin.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkState {
    OnlyCellular,
    BothWifiAndCellular,
    OnlyWifi,
    Offline
}

/**
 * Monitors active network interfaces (Wi-Fi, Cellular) in real-time to provide truthful,
 * dynamic connectivity states and warnings.
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _networkState = MutableStateFlow(NetworkState.Offline)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val _isCellularActive = MutableStateFlow(false)
    val isCellularActive: StateFlow<Boolean> = _isCellularActive.asStateFlow()

    private val _isWifiActive = MutableStateFlow(false)
    val isWifiActive: StateFlow<Boolean> = _isWifiActive.asStateFlow()

    private val capabilitiesByNetwork = mutableMapOf<Network, NetworkCapabilities>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val capabilities = connectivityManager?.getNetworkCapabilities(network) ?: return
            updateCapabilities(network, capabilities)
        }

        override fun onLost(network: Network) {
            synchronized(capabilitiesByNetwork) {
                capabilitiesByNetwork.remove(network)
                publishNetworkStates()
            }
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            updateCapabilities(network, networkCapabilities)
        }
    }

    init {
        updateNetworkStates()
        register()
    }

    private fun register() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
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
        val snapshot = try {
            cm.allNetworks.mapNotNull { network ->
                cm.getNetworkCapabilities(network)?.let { network to it }
            }
        } catch (_: Exception) {
            cm.activeNetwork?.let { network ->
                cm.getNetworkCapabilities(network)?.let { capabilities ->
                    listOf(network to capabilities)
                }
            }.orEmpty()
        }

        synchronized(capabilitiesByNetwork) {
            capabilitiesByNetwork.clear()
            capabilitiesByNetwork.putAll(snapshot)
            publishNetworkStates()
        }
    }

    private fun updateCapabilities(network: Network, capabilities: NetworkCapabilities) {
        synchronized(capabilitiesByNetwork) {
            capabilitiesByNetwork[network] = capabilities
            publishNetworkStates()
        }
    }

    private fun publishNetworkStates() {
        val hasWifi = capabilitiesByNetwork.values.any { capabilities ->
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
        }
        val hasCellular = capabilitiesByNetwork.values.any { capabilities ->
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        _isCellularActive.value = hasCellular
        _isWifiActive.value = hasWifi
        _networkState.value = when {
            hasWifi && hasCellular -> NetworkState.BothWifiAndCellular
            hasWifi -> NetworkState.OnlyWifi
            hasCellular -> NetworkState.OnlyCellular
            else -> NetworkState.Offline
        }
    }
}
