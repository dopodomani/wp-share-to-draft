package io.github.dopodomani.wpsharetodraft.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PasswordVisualTransformation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/** See docs/phase3-android-app-design.md#1-screen-transition-diagram for when this screen is reached. */
@Composable
fun SettingsScreen(
    onSaved: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is SettingsUiState.Editing ->
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.siteUrl,
                    onValueChange = viewModel::onSiteUrlChanged,
                    label = { Text("サイトURL (https://...)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::onUsernameChanged,
                    label = { Text("ユーザー名") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.applicationPassword,
                    onValueChange = viewModel::onApplicationPasswordChanged,
                    label = { Text("Application Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.validationError?.let { Text(it) }
                Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                    Text("保存")
                }
            }
        SettingsUiState.Saved -> LaunchedEffect(Unit) { onSaved() }
    }
}
