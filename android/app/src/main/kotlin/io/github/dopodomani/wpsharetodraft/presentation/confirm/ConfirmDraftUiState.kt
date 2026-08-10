package io.github.dopodomani.wpsharetodraft.presentation.confirm

import io.github.dopodomani.wpsharetodraft.domain.CaptureError
import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import io.github.dopodomani.wpsharetodraft.domain.DraftResult

/**
 * See docs/phase3-android-app-design.md#8-state-transitions-loading--success--error. Each
 * state carries exactly the data it needs -- there is no "loading AND has stale error text"
 * combination possible, since these are mutually exclusive variants, not independent flags.
 */
sealed interface ConfirmDraftUiState {
    data class Idle(
        val item: CaptureItem,
        val isSaveEnabled: Boolean,
    ) : ConfirmDraftUiState

    data class Loading(
        val item: CaptureItem,
    ) : ConfirmDraftUiState

    data class Success(
        val result: DraftResult,
    ) : ConfirmDraftUiState

    data class Error(
        val item: CaptureItem,
        val error: CaptureError,
    ) : ConfirmDraftUiState
}
