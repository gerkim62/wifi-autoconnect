package com.mgeni.autologin

import android.app.Application
import com.mgeni.autologin.data.AppLogger
import com.mgeni.autologin.data.BackgroundManager
import com.mgeni.autologin.data.NotificationHelper
import com.mgeni.autologin.data.PreferencesManager

class MgeniApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        NotificationHelper.createNotificationChannel(this)

        val prefs = PreferencesManager(this)
        if (prefs.hasCompletedOnboarding) {
            BackgroundManager.registerBackgroundNetworkCallback(this)
        }
    }
}
