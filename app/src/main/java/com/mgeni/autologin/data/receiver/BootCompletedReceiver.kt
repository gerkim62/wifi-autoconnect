package com.mgeni.autologin.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mgeni.autologin.data.AppLogger
import com.mgeni.autologin.data.BackgroundManager

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        AppLogger.i("BOOT_RECEIVER", "BootCompletedReceiver triggered with action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            BackgroundManager.registerBackgroundNetworkCallback(context)
        }
    }
}
