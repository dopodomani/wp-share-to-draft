package io.github.dopodomani.wpsharetodraft.presentation.confirm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.dopodomani.wpsharetodraft.domain.CaptureError

/**
 * See docs/phase3-android-app-design.md#8-state-transitions-loading--success--error --
 * this composable is a direct rendering of that state machine, one branch per state.
 */
@Composable
fun ConfirmDraftScreen(
    onOpenSettings: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ConfirmDraftViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is ConfirmDraftUiState.Idle ->
            IdleContent(
                state = state,
                onTitleChanged = viewModel::onTitleChanged,
                onUrlChanged = viewModel::onUrlChanged,
                onMemoChanged = viewModel::onMemoChanged,
                onSave = viewModel::save,
                onCancel = onCancel,
            )
        is ConfirmDraftUiState.Loading -> LoadingContent()
        is ConfirmDraftUiState.Success -> SuccessContent(state = state, onDone = onDone)
        is ConfirmDraftUiState.Error ->
            ErrorContent(
                state = state,
                onRetry = viewModel::retry,
                onEdit = viewModel::edit,
                onOpenSettings = onOpenSettings,
            )
    }
}

/**
 * Each field keeps its own local [TextFieldValue] as the single source of truth for what's
 * rendered, so mid-conversion Japanese IME state ("未確定文字列") survives -- rendering
 * directly from `state` (a value that round-trips through the ViewModel's StateFlow on every
 * keystroke) was confirmed to break IME composition.
 *
 * Keyed on `state.item.sharedAt` rather than left unkeyed or reconciled by comparing values
 * on every recomposition: `sharedAt` is the one field none of `onTitleChanged`/
 * `onUrlChanged`/`onMemoChanged` ever touches, so it's a stable identity for "this share
 * session" that only changes when a genuinely new item arrives (`initialize()`) -- unlike a
 * per-recomposition value comparison, which raced against `collectAsState()`'s StateFlow
 * collection (arriving on a later frame than the field's own snapshot-state write) and
 * stomped the just-typed character back to the stale value on almost every keystroke,
 * breaking IME composition even worse than reading `state` directly did.
 */
@Composable
private fun IdleContent(
    state: ConfirmDraftUiState.Idle,
    onTitleChanged: (String) -> Unit,
    onUrlChanged: (String) -> Unit,
    onMemoChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    var titleField by remember(state.item.sharedAt) { mutableStateOf(TextFieldValue(state.item.title)) }
    var urlField by remember(state.item.sharedAt) { mutableStateOf(TextFieldValue(state.item.url)) }
    var memoField by remember(state.item.sharedAt) { mutableStateOf(TextFieldValue(state.item.memo ?: "")) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = titleField,
            onValueChange = {
                titleField = it
                onTitleChanged(it.text)
            },
            label = { Text("タイトル") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = urlField,
            onValueChange = {
                urlField = it
                onUrlChanged(it.text)
            },
            label = { Text("URL") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = memoField,
            onValueChange = {
                memoField = it
                onMemoChanged(it.text)
            },
            label = { Text("メモ") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onSave, enabled = state.isSaveEnabled, modifier = Modifier.fillMaxWidth()) {
            Text("保存")
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("キャンセル")
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SuccessContent(
    state: ConfirmDraftUiState.Success,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("下書きを作成しました: ${state.result.title}")
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("完了")
        }
    }
}

@Composable
private fun ErrorContent(
    state: ConfirmDraftUiState.Error,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val presentation = state.error.toPresentation()

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(presentation.message)
        when (presentation.action) {
            ErrorAction.RETRY -> Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("再試行") }
            ErrorAction.EDIT -> Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("編集") }
            ErrorAction.OPEN_SETTINGS -> Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("設定を開く") }
        }
    }
}

private enum class ErrorAction { RETRY, EDIT, OPEN_SETTINGS }

private data class ErrorPresentation(val message: String, val action: ErrorAction)

/** Mirrors the user-facing table in docs/phase3-android-app-design.md#7-error-handling exactly. */
private fun CaptureError.toPresentation(): ErrorPresentation =
    when (this) {
        is CaptureError.Validation -> ErrorPresentation(message.ifBlank { "入力内容を確認してください" }, ErrorAction.EDIT)
        CaptureError.HttpsRequired -> ErrorPresentation("サイトのURLがHTTPSではありません", ErrorAction.OPEN_SETTINGS)
        CaptureError.Unauthenticated ->
            ErrorPresentation("認証に失敗しました。Application Passwordを確認してください", ErrorAction.OPEN_SETTINGS)
        CaptureError.InsufficientCapability -> ErrorPresentation("投稿を作成する権限がありません", ErrorAction.OPEN_SETTINGS)
        CaptureError.CategoryUnavailable ->
            ErrorPresentation("素材候補カテゴリーが見つかりません。WordPress側の設定を確認してください", ErrorAction.RETRY)
        is CaptureError.ServerError -> ErrorPresentation("サーバーエラーが発生しました", ErrorAction.RETRY)
        CaptureError.Network.Timeout -> ErrorPresentation("接続がタイムアウトしました", ErrorAction.RETRY)
        CaptureError.Network.DnsFailure -> ErrorPresentation("サイトのURLが見つかりません。URLを確認してください", ErrorAction.OPEN_SETTINGS)
        CaptureError.Network.SslFailure -> ErrorPresentation("サイトの証明書を確認できませんでした", ErrorAction.RETRY)
        CaptureError.Network.Unreachable -> ErrorPresentation("ネットワークに接続できません", ErrorAction.RETRY)
        CaptureError.SettingsNotConfigured -> ErrorPresentation("設定が完了していません", ErrorAction.OPEN_SETTINGS)
        is CaptureError.Unknown -> ErrorPresentation("予期しないエラーが発生しました", ErrorAction.RETRY)
    }
