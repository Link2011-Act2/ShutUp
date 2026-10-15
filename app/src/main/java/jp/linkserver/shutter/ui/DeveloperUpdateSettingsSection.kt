package jp.linkserver.shutter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.linkserver.shutter.R
import jp.linkserver.shutter.WarningDeveloperSettings
import jp.linkserver.shutter.update.UpdateDeveloperSettings

@Composable
internal fun DeveloperUpdateSettingsSection(
    settings: UpdateDeveloperSettings,
    onIgnoreVersionComparisonChange: (Boolean) -> Unit,
    onIgnoreIntervalChange: (Boolean) -> Unit,
    warningSettings: WarningDeveloperSettings = WarningDeveloperSettings(),
    onForceSamsungChange: (Boolean) -> Unit = {},
    onForceNonMainUserChange: (Boolean) -> Unit = {},
    environmentBusy: Boolean = false,
    onEmulateCscChange: (Boolean) -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AboutSectionTitle(stringResource(R.string.settings_developer))
        Column {
            DeveloperUpdateToggle(stringResource(R.string.developer_ignore_version_title),
                stringResource(R.string.developer_ignore_version_description), settings.ignoreVersionComparison,
                SettingsGroupPosition.Top, onIgnoreVersionComparisonChange)
            SettingsDividerGap()
            DeveloperUpdateToggle(stringResource(R.string.developer_ignore_interval_title),
                stringResource(R.string.developer_ignore_interval_description), settings.ignoreInterval,
                if (warningSettings.available) SettingsGroupPosition.Middle else SettingsGroupPosition.Bottom,
                onIgnoreIntervalChange)
            if (warningSettings.available) {
                SettingsDividerGap()
                DeveloperUpdateToggle(stringResource(R.string.developer_force_samsung_title),
                    stringResource(R.string.developer_force_samsung_description), warningSettings.forceSamsung,
                    SettingsGroupPosition.Middle, onForceSamsungChange, enabled = !environmentBusy)
                SettingsDividerGap()
                DeveloperUpdateToggle(stringResource(R.string.developer_force_non_main_user_title),
                    stringResource(R.string.developer_force_non_main_user_description), warningSettings.forceNonMainUser,
                    SettingsGroupPosition.Middle, onForceNonMainUserChange, enabled = !environmentBusy)
                SettingsDividerGap()
                DeveloperUpdateToggle(stringResource(R.string.developer_emulate_csc_title),
                    stringResource(R.string.developer_emulate_csc_description), warningSettings.emulateCsc,
                    SettingsGroupPosition.Bottom, onEmulateCscChange, enabled = !environmentBusy)
            }
        }
        if (warningSettings.available) Text(stringResource(R.string.developer_warning_simulation_note),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
private fun DeveloperUpdateToggle(
    title: String, description: String, checked: Boolean, position: SettingsGroupPosition,
    onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true
) {
    SettingsItemSurface(false, position) {
        Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsSwitch(checked = checked)
        }
    }
}
