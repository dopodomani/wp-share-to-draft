package io.github.dopodomani.wpsharetodraft.presentation.confirm

import app.cash.turbine.test
import io.github.dopodomani.wpsharetodraft.domain.CaptureError
import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import io.github.dopodomani.wpsharetodraft.domain.Destination
import io.github.dopodomani.wpsharetodraft.domain.DraftResult
import io.github.dopodomani.wpsharetodraft.domain.SubmitCaptureUseCase
import io.github.dopodomani.wpsharetodraft.domain.asThrowable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Verifies every transition in docs/phase3-android-app-design.md#8-state-transitions-loading--success--error,
 * including the invariants called out there (no-op save while Loading, Loading/Error carry
 * the exact submitted item). Not built/run against the Android SDK in this environment --
 * see docs/development.md -- but the test logic itself has zero Android imports.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmDraftViewModelTest {
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
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialize sets Idle with the given item and isSaveEnabled true for a complete item`() =
        runTest {
            val viewModel = viewModel(succeedingDestination())

            viewModel.uiState.test {
                skipItems(1) // initial empty Idle before initialize()

                viewModel.initialize(item)

                val state = awaitItem() as ConfirmDraftUiState.Idle
                assertEquals(item, state.item)
                assertTrue(state.isSaveEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isSaveEnabled is false when title is blank`() =
        runTest {
            val viewModel = viewModel(succeedingDestination())

            viewModel.uiState.test {
                skipItems(1)
                viewModel.initialize(item.copy(title = ""))
                val state = awaitItem() as ConfirmDraftUiState.Idle
                assertFalse(state.isSaveEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isSaveEnabled is true when url is blank but title is present`() =
        runTest {
            // url is optional -- see docs/tech-decisions.md#12-url-is-optional.
            val viewModel = viewModel(succeedingDestination())

            viewModel.uiState.test {
                skipItems(1)
                viewModel.initialize(item.copy(url = ""))
                val state = awaitItem() as ConfirmDraftUiState.Idle
                assertTrue(state.isSaveEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save is a no-op unless Idle with isSaveEnabled`() =
        runTest {
            val viewModel = viewModel(succeedingDestination())

            viewModel.uiState.test {
                skipItems(1)
                viewModel.initialize(item.copy(title = "")) // isSaveEnabled == false
                skipItems(1)

                viewModel.save()

                expectNoEvents()
            }
        }

    @Test
    fun `save transitions Idle to Loading to Success, carrying the same item into Loading`() =
        runTest {
            val result = DraftResult(1, "draft", "[INBOX] Title", null, null, "素材候補", Instant.now())
            val viewModel = viewModel(succeedingDestination(result))

            viewModel.uiState.test {
                skipItems(1)
                viewModel.initialize(item)
                skipItems(1)

                viewModel.save()

                val loading = awaitItem() as ConfirmDraftUiState.Loading
                assertEquals(item, loading.item)

                val success = awaitItem() as ConfirmDraftUiState.Success
                assertEquals(result, success.result)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a destination failure surfaces as Error carrying the submitted item`() =
        runTest {
            val viewModel = viewModel(failingDestination(CaptureError.CategoryUnavailable))

            viewModel.uiState.test {
                skipItems(1)
                viewModel.initialize(item)
                skipItems(1)

                viewModel.save()

                awaitItem() // Loading
                val error = awaitItem() as ConfirmDraftUiState.Error
                assertEquals(item, error.item)
                assertEquals(CaptureError.CategoryUnavailable, error.error)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `edit returns from Error to Idle keeping the same item`() =
        runTest {
            val viewModel = viewModel(failingDestination(CaptureError.ServerError(null)))

            viewModel.uiState.test {
                skipItems(1)
                viewModel.initialize(item)
                skipItems(1)
                viewModel.save()
                awaitItem() // Loading
                awaitItem() // Error

                viewModel.edit()

                val idle = awaitItem() as ConfirmDraftUiState.Idle
                assertEquals(item, idle.item)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `retry re-submits the same item from Error`() =
        runTest {
            val result = DraftResult(2, "draft", "[INBOX] Title", null, null, "素材候補", Instant.now())
            var attempt = 0
            val destination =
                object : Destination {
                    override suspend fun send(captured: CaptureItem): Result<DraftResult> {
                        attempt += 1
                        return if (attempt == 1) Result.failure(CaptureError.ServerError(null).asThrowable()) else Result.success(result)
                    }
                }
            val viewModel = viewModel(destination)

            viewModel.uiState.test {
                skipItems(1)
                viewModel.initialize(item)
                skipItems(1)
                viewModel.save()
                awaitItem() // Loading
                awaitItem() // Error

                viewModel.retry()

                val loading = awaitItem() as ConfirmDraftUiState.Loading
                assertEquals(item, loading.item)
                val success = awaitItem() as ConfirmDraftUiState.Success
                assertEquals(result, success.result)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun viewModel(destination: Destination): ConfirmDraftViewModel = ConfirmDraftViewModel(SubmitCaptureUseCase(destination))

    private fun succeedingDestination(
        result: DraftResult = DraftResult(1, "draft", "[INBOX] Title", null, null, "素材候補", Instant.now()),
    ): Destination =
        object : Destination {
            override suspend fun send(item: CaptureItem): Result<DraftResult> = Result.success(result)
        }

    private fun failingDestination(error: CaptureError): Destination =
        object : Destination {
            override suspend fun send(item: CaptureItem): Result<DraftResult> = Result.failure(error.asThrowable())
        }
}
