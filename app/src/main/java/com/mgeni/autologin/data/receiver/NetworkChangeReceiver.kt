package com.mgeni.autologin.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.mgeni.autologin.data.AppLogger
import com.mgeni.autologin.data.BackgroundManager
import com.mgeni.autologin.data.PreferencesManager
import com.mgeni.autologin.data.worker.AutoLoginWorker

class NetworkChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        AppLogger.i("NET_RECEIVER", "NetworkChangeReceiver triggered with action: $action")

        val prefs = PreferencesManager(context)
        if (!prefs.enableBackgroundAutoLogin) {
            AppLogger.d("NET_RECEIVER", "Background auto-login disabled; ignoring event.")
            return
        }

        if (!prefs.hasSavedCredentials()) {
            AppLogger.d("NET_RECEIVER", "No saved credentials found; skipping background worker.")
            return
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        val network: Network? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK, Network::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK)
        } ?: cm.activeNetwork

        if (network != null) {
            val capabilities = cm.getNetworkCapabilities(network)
            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val isCaptive = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true
            val isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

            AppLogger.i("NET_RECEIVER", "Network state: isWifi=$isWifi, isCaptive=$isCaptive, isValidated=$isValidated")

            // Only enqueue worker if the Wi-Fi network is explicitly identified as a captive portal
            if (isWifi && isCaptive) {
                AppLogger.i("NET_RECEIVER", "Captive portal detected on Wi-Fi ($network). Enqueuing Expedited AutoLoginWorker...")
                val workRequest = OneTimeWorkRequestBuilder<AutoLoginWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag(AutoLoginWorker.TAG)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "AutoLoginUniqueWork",
                    ExistingWorkPolicy.KEEP,
                    workRequest
                )
            } else {
                AppLogger.d("NET_RECEIVER", "Network is not a captive portal (isWifi=$isWifi, isCaptive=$isCaptive). Skipping worker.")
            }
        }
    }
}
