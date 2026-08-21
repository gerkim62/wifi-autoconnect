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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgeni.autologin.ui.theme.WarningAmber
import com.mgeni.autologin.ui.theme.WarningContainerDark
import com.mgeni.autologin.ui.theme.WarningContainerLight

/**
 * Sincere advice banner shown when mobile data is on simultaneously with Wi-Fi,
 * which can cause Android to route portal requests away from the local captive portal.
 */
@Composable
fun MobileDataWarningBanner(
    isCellularActive: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isCellularActive,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val isDark = MaterialTheme.colorScheme.background.red < 0.5f
        val bgColor = if (isDark) WarningContainerDark else WarningContainerLight

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
                    imageVector = Icons.Outlined.SignalCellularAlt,
                    contentDescription = "Mobile data active",
                    tint = WarningAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Mobile Data is Active",
                        style = MaterialTheme.typography.labelLarge,
                        color = WarningAmber
                    )
                    Text(
                        text = "Android may prioritize cellular data over Guest Wi-Fi. If you experience connection issues, consider turning off Mobile Data temporarily.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
