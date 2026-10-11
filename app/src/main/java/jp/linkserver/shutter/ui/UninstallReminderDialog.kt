package jp.linkserver.shutter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.linkserver.shutter.ForceUse
import jp.linkserver.shutter.R
import jp.linkserver.shutter.ShutterSound
import jp.linkserver.shutter.ShutterUiState

@Composable
internal fun UninstallReminderDialog(
    state: ShutterUiState, onDismiss: () -> Unit, onRestore: () -> Unit,
    onOpenShizuku: () -> Unit, onAuthorize: () -> Unit, onRefresh: () -> Unit,
    deviceDefault: Int? = ForceUse.deviceDefault()
) {
    val alreadyDefault = deviceDefault != null && state.status == ShutterSound.Status.READY &&
        state.current == deviceDefault && !state.busy
    val detail = when {
        state.busy -> R.string.current_working
        state.status == ShutterSound.Status.NOT_RUNNING -> R.string.home_start_shizuku_hint
        state.status == ShutterSound.Status.NOT_AUTHORIZED -> R.string.home_authorize_hint
        state.current == null -> R.string.home_refresh_hint
        deviceDefault == null -> R.string.device_default_unavailable
        else -> null
    }
    val actionLabel = when {
        state.busy -> R.string.home_working
        state.status == ShutterSound.Status.NOT_RUNNING -> R.string.home_open_shizuku
        state.status == ShutterSound.Status.NOT_AUTHORIZED -> R.string.action_authorize
        state.current == null -> R.string.action_refresh
        else -> R.string.home_restore
    }
    val action = when (state.status) {
        ShutterSound.Status.NOT_RUNNING -> onOpenShizuku
        ShutterSound.Status.NOT_AUTHORIZED -> onAuthorize
        ShutterSound.Status.READY -> if (state.current == null) onRefresh else onRestore
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_restore), null) },
        title = {
            Text(stringResource(if (alreadyDefault) R.string.uninstall_reminder_ready_title
                else R.string.uninstall_reminder_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (alreadyDefault) {
                    Text(stringResource(R.string.uninstall_reminder_already_default))
                } else {
                    Text(stringResource(R.string.home_uninstall_note))
                    detail?.let { Text(stringResource(it)) }
                }
                if (!alreadyDefault && state.error.isNotBlank()) {
                    Text(stringResource(R.string.uninstall_reminder_error),
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (alreadyDefault) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            } else {
                TextButton(onClick = action, enabled = !state.busy &&
                    !(state.status == ShutterSound.Status.READY && state.current != null && deviceDefault == null)) {
                    Text(stringResource(actionLabel))
                }
            }
        },
        dismissButton = {
            if (!alreadyDefault) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        }
    )
}
