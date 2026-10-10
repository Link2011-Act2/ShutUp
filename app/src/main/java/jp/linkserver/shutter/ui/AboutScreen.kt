package jp.linkserver.shutter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.linkserver.shutter.*
import jp.linkserver.shutter.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Compose port of glyphvisualizer AboutScreen, excluding known issues and debug logs. */
@Composable
internal fun AboutScreen(onBack: () -> Unit, onOssLicenses: () -> Unit,
    onCheckUpdates: suspend () -> Result<GitHubRelease?>, onUpdateAvailable: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val versionName = BuildConfig.VERSION_NAME
    val channelName = remember(versionName) { detectReleaseChannel(versionName) }
    val channelTitle = when (channelName) {
        "IntDev" -> R.string.about_channel_development
        "Beta" -> R.string.about_channel_beta
        "Stable", "Release" -> R.string.about_channel_release
        else -> R.string.about_channel_unknown
    }
    val description = when (channelTitle) {
        R.string.about_channel_development -> R.string.about_channel_development_description
        R.string.about_channel_beta -> R.string.about_channel_beta_description
        R.string.about_channel_release -> R.string.about_channel_release_description
        else -> R.string.about_channel_unknown_description
    }
    val channelLabel = stringResource(channelTitle)
    val build = BuildConfig.BUILD_NUMBER
    var showChannel by rememberSaveable { mutableStateOf(false) }
    var showVersion by rememberSaveable { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var updateResult by rememberSaveable { mutableStateOf("unchecked") }
    var latestTag by rememberSaveable { mutableStateOf("") }

    fun checkUpdates() {
        if (checking) return
        checking = true
        scope.launch {
            try {
                val result = onCheckUpdates()
                result.onSuccess { latest ->
                    latestTag = latest?.tag.orEmpty()
                    updateResult = if (latest == null) "latest" else "available"
                    if (latest != null) onUpdateAvailable()
                }.onFailure { updateResult = "failed" }
            } catch (error: CancellationException) {
                throw error
            } finally {
                checking = false
            }
        }
    }

    ScreenScaffold(stringResource(R.string.about_screen_title), onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 18.dp)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            AboutOverviewMosaic(channelName, channelLabel, versionName, versionName.substringBefore('-'),
                BuildConfig.VERSION_CODE, build, false, { showVersion = true }, { showChannel = true })

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AboutSectionTitle(stringResource(R.string.about_updates_title))
                SettingsItemSurface(false, SettingsGroupPosition.Single) {
                    Column {
                        Text(stringResource(R.string.about_updates_body), Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp)
                        Text(stringResource(R.string.update_auto_check_description),
                            Modifier.padding(horizontal = 22.dp).padding(bottom = 16.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(Modifier.padding(horizontal = 22.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val updateStatus = when {
                                checking -> stringResource(R.string.about_update_checking)
                                updateResult == "none" -> stringResource(R.string.about_update_no_releases)
                                updateResult == "unknown" -> stringResource(R.string.about_update_unknown_version, displayVersion(latestTag))
                                updateResult == "available" -> stringResource(R.string.about_update_available, displayVersion(latestTag))
                                updateResult == "latest" -> stringResource(R.string.about_update_latest)
                                updateResult == "failed" -> stringResource(R.string.about_update_failed)
                                else -> stringResource(R.string.about_update_not_checked)
                            }
                            Text(updateStatus,
                                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (updateResult == "unchecked" && !checking) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium)
                            Button(onClick = ::checkUpdates, enabled = !checking) {
                                Text(stringResource(R.string.about_check_updates), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AboutSectionTitle(stringResource(R.string.settings_information))
                Column {
                    SettingsItemSurface(false, SettingsGroupPosition.Top) {
                        AboutSettingsActionCardContent(stringResource(R.string.about_support_title),
                            stringResource(R.string.about_support_description), stringResource(R.string.about_support_open_site),
                            false) { openUrl(context, AppLinks.REPOSITORY) }
                    }
                    SettingsDividerGap()
                    SettingsItemSurface(false, SettingsGroupPosition.Bottom) {
                        AboutSettingsActionCardContent(stringResource(R.string.about_licenses_title),
                            stringResource(R.string.about_licenses_description), stringResource(R.string.about_oss_open),
                            false, onOssLicenses)
                    }
                }
            }
        }
    }
    if (showChannel) TextDialog(stringResource(R.string.about_channel_dialog_title, channelLabel), stringResource(description), { showChannel = false })
    if (showVersion) TextDialog(stringResource(R.string.about_version_details_title),
        stringResource(R.string.about_version_details, displayVersion(versionName), BuildConfig.VERSION_CODE, build),
        { showVersion = false })
}

@Composable
internal fun TextDialog(title: String, text: String, onDismiss: () -> Unit,
    actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { SelectionContainer { Text(text, Modifier.verticalScroll(rememberScrollState())) } },
        confirmButton = {
            TextButton(onClick = { if (onAction == null) onDismiss() else onAction() }) {
                Text(actionLabel ?: stringResource(android.R.string.ok))
            }
        }, dismissButton = {
            if (onAction != null) TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        })
}

