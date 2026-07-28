package io.github.dopodomani.wpsharetodraft.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.github.dopodomani.wpsharetodraft.domain.AppSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Uses a plain (non-encrypted) SharedPreferences from Robolectric -- this class only
 * depends on the SharedPreferences interface, so its storage logic is testable without
 * the Keystore actually being involved. Real encryption-at-rest is WordPress/Android core
 * behavior, not this class's own logic -- see the same unit-vs-integration split applied
 * to the WordPress plugin's InputSanitizer (docs/phase2-wordpress-plugin-design.md).
 *
 * Pinned to API 34 via @Config: Robolectric 4.13 (this project's version) doesn't yet
 * support API 35, which this app's compileSdk/targetSdk targets -- see the same note on
 * IntentParserTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EncryptedSettingsRepositoryTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var repository: EncryptedSettingsRepository

    @Before
    fun setUp() {
        prefs =
            ApplicationProvider.getApplicationContext<Context>()
                .getSharedPreferences("test_material_capture_settings", Context.MODE_PRIVATE)
        repository = EncryptedSettingsRepository(prefs)
    }

    @Test
    fun `hasSettings is false before anything is saved`() =
        runTest {
            assertFalse(repository.hasSettings())
            assertNull(repository.get())
        }

    @Test
    fun `save then get round-trips correctly`() =
        runTest {
            val settings = AppSettings(siteUrl = "https://example.com", username = "user", applicationPassword = "app-password")

            repository.save(settings)

            assertTrue(repository.hasSettings())
            assertEquals(settings, repository.get())
        }
}
