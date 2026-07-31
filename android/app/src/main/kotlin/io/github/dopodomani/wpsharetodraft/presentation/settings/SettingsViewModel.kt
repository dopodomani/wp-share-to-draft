package io.github.dopodomani.wpsharetodraft.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dopodomani.wpsharetodraft.domain.AppSettings
import io.github.dopodomani.wpsharetodraft.domain.ConnectionMethod
import io.github.dopodomani.wpsharetodraft.domain.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(private val settingsRepository: SettingsRepository) : ViewModel() {
        private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Editing())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        fun onSiteUrlChanged(value: String) = updateEditing { it.copy(siteUrl = value, validationError = null) }

        fun onUsernameChanged(value: String) = updateEditing { it.copy(username = value, validationError = null) }

        fun onApplicationPasswordChanged(value: String) = updateEditing { it.copy(applicationPassword = value, validationError = null) }

        fun onConnectionMethodChanged(value: ConnectionMethod) = updateEditing { it.copy(connectionMethod = value) }

        fun save() {
            val current = _uiState.value
            if (current !is SettingsUiState.Editing) return

            if (current.siteUrl.isBlank() || current.username.isBlank() || current.applicationPassword.isBlank()) {
                _uiState.value = current.copy(validationError = "すべての項目を入力してください")
                return
            }
            if (!current.siteUrl.startsWith("https://")) {
                _uiState.value = current.copy(validationError = "サイトURLはhttps://で始まる必要があります")
                return
            }

            viewModelScope.launch {
                settingsRepository.save(
                    AppSettings(
                        siteUrl = current.siteUrl.trimEnd('/'),
                        username = current.username,
                        applicationPassword = current.applicationPassword,
                        connectionMethod = current.connectionMethod,
                    ),
                )
                _uiState.value = SettingsUiState.Saved
            }
        }

        private fun updateEditing(transform: (SettingsUiState.Editing) -> SettingsUiState.Editing) {
            val current = _uiState.value
            if (current is SettingsUiState.Editing) _uiState.value = transform(current)
        }
    }
