package com.mgeni.autologin.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mgeni.autologin.data.NetworkState
import com.mgeni.autologin.ui.components.ErrorStatusIcon
import com.mgeni.autologin.ui.components.MobileDataWarningBanner
import com.mgeni.autologin.ui.components.PrimaryActionButton
import com.mgeni.autologin.ui.components.PullToRefreshLayout
import com.mgeni.autologin.ui.components.TopBarActions
import com.mgeni.autologin.ui.components.WarningStatusIcon

/**
 * Screen 3: Wi-Fi Disconnected / Unreachable Screen
 * Features top bar actions (Gear + Info), pull-to-refresh connectivity check,
 * elevated status card positioning, restore to default settings helper, and retry action button.
 */
@Composable
fun NotOnGuestScreen(
    errorMessage: String,
    networkState: NetworkState,
    onRetryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onHelpClick: (() -> Unit)? = null,
    onRestoreDefaultClick: (() -> Unit)? = null,
    onRefresh: () -> Unit = onRetryClick,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isOnlyCellular = networkState == NetworkState.OnlyCellular
    val isOffline = networkState == NetworkState.Offline
    val isUnsupported = errorMessage.contains("not supported", ignoreCase = true) || errorMessage.contains("supported today", ignoreCase = true)

    val headline = when {
        isOnlyCellular -> "Connected to mobile data"
        isOffline -> "Wi-Fi is disconnected"
        isUnsupported -> "Network not supported"
        else -> "Can't reach the network"
    }

    val description = when {
        isOnlyCellular -> "You are using mobile data, but WifiAuto requires Wi-Fi. Connect to the \"guest\" Wi-Fi network to sign in."
        isOffline -> "Wi-Fi is disconnected. Please turn on Wi-Fi and connect to the \"guest\" Wi-Fi network, then try again."
        isUnsupported -> errorMessage
        else -> if (errorMessage.isNotBlank() && !errorMessage.contains("Make sure you're connected", ignoreCase = true)) {
            errorMessage
        } else {
            "Make sure you are connected to the \"guest\" Wi-Fi network (not a different Wi-Fi or private hotspot)."
        }
    }

    Scaffold(
        topBar = {
            TopBarActions(
                onSettingsClick = onSettingsClick,
                onAboutClick = onAboutClick,
                onHelpClick = onHelpClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        PullToRefreshLayout(
            onRefresh = onRefresh,
            isRefreshing = isRefreshing,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (networkState == NetworkState.BothWifiAndCellular) {
                    MobileDataWarningBanner(
                        networkState = networkState,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Higher top positioning (~20% top bias)
                Spacer(modifier = Modifier.weight(0.20f))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    when {
                        isOnlyCellular -> WarningStatusIcon(icon = Icons.Outlined.SignalCellularAlt)
                        isOffline -> WarningStatusIcon(icon = Icons.Outlined.SignalWifiOff)
                        else -> ErrorStatusIcon()
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = headline,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (!isOnlyCellular && !isOffline && !isUnsupported && onRestoreDefaultClick != null) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onRestoreDefaultClick() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.RestartAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Restore to default settings",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Bottom spacer balancing the elevated position
                Spacer(modifier = Modifier.weight(0.80f))

                PrimaryActionButton(
                    text = "Retry",
                    onClick = onRetryClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 36.dp)
                )
            }
        }
    }
}
