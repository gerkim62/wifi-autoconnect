package com.mgeni.autologin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgeni.autologin.data.ConnectivityResult
import com.mgeni.autologin.data.LoginSubmitResult
import com.mgeni.autologin.data.NetworkMonitor
import com.mgeni.autologin.data.NetworkState
import com.mgeni.autologin.data.PageFetchResult
import com.mgeni.autologin.data.PortalClient
import com.mgeni.autologin.data.PreferencesManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class MainViewModel(
    private val preferencesManager: PreferencesManager,
    private val portalClient: PortalClient = PortalClient(),
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private companion object {
        const val MINIMUM_CHECKING_DISPLAY_MILLIS = 400L
        const val SLOW_OPERATION_NOTICE_MILLIS = 5_000L
    }

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.CheckingConnection())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val networkState: StateFlow<NetworkState> = networkMonitor.networkState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetworkState.Offline)

    val isCellularActive: StateFlow<Boolean> = networkMonitor.isCellularActive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // In-memory retention of last submitted credentials for "Try again" action
    private var lastSubmittedUser: String = ""
    private var lastSubmittedPass: String = ""
    private var lastSubmittedRememberMe: Boolean = true

    private var checkJob: Job? = null

    init {
        startConnectionCheck()
        observeNetworkChanges()
    }

    private fun observeNetworkChanges() {
        viewModelScope.launch {
            var isFirstEmission = true
            networkMonitor.wifiChangeCount.collect { _ ->
                if (isFirstEmission) {
                    isFirstEmission = false
                    return@collect
                }

                val currentState = _uiState.value
                // Preserve user context during active editing / form interaction
                if (currentState is MainUiState.AdvancedSettings || currentState is MainUiState.LoginForm) {
                    return@collect
                }

                if (!networkMonitor.isWifiActive.value) {
                    // Wi-Fi lost / disconnected
                    checkJob?.cancel()
                    _uiState.value = MainUiState.NotOnGuestNetwork(
                        errorMessage = "Make sure you're connected to the Wi-Fi network, then try again."
                    )
                } else {
                    // Wi-Fi connected or changed
                    startConnectionCheck()
                }
            }
        }
    }

    /**
     * Step 1: Initial 204 check on app launch or retry.
     */
    fun startConnectionCheck() {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            val checkingStartedAt = System.currentTimeMillis()
            _uiState.value = MainUiState.CheckingConnection(isTakingLong = false)
            networkMonitor.updateNetworkStates()

            if (preferencesManager.skipInitialInternetCheck) {
                // Bypass 204 internet check and directly load the captive portal page
                keepCheckingScreenVisible(checkingStartedAt)
                proceedToCaptivePortal()
                return@launch
            }

            val slowNoticeJob = launch {
                delay(SLOW_OPERATION_NOTICE_MILLIS)
                if (_uiState.value is MainUiState.CheckingConnection) {
                    _uiState.value = MainUiState.CheckingConnection(isTakingLong = true)
                }
            }

            var result = portalClient.check204Connectivity()

            // If Wi-Fi is actively establishing connection and first ping returned Unreachable,
            // retry to allow DHCP negotiation to settle before failing
            if (result is ConnectivityResult.Unreachable && networkMonitor.isWifiActive.value) {
                for (retry in 1..2) {
                    delay(600L)
                    networkMonitor.updateNetworkStates()
                    result = portalClient.check204Connectivity()
                    if (result !is ConnectivityResult.Unreachable) break
                }
            }

            slowNoticeJob.cancel()

            when (result) {
                is ConnectivityResult.AlreadyConnected -> {
                    keepCheckingScreenVisible(checkingStartedAt)
                    _uiState.value = MainUiState.AlreadyConnected
                }
                is ConnectivityResult.CaptiveDetected -> {
                    // Start useful portal work right away; only the visible screen change is gated.
                    val pageResult = async {
                        fetchCaptivePortalLoginPage(result.portalRedirectUrl)
                    }
                    keepCheckingScreenVisible(checkingStartedAt)
                    handlePortalPageResult(pageResult.await())
                }
                is ConnectivityResult.Unreachable -> {
                    keepCheckingScreenVisible(checkingStartedAt)
                    _uiState.value = MainUiState.NotOnGuestNetwork(
                        errorMessage = "Make sure you're connected to the Wi-Fi network, then try again."
                    )
                }
            }
        }
    }

    /**
     * Step 2: Fetch the captive portal login page and handle auto-login or show login form.
     */
    private suspend fun proceedToCaptivePortal(redirectHint: String? = null) {
        handlePortalPageResult(fetchCaptivePortalLoginPage(redirectHint))
    }

    private suspend fun fetchCaptivePortalLoginPage(redirectHint: String? = null): PageFetchResult {
        val portalUrl = if (!redirectHint.isNullOrBlank() && redirectHint.startsWith("http")) {
            redirectHint
        } else {
            preferencesManager.portalUrl
        }

        return portalClient.fetchLoginPage(portalUrl)
    }

    private suspend fun handlePortalPageResult(pageResult: PageFetchResult) {
        when (pageResult) {
            is PageFetchResult.Error -> {
                _uiState.value = MainUiState.NotOnGuestNetwork(
                    errorMessage = pageResult.message
                )
            }
            is PageFetchResult.Success -> {
                if (preferencesManager.hasSavedCredentials()) {
                    // Auto-submit with saved credentials
                    lastSubmittedUser = preferencesManager.username
                    lastSubmittedPass = preferencesManager.password
                    lastSubmittedRememberMe = preferencesManager.rememberMe

                    executeLogin(
                        actionUrl = pageResult.actionUrl,
                        username = lastSubmittedUser,
                        password = lastSubmittedPass,
                        timeTag = pageResult.timeTag,
                        redirectUrl = pageResult.redirectUrl,
                        rememberMe = lastSubmittedRememberMe
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
     * Step 3: Handle manual login form submission with double-tap prevention.
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

        lastSubmittedUser = trimmedUser
        lastSubmittedPass = pass
        lastSubmittedRememberMe = remember

        viewModelScope.launch {
            _uiState.value = MainUiState.Connecting("Connecting to portal…", isTakingLong = false)

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
        _uiState.value = MainUiState.Connecting("Logging in…", isTakingLong = false)

        var slowJob: Job? = null
        slowJob = viewModelScope.launch {
            delay(SLOW_OPERATION_NOTICE_MILLIS)
            if (_uiState.value is MainUiState.Connecting) {
                _uiState.value = (_uiState.value as MainUiState.Connecting).copy(isTakingLong = true)
            }
        }

        val submitResult = portalClient.submitLogin(
            actionUrl = actionUrl,
            username = username,
            password = password,
            timeTag = timeTag,
            redirectUrl = redirectUrl
        )

        slowJob.cancel()

        when (submitResult) {
            is LoginSubmitResult.Success -> {
                preferencesManager.saveCredentials(username, password, rememberMe)
                _uiState.value = MainUiState.Success
            }
            is LoginSubmitResult.Failed -> {
                // Wipe saved credentials per spec on failure, retain username in prefs
                preferencesManager.clearSavedCredentials()
                _uiState.value = MainUiState.LoginFailed(
                    errorMessage = submitResult.message,
                    savedUsername = username
                )
            }
        }
    }

    private suspend fun keepCheckingScreenVisible(checkingStartedAt: Long) {
        val elapsed = System.currentTimeMillis() - checkingStartedAt
        delay((MINIMUM_CHECKING_DISPLAY_MILLIS - elapsed).coerceAtLeast(0L))
    }

    /**
     * Primary action on Login Failed: Re-attempt login with last submitted credentials.
     */
    fun retryLastSubmittedCredentials() {
        if (lastSubmittedUser.isNotBlank() && lastSubmittedPass.isNotBlank()) {
            viewModelScope.launch {
                _uiState.value = MainUiState.Connecting("Retrying connection…", isTakingLong = false)

                val portalUrl = preferencesManager.portalUrl
                when (val pageResult = portalClient.fetchLoginPage(portalUrl)) {
                    is PageFetchResult.Error -> {
                        _uiState.value = MainUiState.LoginFailed(
                            errorMessage = pageResult.message,
                            savedUsername = lastSubmittedUser
                        )
                    }
                    is PageFetchResult.Success -> {
                        executeLogin(
                            actionUrl = pageResult.actionUrl,
                            username = lastSubmittedUser,
                            password = lastSubmittedPass,
                            timeTag = pageResult.timeTag,
                            redirectUrl = pageResult.redirectUrl,
                            rememberMe = lastSubmittedRememberMe
                        )
                    }
                }
            }
        } else {
            editCredentials(lastSubmittedUser)
        }
    }

    /**
     * Secondary action on Login Failed: Navigate to Login Form with error banner and username retained.
     */
    fun editCredentials(savedUsername: String) {
        _uiState.value = MainUiState.LoginForm(
            username = savedUsername.ifBlank { preferencesManager.username },
            password = "",
            rememberMe = true,
            errorMessage = "Wrong username or password. Try again."
        )
    }

    /**
     * Opens Advanced Settings screen.
     */
    fun openAdvancedSettings() {
        val currentState = _uiState.value
        val currentUrl = preferencesManager.portalUrl
        val isDefault = currentUrl == PreferencesManager.DEFAULT_PORTAL_URL
        val skipInitial = preferencesManager.skipInitialInternetCheck
        _uiState.value = MainUiState.AdvancedSettings(
            portalUrl = currentUrl,
            skipInitialInternetCheck = skipInitial,
            isDefault = isDefault,
            errorMessage = null,
            previousState = currentState
        )
    }

    /**
     * Validates and saves customized Portal URL and preferences in Advanced Settings, then triggers fresh check.
     */
    fun saveAdvancedSettings(newUrl: String, skipInitialCheck: Boolean = false) {
        val cleanedUrl = newUrl.trim()
        val parsed = cleanedUrl.toHttpUrlOrNull()

        if (parsed == null || (parsed.scheme != "http" && parsed.scheme != "https")) {
            val currentSettings = _uiState.value as? MainUiState.AdvancedSettings
            if (currentSettings != null) {
                _uiState.value = currentSettings.copy(
                    portalUrl = cleanedUrl,
                    skipInitialInternetCheck = skipInitialCheck,
                    errorMessage = "Please enter a valid URL starting with http:// or https://"
                )
            }
            return
        }

        preferencesManager.portalUrl = cleanedUrl
        preferencesManager.skipInitialInternetCheck = skipInitialCheck
        startConnectionCheck()
    }

    /**
     * Resets Portal URL and preferences to default and triggers fresh check.
     */
    fun resetAdvancedSettingsToDefault() {
        preferencesManager.resetPortalUrl()
        preferencesManager.skipInitialInternetCheck = false
        startConnectionCheck()
    }

    /**
     * Returns from Advanced Settings without saving.
     */
    fun dismissAdvancedSettings() {
        val previous = (_uiState.value as? MainUiState.AdvancedSettings)?.previousState
            ?: MainUiState.CheckingConnection()
        _uiState.value = previous
    }

    /**
     * Opens dedicated About screen.
     */
    fun openAbout() {
        val currentState = _uiState.value
        _uiState.value = MainUiState.About(previousState = currentState)
    }

    /**
     * Returns from About screen.
     */
    fun dismissAbout() {
        val previous = (_uiState.value as? MainUiState.About)?.previousState
            ?: MainUiState.CheckingConnection()
        _uiState.value = previous
    }

    override fun onCleared() {
        super.onCleared()
        networkMonitor.unregister()
    }
}
