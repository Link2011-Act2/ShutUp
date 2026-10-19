package jp.linkserver.shutter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.linkserver.pyrite.settings.SettingsGroup
import jp.linkserver.pyrite.settings.SettingsSwitchItem
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
        SettingsGroup(title = stringResource(R.string.settings_developer)) {
            item("ignore_version") {
                SettingsSwitchItem(stringResource(R.string.developer_ignore_version_title), settings.ignoreVersionComparison,
                    onIgnoreVersionComparisonChange, summary = stringResource(R.string.developer_ignore_version_description))
            }
            item("ignore_interval") {
                SettingsSwitchItem(stringResource(R.string.developer_ignore_interval_title), settings.ignoreInterval,
                    onIgnoreIntervalChange, summary = stringResource(R.string.developer_ignore_interval_description))
            }
            if (warningSettings.available) {
                item("force_samsung") {
                    SettingsSwitchItem(stringResource(R.string.developer_force_samsung_title), warningSettings.forceSamsung,
                        onForceSamsungChange, enabled = !environmentBusy,
                        summary = stringResource(R.string.developer_force_samsung_description))
                }
                item("force_non_main_user") {
                    SettingsSwitchItem(stringResource(R.string.developer_force_non_main_user_title), warningSettings.forceNonMainUser,
                        onForceNonMainUserChange, enabled = !environmentBusy,
                        summary = stringResource(R.string.developer_force_non_main_user_description))
                }
                item("emulate_csc") {
                    SettingsSwitchItem(stringResource(R.string.developer_emulate_csc_title), warningSettings.emulateCsc,
                        onEmulateCscChange, enabled = !environmentBusy,
                        summary = stringResource(R.string.developer_emulate_csc_description))
                }
            }
        }
        if (warningSettings.available) Text(stringResource(R.string.developer_warning_simulation_note),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp))
    }
}
