package io.github.dopodomani.wpsharetodraft.presentation.settings

import app.cash.turbine.test
import io.github.dopodomani.wpsharetodraft.domain.AppSettings
import io.github.dopodomani.wpsharetodraft.domain.ConnectionMethod
import io.github.dopodomani.wpsharetodraft.domain.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Confirms the fix for a real bug: the ViewModel used to always start from a blank
 * `Editing()`, never reading back previously-saved settings -- opening Settings from the app
 * icon, or via "設定を開く" from an auth error, meant re-typing the site URL/username/
 * Application Password every time even though they were already saved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `previously-saved settings are read back into Editing`() =
        runTest {
            val saved =
                AppSettings(
                    siteUrl = "https://example.com",
                    username = "user",
                    applicationPassword = "app-password",
                    connectionMethod = ConnectionMethod.REST,
                )
            val viewModel = SettingsViewModel(fixedSettings(saved))

            viewModel.uiState.test {
                val state = awaitItem() as SettingsUiState.Editing
                assertEquals("https://example.com", state.siteUrl)
                assertEquals("user", state.username)
                assertEquals("app-password", state.applicationPassword)
                assertEquals(ConnectionMethod.REST, state.connectionMethod)
                assertNull(state.validationError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `no previously-saved settings starts from a blank Editing`() =
        runTest {
            val viewModel = SettingsViewModel(unconfiguredSettings())

            viewModel.uiState.test {
                val state = awaitItem() as SettingsUiState.Editing
                assertEquals("", state.siteUrl)
                assertEquals("", state.username)
                assertEquals("", state.applicationPassword)
                assertEquals(ConnectionMethod.XML_RPC, state.connectionMethod)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save persists the entered values and transitions to Saved`() =
        runTest {
            val repository = unconfiguredSettings()
            val viewModel = SettingsViewModel(repository)

            viewModel.uiState.test {
                awaitItem() // initial Editing() once loading resolves

                viewModel.onSiteUrlChanged("https://example.com/")
                awaitItem()
                viewModel.onUsernameChanged("user")
                awaitItem()
                viewModel.onApplicationPasswordChanged("app-password")
                awaitItem()

                viewModel.save()

                assertEquals(SettingsUiState.Saved, awaitItem())
                assertEquals("https://example.com", repository.lastSaved?.siteUrl)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://",
            "http://example.com",
            "https://user:password@example.com",
            "https://example.com?rest_route=/",
            "https://example.com/#fragment",
            "https://example.com:invalid",
            "not a URL",
        ],
    )
    fun `save rejects values that are not HTTPS WordPress roots`(siteUrl: String) =
        runTest {
            val repository = unconfiguredSettings()
            val viewModel = SettingsViewModel(repository)
            enterRequiredSettings(viewModel, siteUrl)

            viewModel.save()

            val state = viewModel.uiState.value as SettingsUiState.Editing
            assertEquals("有効なHTTPSのサイトURLを入力してください", state.validationError)
            assertNull(repository.lastSaved)
        }

    @Test
    fun `save canonicalizes HTTPS URL while preserving a WordPress subdirectory`() =
        runTest {
            val repository = unconfiguredSettings()
            val viewModel = SettingsViewModel(repository)
            enterRequiredSettings(viewModel, "  HTTPS://EXAMPLE.COM:443/news site/  ")

            viewModel.save()

            assertEquals(SettingsUiState.Saved, viewModel.uiState.value)
            assertEquals("https://example.com/news%20site", repository.lastSaved?.siteUrl)
        }

    @Test
    fun `save accepts a non-default HTTPS port`() =
        runTest {
            val repository = unconfiguredSettings()
            val viewModel = SettingsViewModel(repository)
            enterRequiredSettings(viewModel, "https://example.com:8443/wordpress/")

            viewModel.save()

            assertEquals("https://example.com:8443/wordpress", repository.lastSaved?.siteUrl)
            assertTrue(viewModel.uiState.value is SettingsUiState.Saved)
        }

    private fun enterRequiredSettings(
        viewModel: SettingsViewModel,
        siteUrl: String,
    ) {
        viewModel.onSiteUrlChanged(siteUrl)
        viewModel.onUsernameChanged("user")
        viewModel.onApplicationPasswordChanged("app-password")
    }

    private fun fixedSettings(settings: AppSettings): SettingsRepository =
        object : SettingsRepository {
            override suspend fun hasSettings() = true

            override suspend fun get() = settings

            override suspend fun save(settings: AppSettings) {}
        }

    private fun unconfiguredSettings(): FakeSettingsRepository = FakeSettingsRepository()

    private class FakeSettingsRepository : SettingsRepository {
        var lastSaved: AppSettings? = null

        override suspend fun hasSettings() = lastSaved != null

        override suspend fun get(): AppSettings? = null

        override suspend fun save(settings: AppSettings) {
            lastSaved = settings
        }
    }
}
