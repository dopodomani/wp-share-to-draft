package io.github.dopodomani.wpsharetodraft.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.dopodomani.wpsharetodraft.domain.AppSettings
import io.github.dopodomani.wpsharetodraft.domain.CaptureError
import io.github.dopodomani.wpsharetodraft.domain.CaptureException
import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import io.github.dopodomani.wpsharetodraft.domain.ConnectionMethod
import io.github.dopodomani.wpsharetodraft.domain.Logger
import io.github.dopodomani.wpsharetodraft.domain.SettingsRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Now a thin dispatcher test: [WordPressDestination] itself no longer talks HTTP directly --
 * that's [RestPublisherTest]/`XmlRpcPublisherTest`'s job. Wires a real
 * [WordPressPublisherFactory] over real [RestPublisher]/[XmlRpcPublisher] instances pointed at
 * the same MockWebServer, and asserts (a) it fails fast with no settings, (b) it hits the
 * right endpoint for the current `connectionMethod`, and (c) it logs which transport was used.
 * See docs/phase3c-android-xmlrpc-design.md.
 */
class WordPressDestinationTest {
    private lateinit var server: MockWebServer
    private lateinit var factory: WordPressPublisherFactory

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
        val localhostCertificate =
            HeldCertificate
                .Builder()
                .addSubjectAlternativeName("localhost")
                .build()
        val serverCertificates =
            HandshakeCertificates
                .Builder()
                .heldCertificate(localhostCertificate)
                .build()
        val clientCertificates =
            HandshakeCertificates
                .Builder()
                .addTrustedCertificate(localhostCertificate.certificate)
                .build()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()

        val errorMapper = MaterialCaptureErrorMapper()
        val httpClient =
            OkHttpClient
                .Builder()
                .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                .hostnameVerifier { _, _ -> true }
                .callTimeout(500, TimeUnit.MILLISECONDS)
                .build()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit =
            Retrofit
                .Builder()
                .baseUrl(server.url("/"))
                .client(httpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
        val restPublisher = RestPublisher(retrofit.create(MaterialCaptureApi::class.java), errorMapper)
        val xmlRpcPublisher = XmlRpcPublisher(MaterialCaptureXmlRpcApi(httpClient), errorMapper)
        factory = WordPressPublisherFactory(xmlRpcPublisher, restPublisher)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `no settings configured fails fast without making a network call`() =
        runTest {
            val destination = WordPressDestination(factory, unconfiguredSettings(), FakeLogger())

            val result = destination.send(item)

            assertEquals(CaptureError.SettingsNotConfigured, (result.exceptionOrNull() as CaptureException).error)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `invalid saved site URL fails safely without making a network call`() =
        runTest {
            val settings = AppSettings("https://", "user", "app-password", ConnectionMethod.REST)
            val destination = WordPressDestination(factory, fixedSettings(settings), FakeLogger())

            val result = destination.send(item)

            assertEquals(CaptureError.InvalidSettings, (result.exceptionOrNull() as CaptureException).error)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `hits xmlrpc php and logs XML_RPC when that's the current setting`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(successXmlRpcResponse()))
            val logger = FakeLogger()
            val destination = WordPressDestination(factory, fixedSettings(ConnectionMethod.XML_RPC), logger)

            val result = destination.send(item)

            assertTrue(result.isSuccess) {
                "Expected XML-RPC success, got ${(result.exceptionOrNull() as? CaptureException)?.error}"
            }
            assertEquals("/xmlrpc.php", server.takeRequest().path)
            assertEquals(listOf("Publishing via XML_RPC"), logger.messages)
        }

    @Test
    fun `hits the REST endpoint and logs REST when that's the current setting`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(201).setBody(successRestResponse()))
            val logger = FakeLogger()
            val destination = WordPressDestination(factory, fixedSettings(ConnectionMethod.REST), logger)

            val result = destination.send(item)

            assertTrue(result.isSuccess) {
                "Expected REST success, got ${(result.exceptionOrNull() as? CaptureException)?.error}"
            }
            assertEquals("/wp-json/material-capture/v1/draft", server.takeRequest().path)
            assertEquals(listOf("Publishing via REST"), logger.messages)
        }

    private fun successRestResponse(): String =
        """
        {"post_id":1,"status":"draft","title":"[INBOX] Title",
         "edit_url":"https://x/edit","preview_url":"https://x/preview",
         "category":"素材候補","created_at":"2026-07-28T09:15:03Z"}
        """.trimIndent()

    private fun successXmlRpcResponse(): String =
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

    private fun fixedSettings(connectionMethod: ConnectionMethod): SettingsRepository =
        fixedSettings(AppSettings(server.url("/").toString().trimEnd('/'), "user", "app-password", connectionMethod))

    private fun fixedSettings(appSettings: AppSettings): SettingsRepository =
        object : SettingsRepository {
            override suspend fun hasSettings() = true

            override suspend fun get() = appSettings

            override suspend fun save(settings: AppSettings) {}
        }

    private fun unconfiguredSettings(): SettingsRepository =
        object : SettingsRepository {
            override suspend fun hasSettings() = false

            override suspend fun get(): AppSettings? = null

            override suspend fun save(settings: AppSettings) {}
        }

    private class FakeLogger : Logger {
        val messages = mutableListOf<String>()

        override fun d(
            tag: String,
            message: String,
        ) {
            messages += message
        }
    }
}
