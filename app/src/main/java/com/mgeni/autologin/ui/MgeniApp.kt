package com.mgeni.autologin.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mgeni.autologin.ui.components.BackgroundLoadingIndicator
import com.mgeni.autologin.ui.screens.AboutScreen
import com.mgeni.autologin.ui.screens.AdvancedSettingsScreen
import com.mgeni.autologin.ui.screens.AlreadyConnectedScreen
import com.mgeni.autologin.ui.screens.ConnectingScreen
import com.mgeni.autologin.ui.screens.LoginFailedScreen
import com.mgeni.autologin.ui.screens.LoginScreen
import com.mgeni.autologin.ui.screens.NotOnGuestScreen
import com.mgeni.autologin.ui.screens.OnboardingScreen
import com.mgeni.autologin.ui.screens.SplashScreen
import com.mgeni.autologin.ui.screens.SuccessScreen
import com.mgeni.autologin.ui.viewmodel.MainUiState
import com.mgeni.autologin.ui.viewmodel.MainViewModel

private fun MainUiState.screenOrder(): Int = when (this) {
    is MainUiState.Onboarding -> 0
    is MainUiState.CheckingConnection -> 1
    is MainUiState.NotOnGuestNetwork -> 2
    is MainUiState.LoginForm -> 3
    is MainUiState.Connecting -> 4
    is MainUiState.LoginFailed -> 5
    is MainUiState.AlreadyConnected -> 6
    is MainUiState.Success -> 6
    is MainUiState.AdvancedSettings -> 7
    is MainUiState.About -> 8
}

@Composable
fun MgeniApp(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val networkState by viewModel.networkState.collectAsState()
    val isBackgroundChecking by viewModel.isBackgroundChecking.collectAsState()
    val backgroundStatusMessage by viewModel.backgroundStatusMessage.collectAsState()
    val logCount by viewModel.logCount.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = uiState,
            transitionSpec = {
                val isSameScreenType = initialState::class == targetState::class
                val isCheckingOrConnecting = targetState is MainUiState.CheckingConnection ||
                    initialState is MainUiState.CheckingConnection ||
                    targetState is MainUiState.Connecting ||
                    initialState is MainUiState.Connecting

                if (isSameScreenType) {
                    androidx.compose.animation.EnterTransition.None
                        .togetherWith(androidx.compose.animation.ExitTransition.None)
                } else if (isCheckingOrConnecting) {
                    fadeIn(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing))
                        .togetherWith(fadeOut(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)))
                } else {
                    val isForward = targetState.screenOrder() >= initialState.screenOrder()
                    val fadeSpec = tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
                    val offsetSpec = tween<IntOffset>(durationMillis = 320, easing = FastOutSlowInEasing)

                    if (isForward) {
                        (slideInHorizontally(animationSpec = offsetSpec) { fullWidth -> fullWidth } + fadeIn(animationSpec = fadeSpec))
                            .togetherWith(slideOutHorizontally(animationSpec = offsetSpec) { fullWidth -> -fullWidth / 3 } + fadeOut(animationSpec = fadeSpec))
                    } else {
                        (slideInHorizontally(animationSpec = offsetSpec) { fullWidth -> -fullWidth / 3 } + fadeIn(animationSpec = fadeSpec))
                            .togetherWith(slideOutHorizontally(animationSpec = offsetSpec) { fullWidth -> fullWidth } + fadeOut(animationSpec = fadeSpec))
                    }
                }
            },
            label = "ScreenTransition",
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) { state ->
            when (state) {
                is MainUiState.Onboarding -> {
                    BackHandler {
                        viewModel.dismissOnboarding()
                    }

                    OnboardingScreen(
                        onComplete = { viewModel.completeOnboarding() },
                        isDismissable = state.previousState != null,
                        onDismiss = { viewModel.dismissOnboarding() }
                    )
                }

                is MainUiState.CheckingConnection -> {
                    SplashScreen(
                        isTakingLong = state.isTakingLong,
                        onSettingsClick = { viewModel.openAdvancedSettings() },
                        onAboutClick = { viewModel.openAbout() },
                        onHelpClick = { viewModel.openOnboarding() }
                    )
                }

                is MainUiState.AlreadyConnected -> {
                    AlreadyConnectedScreen(
                        networkState = networkState,
                        onCloseClick = { (context as? Activity)?.finish() },
                        onSettingsClick = { viewModel.openAdvancedSettings() },
                        onAboutClick = { viewModel.openAbout() },
                        onHelpClick = { viewModel.openOnboarding() },
                        onRefresh = { viewModel.startConnectionCheck(isUserInitiated = true) },
                        isRefreshing = isBackgroundChecking
                    )
                }

                is MainUiState.NotOnGuestNetwork -> {
                    NotOnGuestScreen(
                        errorMessage = state.errorMessage,
                        networkState = networkState,
                        onRetryClick = { viewModel.startConnectionCheck(isUserInitiated = true) },
                        onSettingsClick = { viewModel.openAdvancedSettings() },
                        onAboutClick = { viewModel.openAbout() },
                        onHelpClick = { viewModel.openOnboarding() },
                        onRestoreDefaultClick = { viewModel.resetAdvancedSettingsToDefault() },
                        onRefresh = { viewModel.startConnectionCheck(isUserInitiated = true) },
                        isRefreshing = isBackgroundChecking
                    )
                }

                is MainUiState.LoginForm -> {
                    LoginScreen(
                        initialUsername = state.username,
                        initialPassword = state.password,
                        initialRememberMe = state.rememberMe,
                        errorMessage = state.errorMessage,
                        networkState = networkState,
                        onConnectClick = { username, password, rememberMe ->
                            viewModel.submitLoginForm(username, password, rememberMe)
                        },
                        onSettingsClick = { viewModel.openAdvancedSettings() },
                        onAboutClick = { viewModel.openAbout() },
                        onHelpClick = { viewModel.openOnboarding() },
                        onRefresh = { viewModel.startConnectionCheck(isUserInitiated = true) },
                        isRefreshing = isBackgroundChecking
                    )
                }

                is MainUiState.Connecting -> {
                    ConnectingScreen(
                        statusMessage = state.statusMessage,
                        detailMessage = state.detailMessage,
                        isTakingLong = state.isTakingLong,
                        onSettingsClick = { viewModel.openAdvancedSettings() },
                        onAboutClick = { viewModel.openAbout() }
                    )
                }

                is MainUiState.Success -> {
                    SuccessScreen(
                        networkState = networkState,
                        onCloseClick = { (context as? Activity)?.finish() },
                        onSettingsClick = { viewModel.openAdvancedSettings() },
                        onAboutClick = { viewModel.openAbout() },
                        onHelpClick = { viewModel.openOnboarding() },
                        onRefresh = { viewModel.startConnectionCheck(isUserInitiated = true) },
                        isRefreshing = isBackgroundChecking
                    )
                }

                is MainUiState.LoginFailed -> {
                    LoginFailedScreen(
                        errorMessage = state.errorMessage,
                        savedUsername = state.savedUsername,
                        networkState = networkState,
                        onTryAgainClick = {
                            viewModel.retryLastSubmittedCredentials()
                        },
                        onEditCredentialsClick = { username ->
                            viewModel.editCredentials(username)
                        },
                        onSettingsClick = { viewModel.openAdvancedSettings() },
                        onAboutClick = { viewModel.openAbout() },
                        onHelpClick = { viewModel.openOnboarding() },
                        onRefresh = { viewModel.startConnectionCheck(isUserInitiated = true) },
                        isRefreshing = isBackgroundChecking
                    )
                }

                is MainUiState.AdvancedSettings -> {
                    BackHandler {
                        viewModel.dismissAdvancedSettings()
                    }

                    AdvancedSettingsScreen(
                        currentPortalUrl = state.portalUrl,
                        initialCheckInternetOnStartup = state.checkInternetOnStartup,
                        hasSavedCredentials = state.hasSavedCredentials,
                        errorMessage = state.errorMessage,
                        successMessage = state.successMessage,
                        logCount = logCount,
                        onSaveClick = { newUrl, checkInternetOnStartup ->
                            viewModel.saveAdvancedSettings(newUrl, checkInternetOnStartup)
                        },
                        onClearCredentialsClick = {
                            viewModel.clearSavedCredentials()
                        },
                        onResetToDefaultClick = {
                            viewModel.resetAdvancedSettingsToDefault()
                        },
                        onExportLogsClick = {
                            viewModel.exportLogs(context)
                        },
                        onClearLogsClick = {
                            viewModel.clearLogs(context)
                        },
                        onBackClick = {
                            viewModel.dismissAdvancedSettings()
                        }
                    )
                }

                is MainUiState.About -> {
                    BackHandler {
                        viewModel.dismissAbout()
                    }

                    AboutScreen(
                        onBackClick = {
                            viewModel.dismissAbout()
                        },
                        onAppGuideClick = {
                            viewModel.openOnboarding()
                        }
                    )
                }
            }
        }

        // Overlay non-intrusive background loading indicator when background checking is active
        BackgroundLoadingIndicator(
            visible = isBackgroundChecking && uiState !is MainUiState.CheckingConnection && uiState !is MainUiState.Connecting,
            message = backgroundStatusMessage,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp)
        )
    }
}
