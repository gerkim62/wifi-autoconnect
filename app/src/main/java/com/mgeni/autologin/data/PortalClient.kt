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
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
    data object AlreadyAuthenticated : PageFetchResult
    data class Error(val message: String) : PageFetchResult
}

sealed interface HtmlAuthResult {
    data class ExplicitFailure(val reason: String = "Wrong username or password. Check your details and try again.") : HtmlAuthResult
    data object ExplicitSuccess : HtmlAuthResult
    data object Unknown : HtmlAuthResult
}

sealed interface LoginSubmitResult {
    data object Success : LoginSubmitResult
    data class AuthFailed(val message: String) : LoginSubmitResult
    data class NetworkFailed(val message: String) : LoginSubmitResult
}

/**
 * Resilient OkHttp DNS implementation that binds to a specific Wi-Fi [Network] interface
 * and provides instant hardcoded Anycast IPv4 fallbacks for standard connectivity endpoints.
 * Completely eliminates DNS stalls and timeouts (e.g. on Samsung devices with Private DNS enabled).
 */
open class NetworkBoundDns(
    private val network: Network? = null,
    private val fallbackIps: Map<String, List<InetAddress>> = DEFAULT_CONNECTIVITY_FALLBACK_IPS
) : okhttp3.Dns {

    companion object {
        val DEFAULT_CONNECTIVITY_FALLBACK_IPS: Map<String, List<InetAddress>> by lazy {
            mapOf(
                "connectivitycheck.gstatic.com" to listOf(
                    InetAddress.getByName("142.251.47.35"),
                    InetAddress.getByName("142.250.190.46")
                ),
                "clients3.google.com" to listOf(
                    InetAddress.getByName("192.178.54.14"),
                    InetAddress.getByName("172.217.16.206")
                ),
                "www.google.com" to listOf(
                    InetAddress.getByName("142.251.153.119"),
                    InetAddress.getByName("142.251.154.119")
                ),
                "captive.apple.com" to listOf(
                    InetAddress.getByName("17.253.111.203"),
                    InetAddress.getByName("17.253.111.201")
                ),
                "one.one.one.one" to listOf(
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("1.0.0.1")
                )
            )
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val cleanHost = hostname.trim().lowercase()
        val startTime = System.currentTimeMillis()

        // 1. If it's already an IP address, return it directly
        if (cleanHost.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$""")) || cleanHost.contains(":")) {
            return try {
                listOf(InetAddress.getByName(cleanHost))
            } catch (e: Exception) {
                emptyList()
            }
        }

        // 2. Try network-specific resolution if network interface is bound
        if (network != null) {
            try {
                val resolved = network.getAllByName(cleanHost).toList()
                if (resolved.isNotEmpty()) {
                    val elapsed = System.currentTimeMillis() - startTime
                    AppLogger.d("DNS", "Network-bound lookup for $cleanHost succeeded (${elapsed}ms): ${resolved.map { it.hostAddress }}")
                    return resolved
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                AppLogger.w("DNS", "Network-bound lookup for $cleanHost failed (${elapsed}ms): ${e.message}")
            }
        } else {
            // 3. Try default system DNS resolution
            try {
                val resolved = okhttp3.Dns.SYSTEM.lookup(cleanHost)
                if (resolved.isNotEmpty()) {
                    val elapsed = System.currentTimeMillis() - startTime
                    AppLogger.d("DNS", "System DNS lookup for $cleanHost succeeded (${elapsed}ms): ${resolved.map { it.hostAddress }}")
                    return resolved
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                AppLogger.w("DNS", "System DNS lookup for $cleanHost failed (${elapsed}ms): ${e.message}")
            }
        }

        // 4. Fallback to hardcoded Anycast IPs for connectivity check domains
        fallbackIps[cleanHost]?.let { fallbacks ->
            val elapsed = System.currentTimeMillis() - startTime
            AppLogger.i("DNS", "Bypassing DNS for $cleanHost (${elapsed}ms) -> Using hardcoded Anycast fallback IP: ${fallbacks.first().hostAddress}")
            return fallbacks
        }

        throw UnknownHostException("Unable to resolve host \"$cleanHost\": DNS lookup failed and no fallback available.")
    }
}

open class PortalClient(
    private var client: OkHttpClient = createDefaultOkHttpClient()
) {
    private var currentNetwork: Network? = null

    companion object {
        const val CONNECTIVITY_CHECK_URL = "http://connectivitycheck.gstatic.com/generate_204"
        const val FALLBACK_CONNECTIVITY_URL = "http://clients3.google.com/generate_204"

        private val ALLOWED_PROBE_HOSTS = setOf(
            "connectivitycheck.gstatic.com",
            "clients3.google.com",
            "captive.apple.com"
        )

        /**
         * Enforces runtime security: Unencrypted HTTP traffic is strictly permitted ONLY to
         * verified captive probe endpoints or RFC 1918 / link-local / loopback private LAN addresses.
         * Arbitrary cleartext HTTP across the public internet is rejected to prevent credential leaks.
         */
        fun isPermittedCleartextDestination(url: String): Boolean {
            val parsed = url.toHttpUrlOrNull() ?: return false
            if (parsed.scheme.equals("https", ignoreCase = true)) return true

            val host = parsed.host.lowercase()
            if (ALLOWED_PROBE_HOSTS.contains(host)) return true

            return try {
                val inetAddress = InetAddress.getByName(host)
                inetAddress.isSiteLocalAddress ||       // 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16
                        inetAddress.isLinkLocalAddress ||  // 169.254.0.0/16
                        inetAddress.isLoopbackAddress ||   // 127.0.0.1
                        host == "1.1.1.1" || host == "8.8.8.8"
            } catch (_: Exception) {
                // Suffix check for unresolvable mDNS / local LAN hostnames
                host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home")
            }
        }

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
                .dns(NetworkBoundDns(network))

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
     * Inspects the HTML response body returned by the captive portal POST submission
     * to identify explicit Cisco IOS Auth Proxy (and standard portal) success or failure indicators.
     */
    open fun inspectAuthResponseHtml(html: String): HtmlAuthResult {
        if (html.isBlank()) return HtmlAuthResult.Unknown
        val lowerHtml = html.lowercase()

        // 1. Cisco-specific templates first
        val isCiscoFailure = lowerHtml.contains("authentication proxy failed") ||
                (lowerHtml.contains("<title>authentication proxy failed page</title>") ||
                        lowerHtml.contains("authentication failed !"))
        if (isCiscoFailure) {
            return HtmlAuthResult.ExplicitFailure()
        }

        val isCiscoSuccess = lowerHtml.contains("authentication proxy success") ||
                lowerHtml.contains("authentication successful !") ||
                lowerHtml.contains("you can now use all regular services over this network")
        if (isCiscoSuccess) {
            return HtmlAuthResult.ExplicitSuccess
        }

        // 2. Generic portal templates fallback
        val isGenericFailure = lowerHtml.contains("invalid username or password") ||
                lowerHtml.contains("login failed") ||
                lowerHtml.contains("credentials rejected")
        if (isGenericFailure) {
            return HtmlAuthResult.ExplicitFailure()
        }

        val isGenericSuccess = lowerHtml.contains("login successful") ||
                lowerHtml.contains("connection established") ||
                lowerHtml.contains("you are now logged in") ||
                lowerHtml.contains("logged in successfully")
        if (isGenericSuccess) {
            return HtmlAuthResult.ExplicitSuccess
        }

        return HtmlAuthResult.Unknown
    }

    /**
     * Binds OkHttpClient to the specified Wi-Fi Network interface to prevent mobile data routing conflicts.
     */
    open fun bindToNetwork(network: Network?) {
        currentNetwork = network
        client = createDefaultOkHttpClient(network)
    }

    /**
     * Probes direct TCP IP connectivity without DNS dependency (e.g. Cloudflare 1.1.1.1 or Google 8.8.8.8).
     */
    open fun testDirectIpSocket(
        network: Network? = currentNetwork,
        host: String = "1.1.1.1",
        port: Int = 80,
        timeoutMs: Int = 1500
    ): Boolean {
        val startTime = System.currentTimeMillis()
        return try {
            val socket = network?.socketFactory?.createSocket() ?: Socket()
            socket.use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                val elapsed = System.currentTimeMillis() - startTime
                AppLogger.i("CONNECTIVITY_PROBE", "Direct IP socket connect to $host:$port succeeded (${elapsed}ms). Unrestricted IP routing active.")
                true
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            AppLogger.d("CONNECTIVITY_PROBE", "Direct IP socket connect to $host:$port failed (${elapsed}ms): ${e.message}")
            false
        }
    }

    /**
     * Checks internet connectivity using generate_204 endpoint with multi-layered DNS bypass and direct IP probe fallback.
     */
    open suspend fun check204Connectivity(
        connectivityUrl: String = CONNECTIVITY_CHECK_URL
    ): ConnectivityResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val result = executeSingle204Check(connectivityUrl, startTime)

        if (result is ConnectivityResult.AlreadyConnected) {
            return@withContext result
        }

        // If primary check failed due to network / DNS unreachable, attempt direct IP probe and fallback URL
        if (result is ConnectivityResult.Unreachable) {
            val ipDirectConnected = testDirectIpSocket(currentNetwork, "1.1.1.1", 80, 1500) ||
                    testDirectIpSocket(currentNetwork, "8.8.8.8", 53, 1500)
            if (ipDirectConnected) {
                AppLogger.i("CONNECTIVITY_CHECK", "Direct IP probe succeeded. Internet routing active despite DNS/204 glitch.")
                return@withContext ConnectivityResult.AlreadyConnected
            }

            if (connectivityUrl == CONNECTIVITY_CHECK_URL) {
                AppLogger.i("CONNECTIVITY_CHECK", "Retrying with fallback connectivity endpoint: $FALLBACK_CONNECTIVITY_URL")
                val fallbackStart = System.currentTimeMillis()
                val fallbackResult = executeSingle204Check(FALLBACK_CONNECTIVITY_URL, fallbackStart)
                if (fallbackResult !is ConnectivityResult.Unreachable) {
                    return@withContext fallbackResult
                }
            }
        }

        result
    }

    private fun executeSingle204Check(url: String, startTime: Long): ConnectivityResult {
        val request = Request.Builder()
            .url(url)
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .build()

        AppLogger.i("CONNECTIVITY_CHECK", "--> GET $url")

        return try {
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
                        // Some portals intercept 204 and return HTTP 200 with HTML login page or Location header
                        ConnectivityResult.CaptiveDetected(location)
                    }
                    else -> ConnectivityResult.CaptiveDetected(location)
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
            if (!isPermittedCleartextDestination(currentUrl)) {
                AppLogger.w("PORTAL_FETCH", "Blocked cleartext HTTP request to non-private/non-probe domain: $currentUrl")
                return@withContext PageFetchResult.Error("Cleartext HTTP traffic is only permitted to local private gateway addresses.")
            }

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
                            val errorMsg = "HTTP $code: Couldn't reach the login page. Check connection to \"guest\" Wi-Fi."
                            AppLogger.w("PORTAL_FETCH", errorMsg)
                            return@withContext PageFetchResult.Error(
                                "Couldn't reach the login page. Make sure you're connected to the \"guest\" Wi-Fi network, then try again."
                            )
                        }

                        val htmlBody = response.body?.string().orEmpty()
                        val htmlSummary = AppLogger.extractHtmlSummary(htmlBody)
                        AppLogger.d("PORTAL_FETCH", "HTML response ($htmlSummary, ${htmlBody.length} bytes)")
                        return@withContext parseLoginPage(htmlBody, currentUrl)
                    }
                }
            } catch (e: EOFException) {
                val hopElapsed = System.currentTimeMillis() - hopStart
                AppLogger.w("PORTAL_FETCH", "<-- FAILED (${hopElapsed}ms) on $currentUrl: Gateway closed connection (EOFException). Verifying if already authenticated.", e)

                val connectivity = check204Connectivity()
                if (connectivity is ConnectivityResult.AlreadyConnected) {
                    AppLogger.i("PORTAL_FETCH", "Portal dropped connection because client is already authenticated! Connectivity confirmed.")
                    return@withContext PageFetchResult.AlreadyAuthenticated
                }

                return@withContext PageFetchResult.Error(
                    "Connection closed. If you are already connected, tap Refresh to check your internet."
                )
            } catch (e: SocketTimeoutException) {
                val hopElapsed = System.currentTimeMillis() - hopStart
                AppLogger.w("PORTAL_FETCH", "<-- FAILED (${hopElapsed}ms) on $currentUrl: Connection timed out. Target host is not responding.", e)
                return@withContext PageFetchResult.Error(
                    "Connection timed out. Make sure you're connected to the \"guest\" Wi-Fi network, then try again."
                )
            } catch (e: ConnectException) {
                val hopElapsed = System.currentTimeMillis() - hopStart
                AppLogger.w("PORTAL_FETCH", "<-- FAILED (${hopElapsed}ms) on $currentUrl: Connection refused or host unreachable: ${e.message}", e)
                return@withContext PageFetchResult.Error(
                    "Could not connect. Make sure you're connected to the \"guest\" Wi-Fi network, then try again."
                )
            } catch (e: IOException) {
                val hopElapsed = System.currentTimeMillis() - hopStart
                val isEof = e.message?.contains("unexpected end of stream", ignoreCase = true) == true ||
                        e.message?.contains("Connection reset", ignoreCase = true) == true
                AppLogger.w("PORTAL_FETCH", "<-- FAILED (${hopElapsed}ms) on $currentUrl: ${e.message} (isEofReset=$isEof)", e)

                if (isEof) {
                    val connectivity = check204Connectivity()
                    if (connectivity is ConnectivityResult.AlreadyConnected) {
                        AppLogger.i("PORTAL_FETCH", "Portal dropped connection because client is already authenticated! Connectivity confirmed.")
                        return@withContext PageFetchResult.AlreadyAuthenticated
                    }

                    return@withContext PageFetchResult.Error(
                        "Connection closed. If you are already connected, tap Refresh to check your internet."
                    )
                }

                return@withContext PageFetchResult.Error(
                    "Couldn't reach the login page. Make sure you're connected to the \"guest\" Wi-Fi network, then try again."
                )
            } catch (e: Exception) {
                val hopElapsed = System.currentTimeMillis() - hopStart
                AppLogger.e("PORTAL_FETCH", "<-- ERROR (${hopElapsed}ms) on $currentUrl: ${e.localizedMessage}", e)
                return@withContext PageFetchResult.Error(
                    e.localizedMessage ?: "Unexpected connection error. Please try again."
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
        return@withContext PageFetchResult.Error("Could not load the login page. Please try again.")
    }

    /**
     * Parses the HTML of the portal page to extract au_pxytimetag and form action.
     */
    open fun parseLoginPage(html: String, baseUrl: String): PageFetchResult {
        if (html.isBlank()) {
            AppLogger.w("PORTAL_PARSER", "Portal returned blank HTML response.")
            return PageFetchResult.Error("Received an empty response. Please try again.")
        }

        val doc = Jsoup.parse(html, baseUrl)
        val timeTagInput = doc.selectFirst("input[name=au_pxytimetag]")

        val timeTag = timeTagInput?.attr("value")
        if (timeTag.isNullOrBlank()) {
            val htmlAuth = inspectAuthResponseHtml(html)
            if (htmlAuth is HtmlAuthResult.ExplicitSuccess) {
                AppLogger.i("PORTAL_PARSER", "Portal returned Success Page directly during fetch. User is already authenticated!")
                return PageFetchResult.AlreadyAuthenticated
            }
            if (htmlAuth is HtmlAuthResult.ExplicitFailure) {
                AppLogger.w("PORTAL_PARSER", "Portal returned Failure Page directly during fetch: ${htmlAuth.reason}")
                return PageFetchResult.Error(htmlAuth.reason)
            }
            val pageTitle = doc.title().trim()
            AppLogger.w("PORTAL_PARSER", "Missing au_pxytimetag input in form (Page Title: \"$pageTitle\"). Captive portal template is unsupported by WifiAuto.")
            return PageFetchResult.Error(
                "Only the \"guest\" Wi-Fi is supported today. Make sure you're connected to \"guest\", or contact the developer if this is unexpected."
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
     * Submits credentials to the portal, inspects the response HTML for Cisco IOS Auth Proxy
     * success/failure indicators, and runs connectivity verification checks.
     */
    open suspend fun submitLogin(
        actionUrl: String,
        username: String,
        password: String,
        timeTag: String,
        redirectUrl: String = "",
        connectivityUrl: String = CONNECTIVITY_CHECK_URL,
        respectPortalResponse: Boolean = true,
        onStatusUpdate: ((status: String, detail: String?) -> Unit)? = null
    ): LoginSubmitResult = withContext(Dispatchers.IO) {
        if (!isPermittedCleartextDestination(actionUrl)) {
            AppLogger.w("PORTAL_SUBMIT", "Blocked cleartext credential submission to non-private domain: $actionUrl")
            return@withContext LoginSubmitResult.AuthFailed(
                "Blocked cleartext transmission: Target server is not a recognized local private gateway."
            )
        }

        onStatusUpdate?.invoke("Signing in…", "Authenticating…")

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
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .build()

        val submitStart = System.currentTimeMillis()
        AppLogger.i(
            "PORTAL_SUBMIT",
            "--> POST $actionUrl\nPayload: username=$username, password=[REDACTED], au_pxytimetag=$timeTag, redirect_url=$redirectUrl, ok=Submit"
        )

        var bodySnippet = ""
        try {
            client.newCall(postRequest).execute().use { response ->
                val submitElapsed = System.currentTimeMillis() - submitStart
                bodySnippet = try {
                    response.body?.string().orEmpty().take(4000)
                } catch (e: Exception) {
                    AppLogger.w("PORTAL_SUBMIT", "Failed to read response body string: ${e.localizedMessage}", e)
                    ""
                }
                val htmlSummary = AppLogger.extractHtmlSummary(bodySnippet)
                AppLogger.i(
                    "PORTAL_SUBMIT",
                    "<-- POST $actionUrl response: ${response.code} ${response.message} (${submitElapsed}ms)\nHeaders: ${response.headers}\nHTML Summary: $htmlSummary"
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
                "Could not complete sign-in. Please check connection and try again."
            )
        }

        // Inspect HTML response body for Cisco IOS Auth Proxy (and standard) indicators
        val htmlAuth = inspectAuthResponseHtml(bodySnippet)
        AppLogger.i("PORTAL_SUBMIT", "HTML Auth Inspection Result: $htmlAuth")

        when (htmlAuth) {
            is HtmlAuthResult.ExplicitFailure -> {
                if (respectPortalResponse) {
                    AppLogger.i("PORTAL_SUBMIT", "Portal HTML reported explicit rejection: ${htmlAuth.reason}. Declaring AuthFailed immediately (respectPortalResponse=true).")
                    return@withContext LoginSubmitResult.AuthFailed(htmlAuth.reason)
                }

                AppLogger.w("PORTAL_SUBMIT", "Portal HTML reported explicit rejection: ${htmlAuth.reason}. Running 204 connectivity check to verify (respectPortalResponse=false)...")
                onStatusUpdate?.invoke("Authentication Failed", "Verifying credentials…")

                delay(150L)
                val verifyResult = check204Connectivity(connectivityUrl)
                if (verifyResult is ConnectivityResult.AlreadyConnected) {
                    AppLogger.i("PORTAL_SUBMIT", "Unexpected: 204 succeeded despite failure HTML. Internet is active.")
                    return@withContext LoginSubmitResult.Success
                }

                AppLogger.w("PORTAL_SUBMIT", "Rejection confirmed by connectivity check. Returning AuthFailed.")
                return@withContext LoginSubmitResult.AuthFailed(htmlAuth.reason)
            }

            is HtmlAuthResult.ExplicitSuccess -> {
                AppLogger.i("PORTAL_SUBMIT", "Portal HTML reported explicit success: Authentication Successful! Credentials confirmed by gateway.")

                if (respectPortalResponse) {
                    AppLogger.i("PORTAL_SUBMIT", "Declaring Success immediately without verification delay (respectPortalResponse=true).")
                    return@withContext LoginSubmitResult.Success
                }

                onStatusUpdate?.invoke("Connected", "Verifying internet connection…")

                // Run quick non-fatal verification pings
                val backoffDelays = longArrayOf(150L, 300L, 600L)
                for ((index, backoffMs) in backoffDelays.withIndex()) {
                    delay(backoffMs)
                    AppLogger.i("PORTAL_SUBMIT", "Success verification ping #${index + 1} (after ${backoffMs}ms)...")
                    val verifyResult = check204Connectivity(connectivityUrl)
                    if (verifyResult is ConnectivityResult.AlreadyConnected) {
                        AppLogger.i("PORTAL_SUBMIT", "Login verified on attempt #${index + 1}: 204/IP returned OK! Internet active.")
                        break
                    }
                }

                // Explicit gateway success is authoritative: return Success!
                return@withContext LoginSubmitResult.Success
            }

            is HtmlAuthResult.Unknown -> {
                AppLogger.i("PORTAL_SUBMIT", "HTML body did not match explicit auth templates. Beginning exponential backoff connectivity verification...")
                onStatusUpdate?.invoke("Verifying Access", "Checking if internet access is active…")

                // Exponential backoff delays (150ms, 300ms, 600ms, 1200ms)
                val backoffDelays = longArrayOf(150L, 300L, 600L, 1200L)
                var lastVerifyResult: ConnectivityResult = ConnectivityResult.Unreachable("Verifying...")

                for ((index, backoffMs) in backoffDelays.withIndex()) {
                    delay(backoffMs)
                    onStatusUpdate?.invoke("Verifying Access", "Confirming connection (attempt ${index + 1} of ${backoffDelays.size})…")
                    AppLogger.i("PORTAL_SUBMIT", "Verification ping attempt #${index + 1} (after ${backoffMs}ms)...")
                    lastVerifyResult = check204Connectivity(connectivityUrl)

                    if (lastVerifyResult is ConnectivityResult.AlreadyConnected) {
                        AppLogger.i("PORTAL_SUBMIT", "Login verified on attempt #${index + 1}: 204 returned! Internet active.")
                        return@withContext LoginSubmitResult.Success
                    }
                }

                // Evaluate outcome after full backoff window
                return@withContext when (lastVerifyResult) {
                    is ConnectivityResult.AlreadyConnected -> {
                        AppLogger.i("PORTAL_SUBMIT", "Post-submission verification confirmed internet access active.")
                        LoginSubmitResult.Success
                    }
                    is ConnectivityResult.CaptiveDetected -> {
                        AppLogger.w("PORTAL_SUBMIT", "Post-submission verification: Gateway continues to intercept HTTP traffic (CaptiveDetected: redirect=${lastVerifyResult.portalRedirectUrl}). Credentials were not accepted or login was not completed.")
                        LoginSubmitResult.AuthFailed(
                            "Wrong username or password. Check your details and try again."
                        )
                    }
                    is ConnectivityResult.Unreachable -> {
                        AppLogger.w("PORTAL_SUBMIT", "Post-submission verification: 204 Unreachable (${lastVerifyResult.message}).")
                        LoginSubmitResult.NetworkFailed(
                            "Signed in, but internet verification was unreachable. Please check your Wi-Fi connection."
                        )
                    }
                }
            }
        }
    }
}
