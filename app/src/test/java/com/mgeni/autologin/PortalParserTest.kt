package com.mgeni.autologin

import com.mgeni.autologin.data.PageFetchResult
import com.mgeni.autologin.data.PortalClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
