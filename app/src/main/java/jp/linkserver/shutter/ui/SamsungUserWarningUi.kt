package jp.linkserver.shutter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import jp.linkserver.shutter.R

@Composable
internal fun SamsungUserWarningCard() {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_warning_amber), null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.samsung_user_warning_title),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.samsung_user_warning_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun SamsungUserWarningDialog(onCancel: () -> Unit, onContinue: (Boolean) -> Unit) {
    var remember by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.samsung_user_confirmation_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.samsung_user_confirmation_description))
                Row(Modifier.fillMaxWidth().toggleable(remember, role = Role.Checkbox,
                    onValueChange = { remember = it }), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = remember, onCheckedChange = null)
                    Text(stringResource(R.string.samsung_user_confirmation_remember))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.samsung_user_confirmation_cancel)) }
        },
        confirmButton = {
            TextButton(onClick = { onContinue(remember) }) { Text(stringResource(R.string.samsung_user_confirmation_continue)) }
        }
    )
}
