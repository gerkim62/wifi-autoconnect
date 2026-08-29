package com.mgeni.autologin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgeni.autologin.BuildConfig
import com.mgeni.autologin.data.PreferencesManager
import com.mgeni.autologin.ui.components.BannerType
import com.mgeni.autologin.ui.components.ConfirmationDialog
import com.mgeni.autologin.ui.components.PrimaryActionButton
import com.mgeni.autologin.ui.components.SecondaryActionButton
import com.mgeni.autologin.ui.components.StatusBanner
import com.mgeni.autologin.ui.theme.EmeraldContainer
import com.mgeni.autologin.ui.theme.EmeraldPrimary

/**
 * Screen 8: Advanced Settings Screen
 * Allows customizing captive portal URL, skipping initial internet check,
 * managing credentials, exporting diagnostic logs, and tracking dirty state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    currentPortalUrl: String,
    initialCheckInternetOnStartup: Boolean = true,
    hasSavedCredentials: Boolean = false,
    errorMessage: String? = null,
    successMessage: String? = null,
    logCount: Int = 0,
    onSaveClick: (newUrl: String, checkInternetOnStartup: Boolean) -> Unit,
    onClearCredentialsClick: () -> Unit = {},
    onResetToDefaultClick: () -> Unit,
    onExportLogsClick: () -> Unit = {},
    onClearLogsClick: () -> Unit = {},
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var portalUrl by remember(currentPortalUrl) { mutableStateOf(currentPortalUrl) }
    var checkInternetOnStartup by remember(initialCheckInternetOnStartup) { mutableStateOf(initialCheckInternetOnStartup) }
    var showClearCredsDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearLogsDialog by remember { mutableStateOf(false) }

    val hasUnsavedChanges = portalUrl.trim() != currentPortalUrl.trim() ||
        checkInternetOnStartup != initialCheckInternetOnStartup

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    if (showClearCredsDialog) {
        ConfirmationDialog(
            title = "Clear saved credentials?",
            message = "This will remove your stored username and password from this device. You will need to re-enter them on your next sign-in.",
            confirmButtonText = "Clear",
            isDestructive = true,
            icon = Icons.Outlined.PersonOff,
            onConfirm = {
                showClearCredsDialog = false
                onClearCredentialsClick()
            },
            onDismiss = { showClearCredsDialog = false }
        )
    }

    if (showResetDialog) {
        ConfirmationDialog(
            title = "Reset to default?",
            message = "This will restore the default portal URL and enable initial internet checks.",
            confirmButtonText = "Reset",
            isDestructive = false,
            icon = Icons.Outlined.RestartAlt,
            onConfirm = {
                showResetDialog = false
                onResetToDefaultClick()
            },
            onDismiss = { showResetDialog = false }
        )
    }

    if (showClearLogsDialog) {
        ConfirmationDialog(
            title = "Clear diagnostic logs?",
            message = "This will delete all recorded captive portal and network activity logs from this device.",
            confirmButtonText = "Clear Logs",
            isDestructive = true,
            icon = Icons.Outlined.DeleteOutline,
            onConfirm = {
                showClearLogsDialog = false
                onClearLogsClick()
            },
            onDismiss = { showClearLogsDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Advanced Settings",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (hasUnsavedChanges) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = "Unsaved",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.imePadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Success Feedback Banner
                StatusBanner(
                    type = BannerType.Success,
                    message = successMessage ?: "",
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                // Caution Banner
                StatusBanner(
                    type = BannerType.Warning,
                    title = "Caution",
                    message = "Default settings are configured for the \"guest\" Wi-Fi portal.",
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // Validation Error Banner (if any)
                StatusBanner(
                    type = BannerType.Error,
                    message = errorMessage ?: "",
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Portal URL Field
                Text(
                    text = "Portal URL",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Captive portal address for the \"guest\" Wi-Fi network.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                OutlinedTextField(
                    value = portalUrl,
                    onValueChange = { portalUrl = it },
                    label = { Text("Portal URL") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Web,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            onSaveClick(portalUrl, checkInternetOnStartup)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Default: ${PreferencesManager.DEFAULT_PORTAL_URL}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (portalUrl.trim() != PreferencesManager.DEFAULT_PORTAL_URL) {
                        Text(
                            text = "Restore default",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    portalUrl = PreferencesManager.DEFAULT_PORTAL_URL
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Check Internet Connectivity on Startup Option
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .clickable { checkInternetOnStartup = !checkInternetOnStartup }
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Check internet on startup",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Verify connectivity before opening the portal.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = checkInternetOnStartup,
                            onCheckedChange = { checkInternetOnStartup = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Clear Credentials Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .clickable(enabled = hasSavedCredentials) { showClearCredsDialog = true }
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PersonOff,
                            contentDescription = null,
                            tint = if (hasSavedCredentials) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Clear saved credentials",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    color = if (hasSavedCredentials) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            )
                            Text(
                                text = if (hasSavedCredentials) "Remove saved credentials from device." else "No credentials stored.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (hasSavedCredentials) 1f else 0.5f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Diagnostic Logs Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(14.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Diagnostic Logs",
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Captures portal HTTP requests, responses & errors ($logCount events).",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onExportLogsClick() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = "Export Logs",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Export Logs",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (logCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { showClearLogsDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Clear Logs",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Clear",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PrimaryActionButton(
                    text = if (hasUnsavedChanges) "Save Changes" else "Save",
                    onClick = {
                        focusManager.clearFocus()
                        onSaveClick(portalUrl, checkInternetOnStartup)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SecondaryActionButton(
                    text = "Reset to default",
                    onClick = { showResetDialog = true }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "WifiAuto v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
