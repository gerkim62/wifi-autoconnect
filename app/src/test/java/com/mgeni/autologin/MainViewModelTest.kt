package com.mgeni.autologin

import com.mgeni.autologin.data.ConnectivityResult
import com.mgeni.autologin.data.LoginSubmitResult
import com.mgeni.autologin.data.NetworkMonitor
import com.mgeni.autologin.data.PageFetchResult
import com.mgeni.autologin.data.PortalClient
import com.mgeni.autologin.data.PreferencesManager
import com.mgeni.autologin.ui.viewmodel.MainUiState
import com.mgeni.autologin.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var networkMonitor: NetworkMonitor

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        preferencesManager = PreferencesManager(null)
        networkMonitor = NetworkMonitor(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakePortalClient(
        private val connectivityResult: ConnectivityResult = ConnectivityResult.AlreadyConnected
    ) : PortalClient() {
        override suspend fun check204Connectivity(connectivityUrl: String): ConnectivityResult {
            return connectivityResult
        }
    }

    @Test
    fun `wifi drop while on AlreadyConnected immediately transitions to NotOnGuestNetwork`() = runTest(testDispatcher) {
        networkMonitor.emitNetworkStateForTesting(hasWifi = true, hasCellular = false)
        val fakeClient = FakePortalClient(ConnectivityResult.AlreadyConnected)
        val viewModel = MainViewModel(
            preferencesManager = preferencesManager,
            portalClient = fakeClient,
            networkMonitor = networkMonitor
        )

        advanceUntilIdle()
        assertTrue("Expected AlreadyConnected, got ${viewModel.uiState.value}", viewModel.uiState.value is MainUiState.AlreadyConnected)

        // Drop Wi-Fi
        networkMonitor.emitNetworkStateForTesting(hasWifi = false, hasCellular = false)
        advanceUntilIdle()

        assertTrue("Expected NotOnGuestNetwork after Wi-Fi lost, got ${viewModel.uiState.value}", viewModel.uiState.value is MainUiState.NotOnGuestNetwork)
    }

    @Test
    fun `wifi connect while on NotOnGuestNetwork automatically rechecks and connects`() = runTest(testDispatcher) {
        networkMonitor.emitNetworkStateForTesting(hasWifi = false, hasCellular = false)
        var clientResult: ConnectivityResult = ConnectivityResult.Unreachable("No network")
        val fakeClient = object : PortalClient() {
            override suspend fun check204Connectivity(connectivityUrl: String): ConnectivityResult {
                return clientResult
            }
        }
        val viewModel = MainViewModel(
            preferencesManager = preferencesManager,
            portalClient = fakeClient,
            networkMonitor = networkMonitor
        )

        advanceUntilIdle()
        assertTrue("Expected NotOnGuestNetwork initially, got ${viewModel.uiState.value}", viewModel.uiState.value is MainUiState.NotOnGuestNetwork)

        // Reconnect Wi-Fi and return AlreadyConnected
        clientResult = ConnectivityResult.AlreadyConnected
        networkMonitor.emitNetworkStateForTesting(hasWifi = true, hasCellular = false)
        advanceUntilIdle()

        assertTrue("Expected AlreadyConnected after reconnect, got ${viewModel.uiState.value}", viewModel.uiState.value is MainUiState.AlreadyConnected)
    }

    @Test
    fun `wifi state changes do not interrupt active editing in Advanced Settings`() = runTest(testDispatcher) {
        val fakeClient = FakePortalClient(ConnectivityResult.AlreadyConnected)
        val viewModel = MainViewModel(
            preferencesManager = preferencesManager,
            portalClient = fakeClient,
            networkMonitor = networkMonitor
        )

        advanceUntilIdle()
        viewModel.openAdvancedSettings()
        assertTrue(viewModel.uiState.value is MainUiState.AdvancedSettings)

        // Drop Wi-Fi while in Advanced Settings
        networkMonitor.emitNetworkStateForTesting(hasWifi = false, hasCellular = false)
        advanceUntilIdle()

        assertTrue("Expected to remain in AdvancedSettings during editing, got ${viewModel.uiState.value}", viewModel.uiState.value is MainUiState.AdvancedSettings)
    }

    @Test
    fun `wifi state changes do not interrupt user on Login Form`() = runTest(testDispatcher) {
        val fakeClient = FakePortalClient(ConnectivityResult.Unreachable("Offline"))
        val viewModel = MainViewModel(
            preferencesManager = preferencesManager,
            portalClient = fakeClient,
            networkMonitor = networkMonitor
        )

        advanceUntilIdle()
        viewModel.editCredentials("testuser")
        assertTrue(viewModel.uiState.value is MainUiState.LoginForm)

        // Wi-Fi changes while on Login Form
        networkMonitor.emitNetworkStateForTesting(hasWifi = false, hasCellular = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected to remain on LoginForm, got $state", state is MainUiState.LoginForm)
        assertEquals("testuser", (state as MainUiState.LoginForm).username)
    }

    @Test
    fun `checkInternetOnStartup disabled bypasses 204 check and immediately loads portal`() = runTest(testDispatcher) {
        preferencesManager.checkInternetOnStartup = false
        var check204Called = false
        var fetchPageCalled = false

        val fakeClient = object : PortalClient() {
            override suspend fun check204Connectivity(connectivityUrl: String): ConnectivityResult {
                check204Called = true
                return ConnectivityResult.AlreadyConnected
            }

            override suspend fun fetchLoginPage(portalUrl: String): PageFetchResult {
                fetchPageCalled = true
                return PageFetchResult.Success(
                    timeTag = "956629188",
                    actionUrl = "http://10.10.10.10/login.html",
                    redirectUrl = ""
                )
            }
        }

        val viewModel = MainViewModel(
            preferencesManager = preferencesManager,
            portalClient = fakeClient,
            networkMonitor = networkMonitor
        )

        advanceUntilIdle()
        assertTrue("204 check should NOT have been called when checkInternetOnStartup is false", !check204Called)
        assertTrue("fetchLoginPage should have been called directly", fetchPageCalled)
        assertTrue("Expected LoginForm, got ${viewModel.uiState.value}", viewModel.uiState.value is MainUiState.LoginForm)
    }

    @Test
    fun `openAbout and dismissAbout correctly transition and restore uiState`() = runTest(testDispatcher) {
        val fakeClient = FakePortalClient(ConnectivityResult.AlreadyConnected)
        val viewModel = MainViewModel(
            preferencesManager = preferencesManager,
            portalClient = fakeClient,
            networkMonitor = networkMonitor
        )

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is MainUiState.AlreadyConnected)

        viewModel.openAbout()
        val aboutState = viewModel.uiState.value
        assertTrue("Expected About state, got $aboutState", aboutState is MainUiState.About)
        assertTrue((aboutState as MainUiState.About).previousState is MainUiState.AlreadyConnected)

        viewModel.dismissAbout()
        assertTrue("Expected to return to AlreadyConnected, got ${viewModel.uiState.value}", viewModel.uiState.value is MainUiState.AlreadyConnected)
    }

    @Test
    fun `clearSavedCredentials removes stored credentials and updates state`() = runTest(testDispatcher) {
        preferencesManager.saveCredentials("testuser", "testpass", remember = true)
        assertTrue(preferencesManager.hasSavedCredentials())

        val fakeClient = FakePortalClient(ConnectivityResult.AlreadyConnected)
        val viewModel = MainViewModel(
            preferencesManager = preferencesManager,
            portalClient = fakeClient,
            networkMonitor = networkMonitor
        )

        advanceUntilIdle()
        viewModel.openAdvancedSettings()

        val settingsState = viewModel.uiState.value as MainUiState.AdvancedSettings
        assertTrue("Expected hasSavedCredentials to be true initially", settingsState.hasSavedCredentials)

        viewModel.clearSavedCredentials()
        assertTrue("Expected hasSavedCredentials to be false after clear", !preferencesManager.hasSavedCredentials())
        val updatedState = viewModel.uiState.value as MainUiState.AdvancedSettings
        assertTrue("Expected state hasSavedCredentials to be false", !updatedState.hasSavedCredentials)
    }

    @Test
    fun `auth failure transitions directly to LoginForm with BOTH username and password prefilled`() = runTest(testDispatcher) {
        preferencesManager.saveCredentials("john_doe", "secret_pass_123", remember = true)
        assertTrue(preferencesManager.hasSavedCredentials())

        val fakeClient = object : PortalClient() {
            override suspend fun check204Connectivity(connectivityUrl: String): ConnectivityResult {
                return ConnectivityResult.CaptiveDetected(null)
            }

            override suspend fun fetchLoginPage(portalUrl: String): PageFetchResult {
                return PageFetchResult.Success(
                    timeTag = "123456",
                    actionUrl = "http://10.10.10.10/login.html",
                    redirectUrl = ""
                )
            }

            override suspend fun submitLogin(
                actionUrl: String,
                username: String,
                password: String,
                timeTag: String,
                redirectUrl: String,                connectivityUrl: String,
                onStatusUpdate: ((String, String?) -> Unit)?
            ): LoginSubmitResult {
                return LoginSubmitResult.AuthFailed("Wrong username or password. Check your details and try again.")
            }
        }

        val viewModel = MainViewModel(
            preferencesManager = preferencesManager,
            portalClient = fakeClient,
            networkMonitor = networkMonitor
        )

        advanceUntilIdle()

        // State should return directly to LoginForm
        val state = viewModel.uiState.value
        assertTrue("Expected LoginForm on auth failure, got $state", state is MainUiState.LoginForm)
        val loginForm = state as MainUiState.LoginForm

        // Password MUST be prefilled so user can easily fix typos
        assertEquals("john_doe", loginForm.username)
        assertEquals("secret_pass_123", loginForm.password)
        assertTrue(loginForm.errorMessage!!.contains("Wrong username or password"))

        // Saved credentials must remain persistent in PreferencesManager
        assertTrue("Credentials must remain stored", preferencesManager.hasSavedCredentials())
    }

    @Test
    fun `network failure during login preserves credentials and transitions to LoginFailed retry view`() = runTest(testDispatcher) {
        preferencesManager.saveCredentials("john_doe", "secret_pass_123", remember = true)

        val fakeClient = object : PortalClient() {
            override suspend fun check204Connectivity(connectivityUrl: String): ConnectivityResult {
                return ConnectivityResult.CaptiveDetected(null)
            }

            override suspend fun fetchLoginPage(portalUrl: String): PageFetchResult {
                return PageFetchResult.Success(
                    timeTag = "123456",
                    actionUrl = "http://10.10.10.10/login.html",
                    redirectUrl = ""
                )
            }

            override suspend fun submitLogin(
                actionUrl: String,
                username: String,
                password: String,
                timeTag: String,
                redirectUrl: String,
                connectivityUrl: String,
                onStatusUpdate: ((String, String?) -> Unit)?
            ): LoginSubmitResult {
                return LoginSubmitResult.NetworkFailed("Could not reach portal during submission.")
            }
        }

        val viewModel = MainViewModel(
            preferencesManager = preferencesManager,
            portalClient = fakeClient,
            networkMonitor = networkMonitor
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected LoginFailed on network error, got $state", state is MainUiState.LoginFailed)
        assertEquals("john_doe", (state as MainUiState.LoginFailed).savedUsername)
        assertTrue(preferencesManager.hasSavedCredentials())
    }

    @Test
    fun `saveAdvancedSettings preserves settings screen and displays success message`() = runTest(testDispatcher) {
        val fakeClient = FakePortalClient(ConnectivityResult.AlreadyConnected)
        val viewModel = MainViewModel(
            preferencesManager = preferencesManager,
            portalClient = fakeClient,
            networkMonitor = networkMonitor
        )

        advanceUntilIdle()
        viewModel.openAdvancedSettings()
        assertTrue(viewModel.uiState.value is MainUiState.AdvancedSettings)

        viewModel.saveAdvancedSettings("http://192.168.1.1/login.html", checkInternetOnStartup = false)
        advanceUntilIdle()

        val settingsState = viewModel.uiState.value as MainUiState.AdvancedSettings
        assertEquals("http://192.168.1.1/login.html", preferencesManager.portalUrl)
        assertFalse(preferencesManager.checkInternetOnStartup)
        assertEquals("Settings saved successfully.", settingsState.successMessage)
    }

    @Test
    fun `unreachable connection check produces NotOnGuestNetwork advising guest and portal URL`() = runTest(testDispatcher) {
        val fakeClient = FakePortalClient(ConnectivityResult.Unreachable("Network unreachable"))
        val viewModel = MainViewModel(
            preferencesManager = preferencesManager,
            portalClient = fakeClient,
            networkMonitor = networkMonitor
        )

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue("Expected NotOnGuestNetwork, got $state", state is MainUiState.NotOnGuestNetwork)
        val errorMsg = (state as MainUiState.NotOnGuestNetwork).errorMessage
        assertTrue("Expected message to mention 'guest', got $errorMsg", errorMsg.contains("guest", ignoreCase = true))
        assertTrue("Expected message to mention portal URL or Settings, got $errorMsg", errorMsg.contains("portal URL", ignoreCase = true) || errorMsg.contains("Settings", ignoreCase = true))
    }

    @Test
    fun `pull to refresh on LoginForm transitions to AlreadyConnected when 204 check succeeds`() = runTest(testDispatcher) {
        var clientResult: ConnectivityResult = ConnectivityResult.CaptiveDetected(null)
        val fakeClient = object : PortalClient() {
            override suspend fun check204Connectivity(connectivityUrl: String): ConnectivityResult = clientResult
            override suspend fun fetchLoginPage(portalUrl: String): PageFetchResult = PageFetchResult.Success("123", "http://10.10.10.10/login.html", "")
        }

        val viewModel = MainViewModel(
            preferencesManager = preferencesManager,
            portalClient = fakeClient,
            networkMonitor = networkMonitor
        )

        advanceUntilIdle()
        assertTrue("Expected LoginForm initially, got ${viewModel.uiState.value}", viewModel.uiState.value is MainUiState.LoginForm)

        // Internet becomes active, user pulls to refresh
        clientResult = ConnectivityResult.AlreadyConnected
        viewModel.startConnectionCheck(isUserInitiated = true)
        advanceUntilIdle()

        assertTrue("Expected AlreadyConnected after pull to refresh, got ${viewModel.uiState.value}", viewModel.uiState.value is MainUiState.AlreadyConnected)
    }

    @Test
    fun `pull to refresh on AlreadyConnected transitions to NotOnGuestNetwork when connection is lost`() = runTest(testDispatcher) {
        var clientResult: ConnectivityResult = ConnectivityResult.AlreadyConnected
        val fakeClient = object : PortalClient() {
            override suspend fun check204Connectivity(connectivityUrl: String): ConnectivityResult = clientResult
        }

        val viewModel = MainViewModel(
            preferencesManager = preferencesManager,
            portalClient = fakeClient,
            networkMonitor = networkMonitor
        )

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is MainUiState.AlreadyConnected)

        // Connection is lost, user pulls to refresh
        clientResult = ConnectivityResult.Unreachable("No Wi-Fi")
        viewModel.startConnectionCheck(isUserInitiated = true)
        advanceUntilIdle()

        assertTrue("Expected NotOnGuestNetwork, got ${viewModel.uiState.value}", viewModel.uiState.value is MainUiState.NotOnGuestNetwork)
    }

    @Test
    fun `editCredentials preserves rememberMe false preference`() = runTest(testDispatcher) {
        val fakeClient = FakePortalClient(ConnectivityResult.AlreadyConnected)
        val viewModel = MainViewModel(
            preferencesManager = preferencesManager,
            portalClient = fakeClient,
            networkMonitor = networkMonitor
        )

        advanceUntilIdle()
        // Submit with rememberMe = false
        preferencesManager.rememberMe = false
        viewModel.submitLoginForm("testuser", "testpass", remember = false)
        viewModel.editCredentials("testuser")

        val state = viewModel.uiState.value
        assertTrue(state is MainUiState.LoginForm)
        assertFalse("rememberMe should remain false", (state as MainUiState.LoginForm).rememberMe)
    }

    @Test
    fun `clearSavedCredentials resets retry in-memory credentials`() = runTest(testDispatcher) {
        val fakeClient = FakePortalClient(ConnectivityResult.AlreadyConnected)
        val viewModel = MainViewModel(
            preferencesManager = preferencesManager,
            portalClient = fakeClient,
            networkMonitor = networkMonitor
        )

        advanceUntilIdle()
        viewModel.submitLoginForm("testuser", "testpass", remember = true)
        viewModel.clearSavedCredentials()

        // Attempting retry after clear should fallback to editing credentials with empty fields
        viewModel.retryLastSubmittedCredentials()
        val state = viewModel.uiState.value
        assertTrue(state is MainUiState.LoginForm)
        assertEquals("", (state as MainUiState.LoginForm).username)
        assertEquals("", (state as MainUiState.LoginForm).password)
    }

    @Test
    fun `dismissAdvancedSettings restores updated state after successful settings save`() = runTest(testDispatcher) {
        var clientResult: ConnectivityResult = ConnectivityResult.Unreachable("Initial failure")
        val fakeClient = object : PortalClient() {
            override suspend fun check204Connectivity(connectivityUrl: String): ConnectivityResult = clientResult
        }

        val viewModel = MainViewModel(
            preferencesManager = preferencesManager,
            portalClient = fakeClient,
            networkMonitor = networkMonitor
        )

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is MainUiState.NotOnGuestNetwork)

        // Open settings, change URL, and save with successful 204
        viewModel.openAdvancedSettings()
        clientResult = ConnectivityResult.AlreadyConnected
        viewModel.saveAdvancedSettings("http://10.10.10.10/login.html")
        advanceUntilIdle()

        // Dismissing settings should return to AlreadyConnected, not the old NotOnGuestNetwork
        viewModel.dismissAdvancedSettings()
        assertTrue("Expected AlreadyConnected after save and dismiss, got ${viewModel.uiState.value}", viewModel.uiState.value is MainUiState.AlreadyConnected)
    }
}
