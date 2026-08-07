package io.github.dopodomani.wpsharetodraft.data

import io.github.dopodomani.wpsharetodraft.domain.AppSettings
import io.github.dopodomani.wpsharetodraft.domain.CaptureError
import io.github.dopodomani.wpsharetodraft.domain.CaptureException
import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import io.github.dopodomani.wpsharetodraft.domain.ConnectionMethod
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Exercises [XmlRpcPublisher] against an in-process fake HTTP server -- mirrors
 * [RestPublisherTest]'s coverage for the XML-RPC transport. See
 * docs/phase3c-android-xmlrpc-design.md.
 */
class XmlRpcPublisherTest {
    private lateinit var server: MockWebServer
    private lateinit var settings: AppSettings
    private lateinit var publisher: XmlRpcPublisher

    private val item =
        CaptureItem(
            title = "Title",
            url = "https://example.com",
            sharedText = null,
            memo = null,
            source = "chrome_share",
            sharedAt = Instant.parse("2026-07-28T09:15:00Z"),
        )

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        settings =
            AppSettings(
                server.url("/").toString().trimEnd('/'),
                "user",
                "app-password",
                ConnectionMethod.XML_RPC,
            )
        publisher = XmlRpcPublisher(MaterialCaptureXmlRpcApi(OkHttpClient()), MaterialCaptureErrorMapper())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `posts to xmlrpc php and maps a success response to a DraftResult`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(successResponse()))

            val result = publisher.publish(item, settings)

            assertTrue(result.isSuccess)
            assertEquals(1L, result.getOrThrow().postId)
            val request = server.takeRequest()
            assertEquals("/xmlrpc.php", request.path)
            assertNull(request.getHeader("Authorization"))
        }

    @Test
    fun `maps a category_unavailable fault to CategoryUnavailable`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(faultResponse()))

            val result = publisher.publish(item, settings)

            assertEquals(CaptureError.CategoryUnavailable, (result.exceptionOrNull() as CaptureException).error)
        }

    @Test
    fun `a dropped connection maps to Network Unreachable`() =
        runTest {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

            val result = publisher.publish(item, settings)

            assertEquals(CaptureError.Network.Unreachable, (result.exceptionOrNull() as CaptureException).error)
        }

    @Test
    fun `HTTP 429 maps to RateLimited`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(429))

            val result = publisher.publish(item, settings)

            assertEquals(CaptureError.RateLimited, (result.exceptionOrNull() as CaptureException).error)
        }

    @Test
    fun `HTTP 503 maps to ServiceUnavailable`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(503))

            val result = publisher.publish(item, settings)

            assertEquals(CaptureError.ServiceUnavailable, (result.exceptionOrNull() as CaptureException).error)
        }

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
