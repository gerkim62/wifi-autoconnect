package com.mgeni.autologin.ui.viewmodel

sealed interface MainUiState {
    // 1. Splash / Checking connection screen
    data class CheckingConnection(
        val isTakingLong: Boolean = false
    ) : MainUiState

    // 2. Already connected screen
    data object AlreadyConnected : MainUiState

    // 3. Wi-Fi disconnected / connection unreachable screen
    data class NotOnGuestNetwork(
        val errorMessage: String = "Make sure you're connected to the Wi-Fi network, then try again."
    ) : MainUiState

    // 4. Login screen
    data class LoginForm(
        val username: String = "",
        val password: String = "",
        val rememberMe: Boolean = true,
        val errorMessage: String? = null,
        val isSubmitting: Boolean = false
    ) : MainUiState

    // 5. Connecting screen
    data class Connecting(
        val statusMessage: String = "Logging in…",
        val isTakingLong: Boolean = false
    ) : MainUiState

    // 6. Success screen
    data object Success : MainUiState

    // 7. Login failed screen
    data class LoginFailed(
        val errorMessage: String = "Your username or password may be incorrect.",
        val savedUsername: String = ""
    ) : MainUiState

    // 8. Advanced Settings screen
    data class AdvancedSettings(
        val portalUrl: String,
        val skipInitialInternetCheck: Boolean = false,
        val hasSavedCredentials: Boolean = false,
        val isDefault: Boolean,
        val errorMessage: String? = null,
        val previousState: MainUiState
    ) : MainUiState

    // 9. Dedicated About screen
    data class About(
        val previousState: MainUiState
    ) : MainUiState
}
