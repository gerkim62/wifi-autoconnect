package com.mgeni.autologin

import com.mgeni.autologin.data.NetworkBoundDns
import com.mgeni.autologin.data.PageFetchResult
import com.mgeni.autologin.data.PortalClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class PortalParserTest {

    private val portalClient = PortalClient()

    @Test
    fun `parseLoginPage extracts au_pxytimetag and action url accurately`() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Guest Wi-Fi Login</title></head>
            <body>
                <form action="/login.html" method="post">
                    <input type="hidden" name="au_pxytimetag" value="1724256000_abcdef9876543210" />
                    <input type="text" name="username" />
                    <input type="password" name="password" />
                    <input type="submit" name="ok" value="Submit" />
                </form>
            </body>
            </html>
        """.trimIndent()

        val result = portalClient.parseLoginPage(sampleHtml, "http://10.10.10.10/login.html")

        assertTrue("Expected PageFetchResult.Success, got $result", result is PageFetchResult.Success)
        val success = result as PageFetchResult.Success
        assertEquals("1724256000_abcdef9876543210", success.timeTag)
        assertEquals("http://10.10.10.10/login.html", success.actionUrl)
    }

    @Test
    fun `parseLoginPage extracts redirect query parameter when present in base URL`() {
        val sampleHtml = """
            <html>
            <body>
                <form action="http://10.10.10.10/auth" method="post">
                    <input type="hidden" name="au_pxytimetag" value="token_xyz" />
                </form>
            </body>
            </html>
        """.trimIndent()

        val result = portalClient.parseLoginPage(
            sampleHtml,
            "http://10.10.10.10/login.html?redirect=https%3A%2F%2Fexample.com"
        )

        assertTrue(result is PageFetchResult.Success)
        val success = result as PageFetchResult.Success
        assertEquals("token_xyz", success.timeTag)
        assertEquals("https://example.com", success.redirectUrl)
    }

    @Test
    fun `parseLoginPage returns Error when au_pxytimetag is missing`() {
        val invalidHtml = """
            <html>
            <body>
                <form action="/login.html">
                    <input type="text" name="username" />
                </form>
            </body>
            </html>
        """.trimIndent()

        val result = portalClient.parseLoginPage(invalidHtml, "http://10.10.10.10/login.html")

        assertTrue("Expected PageFetchResult.Error when token is missing", result is PageFetchResult.Error)
        val error = result as PageFetchResult.Error
        assertEquals(
            "This network is not supported. Only Guest is supported. Please log in using your web browser, or contact the developer if you need support.",
            error.message
        )
    }

    @Test
    fun `parseLoginPage returns Error when html is empty`() {
        val result = portalClient.parseLoginPage("", "http://10.10.10.10/login.html")
        assertTrue(result is PageFetchResult.Error)
    }

    @Test
    fun `parseLoginPage uses switch_url query parameter for action URL when present`() {
        val sampleHtml = """
            <html>
            <body>
                <form action="/login.html" method="post">
                    <input type="hidden" name="au_pxytimetag" value="token_switch_123" />
                </form>
            </body>
            </html>
        """.trimIndent()

        val result = portalClient.parseLoginPage(
            sampleHtml,
            "http://1.1.1.1/login.html?switch_url=http%3A%2F%2F10.10.10.10%2Flogin.html"
        )

        assertTrue(result is PageFetchResult.Success)
        val success = result as PageFetchResult.Success
        assertEquals("http://10.10.10.10/login.html", success.actionUrl)
        assertEquals("token_switch_123", success.timeTag)
    }

    @Test
    fun `inspectAuthResponseHtml detects Cisco IOS Authentication Proxy Failed Page`() {
        val ciscoFailedHtml = """
            <html>
            <head><title>Authentication Proxy Failed Page</title></head>
            <body>
                <h1>Authentication Failed !</h1>
            </body>
            </html>
        """.trimIndent()

        val result = portalClient.inspectAuthResponseHtml(ciscoFailedHtml)
        assertTrue("Expected ExplicitFailure, got $result", result is com.mgeni.autologin.data.HtmlAuthResult.ExplicitFailure)
    }

    @Test
    fun `inspectAuthResponseHtml detects Cisco IOS Authentication Proxy Success Page`() {
        val ciscoSuccessHtml = """
            <html>
            <head><title>Authentication Proxy Success Page</title></head>
            <body>
                <h1>Authentication Successful !</h1>
            </body>
            </html>
        """.trimIndent()

        val result = portalClient.inspectAuthResponseHtml(ciscoSuccessHtml)
        assertTrue("Expected ExplicitSuccess, got $result", result is com.mgeni.autologin.data.HtmlAuthResult.ExplicitSuccess)
    }

    @Test
    fun `parseLoginPage returns AlreadyAuthenticated when Cisco success page is fetched directly`() {
        val ciscoSuccessHtml = """
            <html>
            <head><title>Authentication Proxy Success Page</title></head>
            <body>
                <h1>Authentication Successful !</h1>
                <table><tr><td>You can now use all regular services over this network</td></tr></table>
            </body>
            </html>
        """.trimIndent()

        val result = portalClient.parseLoginPage(ciscoSuccessHtml, "http://10.10.10.10/login.html")
        assertTrue("Expected AlreadyAuthenticated, got $result", result is PageFetchResult.AlreadyAuthenticated)
    }

    @Test
    fun `NetworkBoundDns resolves direct IP addresses without DNS lookup`() {
        val dns = NetworkBoundDns(network = null)
        val result = dns.lookup("10.10.10.10")
        assertEquals(1, result.size)
        assertEquals("10.10.10.10", result.first().hostAddress)
    }

    @Test
    fun `NetworkBoundDns provides fallback Anycast IPs for connectivity check domains when DNS fails`() {
        val dns = NetworkBoundDns(network = null)
        val connectivityIps = dns.lookup("connectivitycheck.gstatic.com")
        assertNotNull(connectivityIps)
        assertTrue("Expected fallback IPs for connectivitycheck.gstatic.com", connectivityIps.isNotEmpty())
        assertEquals("142.251.47.35", connectivityIps.first().hostAddress)

        val clients3Ips = dns.lookup("clients3.google.com")
        assertTrue(clients3Ips.isNotEmpty())
        assertEquals("192.178.54.14", clients3Ips.first().hostAddress)

        val cloudflareIps = dns.lookup("one.one.one.one")
        assertTrue(cloudflareIps.isNotEmpty())
        assertEquals("1.1.1.1", cloudflareIps.first().hostAddress)
    }

    @Test(expected = UnknownHostException::class)
    fun `NetworkBoundDns throws UnknownHostException for unknown domain without fallback`() {
        val dns = NetworkBoundDns(network = null)
        dns.lookup("some.unknown.invalid.nonexistent.domain.test")
    }
}
