package com.mgeni.autologin.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.mgeni.autologin.ui.components.SecondaryActionButton
import com.mgeni.autologin.ui.components.TopBarActions

/**
 * Screen 7: Login Failed Screen
 * Features top bar actions (Gear + Info), pull-to-refresh connectivity check,
 * elevated status card positioning, and bottom action buttons with generous clearance.
 */
@Composable
fun LoginFailedScreen(
    errorMessage: String,
    savedUsername: String,
    networkState: NetworkState,
    onTryAgainClick: () -> Unit,
    onEditCredentialsClick: (username: String) -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onHelpClick: (() -> Unit)? = null,
    onRefresh: () -> Unit = onTryAgainClick,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
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
                    ErrorStatusIcon()

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Login failed",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = errorMessage.ifBlank { "Your username or password may be incorrect." },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // Bottom spacer balancing the elevated position
                Spacer(modifier = Modifier.weight(0.80f))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 36.dp)
                ) {
                    PrimaryActionButton(
                        text = "Try again",
                        onClick = onTryAgainClick
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SecondaryActionButton(
                        text = "Edit credentials",
                        onClick = { onEditCredentialsClick(savedUsername) }
                    )
                }
            }
        }
    }
}
