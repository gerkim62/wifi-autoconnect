package com.mgeni.autologin.data

import kotlinx.coroutines.Dispatchers
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
    data class Failed(val message: String) : LoginSubmitResult
}

open class PortalClient(
    private val client: OkHttpClient = createDefaultOkHttpClient()
) {
    companion object {
        const val CONNECTIVITY_CHECK_URL = "http://connectivitycheck.gstatic.com/generate_204"

        fun createDefaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }
    }

    /**
     * Checks internet connectivity using Google's generate_204 endpoint.
     */
    open suspend fun check204Connectivity(
        connectivityUrl: String = CONNECTIVITY_CHECK_URL
    ): ConnectivityResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(connectivityUrl)
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    204 -> ConnectivityResult.AlreadyConnected
                    in 300..399 -> {
                        val redirectLocation = response.header("Location")
                        ConnectivityResult.CaptiveDetected(redirectLocation)
                    }
                    200 -> {
                        // Some portals intercept 204 and return HTTP 200 with HTML login page
                        ConnectivityResult.CaptiveDetected(null)
                    }
                    else -> ConnectivityResult.CaptiveDetected(null)
                }
            }
        } catch (e: IOException) {
            ConnectivityResult.Unreachable(
                "Make sure you're connected to the Wi-Fi network."
            )
        } catch (e: Exception) {
            ConnectivityResult.Unreachable(
                e.localizedMessage ?: "Unexpected network error occurred."
            )
        }
    }

    /**
     * Fetches the portal login HTML and extracts the required hidden tokens.
     */
    open suspend fun fetchLoginPage(portalUrl: String): PageFetchResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(portalUrl)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .header("Cache-Control", "no-cache")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code !in 300..399) {
                    return@withContext PageFetchResult.Error(
                        "Couldn't reach the portal. Check that you're connected to Wi-Fi."
                    )
                }

                val htmlBody = response.body?.string().orEmpty()
                parseLoginPage(htmlBody, portalUrl)
            }
        } catch (e: IOException) {
            PageFetchResult.Error(
                "Couldn't reach the portal. Check that you're connected to Wi-Fi."
            )
        } catch (e: Exception) {
            PageFetchResult.Error(
                e.localizedMessage ?: "Unexpected error connecting to the portal."
            )
        }
    }

    /**
     * Parses the HTML of the portal page to extract au_pxytimetag and form action.
     */
    open fun parseLoginPage(html: String, baseUrl: String): PageFetchResult {
        if (html.isBlank()) {
            return PageFetchResult.Error("The portal returned an empty response.")
        }

        val doc = Jsoup.parse(html, baseUrl)
        val timeTagInput = doc.selectFirst("input[name=au_pxytimetag]")

        val timeTag = timeTagInput?.attr("value")
        if (timeTag.isNullOrBlank()) {
            return PageFetchResult.Error(
                "This network is not supported. Only Guest is supported. Please log in using your web browser, or contact the developer if you need support."
            )
        }

        // Action URL
        val form = doc.selectFirst("form")
        val formAction = form?.attr("action")?.trim().orEmpty()
        val resolvedActionUrl = when {
            formAction.isBlank() -> baseUrl
            formAction.startsWith("http://", ignoreCase = true) || formAction.startsWith("https://", ignoreCase = true) -> formAction
            else -> {
                val parsedBase = baseUrl.toHttpUrlOrNull()
                parsedBase?.resolve(formAction)?.toString() ?: baseUrl
            }
        }

        // Redirect URL extracted from URL query param `redirect=` or hidden input
        var redirectUrl = ""
        val parsedBaseUrl = baseUrl.toHttpUrlOrNull()
        val queryRedirect = parsedBaseUrl?.queryParameter("redirect")
        if (!queryRedirect.isNullOrBlank()) {
            redirectUrl = queryRedirect
        } else {
            val redirectInput = doc.selectFirst("input[name=redirect_url]") ?: doc.selectFirst("input[name=redirect]")
            if (redirectInput != null) {
                redirectUrl = redirectInput.attr("value")
            }
        }

        return PageFetchResult.Success(
            timeTag = timeTag,
            actionUrl = resolvedActionUrl,
            redirectUrl = redirectUrl
        )
    }

    /**
     * Submits the credentials to the portal and verifies internet access via 204 check.
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

        try {
            // Execute the POST request
            client.newCall(postRequest).execute().use { _ ->
                // Per spec: Do not treat redirect from portal as success.
                // We must verify real internet access via the 204 check.
            }
        } catch (e: Exception) {
            return@withContext LoginSubmitResult.Failed(
                "Login failed. Network error during submission."
            )
        }

        // Post-submit verification check
        when (val verifyResult = check204Connectivity(connectivityUrl)) {
            is ConnectivityResult.AlreadyConnected -> LoginSubmitResult.Success
            is ConnectivityResult.CaptiveDetected -> LoginSubmitResult.Failed(
                "Login failed. Your username or password may be incorrect."
            )
            is ConnectivityResult.Unreachable -> LoginSubmitResult.Failed(
                "Login failed. Check that you're connected to Wi-Fi."
            )
        }
    }
}
