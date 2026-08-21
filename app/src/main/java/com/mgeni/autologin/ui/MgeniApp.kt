package com.mgeni.autologin.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun MgeniApp(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isCellularActive by viewModel.isCellularActive.collectAsState()

    AnimatedContent(
        targetState = uiState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "ScreenTransition",
        modifier = modifier.fillMaxSize()
    ) { state ->
        when (state) {
            is MainUiState.CheckingConnection -> {
                SplashScreen()
            }

            is MainUiState.AlreadyConnected -> {
                AlreadyConnectedScreen(
                    isCellularActive = isCellularActive,
                    onCloseClick = { (context as? Activity)?.finish() }
                )
            }

            is MainUiState.NotOnGuestNetwork -> {
                NotOnGuestScreen(
                    errorMessage = state.errorMessage,
                    isCellularActive = isCellularActive,
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
                    isCellularActive = isCellularActive,
                    onConnectClick = { username, password, rememberMe ->
                        viewModel.submitLoginForm(username, password, rememberMe)
                    },
                    onAdvancedSettingsClick = { viewModel.openAdvancedSettings() }
                )
            }

            is MainUiState.Connecting -> {
                ConnectingScreen(
                    statusMessage = state.statusMessage
                )
            }

            is MainUiState.Success -> {
                SuccessScreen(
                    isCellularActive = isCellularActive,
                    onCloseClick = { (context as? Activity)?.finish() }
                )
            }

            is MainUiState.LoginFailed -> {
                LoginFailedScreen(
                    errorMessage = state.errorMessage,
                    savedUsername = state.savedUsername,
                    isCellularActive = isCellularActive,
                    onTryAgainClick = { savedUsername ->
                        viewModel.retryAfterLoginFailed(savedUsername)
                    }
                )
            }

            is MainUiState.AdvancedSettings -> {
                BackHandler {
                    viewModel.dismissAdvancedSettings()
                }

                AdvancedSettingsScreen(
                    currentPortalUrl = state.portalUrl,
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
