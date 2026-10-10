package jp.linkserver.shutter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.linkserver.shutter.R
import jp.linkserver.shutter.maintain.AutoMaintainState

@Composable
internal fun AutoMaintainSettingsSection(state: AutoMaintainState, onEnabledChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AboutSectionTitle(stringResource(R.string.settings_shutter_sound))
        SettingsItemSurface(false, SettingsGroupPosition.Single) {
            Row(Modifier.fillMaxWidth().toggleable(state.enabled, role = Role.Switch, onValueChange = onEnabledChange)
                .padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.auto_maintain_title), style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.auto_maintain_description), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(when {
                        !state.enabled -> R.string.auto_maintain_state_off
                        !state.armed -> R.string.auto_maintain_state_paused
                        else -> R.string.auto_maintain_state_active
                    }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Switch(state.enabled, onCheckedChange = null)
            }
        }
    }
}
