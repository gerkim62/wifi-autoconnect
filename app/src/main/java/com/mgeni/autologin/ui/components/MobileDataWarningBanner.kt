package com.mgeni.autologin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgeni.autologin.data.NetworkState
import com.mgeni.autologin.ui.theme.WarningAmber
import com.mgeni.autologin.ui.theme.WarningContainerDark
import com.mgeni.autologin.ui.theme.WarningContainerLight

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

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val isDark = MaterialTheme.colorScheme.background.red < 0.5f
        val bgColor = if (isDark) WarningContainerDark else WarningContainerLight

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

        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = WarningAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = WarningAmber
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

