package jp.linkserver.shutter.ui

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*
import jp.linkserver.shutter.update.UpdateDownloadViewModel
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
    UpdateOverviewContent(pinned, download, onBack, onAction = { downloads.action(pinned, it) })
}
