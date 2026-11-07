package jp.linkserver.shutter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*
import jp.linkserver.shutter.R
import jp.linkserver.shutter.update.UpdateDownloadViewModel
import jp.linkserver.shutter.update.UpdateDownloadState
import kotlinx.coroutines.launch

@Composable
internal fun AppUpdateAction(update: UpdateCandidate, onClick: () -> Unit) {
    UpdateNotificationIcon(GoldyState(notificationUpdate = update), { onClick() },
        strings = goldyNotificationStrings())
}

@Composable
internal fun AppUpdateBanner(update: UpdateCandidate, onOpen: () -> Unit, onDismiss: () -> Unit) {
    UpdateNotificationBanner(GoldyState(notificationUpdate = update, showBanner = true), { onOpen() }, onDismiss,
        strings = goldyNotificationStrings())
}

@Composable
internal fun AppUpdatePrompt(state: GoldyState, onCheck: suspend () -> CheckOutcome, onConfirm: () -> Unit,
    onIgnore: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    UpdateCheckDialog(state, { scope.launch { onCheck() } }, { onConfirm() }, { onIgnore() }, onDismiss,
        strings = goldyNotificationStrings())
}

@Composable
internal fun UpdateOverviewScreen(candidate: UpdateCandidate?, onBack: () -> Unit,
    downloads: UpdateDownloadViewModel = viewModel()) {
    val download by downloads.state.collectAsStateWithLifecycle()
    val selected by downloads.selection.collectAsStateWithLifecycle()
    LaunchedEffect(candidate) { downloads.selectCandidate(candidate) }
    val pinned = selected ?: candidate
    UpdateOverviewContent(pinned, download, onBack,
        header = { RetainedInstallRequests(download, downloads::reopen, downloads::release) },
        onAction = { downloads.action(pinned, it) })
}

/** No onResume/Activity-result cleanup: only an explicit closed-reader confirmation releases a URI. */
@Composable
internal fun RetainedInstallRequests(state: UpdateDownloadState,
    onReopen: (InstallRequest) -> Unit, onRelease: (InstallRequest) -> Unit) {
    var confirmId by rememberSaveable { mutableStateOf<String?>(null) }
    var release by rememberSaveable { mutableStateOf(false) }
    if (state.restoring) Text(stringResource(R.string.goldy_update_restore_loading))
    if (state.requests.isNotEmpty()) {
        val card = LocalUpdateCardStyle.current
        Surface(shape = card.shape ?: MaterialTheme.shapes.large,
            color = card.containerColor.takeUnless { it == Color.Unspecified } ?: MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.testTag("retained-install-requests")) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.goldy_update_retained_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.goldy_update_retained_body), style = MaterialTheme.typography.bodySmall)
                state.requests.forEach { request ->
                    Text(request.metadata.versionName, style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(onClick = { confirmId = request.id; release = false },
                        enabled = !state.busy && !state.restoring,
                        modifier = Modifier.fillMaxWidth().testTag("retained-reopen-${request.id}")) {
                        Text(stringResource(R.string.goldy_update_reopen))
                    }
                    TextButton(onClick = { confirmId = request.id; release = true },
                        enabled = !state.busy && !state.restoring,
                        modifier = Modifier.fillMaxWidth().testTag("retained-release-${request.id}")) {
                        Text(stringResource(R.string.goldy_update_release))
                    }
                }
            }
        }
    }
    state.requests.firstOrNull { it.id == confirmId }?.let { request ->
        AlertDialog(onDismissRequest = { confirmId = null }, modifier = Modifier.testTag("retained-confirmation"),
            title = { Text(stringResource(if (release) R.string.goldy_update_release_title else R.string.goldy_update_reopen)) },
            text = { Text(stringResource(if (release) R.string.goldy_update_release_body else R.string.goldy_update_reopen_body)) },
            confirmButton = { TextButton(modifier = Modifier.testTag("retained-confirm"), onClick = {
                confirmId = null
                if (release) onRelease(request) else onReopen(request)
            }) { Text(stringResource(if (release) R.string.goldy_update_release_confirm else R.string.goldy_update_reopen)) } },
            dismissButton = { TextButton(onClick = { confirmId = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
}
