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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import com.mgeni.autologin.ui.screens.AdvancedSettingsScreen
import com.mgeni.autologin.ui.screens.AlreadyConnectedScreen
import com.mgeni.autologin.ui.screens.ConnectingScreen
import com.mgeni.autologin.ui.screens.LoginFailedScreen
import com.mgeni.autologin.ui.screens.LoginScreen
import com.mgeni.autologin.ui.screens.NotOnGuestScreen
import com.mgeni.autologin.ui.screens.SplashScreen
import com.mgeni.autologin.ui.screens.SuccessScreen
import com.mgeni.autologin.ui.viewmodel.MainUiState
import com.mgeni.autologin.ui.viewmodel.MainViewModel

private fun MainUiState.screenOrder(): Int = when (this) {
    is MainUiState.CheckingConnection -> 0
    is MainUiState.NotOnGuestNetwork -> 1
    is MainUiState.LoginForm -> 2
    is MainUiState.Connecting -> 3
    is MainUiState.LoginFailed -> 4
    is MainUiState.AlreadyConnected -> 5
    is MainUiState.Success -> 5
    is MainUiState.AdvancedSettings -> 6
}

@Composable
fun MgeniApp(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val networkState by viewModel.networkState.collectAsState()

    AnimatedContent(
        targetState = uiState,
        transitionSpec = {
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
        },
        label = "ScreenTransition",
        modifier = modifier.fillMaxSize()
    ) { state ->
        when (state) {
            is MainUiState.CheckingConnection -> {
                SplashScreen(
                    isTakingLong = state.isTakingLong
                )
            }

            is MainUiState.AlreadyConnected -> {
                AlreadyConnectedScreen(
                    networkState = networkState,
                    onCloseClick = { (context as? Activity)?.finish() }
                )
            }

            is MainUiState.NotOnGuestNetwork -> {
                NotOnGuestScreen(
                    errorMessage = state.errorMessage,
                    networkState = networkState,
                    onRetryClick = { viewModel.startConnectionCheck() },
                    onAdvancedSettingsClick = { viewModel.openAdvancedSettings() }
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
                    onAdvancedSettingsClick = { viewModel.openAdvancedSettings() }
                )
            }

            is MainUiState.Connecting -> {
                ConnectingScreen(
                    statusMessage = state.statusMessage,
                    isTakingLong = state.isTakingLong
                )
            }

            is MainUiState.Success -> {
                SuccessScreen(
                    networkState = networkState,
                    onCloseClick = { (context as? Activity)?.finish() }
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
                    }
                )
            }

            is MainUiState.AdvancedSettings -> {
                BackHandler {
                    viewModel.dismissAdvancedSettings()
                }

                AdvancedSettingsScreen(
                    currentPortalUrl = state.portalUrl,
                    errorMessage = state.errorMessage,
                    onSaveClick = { newUrl ->
                        viewModel.saveAdvancedSettings(newUrl)
                    },
                    onResetToDefaultClick = {
                        viewModel.resetAdvancedSettingsToDefault()
                    },
                    onBackClick = {
                        viewModel.dismissAdvancedSettings()
                    }
                )
            }
        }
    }
}

