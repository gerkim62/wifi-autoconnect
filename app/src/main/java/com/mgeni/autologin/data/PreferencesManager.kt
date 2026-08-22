package com.mgeni.autologin.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user credentials and portal preferences using Android SharedPreferences (plaintext).
 */
open class PreferencesManager(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val memoryStore = mutableMapOf<String, Any>()

    companion object {
        private const val PREFS_NAME = "mgeni_prefs"
        const val DEFAULT_PORTAL_URL = "http://10.10.10.10/login.html"
        const val DEFAULT_CONNECTIVITY_URL = "http://connectivitycheck.gstatic.com/generate_204"

        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_PORTAL_URL = "portal_url"
        private const val KEY_REMEMBER_ME = "remember_me"
        private const val KEY_SKIP_INITIAL_CHECK = "skip_initial_check"
    }

    var username: String
        get() = prefs?.getString(KEY_USERNAME, "") ?: (memoryStore[KEY_USERNAME] as? String ?: "")
        set(value) {
            prefs?.edit()?.putString(KEY_USERNAME, value)?.apply()
            memoryStore[KEY_USERNAME] = value
        }

    var password: String
        get() = prefs?.getString(KEY_PASSWORD, "") ?: (memoryStore[KEY_PASSWORD] as? String ?: "")
        set(value) {
            prefs?.edit()?.putString(KEY_PASSWORD, value)?.apply()
            memoryStore[KEY_PASSWORD] = value
        }

    var portalUrl: String
        get() = prefs?.getString(KEY_PORTAL_URL, DEFAULT_PORTAL_URL)
            ?: (memoryStore[KEY_PORTAL_URL] as? String ?: DEFAULT_PORTAL_URL)
        set(value) {
            val trimmed = value.trim()
            prefs?.edit()?.putString(KEY_PORTAL_URL, trimmed)?.apply()
            memoryStore[KEY_PORTAL_URL] = trimmed
        }

    var rememberMe: Boolean
        get() = prefs?.getBoolean(KEY_REMEMBER_ME, true) ?: (memoryStore[KEY_REMEMBER_ME] as? Boolean ?: true)
        set(value) {
            prefs?.edit()?.putBoolean(KEY_REMEMBER_ME, value)?.apply()
            memoryStore[KEY_REMEMBER_ME] = value
        }

    var skipInitialInternetCheck: Boolean
        get() = prefs?.getBoolean(KEY_SKIP_INITIAL_CHECK, false)
            ?: (memoryStore[KEY_SKIP_INITIAL_CHECK] as? Boolean ?: false)
        set(value) {
            prefs?.edit()?.putBoolean(KEY_SKIP_INITIAL_CHECK, value)?.apply()
            memoryStore[KEY_SKIP_INITIAL_CHECK] = value
        }

    fun hasSavedCredentials(): Boolean {
        return username.isNotBlank() && password.isNotBlank()
    }

    fun saveCredentials(user: String, pass: String, remember: Boolean) {
        if (prefs != null) {
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
        memoryStore[KEY_REMEMBER_ME] = remember
        if (remember) {
            memoryStore[KEY_USERNAME] = user.trim()
            memoryStore[KEY_PASSWORD] = pass
        } else {
            memoryStore.remove(KEY_USERNAME)
            memoryStore.remove(KEY_PASSWORD)
        }
    }

    fun clearSavedCredentials() {
        prefs?.edit()?.apply {
            remove(KEY_USERNAME)
            remove(KEY_PASSWORD)
            putBoolean(KEY_REMEMBER_ME, false)
            apply()
        }
        memoryStore.remove(KEY_USERNAME)
        memoryStore.remove(KEY_PASSWORD)
        memoryStore[KEY_REMEMBER_ME] = false
    }

    fun resetPortalUrl() {
        prefs?.edit()?.putString(KEY_PORTAL_URL, DEFAULT_PORTAL_URL)?.apply()
        memoryStore[KEY_PORTAL_URL] = DEFAULT_PORTAL_URL
    }
}
