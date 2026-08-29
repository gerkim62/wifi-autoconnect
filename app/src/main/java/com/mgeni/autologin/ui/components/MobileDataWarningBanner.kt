package com.mgeni.autologin.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mgeni.autologin.data.NetworkState


/**
 * Dynamic network advice banner shown when connectivity requires user attention:
 * - Only Mobile Data is active (Wi-Fi disconnected)
 * - Both Wi-Fi and Mobile Data are active (Android cellular priority conflict)
 * - Completely offline
 * Automatically hidden when normal Wi-Fi only is active.
 */
@Composable
fun MobileDataWarningBanner(
    networkState: NetworkState,
    modifier: Modifier = Modifier
) {
    val isVisible = networkState != NetworkState.OnlyWifi

    if (!isVisible) return

    val (title, description, icon) = when (networkState) {
        NetworkState.OnlyCellular -> Triple(
            "Only Mobile Data is Active",
            "Wi-Fi is disconnected. Please connect to your Wi-Fi network to sign in.",
            Icons.Outlined.SignalCellularAlt
        )
        NetworkState.BothWifiAndCellular -> Triple(
            "Both Wi-Fi and Mobile Data are Active",
            "Android may prioritize mobile data over Wi-Fi. If you experience connection issues, consider turning off Mobile Data temporarily.",
            Icons.Outlined.SignalCellularAlt
        )
        NetworkState.Offline -> Triple(
            "No Connection",
            "Wi-Fi is disconnected. Please turn on and connect to Wi-Fi to continue.",
            Icons.Outlined.SignalWifiOff
        )
        NetworkState.OnlyWifi -> Triple("", "", Icons.Outlined.SignalCellularAlt)
    }

    StatusBanner(
        type = BannerType.Warning,
        title = title,
        message = description,
        icon = icon,
        visible = isVisible,
        modifier = modifier
    )
}

