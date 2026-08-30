package com.mgeni.autologin.data

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages user credentials and portal preferences using Android SharedPreferences with thread-safe in-memory fallback.
 */
open class PreferencesManager(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val memoryStore = ConcurrentHashMap<String, Any>()

    companion object {
        private const val PREFS_NAME = "mgeni_prefs"
        const val DEFAULT_PORTAL_URL = "http://10.10.10.10/login.html"
        const val DEFAULT_CONNECTIVITY_URL = "http://connectivitycheck.gstatic.com/generate_204"

        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_PORTAL_URL = "portal_url"
        private const val KEY_REMEMBER_ME = "remember_me"
        private const val KEY_CHECK_INTERNET_ON_STARTUP = "check_internet_on_startup"
        private const val KEY_SKIP_INITIAL_CHECK = "skip_initial_check"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
        private const val KEY_ENABLE_BACKGROUND_NOTIFICATIONS = "enable_background_notifications"
        private const val KEY_LAST_BACKGROUND_LOGIN_TIME = "last_background_login_time"
        private const val KEY_RESPECT_PORTAL_RESPONSE = "respect_portal_response"
    }

    var hasCompletedOnboarding: Boolean
        get() = prefs?.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false)
            ?: (memoryStore[KEY_HAS_COMPLETED_ONBOARDING] as? Boolean ?: false)
        set(value) {
            val previous = hasCompletedOnboarding
            prefs?.edit()?.putBoolean(KEY_HAS_COMPLETED_ONBOARDING, value)?.apply()
            memoryStore[KEY_HAS_COMPLETED_ONBOARDING] = value
            if (previous != value) {
                AppLogger.i("PREFERENCES", "hasCompletedOnboarding set to $value")
            }
        }

    var username: String
        get() = prefs?.getString(KEY_USERNAME, "") ?: (memoryStore[KEY_USERNAME] as? String ?: "")
        set(value) {
            val trimmed = value.trim()
            prefs?.edit()?.putString(KEY_USERNAME, trimmed)?.apply()
            memoryStore[KEY_USERNAME] = trimmed
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
            val previous = portalUrl
            prefs?.edit()?.putString(KEY_PORTAL_URL, trimmed)?.apply()
            memoryStore[KEY_PORTAL_URL] = trimmed
            if (previous != trimmed) {
                AppLogger.i("PREFERENCES", "Portal URL changed: '$previous' -> '$trimmed'")
            }
        }

    var rememberMe: Boolean
        get() = prefs?.getBoolean(KEY_REMEMBER_ME, true) ?: (memoryStore[KEY_REMEMBER_ME] as? Boolean ?: true)
        set(value) {
            prefs?.edit()?.putBoolean(KEY_REMEMBER_ME, value)?.apply()
            memoryStore[KEY_REMEMBER_ME] = value
        }

    var checkInternetOnStartup: Boolean
        get() {
            if (prefs != null) {
                if (prefs.contains(KEY_CHECK_INTERNET_ON_STARTUP)) {
                    return prefs.getBoolean(KEY_CHECK_INTERNET_ON_STARTUP, true)
                }
                if (prefs.contains(KEY_SKIP_INITIAL_CHECK)) {
                    return !prefs.getBoolean(KEY_SKIP_INITIAL_CHECK, false)
                }
            }
            return (memoryStore[KEY_CHECK_INTERNET_ON_STARTUP] as? Boolean)
                ?: ((memoryStore[KEY_SKIP_INITIAL_CHECK] as? Boolean)?.let { !it } ?: true)
        }
        set(value) {
            val previous = checkInternetOnStartup
            prefs?.edit()?.putBoolean(KEY_CHECK_INTERNET_ON_STARTUP, value)?.apply()
            memoryStore[KEY_CHECK_INTERNET_ON_STARTUP] = value
            if (previous != value) {
                AppLogger.i("PREFERENCES", "checkInternetOnStartup changed: $previous -> $value")
            }
        }

    var respectPortalResponse: Boolean
        get() = prefs?.getBoolean(KEY_RESPECT_PORTAL_RESPONSE, true)
            ?: (memoryStore[KEY_RESPECT_PORTAL_RESPONSE] as? Boolean ?: true)
        set(value) {
            val previous = respectPortalResponse
            prefs?.edit()?.putBoolean(KEY_RESPECT_PORTAL_RESPONSE, value)?.apply()
            memoryStore[KEY_RESPECT_PORTAL_RESPONSE] = value
            if (previous != value) {
                AppLogger.i("PREFERENCES", "respectPortalResponse changed: $previous -> $value")
            }
        }

    var skipInitialInternetCheck: Boolean
        get() = !checkInternetOnStartup
        set(value) {
            checkInternetOnStartup = !value
        }

    var enableBackgroundNotifications: Boolean
        get() = prefs?.getBoolean(KEY_ENABLE_BACKGROUND_NOTIFICATIONS, false)
            ?: (memoryStore[KEY_ENABLE_BACKGROUND_NOTIFICATIONS] as? Boolean ?: false)
        set(value) {
            val previous = enableBackgroundNotifications
            prefs?.edit()?.putBoolean(KEY_ENABLE_BACKGROUND_NOTIFICATIONS, value)?.apply()
            memoryStore[KEY_ENABLE_BACKGROUND_NOTIFICATIONS] = value
            if (previous != value) {
                AppLogger.i("PREFERENCES", "enableBackgroundNotifications changed: $previous -> $value")
            }
        }

    var lastBackgroundLoginTime: Long
        get() = prefs?.getLong(KEY_LAST_BACKGROUND_LOGIN_TIME, 0L)
            ?: (memoryStore[KEY_LAST_BACKGROUND_LOGIN_TIME] as? Long ?: 0L)
        set(value) {
            prefs?.edit()?.putLong(KEY_LAST_BACKGROUND_LOGIN_TIME, value)?.apply()
            memoryStore[KEY_LAST_BACKGROUND_LOGIN_TIME] = value
        }

    fun hasSavedCredentials(): Boolean {
        return username.isNotBlank() && password.isNotBlank()
    }

    fun saveCredentials(user: String, pass: String, remember: Boolean) {
        val trimmedUser = user.trim()
        AppLogger.i("PREFERENCES", "Saving credentials: user=$trimmedUser, remember=$remember")
        if (prefs != null) {
            prefs.edit().apply {
                putBoolean(KEY_REMEMBER_ME, remember)
                if (remember) {
                    putString(KEY_USERNAME, trimmedUser)
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
            memoryStore[KEY_USERNAME] = trimmedUser
            memoryStore[KEY_PASSWORD] = pass
        } else {
            memoryStore.remove(KEY_USERNAME)
            memoryStore.remove(KEY_PASSWORD)
        }
    }

    fun clearSavedCredentials() {
        AppLogger.i("PREFERENCES", "Explicitly clearing saved credentials from device.")
        if (prefs != null) {
            prefs.edit().apply {
                remove(KEY_USERNAME)
                remove(KEY_PASSWORD)
                putBoolean(KEY_REMEMBER_ME, true)
                apply()
            }
        }
        memoryStore.remove(KEY_USERNAME)
        memoryStore.remove(KEY_PASSWORD)
        memoryStore[KEY_REMEMBER_ME] = true
    }

    fun resetPortalUrl() {
        val previous = portalUrl
        AppLogger.i("PREFERENCES", "Resetting portal URL to default: '$previous' -> '$DEFAULT_PORTAL_URL'")
        prefs?.edit()?.putString(KEY_PORTAL_URL, DEFAULT_PORTAL_URL)?.apply()
        memoryStore[KEY_PORTAL_URL] = DEFAULT_PORTAL_URL
    }
}
