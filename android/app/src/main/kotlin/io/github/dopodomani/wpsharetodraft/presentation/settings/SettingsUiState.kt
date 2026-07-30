package io.github.dopodomani.wpsharetodraft.presentation.settings

import io.github.dopodomani.wpsharetodraft.domain.ConnectionMethod

/** See docs/phase3-android-app-design.md#3-viewmodel-construction. */
sealed interface SettingsUiState {
    data class Editing(
        val siteUrl: String = "",
        val username: String = "",
        val applicationPassword: String = "",
        val connectionMethod: ConnectionMethod = ConnectionMethod.XML_RPC,
        val validationError: String? = null,
    ) : SettingsUiState

    data object Saved : SettingsUiState
}
