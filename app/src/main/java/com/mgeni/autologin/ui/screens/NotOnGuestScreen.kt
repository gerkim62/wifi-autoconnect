package com.mgeni.autologin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Features top bar actions (Gear + Info), pull-to-refresh connectivity check, and bottom-anchored Retry button.
 */
@Composable
fun NotOnGuestScreen(
    errorMessage: String,
    networkState: NetworkState,
    onRetryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onRefresh: () -> Unit = onRetryClick,
    modifier: Modifier = Modifier
) {
    val isOnlyCellular = networkState == NetworkState.OnlyCellular
    val isOffline = networkState == NetworkState.Offline
    val isUnsupported = errorMessage.contains("not supported", ignoreCase = true)

    val headline = when {
        isOnlyCellular -> "Connected to mobile data"
        isOffline -> "Wi-Fi is disconnected"
        isUnsupported -> "Network not supported"
        else -> "Can't reach the network"
    }

    val description = when {
        isOnlyCellular -> "You are using mobile data, but WifiAuto requires Wi-Fi. Connect to the Guest Wi-Fi network to sign in."
        isOffline -> "Wi-Fi is disconnected. Please turn on Wi-Fi and connect to the Guest network, then try again."
        isUnsupported -> errorMessage
        else -> if (errorMessage.isNotBlank() && !errorMessage.contains("Make sure you're connected", ignoreCase = true)) {
            errorMessage
        } else {
            "Make sure you are connected to the Guest Wi-Fi network (not a different Wi-Fi or private hotspot), then try again."
        }
    }

    Scaffold(
        topBar = {
            TopBarActions(
                onSettingsClick = onSettingsClick,
                onAboutClick = onAboutClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        PullToRefreshLayout(
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Show ambient advice banner when both Wi-Fi and Cellular are active
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (networkState == NetworkState.BothWifiAndCellular) {
                        MobileDataWarningBanner(networkState = networkState)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
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
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    PrimaryActionButton(
                        text = "Retry",
                        onClick = onRetryClick
                    )
                }
            }
        }
    }
}
