package com.mgeni.autologin.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.mgeni.autologin.data.receiver.NetworkChangeReceiver

object BackgroundManager {

    private const val PENDING_INTENT_REQUEST_CODE = 2001
    const val ACTION_NETWORK_EVENT = "com.mgeni.autologin.ACTION_NETWORK_EVENT"

    fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NetworkChangeReceiver::class.java).apply {
            action = ACTION_NETWORK_EVENT
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, PENDING_INTENT_REQUEST_CODE, intent, flags)
    }

    fun registerBackgroundNetworkCallback(context: Context) {
        val prefs = PreferencesManager(context)
        if (!prefs.enableBackgroundAutoLogin) {
            AppLogger.d("BACKGROUND_MGR", "Background auto-login is disabled in preferences; skipping registration.")
            return
        }

        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm == null) {
                AppLogger.w("BACKGROUND_MGR", "ConnectivityManager unavailable.")
                return
            }

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            val pendingIntent = getPendingIntent(context)
            
            // First unregister existing to prevent duplicate requests limit (100 max per UID)
            try {
                cm.unregisterNetworkCallback(pendingIntent)
            } catch (_: Exception) {
                // Ignore if not previously registered
            }

            cm.registerNetworkCallback(request, pendingIntent)
            AppLogger.i("BACKGROUND_MGR", "Successfully registered background NetworkCallback with PendingIntent.")
        } catch (e: Exception) {
            AppLogger.e("BACKGROUND_MGR", "Failed to register background NetworkCallback: ${e.localizedMessage}", e)
        }
    }

    fun unregisterBackgroundNetworkCallback(context: Context) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val pendingIntent = getPendingIntent(context)
            cm.unregisterNetworkCallback(pendingIntent)
            AppLogger.i("BACKGROUND_MGR", "Unregistered background NetworkCallback PendingIntent.")
        } catch (e: Exception) {
            AppLogger.w("BACKGROUND_MGR", "Failed to unregister background NetworkCallback: ${e.localizedMessage}")
        }
    }
}
