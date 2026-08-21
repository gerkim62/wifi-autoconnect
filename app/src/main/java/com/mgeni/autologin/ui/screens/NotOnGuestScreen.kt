package com.mgeni.autologin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mgeni.autologin.ui.components.AdvancedSettingsLink
import com.mgeni.autologin.ui.components.ErrorStatusIcon
import com.mgeni.autologin.ui.components.MobileDataWarningBanner
import com.mgeni.autologin.ui.components.PrimaryActionButton

/**
 * Screen 3: Not on Guest Wi-Fi Screen
 * Shown when the 204 check times out or fails.
 */
@Composable
fun NotOnGuestScreen(
    errorMessage: String,
    isCellularActive: Boolean,
    onRetryClick: () -> Unit,
    onAdvancedSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            MobileDataWarningBanner(isCellularActive = isCellularActive)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                ErrorStatusIcon()

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Can't reach the network",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = errorMessage.ifBlank {
                        "Make sure you're connected to the Guest Wi-Fi network, then try again."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PrimaryActionButton(
                    text = "Retry",
                    onClick = onRetryClick
                )

                Spacer(modifier = Modifier.height(12.dp))

                AdvancedSettingsLink(
                    onClick = onAdvancedSettingsClick
                )
            }
        }
    }
}
