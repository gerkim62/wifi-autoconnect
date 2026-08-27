package com.mgeni.autologin.data

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface ConnectivityResult {
    data object AlreadyConnected : ConnectivityResult
    data class CaptiveDetected(val portalRedirectUrl: String? = null) : ConnectivityResult
    data class Unreachable(val message: String) : ConnectivityResult
}

sealed interface PageFetchResult {
    data class Success(
        val timeTag: String,
        val actionUrl: String,
        val redirectUrl: String
    ) : PageFetchResult
    data class Error(val message: String) : PageFetchResult
}

sealed interface LoginSubmitResult {
    data object Success : LoginSubmitResult
    data class AuthFailed(val message: String) : LoginSubmitResult
    data class NetworkFailed(val message: String) : LoginSubmitResult
}

open class PortalClient(
    private var client: OkHttpClient = createDefaultOkHttpClient()
) {
    companion object {
        const val CONNECTIVITY_CHECK_URL = "http://connectivitycheck.gstatic.com/generate_204"

        fun createDefaultOkHttpClient(network: Network? = null): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .writeTimeout(12, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.SECONDS))
                .followRedirects(false)
                .followSslRedirects(false)

            if (network != null) {
                try {
                    builder.socketFactory(network.socketFactory)
                } catch (e: Exception) {
                    AppLogger.w("PORTAL_CLIENT", "Failed to bind socketFactory to network $network: ${e.localizedMessage}", e)
                }
            }

            return builder.build()
        }
    }

    /**
     * Binds OkHttpClient to the specified Wi-Fi Network interface to prevent mobile data routing conflicts.
     */
    open fun bindToNetwork(network: Network?) {
        client = createDefaultOkHttpClient(network)
    }

    /**
     * Checks internet connectivity using Google's generate_204 endpoint.
     */
    open suspend fun check204Connectivity(
        connectivityUrl: String = CONNECTIVITY_CHECK_URL
    ): ConnectivityResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val request = Request.Builder()
            .url(connectivityUrl)
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .build()

        AppLogger.i("CONNECTIVITY_CHECK", "--> GET $connectivityUrl")

        try {
            client.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - startTime
                val code = response.code
                val location = response.header("Location")
                AppLogger.i(
                    "CONNECTIVITY_CHECK",
                    "<-- $code ${response.message} (${elapsed}ms)\nHeaders: ${response.headers}\nLocation: $location"
                )

                val result = when (code) {
                    204 -> ConnectivityResult.AlreadyConnected
                    in 300..399 -> ConnectivityResult.CaptiveDetected(location)
                    200 -> {
                        // Some portals intercept 204 and return HTTP 200 with HTML login page
                        ConnectivityResult.CaptiveDetected(null)
                    }
                    else -> ConnectivityResult.CaptiveDetected(null)
                }
                AppLogger.i("CONNECTIVITY_CHECK", "Result: $result")
                result
            }
        } catch (e: IOException) {
            val elapsed = System.currentTimeMillis() - startTime
            AppLogger.w("CONNECTIVITY_CHECK", "<-- FAILED (${elapsed}ms): ${e.message}", e)
            ConnectivityResult.Unreachable(
                "Make sure you're connected to the Wi-Fi network."
            )
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            AppLogger.e("CONNECTIVITY_CHECK", "<-- ERROR (${elapsed}ms): ${e.localizedMessage}", e)
            ConnectivityResult.Unreachable(
                e.localizedMessage ?: "Unexpected network error occurred."
            )
        }
    }

    /**
     * Fetches the portal login HTML and extracts the required hidden tokens,
     * following any HTTP redirects up to 5 hops to reach the actual login form.
     */
    open suspend fun fetchLoginPage(portalUrl: String): PageFetchResult = withContext(Dispatchers.IO) {
        var currentUrl = portalUrl
        var redirectsFollowed = 0
        val maxRedirects = 5

        AppLogger.i("PORTAL_FETCH", "Starting login page fetch for: $portalUrl")

        while (redirectsFollowed < maxRedirects) {
            val request = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .build()

            val hopStart = System.currentTimeMillis()
            AppLogger.i("PORTAL_FETCH", "--> [Hop $redirectsFollowed] GET $currentUrl")

            var nextRedirectUrl: String? = null

            try {
                client.newCall(request).execute().use { response ->
                    val hopElapsed = System.currentTimeMillis() - hopStart
                    val code = response.code
                    AppLogger.i(
                        "PORTAL_FETCH",
                        "<-- [Hop $redirectsFollowed] $code ${response.message} (${hopElapsed}ms)\nHeaders: ${response.headers}"
                    )

                    if (response.isRedirect) {
                        val location = response.header("Location")
                        AppLogger.i("PORTAL_FETCH", "Redirect Location: $location")
                        if (!location.isNullOrBlank()) {
                            val resolvedUrl = currentUrl.toHttpUrlOrNull()?.resolve(location)?.toString() ?: location
                            nextRedirectUrl = resolvedUrl
                        }
                    } else {
                        if (!response.isSuccessful) {
                            val errorMsg = "HTTP $code: Couldn't reach the portal. Check connection to \"guest\" Wi-Fi."
                            AppLogger.w("PORTAL_FETCH", errorMsg)
                            return@withContext PageFetchResult.Error(
                                "Couldn't reach the portal. Check that you're connected to the \"guest\" Wi-Fi network, or check if the portal URL is correct."
                            )
                        }

                        val htmlBody = response.body?.string().orEmpty()
                        AppLogger.d("PORTAL_FETCH", "HTML Body received (${htmlBody.length} bytes):\n${htmlBody.take(2000)}")
                        return@withContext parseLoginPage(htmlBody, currentUrl)
                    }
                }
            } catch (e: IOException) {
                val hopElapsed = System.currentTimeMillis() - hopStart
                AppLogger.w("PORTAL_FETCH", "<-- FAILED (${hopElapsed}ms) on $currentUrl: ${e.message}", e)
                return@withContext PageFetchResult.Error(
                    "Couldn't reach the portal. Check that you're connected to the \"guest\" Wi-Fi network, or check if the portal URL is correct."
                )
            } catch (e: Exception) {
                val hopElapsed = System.currentTimeMillis() - hopStart
                AppLogger.e("PORTAL_FETCH", "<-- ERROR (${hopElapsed}ms) on $currentUrl: ${e.localizedMessage}", e)
                return@withContext PageFetchResult.Error(
                    e.localizedMessage ?: "Unexpected error connecting to the portal."
                )
            }

            if (nextRedirectUrl != null) {
                currentUrl = nextRedirectUrl!!
                redirectsFollowed++
            } else {
                break
            }
        }

        AppLogger.w("PORTAL_FETCH", "Exceeded max redirects ($maxRedirects) or could not load login page.")
        return@withContext PageFetchResult.Error("Could not load captive portal login page.")
    }

    /**
     * Parses the HTML of the portal page to extract au_pxytimetag and form action.
     */
    open fun parseLoginPage(html: String, baseUrl: String): PageFetchResult {
        if (html.isBlank()) {
            AppLogger.w("PORTAL_PARSER", "Portal returned blank HTML response.")
            return PageFetchResult.Error("The portal returned an empty response.")
        }

        val doc = Jsoup.parse(html, baseUrl)
        val timeTagInput = doc.selectFirst("input[name=au_pxytimetag]")

        val timeTag = timeTagInput?.attr("value")
        if (timeTag.isNullOrBlank()) {
            AppLogger.w("PORTAL_PARSER", "Missing au_pxytimetag input. Network unsupported or already authenticated.")
            return PageFetchResult.Error(
                "This network is not supported. Only Guest is supported. Please log in using your web browser, or contact the developer if you need support."
            )
        }

        // Action URL: Check switch_url query param first (used by Cisco/Aruba/Huawei captive portal redirects)
        val form = doc.selectFirst("form")
        val formAction = form?.attr("action")?.trim().orEmpty()
        val parsedBaseUrl = baseUrl.toHttpUrlOrNull()
        val querySwitchUrl = parsedBaseUrl?.queryParameter("switch_url")

        val resolvedActionUrl = when {
            !querySwitchUrl.isNullOrBlank() && (querySwitchUrl.startsWith("http://", ignoreCase = true) || querySwitchUrl.startsWith("https://", ignoreCase = true)) -> querySwitchUrl
            formAction.isBlank() -> baseUrl
            formAction.startsWith("http://", ignoreCase = true) || formAction.startsWith("https://", ignoreCase = true) -> formAction
            else -> {
                parsedBaseUrl?.resolve(formAction)?.toString() ?: baseUrl
            }
        }

        // Redirect URL extracted from URL query param `redirect=`, `redirect_url=`, `userurl=`, or hidden input
        var redirectUrl = ""
        val queryRedirect = parsedBaseUrl?.queryParameter("redirect")
            ?: parsedBaseUrl?.queryParameter("redirect_url")
            ?: parsedBaseUrl?.queryParameter("userurl")
        if (!queryRedirect.isNullOrBlank()) {
            redirectUrl = queryRedirect
        } else {
            val redirectInput = doc.selectFirst("input[name=redirect_url]") ?: doc.selectFirst("input[name=redirect]")
            if (redirectInput != null) {
                redirectUrl = redirectInput.attr("value")
            }
        }

        AppLogger.i("PORTAL_PARSER", "Parse success: timeTag=$timeTag, actionUrl=$resolvedActionUrl, redirectUrl=$redirectUrl")

        return PageFetchResult.Success(
            timeTag = timeTag,
            actionUrl = resolvedActionUrl,
            redirectUrl = redirectUrl
        )
    }

    /**
     * Submits credentials to the portal and performs exponential backoff verification checks
     * to ensure the router firewall has applied routing rules without premature false-failures.
     */
    open suspend fun submitLogin(
        actionUrl: String,
        username: String,
        password: String,
        timeTag: String,
        redirectUrl: String = "",
        connectivityUrl: String = CONNECTIVITY_CHECK_URL
    ): LoginSubmitResult = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .add("ok", "Submit")
            .add("au_pxytimetag", timeTag)
            .add("redirect_url", redirectUrl)
            .build()

        val postRequest = Request.Builder()
            .url(actionUrl)
            .post(formBody)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .build()

        val submitStart = System.currentTimeMillis()
        AppLogger.i(
            "PORTAL_SUBMIT",
            "--> POST $actionUrl\nPayload: username=$username, password=[REDACTED], au_pxytimetag=$timeTag, redirect_url=$redirectUrl, ok=Submit"
        )

        try {
            client.newCall(postRequest).execute().use { response ->
                val submitElapsed = System.currentTimeMillis() - submitStart
                val bodySnippet = try {
                    response.body?.string().orEmpty().take(2000)
                } catch (e: Exception) {
                    AppLogger.w("PORTAL_SUBMIT", "Failed to read response body string: ${e.localizedMessage}", e)
                    ""
                }
                AppLogger.i(
                    "PORTAL_SUBMIT",
                    "<-- POST $actionUrl response: ${response.code} ${response.message} (${submitElapsed}ms)\nHeaders: ${response.headers}\nBody snippet: $bodySnippet"
                )

                // Immediate Auth rejection on explicit 4xx error codes
                if (response.code == 401 || response.code == 403) {
                    AppLogger.w("PORTAL_SUBMIT", "Portal returned explicit HTTP ${response.code} auth rejection.")
                    return@withContext LoginSubmitResult.AuthFailed(
                        "Wrong username or password. Check your details and try again."
                    )
                }
            }
        } catch (e: Exception) {
            val submitElapsed = System.currentTimeMillis() - submitStart
            AppLogger.e("PORTAL_SUBMIT", "<-- POST FAILED (${submitElapsed}ms): ${e.localizedMessage}", e)
            return@withContext LoginSubmitResult.NetworkFailed(
                "Could not reach portal during submission. Please check connection and try again."
            )
        }

        AppLogger.i("PORTAL_SUBMIT", "Beginning exponential backoff connectivity verification...")
        // Snappy exponential backoff delays (150ms, 300ms, 600ms, 1200ms)
        val backoffDelays = longArrayOf(150L, 300L, 600L, 1200L)
        var lastVerifyResult: ConnectivityResult = ConnectivityResult.Unreachable("Verifying...")

        for ((index, backoffMs) in backoffDelays.withIndex()) {
            delay(backoffMs)
            AppLogger.i("PORTAL_SUBMIT", "Verification ping attempt #${index + 1} (after ${backoffMs}ms)...")
            lastVerifyResult = check204Connectivity(connectivityUrl)

            if (lastVerifyResult is ConnectivityResult.AlreadyConnected) {
                AppLogger.i("PORTAL_SUBMIT", "Login verified on attempt #${index + 1}: 204 returned! Internet active.")
                return@withContext LoginSubmitResult.Success
            }
        }

        // Evaluate outcome after full backoff window
        when (lastVerifyResult) {
            is ConnectivityResult.AlreadyConnected -> {
                LoginSubmitResult.Success
            }
            is ConnectivityResult.CaptiveDetected -> {
                AppLogger.w("PORTAL_SUBMIT", "Verification completed: Portal returned authentication rejection or remains captive.")
                LoginSubmitResult.AuthFailed(
                    "Wrong username or password. Check your details and try again."
                )
            }
            is ConnectivityResult.Unreachable -> {
                AppLogger.w("PORTAL_SUBMIT", "Verification completed: 204 Unreachable.")
                LoginSubmitResult.NetworkFailed(
                    "Portal submitted, but internet verification was unreachable. Please check your Wi-Fi connection."
                )
            }
        }
    }
}
