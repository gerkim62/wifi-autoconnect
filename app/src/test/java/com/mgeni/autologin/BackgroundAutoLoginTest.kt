package com.mgeni.autologin

import com.mgeni.autologin.data.BackgroundManager
import com.mgeni.autologin.data.NotificationHelper
import com.mgeni.autologin.data.PreferencesManager
import com.mgeni.autologin.data.worker.AutoLoginWorker
import com.mgeni.autologin.ui.viewmodel.MainUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundAutoLoginTest {

    @Test
    fun `PreferencesManager background defaults and mutations work correctly`() {
        val prefs = PreferencesManager()

        // Defaults
        assertTrue(prefs.enableBackgroundAutoLogin)
        assertFalse(prefs.enableBackgroundNotifications)
        assertEquals(0L, prefs.lastBackgroundLoginTime)

        // Mutate
        prefs.enableBackgroundAutoLogin = false
        assertFalse(prefs.enableBackgroundAutoLogin)

        prefs.enableBackgroundNotifications = true
        assertTrue(prefs.enableBackgroundNotifications)

        val timestamp = 1724950000000L
        prefs.lastBackgroundLoginTime = timestamp
        assertEquals(timestamp, prefs.lastBackgroundLoginTime)
    }

    @Test
    fun `BackgroundManager and NotificationHelper constants are valid`() {
        assertEquals("com.mgeni.autologin.ACTION_NETWORK_EVENT", BackgroundManager.ACTION_NETWORK_EVENT)
        assertEquals("mgeni_background_status", NotificationHelper.CHANNEL_ID)
        assertEquals("AutoLoginWorker", AutoLoginWorker.TAG)
    }

    @Test
    fun `MainUiState AdvancedSettings retains background preferences`() {
        val settings = MainUiState.AdvancedSettings(
            portalUrl = "http://10.10.10.10/login.html",
            checkInternetOnStartup = true,
            enableBackgroundAutoLogin = true,
            enableBackgroundNotifications = true
        )

        assertTrue(settings.enableBackgroundAutoLogin)
        assertTrue(settings.enableBackgroundNotifications)
    }
}
