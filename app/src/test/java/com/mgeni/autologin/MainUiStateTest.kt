package com.mgeni.autologin

import com.mgeni.autologin.ui.viewmodel.MainUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiStateTest {

    @Test
    fun `LoginForm defaults are correct`() {
        val form = MainUiState.LoginForm(
            username = "testuser",
            password = "secretpassword",
            rememberMe = true
        )
        assertEquals("testuser", form.username)
        assertEquals("secretpassword", form.password)
        assertTrue(form.rememberMe)
        assertFalse(form.isSubmitting)
    }

    @Test
    fun `LoginFailed retains saved username`() {
        val failed = MainUiState.LoginFailed(
            errorMessage = "Invalid password",
            savedUsername = "saved_user"
        )
        assertEquals("Invalid password", failed.errorMessage)
        assertEquals("saved_user", failed.savedUsername)
    }

    @Test
    fun `AdvancedSettings stores previous state and defaults`() {
        val previous = MainUiState.LoginForm(username = "user1")
        val settings = MainUiState.AdvancedSettings(
            portalUrl = "http://10.10.10.10/login.html",
            isDefault = true,
            previousState = previous
        )
        assertEquals("http://10.10.10.10/login.html", settings.portalUrl)
        assertTrue(settings.isDefault)
        assertEquals(previous, settings.previousState)
    }
}
