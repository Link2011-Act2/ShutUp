package jp.linkserver.shutter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.linkserver.shutter.R
import jp.linkserver.shutter.maintain.AutoMaintainState
import jp.linkserver.pyrite.settings.SettingsSwitchItem

@Composable
internal fun AutoMaintainSettingsSection(state: AutoMaintainState, onEnabledChange: (Boolean) -> Unit) {
    SettingsSwitchItem(stringResource(R.string.auto_maintain_title), state.enabled, onEnabledChange,
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.auto_maintain_description), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.enabled) {
                    Text(stringResource(if (state.armed) R.string.auto_maintain_state_active
                        else R.string.auto_maintain_state_paused),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        })
}
