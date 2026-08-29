package com.mgeni.autologin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.mgeni.autologin.R
import com.mgeni.autologin.ui.components.TopBarActions
import com.mgeni.autologin.ui.theme.EmeraldContainer
import com.mgeni.autologin.ui.theme.EmeraldPrimary

/**
 * Screen 1: Splash / Loading Screen
 * Shown immediately on app open while the 204 check runs, with progressive delay notice, Settings, and About access.
 */
@Composable
fun SplashScreen(
    isTakingLong: Boolean = false,
    onSettingsClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onHelpClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBarActions(
            onSettingsClick = onSettingsClick,
            onAboutClick = onAboutClick,
            onHelpClick = onHelpClick,
            modifier = Modifier.padding(8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "WifiAuto Logo",
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(22.dp))
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "WifiAuto",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Checking connection…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            CircularProgressIndicator(
                color = EmeraldPrimary,
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp
            )

            AnimatedVisibility(visible = isTakingLong) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 24.dp, start = 16.dp, end = 16.dp)
                ) {
                    Text(
                        text = "This is taking longer than usual. Please ensure you are connected to the Wi-Fi network…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
}
