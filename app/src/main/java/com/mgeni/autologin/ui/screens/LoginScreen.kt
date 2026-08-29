package com.mgeni.autologin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgeni.autologin.data.NetworkState
import com.mgeni.autologin.ui.components.BannerType
import com.mgeni.autologin.ui.components.MobileDataWarningBanner
import com.mgeni.autologin.ui.components.PrimaryActionButton
import com.mgeni.autologin.ui.components.PullToRefreshLayout
import com.mgeni.autologin.ui.components.StatusBanner
import com.mgeni.autologin.ui.components.TopBarActions
import com.mgeni.autologin.ui.theme.EmeraldContainer
import com.mgeni.autologin.ui.theme.EmeraldPrimary

/**
 * Screen 4: Login Screen
 * With top bar action icons (Help + Info + Gear), pull-to-refresh, double-tap prevention, and high-contrast error banner.
 */
@Composable
fun LoginScreen(
    initialUsername: String,
    initialPassword: String,
    initialRememberMe: Boolean,
    errorMessage: String?,
    networkState: NetworkState,
    onConnectClick: (username: String, password: String, rememberMe: Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onHelpClick: () -> Unit = onAboutClick,
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    var username by remember(initialUsername) { mutableStateOf(initialUsername) }
    var password by remember(initialPassword) { mutableStateOf(initialPassword) }
    var rememberMe by remember(initialRememberMe) { mutableStateOf(initialRememberMe) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage, initialUsername, initialPassword) {
        isSubmitting = false
    }

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val triggerSubmit = {
        var hasError = false
        if (username.trim().isBlank()) {
            usernameError = "Please enter your username"
            hasError = true
        }
        if (password.isBlank()) {
            passwordError = "Please enter your password"
            hasError = true
        }

        if (!hasError && !isSubmitting) {
            isSubmitting = true
            focusManager.clearFocus()
            onConnectClick(username.trim(), password, rememberMe)
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
        modifier = modifier.imePadding()
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
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    // Mobile Data warning banner
                    MobileDataWarningBanner(
                        networkState = networkState,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Error banner if any (High contrast WCAG AAA)
                    StatusBanner(
                        type = BannerType.Error,
                        message = errorMessage ?: "",
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Logo & App Name Header
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = com.mgeni.autologin.R.drawable.app_logo),
                            contentDescription = "WifiAuto Logo",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "WifiAuto",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = "Enter credentials for the \"guest\" Wi-Fi network to sign in",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Authorization Disclaimer Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "For Authorized Use Only",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Please ensure you have proper authorization and valid credentials before connecting to the network.",
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp, fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Username Input
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            if (usernameError != null) usernameError = null
                            isSubmitting = false
                        },
                        label = { Text("Username") },
                        isError = usernameError != null,
                        supportingText = if (usernameError != null) {
                            { Text(text = usernameError!!, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = if (usernameError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        },
                        singleLine = true,
                        enabled = !isSubmitting,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Password Input
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            if (passwordError != null) passwordError = null
                            isSubmitting = false
                        },
                        label = { Text("Password") },
                        isError = passwordError != null,
                        supportingText = if (passwordError != null) {
                            { Text(text = passwordError!!, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (passwordError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !isSubmitting,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { triggerSubmit() }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Remember Me Checkbox Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isSubmitting) { rememberMe = !rememberMe }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { if (!isSubmitting) rememberMe = it },
                            enabled = !isSubmitting,
                            colors = CheckboxDefaults.colors(
                                checkedColor = EmeraldPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Remember me",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action button at bottom
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 36.dp)
                ) {
                    PrimaryActionButton(
                        text = "Connect",
                        onClick = triggerSubmit,
                        enabled = !isSubmitting,
                        isLoading = isSubmitting
                    )
                }
            }
        }
    }
}
