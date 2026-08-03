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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
