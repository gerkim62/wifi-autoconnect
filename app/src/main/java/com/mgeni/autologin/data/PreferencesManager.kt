package com.mgeni.autologin.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user credentials and portal preferences using Android SharedPreferences (plaintext).
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "mgeni_prefs"
        const val DEFAULT_PORTAL_URL = "http://10.10.10.10/login.html"
        const val DEFAULT_CONNECTIVITY_URL = "http://connectivitycheck.gstatic.com/generate_204"

        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_PORTAL_URL = "portal_url"
        private const val KEY_REMEMBER_ME = "remember_me"
    }

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var portalUrl: String
        get() = prefs.getString(KEY_PORTAL_URL, DEFAULT_PORTAL_URL) ?: DEFAULT_PORTAL_URL
        set(value) = prefs.edit().putString(KEY_PORTAL_URL, value.trim()).apply()

    var rememberMe: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER_ME, true)
        set(value) = prefs.edit().putBoolean(KEY_REMEMBER_ME, value).apply()

    fun hasSavedCredentials(): Boolean {
        return rememberMe && username.isNotBlank() && password.isNotBlank()
    }

    fun saveCredentials(user: String, pass: String, remember: Boolean) {
        prefs.edit().apply {
            putBoolean(KEY_REMEMBER_ME, remember)
            if (remember) {
                putString(KEY_USERNAME, user.trim())
                putString(KEY_PASSWORD, pass)
            } else {
                remove(KEY_USERNAME)
                remove(KEY_PASSWORD)
            }
            apply()
        }
    }

    fun clearSavedCredentials() {
        prefs.edit().apply {
            remove(KEY_PASSWORD)
            // Note: Per spec, username is retained on login failure for user convenience
            apply()
        }
    }

    fun resetPortalUrl() {
        prefs.edit().putString(KEY_PORTAL_URL, DEFAULT_PORTAL_URL).apply()
    }
}
