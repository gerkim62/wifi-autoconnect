package com.mgeni.autologin.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
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
open class NetworkMonitor(context: Context? = null) {

    private val connectivityManager =
        context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _networkState = MutableStateFlow(NetworkState.Offline)
    open val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val _isCellularActive = MutableStateFlow(false)
    open val isCellularActive: StateFlow<Boolean> = _isCellularActive.asStateFlow()

    private val _isWifiActive = MutableStateFlow(false)
    open val isWifiActive: StateFlow<Boolean> = _isWifiActive.asStateFlow()

    private val _wifiChangeCount = MutableStateFlow(0L)
    open val wifiChangeCount: StateFlow<Long> = _wifiChangeCount.asStateFlow()

    private val capabilitiesByNetwork = mutableMapOf<Network, NetworkCapabilities>()
    private var lastWifiNetworks: Set<Network> = emptySet()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val capabilities = connectivityManager?.getNetworkCapabilities(network)
            if (capabilities != null) {
                updateCapabilities(network, capabilities)
            } else {
                updateNetworkStates()
            }
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
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                _wifiChangeCount.value += 1L
            }
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            val capabilities = capabilitiesByNetwork[network] ?: connectivityManager?.getNetworkCapabilities(network)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                _wifiChangeCount.value += 1L
            }
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
        val wifiNetworks = capabilitiesByNetwork.filter { (_, capabilities) ->
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
        }.keys

        val hasWifi = wifiNetworks.isNotEmpty()
        val hasCellular = capabilitiesByNetwork.values.any { capabilities ->
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
        }

        if (wifiNetworks != lastWifiNetworks) {
            lastWifiNetworks = wifiNetworks.toSet()
            _wifiChangeCount.value += 1L
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

    /**
     * Testing hook to simulate network interface state transitions without Android Framework dependencies.
     */
    fun emitNetworkStateForTesting(hasWifi: Boolean, hasCellular: Boolean) {
        _isCellularActive.value = hasCellular
        val wifiChanged = _isWifiActive.value != hasWifi
        _isWifiActive.value = hasWifi
        _networkState.value = when {
            hasWifi && hasCellular -> NetworkState.BothWifiAndCellular
            hasWifi -> NetworkState.OnlyWifi
            hasCellular -> NetworkState.OnlyCellular
            else -> NetworkState.Offline
        }
        if (wifiChanged) {
            _wifiChangeCount.value += 1L
        }
    }
}
