package jp.linkserver.shutter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.linkserver.shutter.GalaxyControlSettings
import jp.linkserver.shutter.R

@Composable
internal fun GalaxyControlSettingsSection(settings: GalaxyControlSettings, busy: Boolean, hasChange: Boolean,
    canRestore: Boolean, onRestore: () -> Unit, onChange: (Boolean) -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AboutSectionTitle(stringResource(R.string.galaxy_settings_title))
        SettingsItemSurface(false, SettingsGroupPosition.Single) {
            Row(Modifier.fillMaxWidth().toggleable(settings.forceAudioPolicy, enabled = !busy && !hasChange,
                role = Role.Switch, onValueChange = { if (it) confirm = true else onChange(false) })
                .padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.galaxy_force_audio_title), style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.galaxy_force_audio_description), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SettingsSwitch(settings.forceAudioPolicy)
            }
        }
        if (hasChange) {
            Text(stringResource(R.string.galaxy_method_restore_first), Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRestore, enabled = !busy && canRestore) { Text(stringResource(R.string.restore_previous)) }
        }
    }
    if (confirm && !busy && !hasChange) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.galaxy_force_audio_confirmation_title)) },
        text = { Text(stringResource(R.string.galaxy_force_audio_confirmation_description)) },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.samsung_user_confirmation_cancel)) } },
        confirmButton = { TextButton(onClick = { confirm = false; onChange(true) }) {
            Text(stringResource(R.string.galaxy_force_audio_enable))
        } }
    )
}
