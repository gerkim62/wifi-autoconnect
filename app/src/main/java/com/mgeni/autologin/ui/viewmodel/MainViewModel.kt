package com.mgeni.autologin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgeni.autologin.data.ConnectivityResult
import com.mgeni.autologin.data.LoginSubmitResult
import com.mgeni.autologin.data.NetworkMonitor
import com.mgeni.autologin.data.PageFetchResult
import com.mgeni.autologin.data.PortalClient
import com.mgeni.autologin.data.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val preferencesManager: PreferencesManager,
    private val portalClient: PortalClient = PortalClient(),
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.CheckingConnection)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val isCellularActive: StateFlow<Boolean> = networkMonitor.isCellularActive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        startConnectionCheck()
    }

    /**
     * Step 1: Initial 204 check on app launch or retry.
     */
    fun startConnectionCheck() {
        viewModelScope.launch {
            _uiState.value = MainUiState.CheckingConnection
            networkMonitor.updateNetworkStates()

            when (val result = portalClient.check204Connectivity()) {
                is ConnectivityResult.AlreadyConnected -> {
                    _uiState.value = MainUiState.AlreadyConnected
                }
                is ConnectivityResult.CaptiveDetected -> {
                    proceedToCaptivePortal(result.portalRedirectUrl)
                }
                is ConnectivityResult.Unreachable -> {
                    _uiState.value = MainUiState.NotOnGuestNetwork(
                        errorMessage = "Make sure you're connected to the Guest Wi-Fi network, then try again."
                    )
                }
            }
        }
    }

    /**
     * Step 2: Fetch the captive portal login page and handle auto-login or show login form.
     */
    private suspend fun proceedToCaptivePortal(redirectHint: String? = null) {
        val portalUrl = if (!redirectHint.isNullOrBlank() && redirectHint.startsWith("http")) {
            redirectHint
        } else {
            preferencesManager.portalUrl
        }

        when (val pageResult = portalClient.fetchLoginPage(portalUrl)) {
            is PageFetchResult.Error -> {
                _uiState.value = MainUiState.NotOnGuestNetwork(
                    errorMessage = pageResult.message
                )
            }
            is PageFetchResult.Success -> {
                if (preferencesManager.hasSavedCredentials()) {
                    // Auto-submit with saved credentials
                    executeLogin(
                        actionUrl = pageResult.actionUrl,
                        username = preferencesManager.username,
                        password = preferencesManager.password,
                        timeTag = pageResult.timeTag,
                        redirectUrl = pageResult.redirectUrl,
                        rememberMe = preferencesManager.rememberMe
                    )
                } else {
                    // Show login form
                    _uiState.value = MainUiState.LoginForm(
                        username = preferencesManager.username,
                        password = "",
                        rememberMe = preferencesManager.rememberMe,
                        errorMessage = null
                    )
                }
            }
        }
    }

    /**
     * Step 3: Execute login POST and verify post-submission connectivity.
     */
    fun submitLoginForm(user: String, pass: String, remember: Boolean) {
        val trimmedUser = user.trim()
        if (trimmedUser.isBlank()) {
            _uiState.value = MainUiState.LoginForm(
                username = user,
                password = pass,
                rememberMe = remember,
                errorMessage = "Please enter your username."
            )
            return
        }

        if (pass.isBlank()) {
            _uiState.value = MainUiState.LoginForm(
                username = user,
                password = pass,
                rememberMe = remember,
                errorMessage = "Please enter your password."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = MainUiState.Connecting("Connecting to portal…")

            // Fetch a fresh token before submitting
            val portalUrl = preferencesManager.portalUrl
            when (val pageResult = portalClient.fetchLoginPage(portalUrl)) {
                is PageFetchResult.Error -> {
                    _uiState.value = MainUiState.LoginForm(
                        username = trimmedUser,
                        password = pass,
                        rememberMe = remember,
                        errorMessage = pageResult.message
                    )
                }
                is PageFetchResult.Success -> {
                    executeLogin(
                        actionUrl = pageResult.actionUrl,
                        username = trimmedUser,
                        password = pass,
                        timeTag = pageResult.timeTag,
                        redirectUrl = pageResult.redirectUrl,
                        rememberMe = remember
                    )
                }
            }
        }
    }

    private suspend fun executeLogin(
        actionUrl: String,
        username: String,
        password: String,
        timeTag: String,
        redirectUrl: String,
        rememberMe: Boolean
    ) {
        _uiState.value = MainUiState.Connecting("Logging in…")

        val submitResult = portalClient.submitLogin(
            actionUrl = actionUrl,
            username = username,
            password = password,
            timeTag = timeTag,
            redirectUrl = redirectUrl
        )

        when (submitResult) {
            is LoginSubmitResult.Success -> {
                // Save credentials if rememberMe is enabled
                preferencesManager.saveCredentials(username, password, rememberMe)
                _uiState.value = MainUiState.Success
            }
            is LoginSubmitResult.Failed -> {
                // Wipe saved credentials per spec on failure, retain username
                preferencesManager.clearSavedCredentials()
                _uiState.value = MainUiState.LoginFailed(
                    errorMessage = submitResult.message,
                    savedUsername = username
                )
            }
        }
    }

    /**
     * Transitions from LoginFailed back to LoginForm.
     */
    fun retryAfterLoginFailed(savedUsername: String) {
        _uiState.value = MainUiState.LoginForm(
            username = savedUsername.ifBlank { preferencesManager.username },
            password = "",
            rememberMe = true,
            errorMessage = null
        )
    }

    /**
     * Opens Advanced Settings screen.
     */
    fun openAdvancedSettings() {
        val currentState = _uiState.value
        val currentUrl = preferencesManager.portalUrl
        val isDefault = currentUrl == PreferencesManager.DEFAULT_PORTAL_URL
        _uiState.value = MainUiState.AdvancedSettings(
            portalUrl = currentUrl,
            isDefault = isDefault,
            previousState = currentState
        )
    }

    /**
     * Saves customized Portal URL in Advanced Settings.
     */
    fun saveAdvancedSettings(newUrl: String) {
        val cleanedUrl = newUrl.trim()
        if (cleanedUrl.isNotBlank()) {
            preferencesManager.portalUrl = cleanedUrl
        }
        val previous = (_uiState.value as? MainUiState.AdvancedSettings)?.previousState
            ?: MainUiState.CheckingConnection
        _uiState.value = previous
        startConnectionCheck()
    }

    /**
     * Resets Portal URL to default.
     */
    fun resetAdvancedSettingsToDefault() {
        preferencesManager.resetPortalUrl()
        val previous = (_uiState.value as? MainUiState.AdvancedSettings)?.previousState
            ?: MainUiState.CheckingConnection
        _uiState.value = previous
        startConnectionCheck()
    }

    /**
     * Returns from Advanced Settings without saving.
     */
    fun dismissAdvancedSettings() {
        val previous = (_uiState.value as? MainUiState.AdvancedSettings)?.previousState
            ?: MainUiState.CheckingConnection
        _uiState.value = previous
    }

    override fun onCleared() {
        super.onCleared()
        networkMonitor.unregister()
    }
}
