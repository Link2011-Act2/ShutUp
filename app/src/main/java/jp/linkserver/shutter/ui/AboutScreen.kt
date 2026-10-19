package jp.linkserver.shutter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.linkserver.pyrite.about.*
import jp.linkserver.shutter.*
import jp.linkserver.shutter.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Host-owned update state/dialogs around Pyrite's shared About components. */
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
            AboutOverviewMosaic(
                info = AboutInfo(stringResource(R.string.app_name), "$channelLabel ${stringResource(
                    R.string.about_overview_release_line, versionName, BuildConfig.VERSION_CODE).replace(' ', '\u00A0')}"),
                overview = AboutOverviewContent(
                    channel = AboutDetail(stringResource(R.string.about_channel), channelName),
                    build = AboutDetail(stringResource(R.string.about_build), build),
                    version = AboutDetail(stringResource(R.string.about_version),
                        stringResource(R.string.about_version_value, versionName.substringBefore('-'))),
                    attribution = AboutAttribution(stringResource(R.string.about_forked_from),
                        "soralis0912/ShutterSoundToggle", "https://github.com/soralis0912/ShutterSoundToggle")
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                icon = {
                    // This remains ShutUp's launcher artwork, not a library asset.
                    Surface(Modifier.fillMaxSize(), CircleShape,
                        color = colorResource(R.color.ic_launcher_background),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), null,
                            Modifier.fillMaxSize().graphicsLayer(scaleX = 1.5f, scaleY = 1.5f))
                    }
                },
                onVersionClick = { showVersion = true }, onChannelClick = { showChannel = true },
                onAttributionClick = { openUrl(context, it.url) }
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.about_updates_title), Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                val updateStatus = when {
                    checking -> stringResource(R.string.about_update_checking)
                    updateResult == "none" -> stringResource(R.string.about_update_no_releases)
                    updateResult == "unknown" -> stringResource(R.string.about_update_unknown_version, displayVersion(latestTag))
                    updateResult == "available" -> stringResource(R.string.about_update_available, displayVersion(latestTag))
                    updateResult == "latest" -> stringResource(R.string.about_update_latest)
                    updateResult == "failed" -> stringResource(R.string.about_update_failed)
                    else -> stringResource(R.string.about_update_not_checked)
                }
                AboutUpdateCard(stringResource(R.string.about_updates_body), updateStatus,
                    stringResource(R.string.about_check_updates), ::checkUpdates,
                    checking = checking, hasResult = updateResult != "unchecked")
            }
            AboutActionGroup(listOf(
                AboutAction("support", stringResource(R.string.about_support_title),
                    stringResource(R.string.about_support_description), stringResource(R.string.about_support_open_site)),
                AboutAction("licenses", stringResource(R.string.about_licenses_title),
                    stringResource(R.string.about_licenses_description), stringResource(R.string.about_oss_open))
            ), onAction = { if (it.key == "licenses") onOssLicenses() else openUrl(context, AppLinks.REPOSITORY) },
                title = stringResource(R.string.settings_information))
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

