package com.mgeni.autologin

import com.mgeni.autologin.data.AppLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLoggerTest {

    @Before
    fun setUp() {
        AppLogger.clearLogs()
    }

    @Test
    fun `AppLogger records entries and updates count`() {
        assertEquals(0, AppLogger.logCount.value)

        AppLogger.i("TEST_TAG", "Information event")
        AppLogger.w("TEST_TAG", "Warning event")
        AppLogger.e("TEST_TAG", "Error event")

        assertEquals(3, AppLogger.logCount.value)
        val formatted = AppLogger.getFormattedLogs()
        assertTrue(formatted.contains("Information event"))
        assertTrue(formatted.contains("Warning event"))
        assertTrue(formatted.contains("Error event"))
        assertTrue(formatted.contains("[TEST_TAG]"))
    }

    @Test
    fun `AppLogger logHttp captures POST payload and sanitizes sensitive password`() {
        AppLogger.logHttp(
            direction = "--> POST",
            url = "http://10.10.10.10/login.html",
            details = "Payload: username=test_user&password=my_secret_password&ok=Submit"
        )

        val formatted = AppLogger.getFormattedLogs()
        assertTrue(formatted.contains("--> POST http://10.10.10.10/login.html"))
        assertTrue(formatted.contains("username=test_user&password=[REDACTED]&ok=Submit"))
        assertTrue("Plaintext password should NOT appear in logs", !formatted.contains("my_secret_password"))
    }

    @Test
    fun `AppLogger clearLogs resets count and removes entries`() {
        AppLogger.i("TEST", "Entry 1")
        AppLogger.i("TEST", "Entry 2")
        assertEquals(2, AppLogger.logCount.value)

        AppLogger.clearLogs()
        assertEquals(0, AppLogger.logCount.value)
    }
}
