package io.github.dopodomani.wpsharetodraft.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.dopodomani.wpsharetodraft.domain.ConnectionMethod

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
                Text("接続方式")
                Column(modifier = Modifier.selectableGroup()) {
                    ConnectionMethodOption(
                        label = "XML-RPC",
                        selected = state.connectionMethod == ConnectionMethod.XML_RPC,
                        onClick = { viewModel.onConnectionMethodChanged(ConnectionMethod.XML_RPC) },
                    )
                    ConnectionMethodOption(
                        label = "REST API",
                        selected = state.connectionMethod == ConnectionMethod.REST,
                        onClick = { viewModel.onConnectionMethodChanged(ConnectionMethod.REST) },
                    )
                }
                Text("REST APIが利用できないWordPress環境ではXML-RPCを使用します。")
                state.validationError?.let { Text(it) }
                Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                    Text("保存")
                }
            }
        SettingsUiState.Saved -> {
            // onSaved() navigates to Confirm when a share was pending, but is a deliberate
            // no-op when Settings itself is the destination (launched from the icon, nothing
            // to navigate to) -- so this branch must render something on its own rather than
            // relying on navigation to replace it, or that second case is a permanently blank
            // screen. See docs/phase3-android-smoke-test-results.md's 2026-07-30 entry.
            LaunchedEffect(Unit) { onSaved() }
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("設定を保存しました")
            }
        }
    }
}

@Composable
private fun ConnectionMethodOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
