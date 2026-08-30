package com.mgeni.autologin.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgeni.autologin.data.AppLogger
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

    private val _isBackgroundChecking = MutableStateFlow(false)
    val isBackgroundChecking: StateFlow<Boolean> = _isBackgroundChecking.asStateFlow()

    private val _backgroundStatusMessage = MutableStateFlow("Checking connection…")
    val backgroundStatusMessage: StateFlow<String> = _backgroundStatusMessage.asStateFlow()

    val logCount: StateFlow<Int> = AppLogger.logCount

    val networkState: StateFlow<NetworkState> = networkMonitor.networkState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetworkState.Offline)

    val isCellularActive: StateFlow<Boolean> = networkMonitor.isCellularActive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Tracks whether cold-start check has finished so we never flash full-screen splash during user interaction
    private var hasCompletedInitialLaunch = false

    // In-memory retention of last submitted credentials for retry actions
    private var lastSubmittedUser: String = ""
    private var lastSubmittedPass: String = ""
    private var lastSubmittedRememberMe: Boolean = true

    private var checkJob: Job? = null
    private var loginJob: Job? = null

    init {
        AppLogger.i("VIEW_MODEL", "MainViewModel initialized. Checking onboarding status.")
        if (!preferencesManager.hasCompletedOnboarding) {
            AppLogger.i("VIEW_MODEL", "First-time launch detected; showing Onboarding screen.")
            _uiState.value = MainUiState.Onboarding()
        } else {
            AppLogger.i("VIEW_MODEL", "Launching initial cold-start connection check.")
            startConnectionCheck(isUserInitiated = true)
        }
        observeNetworkChanges()
    }

    private fun observeNetworkChanges() {
        viewModelScope.launch {
            networkMonitor.activeWifiNetwork.collect { wifiNetwork ->
                portalClient.bindToNetwork(wifiNetwork)
            }
        }

        viewModelScope.launch {
            var isFirstEmission = true
            networkMonitor.wifiChangeCount.collect { changeCount ->
                if (isFirstEmission) {
                    isFirstEmission = false
                    return@collect
                }

                AppLogger.i("VIEW_MODEL", "Wi-Fi change observed (changeCount=$changeCount, isWifiActive=${networkMonitor.isWifiActive.value})")

                val currentState = _uiState.value
                // Never interrupt active user screens (Settings, About, Onboarding, LoginForm typing)
                if (currentState is MainUiState.Settings ||
                    currentState is MainUiState.About ||
                    currentState is MainUiState.Onboarding ||
                    currentState is MainUiState.LoginForm
                ) {
                    AppLogger.d("VIEW_MODEL", "Wi-Fi event received while user is on ${currentState::class.simpleName}; maintaining current screen.")
                    return@collect
                }

                if (!networkMonitor.isWifiActive.value) {
                    // Wi-Fi lost / disconnected
                    checkJob?.cancel()
                    _isBackgroundChecking.value = false
                    _uiState.value = MainUiState.NotOnGuestNetwork(
                        errorMessage = "Make sure you're connected to the \"guest\" Wi-Fi network, or check Settings."
                    )
                } else {
                    // Wi-Fi connected or changed: run dynamic non-intrusive background check
                    startConnectionCheck(isUserInitiated = false)
                }
            }
        }

        viewModelScope.launch {
            var isFirstState = true
            networkMonitor.networkState.collect { netState ->
                if (isFirstState) {
                    isFirstState = false
                    return@collect
                }
                AppLogger.i("VIEW_MODEL", "NetworkState change observed: $netState")
                val currentState = _uiState.value
                if (currentState is MainUiState.Settings ||
                    currentState is MainUiState.About ||
                    currentState is MainUiState.Onboarding ||
                    currentState is MainUiState.LoginForm
                ) {
                    AppLogger.d("VIEW_MODEL", "NetworkState changed to $netState while user is on ${currentState::class.simpleName}; maintaining current screen.")
                    return@collect
                }

                if (netState == NetworkState.Offline || netState == NetworkState.OnlyCellular) {
                    checkJob?.cancel()
                    _isBackgroundChecking.value = false
                    _uiState.value = MainUiState.NotOnGuestNetwork(
                        errorMessage = if (netState == NetworkState.OnlyCellular) {
                            "You are using mobile data, but WifiAuto requires Wi-Fi. Connect to the \"guest\" Wi-Fi network to sign in."
                        } else {
                            "Wi-Fi is disconnected. Please turn on Wi-Fi and connect to the \"guest\" Wi-Fi network, then try again."
                        }
                    )
                }
            }
        }
    }

    private fun updateModalPreviousState(newState: MainUiState) {
        val current = _uiState.value
        if (current is MainUiState.Settings) {
            _uiState.value = current.copy(previousState = newState)
        } else if (current is MainUiState.About) {
            _uiState.value = current.copy(previousState = newState)
        } else if (current is MainUiState.Onboarding) {
            _uiState.value = current.copy(previousState = newState)
        }
    }

    /**
     * Step 1: Initial 204 check on app launch, manual refresh, or background Wi-Fi event.
     * @param isUserInitiated If true (cold start or explicit user pull-to-refresh), shows full-screen checking;
     *                        if false (background event), shows non-intrusive top indicator.
     */
    fun startConnectionCheck(isUserInitiated: Boolean = true) {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            val currentState = _uiState.value
            val isModal = currentState is MainUiState.Settings || currentState is MainUiState.About

            if (isUserInitiated && !isModal) {
                _uiState.value = MainUiState.CheckingConnection(isTakingLong = false)
                _isBackgroundChecking.value = false
            } else {
                _isBackgroundChecking.value = true
                _backgroundStatusMessage.value = "Checking connection…"
            }

            val checkingStartedAt = System.currentTimeMillis()
            portalClient.bindToNetwork(networkMonitor.activeWifiNetwork.value)
            networkMonitor.updateNetworkStates()

            if (!preferencesManager.checkInternetOnStartup) {
                AppLogger.i("VIEW_MODEL", "checkInternetOnStartup is disabled; bypassing 204 check and loading portal directly.")
                if (isUserInitiated && !isModal) {
                    keepCheckingScreenVisible(checkingStartedAt)
                }
                proceedToCaptivePortal(isUserInitiated = isUserInitiated)
                _isBackgroundChecking.value = false
                hasCompletedInitialLaunch = true
                return@launch
            }

            val slowNoticeJob = launch {
                delay(SLOW_OPERATION_NOTICE_MILLIS)
                if (_uiState.value is MainUiState.CheckingConnection) {
                    _uiState.value = MainUiState.CheckingConnection(isTakingLong = true)
                }
                if (_isBackgroundChecking.value) {
                    _backgroundStatusMessage.value = "Connection check is taking longer than usual…"
                }
            }

            var result = portalClient.check204Connectivity()

            // If Wi-Fi is actively establishing connection and first ping returned Unreachable,
            // retry with backoff to allow Wi-Fi routes to stabilize before concluding Unreachable
            if (result is ConnectivityResult.Unreachable && networkMonitor.isWifiActive.value) {
                for (retry in 1..3) {
                    AppLogger.i("VIEW_MODEL", "Wi-Fi route stabilization retry #$retry for connectivity probe...")
                    delay(800L)
                    networkMonitor.updateNetworkStates()
                    portalClient.bindToNetwork(networkMonitor.activeWifiNetwork.value)
                    result = portalClient.check204Connectivity()
                    if (result !is ConnectivityResult.Unreachable) break
                }
            }

            slowNoticeJob.cancel()

            when (result) {
                is ConnectivityResult.AlreadyConnected -> {
                    AppLogger.i("VIEW_MODEL", "Connectivity probe confirmed: Internet already active.")
                    if (isUserInitiated && !isModal) {
                        keepCheckingScreenVisible(checkingStartedAt)
                        _uiState.value = MainUiState.AlreadyConnected
                    } else if (isModal) {
                        updateModalPreviousState(MainUiState.AlreadyConnected)
                    } else {
                        _uiState.value = MainUiState.AlreadyConnected
                    }
                }
                is ConnectivityResult.CaptiveDetected -> {
                    AppLogger.i("VIEW_MODEL", "Captive portal detected. Redirect hint: ${result.portalRedirectUrl}")
                    if (isUserInitiated && !isModal) {
                        _uiState.value = MainUiState.CheckingConnection(message = "Opening login page…")
                    } else {
                        _backgroundStatusMessage.value = "Opening login page…"
                    }
                    val pageResult = async {
                        fetchCaptivePortalLoginPage(result.portalRedirectUrl)
                    }
                    if (isUserInitiated && !isModal) {
                        keepCheckingScreenVisible(checkingStartedAt)
                    }
                    handlePortalPageResult(pageResult.await(), isUserInitiated = isUserInitiated)
                }
                is ConnectivityResult.Unreachable -> {
                    AppLogger.w("VIEW_MODEL", "Connectivity probe unreachable: ${result.message}")
                    val unreachableState = MainUiState.NotOnGuestNetwork(
                        errorMessage = "Make sure you're connected to the \"guest\" Wi-Fi network, or check Settings."
                    )
                    if (isUserInitiated && !isModal) {
                        keepCheckingScreenVisible(checkingStartedAt)
                        _uiState.value = unreachableState
                    } else if (isModal) {
                        updateModalPreviousState(unreachableState)
                    } else {
                        _uiState.value = unreachableState
                    }
                }
            }

            _isBackgroundChecking.value = false
            hasCompletedInitialLaunch = true
        }
    }

    /**
     * Step 2: Fetch the captive portal login page and handle auto-login or show login form.
     */
    private suspend fun proceedToCaptivePortal(redirectHint: String? = null, isUserInitiated: Boolean = true) {
        val currentState = _uiState.value
        val isModal = currentState is MainUiState.Settings || currentState is MainUiState.About
        if (isUserInitiated && !isModal) {
            _uiState.value = MainUiState.CheckingConnection(message = "Opening login page…")
        }
        handlePortalPageResult(fetchCaptivePortalLoginPage(redirectHint), isUserInitiated = isUserInitiated)
    }

    private suspend fun fetchCaptivePortalLoginPage(redirectHint: String? = null): PageFetchResult {
        val configuredUrl = preferencesManager.portalUrl.trim()
        val isCustomUrlConfigured = configuredUrl.isNotBlank() && configuredUrl != PreferencesManager.DEFAULT_PORTAL_URL

        val portalUrl = when {
            !redirectHint.isNullOrBlank() && redirectHint.startsWith("http") -> {
                AppLogger.d("VIEW_MODEL", "Using probe redirect hint URL: $redirectHint")
                redirectHint
            }
            isCustomUrlConfigured -> {
                AppLogger.d("VIEW_MODEL", "Using user-configured preferred Portal URL from Settings: $configuredUrl")
                configuredUrl
            }
            else -> {
                val gatewayIp = networkMonitor.getActiveWifiGatewayIp()
                if (!gatewayIp.isNullOrBlank()) {
                    AppLogger.d("VIEW_MODEL", "Using dynamically resolved Wi-Fi gateway IP: $gatewayIp")
                    "http://$gatewayIp/login.html"
                } else {
                    configuredUrl.ifBlank { PreferencesManager.DEFAULT_PORTAL_URL }
                }
            }
        }

        return portalClient.fetchLoginPage(portalUrl)
    }

    private suspend fun handlePortalPageResult(pageResult: PageFetchResult, isUserInitiated: Boolean = true) {
        val currentState = _uiState.value
        val isModal = currentState is MainUiState.Settings || currentState is MainUiState.About

        when (pageResult) {
            is PageFetchResult.AlreadyAuthenticated -> {
                AppLogger.i("VIEW_MODEL", "Portal confirmed device is already authenticated.")
                if (isModal) {
                    updateModalPreviousState(MainUiState.AlreadyConnected)
                } else {
                    _uiState.value = MainUiState.AlreadyConnected
                }
            }
            is PageFetchResult.Error -> {
                AppLogger.w("VIEW_MODEL", "Portal page fetch returned error: ${pageResult.message}")
                val errorState = MainUiState.NotOnGuestNetwork(errorMessage = pageResult.message)
                if (isModal) {
                    updateModalPreviousState(errorState)
                } else {
                    _uiState.value = errorState
                }
            }
            is PageFetchResult.Success -> {
                if (preferencesManager.hasVerifiedCredentials()) {
                    AppLogger.i("VIEW_MODEL", "Verified saved credentials found for user '${preferencesManager.username}'. Initiating automatic login.")
                    lastSubmittedUser = preferencesManager.username
                    lastSubmittedPass = preferencesManager.password
                    lastSubmittedRememberMe = preferencesManager.rememberMe

                    executeLogin(
                        actionUrl = pageResult.actionUrl,
                        username = lastSubmittedUser,
                        password = lastSubmittedPass,
                        timeTag = pageResult.timeTag,
                        redirectUrl = pageResult.redirectUrl,
                        rememberMe = lastSubmittedRememberMe,
                        isUserInitiated = isUserInitiated
                    )
                } else {
                    AppLogger.i("VIEW_MODEL", "No verified credentials found (hasSaved=${preferencesManager.hasSavedCredentials()}, verified=${preferencesManager.isCredentialsVerified}). Transitioning to LoginForm.")
                    val formState = MainUiState.LoginForm(
                        username = preferencesManager.username,
                        password = preferencesManager.password,
                        rememberMe = preferencesManager.rememberMe,
                        errorMessage = null
                    )
                    if (isModal) {
                        updateModalPreviousState(formState)
                    } else {
                        _uiState.value = formState
                    }
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

        if (remember) {
            // Save credentials immediately as unverified so user input is never discarded even on unexpected kill
            preferencesManager.saveCredentials(trimmedUser, pass, remember, isVerified = false)
        }

        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            _uiState.value = MainUiState.Connecting("Connecting…", isTakingLong = false)
            _isBackgroundChecking.value = false

            try {
                val portalUrl = preferencesManager.portalUrl
                when (val pageResult = portalClient.fetchLoginPage(portalUrl)) {
                    is PageFetchResult.AlreadyAuthenticated -> {
                        AppLogger.i("VIEW_MODEL", "Portal confirmed device is already authenticated during form submit.")
                        _uiState.value = MainUiState.AlreadyConnected
                    }
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
                            rememberMe = remember,
                            isUserInitiated = true
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("VIEW_MODEL", "Exception in submitLoginForm: ${e.localizedMessage}", e)
                _uiState.value = MainUiState.LoginForm(
                    username = trimmedUser,
                    password = pass,
                    rememberMe = remember,
                    errorMessage = "Network error while connecting. Please try again."
                )
            }
        }
    }

    private suspend fun executeLogin(
        actionUrl: String,
        username: String,
        password: String,
        timeTag: String,
        redirectUrl: String,
        rememberMe: Boolean,
        isUserInitiated: Boolean = true
    ) {
        val currentState = _uiState.value
        val isModal = currentState is MainUiState.Settings || currentState is MainUiState.About

        if (!isModal) {
            _uiState.value = MainUiState.Connecting(
                statusMessage = "Signing in…",
                detailMessage = "Authenticating…",
                isTakingLong = false
            )
            _isBackgroundChecking.value = false
        } else {
            _isBackgroundChecking.value = true
            _backgroundStatusMessage.value = "Signing in…"
        }

        var slowJob: Job? = null
        slowJob = viewModelScope.launch {
            delay(SLOW_OPERATION_NOTICE_MILLIS)
            if (_uiState.value is MainUiState.Connecting) {
                _uiState.value = (_uiState.value as MainUiState.Connecting).copy(isTakingLong = true)
            }
            if (_isBackgroundChecking.value) {
                _backgroundStatusMessage.value = "Sign-in is taking longer than usual…"
            }
        }

        val submitResult = try {
            portalClient.submitLogin(
                actionUrl = actionUrl,
                username = username,
                password = password,
                timeTag = timeTag,
                redirectUrl = redirectUrl,
                respectPortalResponse = preferencesManager.respectPortalResponse,
                onStatusUpdate = { status, detail ->
                    if (!isModal) {
                        if (_uiState.value is MainUiState.Connecting) {
                            _uiState.value = (_uiState.value as MainUiState.Connecting).copy(
                                statusMessage = status,
                                detailMessage = detail
                            )
                        }
                    } else {
                        _backgroundStatusMessage.value = detail ?: status
                    }
                }
            )
        } catch (e: Exception) {
            AppLogger.e("VIEW_MODEL", "executeLogin exception: ${e.localizedMessage}", e)
            LoginSubmitResult.NetworkFailed("Network error during submission: ${e.localizedMessage ?: "Please try again."}")
        }

        slowJob.cancel()
        _isBackgroundChecking.value = false

        when (submitResult) {
            is LoginSubmitResult.Success -> {
                AppLogger.i("VIEW_MODEL", "Login succeeded for '$username'. Marking credentials verified and saving.")
                preferencesManager.saveCredentials(username, password, rememberMe, isVerified = true)
                if (isModal) {
                    updateModalPreviousState(MainUiState.Success)
                } else {
                    _uiState.value = MainUiState.Success
                }
            }
            is LoginSubmitResult.AuthFailed -> {
                AppLogger.w("VIEW_MODEL", "Authentication rejected by portal: ${submitResult.message}. Preserving credentials as unverified.")
                if (rememberMe) {
                    // Keep credentials saved in preferences so user never loses them, marked unverified
                    preferencesManager.saveCredentials(username, password, rememberMe, isVerified = false)
                }
                // Return directly to LoginForm with BOTH username and password prefilled so user can fix typo easily!
                val formState = MainUiState.LoginForm(
                    username = username,
                    password = password,
                    rememberMe = rememberMe,
                    errorMessage = submitResult.message
                )
                if (isModal) {
                    updateModalPreviousState(formState)
                } else {
                    _uiState.value = formState
                }
            }
            is LoginSubmitResult.NetworkFailed -> {
                AppLogger.w("VIEW_MODEL", "Network / timeout issue during login: ${submitResult.message}. Credentials remain safe.")
                // For network/timeout issues, show LoginFailed with option to retry, keeping credentials safe
                val failedState = MainUiState.LoginFailed(
                    errorMessage = submitResult.message,
                    savedUsername = username
                )
                if (isModal) {
                    updateModalPreviousState(failedState)
                } else {
                    _uiState.value = failedState
                }
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
            loginJob?.cancel()
            loginJob = viewModelScope.launch {
                _uiState.value = MainUiState.Connecting("Retrying connection…", isTakingLong = false)

                try {
                    val portalUrl = preferencesManager.portalUrl
                    when (val pageResult = portalClient.fetchLoginPage(portalUrl)) {
                        is PageFetchResult.AlreadyAuthenticated -> {
                            AppLogger.i("VIEW_MODEL", "Retry revealed device is already authenticated.")
                            _uiState.value = MainUiState.AlreadyConnected
                        }
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
                                rememberMe = lastSubmittedRememberMe,
                                isUserInitiated = true
                            )
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("VIEW_MODEL", "Exception in retryLastSubmittedCredentials: ${e.localizedMessage}", e)
                    _uiState.value = MainUiState.LoginFailed(
                        errorMessage = "Connection error during retry. Please try again.",
                        savedUsername = lastSubmittedUser
                    )
                }
            }
        } else {
            editCredentials(lastSubmittedUser)
        }
    }

    /**
     * Secondary action on Login Failed: Navigate to Login Form with error banner and credentials prefilled.
     */
    fun editCredentials(savedUsername: String) {
        _uiState.value = MainUiState.LoginForm(
            username = savedUsername.ifBlank { preferencesManager.username },
            password = if (lastSubmittedPass.isNotBlank()) lastSubmittedPass else preferencesManager.password,
            rememberMe = lastSubmittedRememberMe,
            errorMessage = "Check your credentials and try again."
        )
    }

    /**
     * Opens Settings screen.
     */
    fun openSettings() {
        val currentState = _uiState.value
        val currentUrl = preferencesManager.portalUrl
        val isDefault = currentUrl == PreferencesManager.DEFAULT_PORTAL_URL
        val checkInternet = preferencesManager.checkInternetOnStartup
        val hasSavedCreds = preferencesManager.hasSavedCredentials()
        _uiState.value = MainUiState.Settings(
            portalUrl = currentUrl,
            checkInternetOnStartup = checkInternet,
            respectPortalResponse = preferencesManager.respectPortalResponse,
            enableBackgroundNotifications = preferencesManager.enableBackgroundNotifications,
            hasSavedCredentials = hasSavedCreds,
            isDefault = isDefault,
            errorMessage = null,
            successMessage = null,
            logCount = AppLogger.logCount.value,
            previousState = currentState
        )
    }

    /**
     * Clears stored login credentials from preferences and reset memory cache explicitly.
     */
    fun clearSavedCredentials() {
        preferencesManager.clearSavedCredentials()
        lastSubmittedUser = ""
        lastSubmittedPass = ""
        lastSubmittedRememberMe = true
        val currentSettings = _uiState.value as? MainUiState.Settings
        if (currentSettings != null) {
            _uiState.value = currentSettings.copy(
                hasSavedCredentials = false,
                successMessage = "Saved credentials cleared from this device."
            )
        }
    }

    /**
     * Saves customized Portal URL in Settings without forced navigation.
     */
    fun savePortalUrl(newUrl: String) {
        val cleanedUrl = newUrl.trim()
        val parsed = cleanedUrl.toHttpUrlOrNull()

        if (parsed == null || (parsed.scheme != "http" && parsed.scheme != "https")) {
            val currentSettings = _uiState.value as? MainUiState.Settings
            if (currentSettings != null) {
                _uiState.value = currentSettings.copy(
                    portalUrl = cleanedUrl,
                    errorMessage = "Please enter a valid URL starting with http:// or https://",
                    successMessage = null
                )
            }
            return
        }

        preferencesManager.portalUrl = cleanedUrl
        val isDefault = cleanedUrl == PreferencesManager.DEFAULT_PORTAL_URL

        val currentSettings = _uiState.value as? MainUiState.Settings
        if (currentSettings != null) {
            _uiState.value = currentSettings.copy(
                portalUrl = cleanedUrl,
                isDefault = isDefault,
                errorMessage = null,
                successMessage = "Portal URL saved."
            )
        }

        startConnectionCheck(isUserInitiated = false)
    }

    /**
     * Toggles checkInternetOnStartup setting and saves it immediately.
     */
    fun toggleCheckInternetOnStartup(enabled: Boolean) {
        preferencesManager.checkInternetOnStartup = enabled
        val currentSettings = _uiState.value as? MainUiState.Settings
        if (currentSettings != null) {
            _uiState.value = currentSettings.copy(checkInternetOnStartup = enabled)
        }
    }

    /**
     * Toggles respectPortalResponse setting and saves it immediately.
     */
    fun toggleRespectPortalResponse(enabled: Boolean) {
        preferencesManager.respectPortalResponse = enabled
        val currentSettings = _uiState.value as? MainUiState.Settings
        if (currentSettings != null) {
            _uiState.value = currentSettings.copy(respectPortalResponse = enabled)
        }
    }

    /**
     * Toggles enableBackgroundNotifications setting and saves it immediately.
     */
    fun toggleBackgroundNotifications(enabled: Boolean) {
        preferencesManager.enableBackgroundNotifications = enabled
        val currentSettings = _uiState.value as? MainUiState.Settings
        if (currentSettings != null) {
            _uiState.value = currentSettings.copy(enableBackgroundNotifications = enabled)
        }
    }

    /**
     * Resets Portal URL and preferences to default without forced page dismissal.
     */
    fun resetSettingsToDefault() {
        preferencesManager.resetPortalUrl()
        preferencesManager.checkInternetOnStartup = true
        preferencesManager.respectPortalResponse = true
        preferencesManager.enableBackgroundNotifications = false

        val currentSettings = _uiState.value as? MainUiState.Settings
        if (currentSettings != null) {
            _uiState.value = currentSettings.copy(
                portalUrl = PreferencesManager.DEFAULT_PORTAL_URL,
                checkInternetOnStartup = true,
                respectPortalResponse = true,
                enableBackgroundNotifications = false,
                isDefault = true,
                errorMessage = null,
                successMessage = "Settings restored to defaults."
            )
            startConnectionCheck(isUserInitiated = false)
        } else {
            startConnectionCheck(isUserInitiated = true)
        }
    }

    /**
     * Returns from Settings without saving.
     */
    fun dismissSettings() {
        val previous = (_uiState.value as? MainUiState.Settings)?.previousState
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

    /**
     * Completes onboarding flow and persists the completion flag in Preferences.
     */
    fun completeOnboarding(enableNotifications: Boolean = false) {
        AppLogger.i("VIEW_MODEL", "Onboarding completed by user (enableNotifications=$enableNotifications).")
        preferencesManager.enableBackgroundNotifications = enableNotifications
        preferencesManager.hasCompletedOnboarding = true
        _uiState.value = MainUiState.CheckingConnection(isTakingLong = false)
        startConnectionCheck(isUserInitiated = true)
    }

    /**
     * Opens the onboarding / app guide screen.
     */
    fun openOnboarding() {
        val current = _uiState.value
        if (current !is MainUiState.Onboarding) {
            AppLogger.i("VIEW_MODEL", "Opening Onboarding / App Guide screen.")
            _uiState.value = MainUiState.Onboarding(
                previousState = current,
                initialEnableNotifications = preferencesManager.enableBackgroundNotifications
            )
        }
    }

    /**
     * Dismisses the onboarding / app guide screen.
     */
    fun dismissOnboarding() {
        if (!preferencesManager.hasCompletedOnboarding) {
            completeOnboarding(false)
            return
        }
        val previous = (_uiState.value as? MainUiState.Onboarding)?.previousState
            ?: MainUiState.CheckingConnection()
        _uiState.value = previous
    }

    /**
     * Exports logs via Android native share sheet using .log file format.
     */
    fun exportLogs(context: Context) {
        try {
            val file = AppLogger.prepareExportFile(context)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "WifiAuto Diagnostic Logs (${file.name})")
                putExtra(Intent.EXTRA_TEXT, "WifiAuto Captive Portal diagnostic log export (${file.name}) attached.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Export Diagnostic Logs (${file.name})").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            AppLogger.i("VIEW_MODEL", "Export logs share sheet launched for file: ${file.name}")
        } catch (e: Exception) {
            AppLogger.e("VIEW_MODEL", "Failed to launch log export share sheet: ${e.localizedMessage}", e)
        }
    }

    /**
     * Clears all recorded diagnostic logs.
     */
    fun clearLogs(context: Context) {
        AppLogger.clearLogs(context)
        val currentSettings = _uiState.value as? MainUiState.Settings
        if (currentSettings != null) {
            _uiState.value = currentSettings.copy(
                logCount = 0,
                successMessage = "Logs cleared successfully."
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        networkMonitor.unregister()
    }
}
