package jp.linkserver.shutter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.linkserver.shutter.R
import jp.linkserver.shutter.update.UpdateDeveloperSettings

@Composable
internal fun DeveloperUpdateSettingsSection(
    settings: UpdateDeveloperSettings,
    onIgnoreVersionComparisonChange: (Boolean) -> Unit,
    onIgnoreIntervalChange: (Boolean) -> Unit
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
                SettingsGroupPosition.Bottom, onIgnoreIntervalChange)
        }
    }
}

@Composable
private fun DeveloperUpdateToggle(
    title: String, description: String, checked: Boolean, position: SettingsGroupPosition,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsItemSurface(false, position) {
        Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked, onCheckedChange = null)
        }
    }
}
