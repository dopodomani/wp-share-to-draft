package io.github.dopodomani.wpsharetodraft.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.dopodomani.wpsharetodraft.domain.AppSettings
import io.github.dopodomani.wpsharetodraft.domain.CaptureError
import io.github.dopodomani.wpsharetodraft.domain.CaptureException
import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import io.github.dopodomani.wpsharetodraft.domain.SettingsRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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
 * Exercises [WordPressDestination] against an in-process fake HTTP server (OkHttp's
 * MockWebServer) -- no live WordPress instance, still runs with no network/device. See
 * docs/phase2-wordpress-plugin-design.md#integration-test-scope-designed-separately-gates-phase-4
 * for the (real-WordPress) integration layer this deliberately does NOT replace.
 */
class WordPressDestinationTest {
    private lateinit var server: MockWebServer
    private lateinit var api: MaterialCaptureApi
    private lateinit var settingsRepository: SettingsRepository

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
        settingsRepository = fakeSettings()

        val json = Json { ignoreUnknownKeys = true }
        val client =
            OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(settingsRepository))
                .callTimeout(500, TimeUnit.MILLISECONDS)
                .build()
        val retrofit =
            Retrofit.Builder()
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

            val result = destination().send(item)

            assertTrue(result.isSuccess)
            assertEquals(1L, result.getOrThrow().postId)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertTrue(recorded.getHeader("Authorization")?.startsWith("Basic ") == true)
        }

    @Test
    fun `maps a 400 invalid_url response to Validation`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(400).setBody("""{"code":"invalid_url","message":"bad url"}"""),
            )

            val result = destination().send(item)

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

            val result = destination().send(item)

            assertEquals(CaptureError.Unauthenticated, (result.exceptionOrNull() as CaptureException).error)
        }

    @Test
    fun `no settings configured fails fast without making a network call`() =
        runTest {
            val result = destination(unconfiguredSettings()).send(item)

            assertEquals(CaptureError.SettingsNotConfigured, (result.exceptionOrNull() as CaptureException).error)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `a dropped connection maps to Network Unreachable`() =
        runTest {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

            val result = destination().send(item)

            assertEquals(CaptureError.Network.Unreachable, (result.exceptionOrNull() as CaptureException).error)
        }

    private fun destination(repository: SettingsRepository = settingsRepository): WordPressDestination =
        WordPressDestination(api, repository, MaterialCaptureErrorMapper())

    private fun fakeSettings(): SettingsRepository =
        object : SettingsRepository {
            override suspend fun hasSettings() = true

            override suspend fun get() = AppSettings(server.url("/").toString().trimEnd('/'), "user", "app-password")

            override suspend fun save(settings: AppSettings) {}
        }

    private fun unconfiguredSettings(): SettingsRepository =
        object : SettingsRepository {
            override suspend fun hasSettings() = false

            override suspend fun get(): AppSettings? = null

            override suspend fun save(settings: AppSettings) {}
        }
}
