package io.github.dopodomani.wpsharetodraft.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dopodomani.wpsharetodraft.data.WordPressSiteUrl
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
        private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                val existing = settingsRepository.get()
                _uiState.value =
                    if (existing != null) {
                        SettingsUiState.Editing(
                            siteUrl = existing.siteUrl,
                            username = existing.username,
                            applicationPassword = existing.applicationPassword,
                            connectionMethod = existing.connectionMethod,
                        )
                    } else {
                        SettingsUiState.Editing()
                    }
            }
        }

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
            val normalizedSiteUrl = WordPressSiteUrl.normalize(current.siteUrl)
            if (normalizedSiteUrl == null) {
                _uiState.value = current.copy(validationError = "有効なHTTPSのサイトURLを入力してください")
                return
            }

            viewModelScope.launch {
                settingsRepository.save(
                    AppSettings(
                        siteUrl = normalizedSiteUrl,
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
