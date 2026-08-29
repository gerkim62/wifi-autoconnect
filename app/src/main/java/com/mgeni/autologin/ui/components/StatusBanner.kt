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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgeni.autologin.ui.theme.ErrorContainerDark
import com.mgeni.autologin.ui.theme.ErrorContainerLight
import com.mgeni.autologin.ui.theme.ErrorTextDark
import com.mgeni.autologin.ui.theme.ErrorTextLight
import com.mgeni.autologin.ui.theme.InfoContainerDark
import com.mgeni.autologin.ui.theme.InfoContainerLight
import com.mgeni.autologin.ui.theme.InfoTextDark
import com.mgeni.autologin.ui.theme.InfoTextLight
import com.mgeni.autologin.ui.theme.SuccessContainerDark
import com.mgeni.autologin.ui.theme.SuccessContainerLight
import com.mgeni.autologin.ui.theme.SuccessTextDark
import com.mgeni.autologin.ui.theme.SuccessTextLight
import com.mgeni.autologin.ui.theme.WarningContainerDark
import com.mgeni.autologin.ui.theme.WarningContainerLight
import com.mgeni.autologin.ui.theme.WarningTextDark
import com.mgeni.autologin.ui.theme.WarningTextLight

enum class BannerType {
    Success,
    Error,
    Warning,
    Info
}

/**
 * Universal WCAG AAA compliant feedback banner.
 * Ensures maximum contrast ratio and visual clarity in both Light and Dark themes.
 */
@Composable
fun StatusBanner(
    type: BannerType,
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    visible: Boolean = message.isNotBlank(),
    onDismiss: (() -> Unit)? = null
) {
    AnimatedVisibility(
        visible = visible && message.isNotBlank(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val isDark = MaterialTheme.colorScheme.background.red < 0.5f

        val (containerColor, textColor, defaultIcon) = when (type) {
            BannerType.Success -> Triple(
                if (isDark) SuccessContainerDark else SuccessContainerLight,
                if (isDark) SuccessTextDark else SuccessTextLight,
                Icons.Outlined.CheckCircle
            )
            BannerType.Error -> Triple(
                if (isDark) ErrorContainerDark else ErrorContainerLight,
                if (isDark) ErrorTextDark else ErrorTextLight,
                Icons.Outlined.ErrorOutline
            )
            BannerType.Warning -> Triple(
                if (isDark) WarningContainerDark else WarningContainerLight,
                if (isDark) WarningTextDark else WarningTextLight,
                Icons.Outlined.WarningAmber
            )
            BannerType.Info -> Triple(
                if (isDark) InfoContainerDark else InfoContainerLight,
                if (isDark) InfoTextDark else InfoTextLight,
                Icons.Outlined.Info
            )
        }

        val displayIcon = icon ?: defaultIcon

        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = if (title != null) Alignment.Top else Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = displayIcon,
                    contentDescription = title ?: type.name,
                    tint = textColor,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    if (!title.isNullOrBlank()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = textColor
                            )
                        )
                        Spacer(modifier = Modifier.padding(top = 2.dp))
                    }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = textColor,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    )
                }

                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Dismiss",
                            tint = textColor.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
