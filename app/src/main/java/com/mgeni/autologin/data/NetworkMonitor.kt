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
 * dynamic connectivity states and warnings without false-positive jitter triggers.
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

    private val _activeWifiNetwork = MutableStateFlow<Network?>(null)
    open val activeWifiNetwork: StateFlow<Network?> = _activeWifiNetwork.asStateFlow()

    private val capabilitiesByNetwork = mutableMapOf<Network, NetworkCapabilities>()
    private val linkPropertiesByNetwork = mutableMapOf<Network, LinkProperties>()
    private var lastWifiNetworks: Set<Network> = emptySet()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            AppLogger.d("NETWORK_MONITOR", "onAvailable: network=$network")
            val cm = connectivityManager
            val capabilities = cm?.getNetworkCapabilities(network)
            val linkProperties = cm?.getLinkProperties(network)
            synchronized(capabilitiesByNetwork) {
                if (capabilities != null) capabilitiesByNetwork[network] = capabilities
                if (linkProperties != null) linkPropertiesByNetwork[network] = linkProperties
            }
            publishNetworkStates()
        }

        override fun onLost(network: Network) {
            AppLogger.d("NETWORK_MONITOR", "onLost: network=$network")
            synchronized(capabilitiesByNetwork) {
                capabilitiesByNetwork.remove(network)
                linkPropertiesByNetwork.remove(network)
            }
            publishNetworkStates()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            synchronized(capabilitiesByNetwork) {
                capabilitiesByNetwork[network] = networkCapabilities
            }
            publishNetworkStates()
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            AppLogger.d("NETWORK_MONITOR", "onLinkPropertiesChanged: network=$network, ipCount=${linkProperties.linkAddresses.size}")
            synchronized(capabilitiesByNetwork) {
                linkPropertiesByNetwork[network] = linkProperties
            }
            publishNetworkStates()
        }
    }

    private val defaultNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            AppLogger.d("NETWORK_MONITOR", "defaultNetwork onAvailable: network=$network")
            publishNetworkStates()
        }

        override fun onLost(network: Network) {
            AppLogger.d("NETWORK_MONITOR", "defaultNetwork onLost: network=$network")
            publishNetworkStates()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            publishNetworkStates()
        }
    }

    init {
        updateNetworkStates()
        register()
    }

    private fun register() {
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
            connectivityManager?.registerDefaultNetworkCallback(defaultNetworkCallback)
        } catch (e: Exception) {
            AppLogger.w("NETWORK_MONITOR", "Failed to register network callbacks: ${e.localizedMessage}", e)
        }
    }

    fun unregister() {
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
            connectivityManager?.unregisterNetworkCallback(defaultNetworkCallback)
        } catch (e: Exception) {
            AppLogger.w("NETWORK_MONITOR", "Failed to unregister network callbacks: ${e.localizedMessage}", e)
        }
    }

    fun updateNetworkStates() {
        val cm = connectivityManager ?: return
        val capSnapshot = mutableMapOf<Network, NetworkCapabilities>()
        val linkSnapshot = mutableMapOf<Network, LinkProperties>()

        try {
            cm.allNetworks.forEach { network ->
                cm.getNetworkCapabilities(network)?.let { capSnapshot[network] = it }
                cm.getLinkProperties(network)?.let { linkSnapshot[network] = it }
            }
        } catch (e: Exception) {
            AppLogger.w("NETWORK_MONITOR", "Failed to query allNetworks, falling back to activeNetwork: ${e.localizedMessage}", e)
            cm.activeNetwork?.let { network ->
                cm.getNetworkCapabilities(network)?.let { capSnapshot[network] = it }
                cm.getLinkProperties(network)?.let { linkSnapshot[network] = it }
            }
        }

        synchronized(capabilitiesByNetwork) {
            capabilitiesByNetwork.clear()
            capabilitiesByNetwork.putAll(capSnapshot)
            linkPropertiesByNetwork.clear()
            linkPropertiesByNetwork.putAll(linkSnapshot)
        }
        publishNetworkStates()
    }

    private fun publishNetworkStates() {
        val wifiNetworks = synchronized(capabilitiesByNetwork) {
            capabilitiesByNetwork.filter { (net, capabilities) ->
                val lp = linkPropertiesByNetwork[net]
                val hasIpConfig = lp == null || lp.linkAddresses.isNotEmpty()
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED) &&
                    hasIpConfig
            }.keys
        }

        val hasWifi = wifiNetworks.isNotEmpty()
        val hasCellular = capabilitiesByNetwork.values.any { capabilities ->
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
        }

        if (wifiNetworks != lastWifiNetworks) {
            AppLogger.i("NETWORK_MONITOR", "Wi-Fi network set changed from $lastWifiNetworks to $wifiNetworks (hasWifi=$hasWifi)")
            lastWifiNetworks = wifiNetworks.toSet()
            _wifiChangeCount.value += 1L
        }

        _activeWifiNetwork.value = wifiNetworks.firstOrNull()
        _isCellularActive.value = hasCellular
        _isWifiActive.value = hasWifi
        val newState = when {
            hasWifi && hasCellular -> NetworkState.BothWifiAndCellular
            hasWifi -> NetworkState.OnlyWifi
            hasCellular -> NetworkState.OnlyCellular
            else -> NetworkState.Offline
        }

        if (_networkState.value != newState) {
            AppLogger.i("NETWORK_MONITOR", "NetworkState transitioned: ${_networkState.value} -> $newState (wifi=$hasWifi, cell=$hasCellular)")
            _networkState.value = newState
        }
    }

    /**
     * Testing hook to simulate network interface state transitions without Android Framework dependencies.
     */
    fun emitNetworkStateForTesting(hasWifi: Boolean, hasCellular: Boolean, network: Network? = null) {
        _isCellularActive.value = hasCellular
        _activeWifiNetwork.value = if (hasWifi) network else null
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
