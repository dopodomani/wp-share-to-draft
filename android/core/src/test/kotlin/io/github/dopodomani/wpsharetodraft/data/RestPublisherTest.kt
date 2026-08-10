package io.github.dopodomani.wpsharetodraft.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.dopodomani.wpsharetodraft.domain.AppSettings
import io.github.dopodomani.wpsharetodraft.domain.CaptureError
import io.github.dopodomani.wpsharetodraft.domain.CaptureException
import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Exercises [RestPublisher] against an in-process fake HTTP server (OkHttp's MockWebServer) --
 * no live WordPress instance, still runs with no network/device. Renamed/adapted from the
 * original `WordPressDestinationTest`, which tested this same REST logic before it moved
 * behind [WordPressPublisher] -- see docs/phase3c-android-xmlrpc-design.md.
 */
class RestPublisherTest {
    private lateinit var server: MockWebServer
    private lateinit var api: MaterialCaptureApi
    private lateinit var settings: AppSettings

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
        settings = AppSettings(server.url("/").toString().trimEnd('/'), "user", "app-password")

        val json = Json { ignoreUnknownKeys = true }
        val client =
            OkHttpClient
                .Builder()
                .callTimeout(500, TimeUnit.MILLISECONDS)
                .build()
        val retrofit =
            Retrofit
                .Builder()
                .baseUrl(server.url("/"))
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
        api = retrofit.create(MaterialCaptureApi::class.java)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sends a POST with a basic auth header and maps a 201 to a DraftResult`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(201)
                    .setBody(
                        """
                        {"post_id":1,"status":"draft","title":"[INBOX] Title",
                         "edit_url":"https://x/edit","preview_url":"https://x/preview",
                         "category":"素材候補","created_at":"2026-07-28T09:15:03Z"}
                        """.trimIndent(),
                    ),
            )

            val result = publisher().publish(item, settings)

            assertTrue(result.isSuccess)
            assertEquals(1L, result.getOrThrow().postId)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals(Credentials.basic("user", "app-password"), recorded.getHeader("Authorization"))
        }

    @Test
    fun `maps a 400 invalid_url response to Validation`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(400).setBody("""{"code":"invalid_url","message":"bad url"}"""),
            )

            val result = publisher().publish(item, settings)

            assertEquals(
                CaptureError.Validation("invalid_url", "bad url"),
                (result.exceptionOrNull() as CaptureException).error,
            )
        }

    @Test
    fun `maps a 401 response to Unauthenticated`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(401).setBody("""{"code":"rest_not_logged_in","message":"..."}"""),
            )

            val result = publisher().publish(item, settings)

            assertEquals(CaptureError.Unauthenticated, (result.exceptionOrNull() as CaptureException).error)
        }

    @Test
    fun `a dropped connection maps to Network Unreachable`() =
        runTest {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

            val result = publisher().publish(item, settings)

            assertEquals(CaptureError.Network.Unreachable, (result.exceptionOrNull() as CaptureException).error)
        }

    @Test
    fun `a 201 with malformed JSON maps to Unknown instead of crashing`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(201).setBody("""{"post_id": not valid json"""))

            val result = publisher().publish(item, settings)

            assertTrue((result.exceptionOrNull() as CaptureException).error is CaptureError.Unknown)
        }

    @Test
    fun `a 201 with a missing required field maps to Unknown instead of crashing`() =
        runTest {
            // No "post_id" at all -- DraftResponseDto has no default for it.
            server.enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """{"status":"draft","title":"[INBOX] Title","edit_url":null,"preview_url":null,
                       "category":"素材候補","created_at":"2026-07-28T09:15:03Z"}""",
                ),
            )

            val result = publisher().publish(item, settings)

            assertTrue((result.exceptionOrNull() as CaptureException).error is CaptureError.Unknown)
        }

    @Test
    fun `a 201 with an unparseable created_at maps to Unknown instead of crashing`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """{"post_id":1,"status":"draft","title":"[INBOX] Title","edit_url":null,"preview_url":null,
                       "category":"素材候補","created_at":"not-a-timestamp"}""",
                ),
            )

            val result = publisher().publish(item, settings)

            assertTrue((result.exceptionOrNull() as CaptureException).error is CaptureError.Unknown)
        }

    private fun publisher(): RestPublisher = RestPublisher(api, MaterialCaptureErrorMapper())
}
