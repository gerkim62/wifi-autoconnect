package com.mgeni.autologin.data.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mgeni.autologin.data.AppLogger
import com.mgeni.autologin.data.ConnectivityResult
import com.mgeni.autologin.data.LoginSubmitResult
import com.mgeni.autologin.data.NotificationHelper
import com.mgeni.autologin.data.PageFetchResult
import com.mgeni.autologin.data.PortalClient
import com.mgeni.autologin.data.PreferencesManager

class AutoLoginWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val TAG = "AutoLoginWorker"
        private const val MIN_LOGIN_INTERVAL_MILLIS = 10_000L
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val prefs = PreferencesManager(context)

        AppLogger.i("AUTO_LOGIN_WORKER", "Background auto-login worker triggered.")

        if (!prefs.hasVerifiedCredentials()) {
            AppLogger.d("AUTO_LOGIN_WORKER", "No verified saved credentials found for background login. Skipping.")
            return Result.success()
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - prefs.lastBackgroundLoginTime < MIN_LOGIN_INTERVAL_MILLIS) {
            AppLogger.d("AUTO_LOGIN_WORKER", "Rate limit cooldown active (${currentTime - prefs.lastBackgroundLoginTime}ms ago). Skipping.")
            return Result.success()
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            AppLogger.w("AUTO_LOGIN_WORKER", "ConnectivityManager is unavailable.")
            return Result.failure()
        }

        // Find active or available Wi-Fi network
        val wifiNetwork = cm.allNetworks.firstOrNull { net ->
            val caps = cm.getNetworkCapabilities(net) ?: return@firstOrNull false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
        } ?: cm.activeNetwork

        if (wifiNetwork == null) {
            AppLogger.w("AUTO_LOGIN_WORKER", "No active Wi-Fi network detected.")
            return Result.success()
        }

        val portalClient = PortalClient()
        portalClient.bindToNetwork(wifiNetwork)
        AppLogger.i("AUTO_LOGIN_WORKER", "Configured request-scoped Wi-Fi socket routing on network: $wifiNetwork")

        return try {
            val connectivityResult = portalClient.check204Connectivity()
            AppLogger.i("AUTO_LOGIN_WORKER", "Initial connectivity check: $connectivityResult")

            when (connectivityResult) {
                is ConnectivityResult.AlreadyConnected -> {
                    AppLogger.i("AUTO_LOGIN_WORKER", "Internet is already active on this network. No sign-in required.")
                    Result.success()
                }
                is ConnectivityResult.CaptiveDetected -> {
                    val redirectHint = connectivityResult.portalRedirectUrl
                    val configuredUrl = prefs.portalUrl.trim()
                    val isCustomUrlConfigured = configuredUrl.isNotBlank() && configuredUrl != PreferencesManager.DEFAULT_PORTAL_URL
                    val detectedGatewayIp = cm.getLinkProperties(wifiNetwork)?.routes
                        ?.firstOrNull { it.isDefaultRoute && it.gateway != null }?.gateway?.hostAddress

                    val targetUrl = when {
                        !redirectHint.isNullOrBlank() && redirectHint.startsWith("http") -> redirectHint
                        isCustomUrlConfigured -> configuredUrl
                        else -> detectedGatewayIp?.let { "http://$it/login.html" } ?: configuredUrl.ifBlank { PreferencesManager.DEFAULT_PORTAL_URL }
                    }
                    AppLogger.i("AUTO_LOGIN_WORKER", "Targeting login page URL: $targetUrl (redirectHint=$redirectHint, isCustomConfigured=$isCustomUrlConfigured, gatewayIp=$detectedGatewayIp)")
                    val pageResult = portalClient.fetchLoginPage(targetUrl)
                    
                    when (pageResult) {
                        is PageFetchResult.Success -> {
                            AppLogger.i("AUTO_LOGIN_WORKER", "Portal form parsed successfully. Submitting saved credentials.")
                            val submitResult = portalClient.submitLogin(
                                actionUrl = pageResult.actionUrl,
                                username = prefs.username,
                                password = prefs.password,
                                timeTag = pageResult.timeTag,
                                redirectUrl = pageResult.redirectUrl,
                                respectPortalResponse = prefs.respectPortalResponse
                            )

                            when (submitResult) {
                                is LoginSubmitResult.Success -> {
                                    prefs.lastBackgroundLoginTime = System.currentTimeMillis()
                                    prefs.markCredentialsVerified(true)
                                    AppLogger.i("AUTO_LOGIN_WORKER", "Background auto-login completed successfully!")
                                    if (prefs.enableBackgroundNotifications) {
                                        NotificationHelper.showLoginSuccessNotification(context)
                                    }
                                    Result.success()
                                }
                                is LoginSubmitResult.AuthFailed -> {
                                    prefs.markCredentialsVerified(false)
                                    AppLogger.w("AUTO_LOGIN_WORKER", "Authentication failed in background: ${submitResult.message}")
                                    if (prefs.enableBackgroundNotifications) {
                                        NotificationHelper.showLoginFailedNotification(context, submitResult.message)
                                    }
                                    Result.failure()
                                }
                                is LoginSubmitResult.NetworkFailed -> {
                                    AppLogger.w("AUTO_LOGIN_WORKER", "Network error during submit: ${submitResult.message}")
                                    Result.retry()
                                }
                            }
                        }
                        is PageFetchResult.AlreadyAuthenticated -> {
                            AppLogger.i("AUTO_LOGIN_WORKER", "Portal indicated session already authenticated.")
                            Result.success()
                        }
                        is PageFetchResult.Error -> {
                            AppLogger.w("AUTO_LOGIN_WORKER", "Failed to fetch login page: ${pageResult.message}")
                            if (prefs.enableBackgroundNotifications) {
                                NotificationHelper.showLoginFailedNotification(
                                    context,
                                    "Guest Wi-Fi Sign-In Needed: Unsupported portal format or sign-in required."
                                )
                            }
                            Result.failure()
                        }
                    }
                }
                is ConnectivityResult.Unreachable -> {
                    AppLogger.w("AUTO_LOGIN_WORKER", "Captive portal host is currently unreachable: ${connectivityResult.message}")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            AppLogger.e("AUTO_LOGIN_WORKER", "Exception during background auto-login: ${e.localizedMessage}", e)
            Result.failure()
        }
    }
}
