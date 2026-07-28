package io.github.dopodomani.wpsharetodraft.presentation.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dopodomani.wpsharetodraft.domain.CaptureError
import io.github.dopodomani.wpsharetodraft.domain.CaptureItem
import io.github.dopodomani.wpsharetodraft.domain.SubmitCaptureUseCase
import io.github.dopodomani.wpsharetodraft.domain.asCaptureErrorOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

private val EMPTY_ITEM = CaptureItem(title = "", url = "", sharedText = null, memo = null, source = "", sharedAt = Instant.EPOCH)

/**
 * Exposes only a single [StateFlow] of [ConfirmDraftUiState] -- no separate loading/error
 * boolean flags alongside a data field. See docs/phase3-android-app-design.md#3-viewmodel-construction
 * and #8-state-transitions-loading--success--error for the invariants this enforces.
 */
@HiltViewModel
class ConfirmDraftViewModel
    @Inject
    constructor(private val submitCaptureUseCase: SubmitCaptureUseCase) : ViewModel() {
        private val _uiState =
            MutableStateFlow<ConfirmDraftUiState>(ConfirmDraftUiState.Idle(EMPTY_ITEM, isSaveEnabled = false))
        val uiState: StateFlow<ConfirmDraftUiState> = _uiState.asStateFlow()

        /** Called once, from ShareReceiverActivity, with the parsed intent data. */
        fun initialize(item: CaptureItem) {
            _uiState.value = ConfirmDraftUiState.Idle(item, isSaveEnabled(item))
        }

        fun onTitleChanged(value: String) = updateItem { it.copy(title = value) }

        fun onUrlChanged(value: String) = updateItem { it.copy(url = value) }

        fun onMemoChanged(value: String) = updateItem { it.copy(memo = value) }

        /** No-op unless currently Idle with isSaveEnabled -- no double-submission from a double-tap while Loading. */
        fun save() {
            val current = _uiState.value
            if (current !is ConfirmDraftUiState.Idle || !current.isSaveEnabled) return
            submit(current.item)
        }

        /** Re-runs submission with the same item that failed. */
        fun retry() {
            val current = _uiState.value
            if (current !is ConfirmDraftUiState.Error) return
            submit(current.item)
        }

        /** Error -> Idle, keeping the item and its edits. */
        fun edit() {
            val current = _uiState.value
            if (current !is ConfirmDraftUiState.Error) return
            _uiState.value = ConfirmDraftUiState.Idle(current.item, isSaveEnabled(current.item))
        }

        private fun updateItem(transform: (CaptureItem) -> CaptureItem) {
            val current = _uiState.value
            if (current !is ConfirmDraftUiState.Idle) return
            val updated = transform(current.item)
            _uiState.value = ConfirmDraftUiState.Idle(updated, isSaveEnabled(updated))
        }

        private fun submit(item: CaptureItem) {
            _uiState.value = ConfirmDraftUiState.Loading(item)
            viewModelScope.launch {
                submitCaptureUseCase.submit(item)
                    .onSuccess { result -> _uiState.value = ConfirmDraftUiState.Success(result) }
                    .onFailure { throwable ->
                        val error = throwable.asCaptureErrorOrNull() ?: CaptureError.Unknown(throwable.message)
                        _uiState.value = ConfirmDraftUiState.Error(item, error)
                    }
            }
        }

        private fun isSaveEnabled(item: CaptureItem): Boolean = item.title.isNotBlank() && item.url.isNotBlank()
    }
