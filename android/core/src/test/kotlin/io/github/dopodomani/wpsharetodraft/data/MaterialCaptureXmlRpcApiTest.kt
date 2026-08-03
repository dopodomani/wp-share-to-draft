package io.github.dopodomani.wpsharetodraft.data

import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.xml.sax.InputSource
import java.io.StringReader
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Round-trips [MaterialCaptureXmlRpcApi.createDraft] against an in-process fake HTTP server:
 * confirms the request body it sends is well-formed XML carrying the right method/params, and
 * that both success and fault response shapes parse correctly -- including malformed/non-XML
 * bodies mapping to a [XmlRpcResult.Fault] rather than crashing. See
 * docs/phase3c-android-xmlrpc-design.md.
 */
class MaterialCaptureXmlRpcApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: MaterialCaptureXmlRpcApi

    private val item =
        CaptureItem(
            title = "Title",
            url = "https://example.com",
            sharedText = "shared text",
            memo = null,
            source = "chrome_share",
            sharedAt = Instant.parse("2026-07-28T09:15:00Z"),
        )

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = MaterialCaptureXmlRpcApi(OkHttpClient())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sends a well-formed methodCall with the material_capture createDraft method name`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(successResponse()))

            api.createDraft(server.url("/xmlrpc.php").toString(), "user", "app-password", item)

            val requestBody = server.takeRequest().body.readUtf8()
            val document =
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(requestBody)))
            assertEquals("methodCall", document.documentElement.tagName)
            assertEquals(
                "material_capture.createDraft",
                document.getElementsByTagName("methodName").item(0).textContent,
            )
            assertEquals(8, document.getElementsByTagName("param").length)
        }

    @Test
    fun `a null field is sent as nil rather than the literal string null`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(successResponse()))

            api.createDraft(server.url("/xmlrpc.php").toString(), "user", "app-password", item)

            val requestBody = server.takeRequest().body.readUtf8()
            assertTrue(requestBody.contains("<nil/>"))
        }

    @Test
    fun `parses a success methodResponse into a DraftResult`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(successResponse()))

            val result = api.createDraft(server.url("/xmlrpc.php").toString(), "user", "app-password", item)

            val success = result as XmlRpcResult.Success
            assertEquals(1L, success.result.postId)
            assertEquals("draft", success.result.status)
            assertEquals("素材候補", success.result.category)
        }

    @Test
    fun `parses a fault methodResponse into faultCode and faultString`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(faultResponse()))

            val result = api.createDraft(server.url("/xmlrpc.php").toString(), "user", "app-password", item)

            val fault = result as XmlRpcResult.Fault
            assertEquals(409, fault.faultCode)
            assertEquals("category_unavailable", fault.faultString)
        }

    @Test
    fun `a malformed non-XML body maps to a Fault instead of crashing`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("not xml at all"))

            val result = api.createDraft(server.url("/xmlrpc.php").toString(), "user", "app-password", item)

            assertTrue(result is XmlRpcResult.Fault)
        }

    /**
     * Defense in depth (not a fix for an observed attack): the site is the user's own
     * HTTPS-configured WordPress host, but a compromised or misbehaving server is exactly the
     * scenario worth guarding against. A plain DocumentBuilderFactory resolves DOCTYPE
     * declarations and external entities by default; confirms the response parser rejects any
     * DOCTYPE outright (mapping to a Fault) rather than expanding it.
     */
    @Test
    fun `a response containing a DOCTYPE with an external entity is rejected, not expanded`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(xxePayloadResponse()))

            val result = api.createDraft(server.url("/xmlrpc.php").toString(), "user", "app-password", item)

            assertTrue(result is XmlRpcResult.Fault)
        }

    private fun xxePayloadResponse(): String =
        """
        <?xml version="1.0"?>
        <!DOCTYPE methodResponse [
          <!ENTITY xxe SYSTEM "file:///etc/passwd">
        ]>
        <methodResponse><params><param><value><struct>
        <member><name>post_id</name><value><int>1</int></value></member>
        <member><name>status</name><value><string>&xxe;</string></value></member>
        <member><name>title</name><value><string>Title</string></value></member>
        <member><name>category</name><value><string>素材候補</string></value></member>
        <member><name>created_at</name><value><string>2026-07-28T09:15:03Z</string></value></member>
        </struct></value></param></params></methodResponse>
        """.trimIndent()

    private fun successResponse(): String =
        """
        <?xml version="1.0"?><methodResponse><params><param><value><struct>
        <member><name>post_id</name><value><int>1</int></value></member>
        <member><name>status</name><value><string>draft</string></value></member>
        <member><name>title</name><value><string>[INBOX] Title</string></value></member>
        <member><name>edit_url</name><value><string>https://x/edit</string></value></member>
        <member><name>preview_url</name><value><string>https://x/preview</string></value></member>
        <member><name>category</name><value><string>素材候補</string></value></member>
        <member><name>created_at</name><value><string>2026-07-28T09:15:03Z</string></value></member>
        </struct></value></param></params></methodResponse>
        """.trimIndent()

    private fun faultResponse(): String =
        """
        <?xml version="1.0"?><methodResponse><fault><value><struct>
        <member><name>faultCode</name><value><int>409</int></value></member>
        <member><name>faultString</name><value><string>category_unavailable</string></value></member>
        </struct></value></fault></methodResponse>
        """.trimIndent()
}
