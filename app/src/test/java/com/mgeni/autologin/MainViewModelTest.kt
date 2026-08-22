package com.mgeni.autologin

import com.mgeni.autologin.data.ConnectivityResult
import com.mgeni.autologin.data.NetworkMonitor
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
    fun `skipInitialInternetCheck bypasses 204 check and immediately loads portal`() = runTest(testDispatcher) {
        preferencesManager.skipInitialInternetCheck = true
        var check204Called = false
        var fetchPageCalled = false

        val fakeClient = object : PortalClient() {
            override suspend fun check204Connectivity(connectivityUrl: String): ConnectivityResult {
                check204Called = true
                return ConnectivityResult.AlreadyConnected
            }

            override suspend fun fetchLoginPage(portalUrl: String): com.mgeni.autologin.data.PageFetchResult {
                fetchPageCalled = true
                return com.mgeni.autologin.data.PageFetchResult.Success(
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
        assertTrue("204 check should NOT have been called when skipInitialInternetCheck is true", !check204Called)
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
}
