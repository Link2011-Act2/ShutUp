package jp.linkserver.shutter.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.linkserver.shutter.*
import jp.linkserver.shutter.R
import jp.linkserver.shutter.update.UpdateDownloadState
import jp.linkserver.shutter.update.UpdateDownloadViewModel
import java.util.Locale

@Composable
internal fun AppUpdateAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { Badge() }) {
            Icon(painterResource(R.drawable.ic_browser_updated), stringResource(R.string.update_action_description))
        }
    }
}

@Composable
internal fun AppUpdateBanner(update: GitHubRelease, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Surface(onClick = onOpen, shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.update_screen_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.update_banner_body, displayVersion(update.tag)),
                    style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, stringResource(R.string.update_banner_dismiss))
            }
        }
    }
}

@Composable
internal fun AppUpdatePrompt(update: GitHubRelease, onConfirm: () -> Unit,
    onIgnore: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_browser_updated), null) },
        title = { Text(stringResource(R.string.update_screen_title)) },
        text = { Text(stringResource(R.string.update_prompt_body, displayVersion(update.tag))) },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.update_confirm))
                }
                OutlinedButton(onClick = onIgnore, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.update_ignore))
                }
            }
        })
}

@Composable
internal fun UpdateOverviewScreen(release: GitHubRelease, onBack: () -> Unit) {
    val context = LocalContext.current
    val downloads: UpdateDownloadViewModel = viewModel()
    val download by downloads.state.collectAsStateWithLifecycle()
    val active = download.takeIf { it.tag == release.tag } ?: UpdateDownloadState()
    var feedback by rememberSaveable(release.tag) { mutableStateOf<String?>(null) }

    fun openInstaller() {
        val file = active.file ?: return
        feedback = null
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) { feedback = context.getString(R.string.update_install_failed) }
    }

    LaunchedEffect(active.pendingInstall, active.file) {
        if (active.pendingInstall && active.file != null) {
            downloads.consumeInstallRequest()
            openInstaller()
        }
    }

    UpdateOverviewContent(release, active, onBack, feedback) {
        feedback = null
        when {
            download.busy -> downloads.cancel()
            release.apkUrl == null -> openUrl(context, release.url)
            !context.packageManager.canRequestPackageInstalls() -> {
                feedback = context.getString(R.string.update_permission_required)
                try {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")))
                } catch (_: Exception) { feedback = context.getString(R.string.settings_open_failed) }
            }
            active.file?.exists() == true -> openInstaller()
            else -> downloads.download(release)
        }
    }
}

@Composable
internal fun UpdateOverviewContent(release: GitHubRelease, download: UpdateDownloadState,
    onBack: () -> Unit, feedback: String? = null, onAction: () -> Unit) {
    val context = LocalContext.current
    var intermediateExpanded by rememberSaveable(release.tag) { mutableStateOf(false) }
    ScreenScaffold(stringResource(R.string.update_page_title), onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(displayVersion(release.tag), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                val channel = when (detectReleaseChannel(release.tag)) {
                    "IntDev" -> R.string.about_channel_development
                    "Beta" -> R.string.about_channel_beta
                    else -> if (release.prerelease) R.string.about_channel_unknown else R.string.about_channel_release
                }
                SuggestionChip(onClick = {}, label = { Text(stringResource(channel)) })
                AboutSectionTitle(stringResource(R.string.update_notes_title))
                SettingsItemSurface(false, SettingsGroupPosition.Single) {
                    SelectionContainer {
                        Text(release.notes.ifBlank { stringResource(R.string.about_update_no_notes) },
                            Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (release.intermediateNotes.isNotEmpty()) {
                    OutlinedButton(onClick = { intermediateExpanded = !intermediateExpanded },
                        modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.update_intermediate_notes, release.intermediateNotes.size))
                    }
                    if (intermediateExpanded) release.intermediateNotes.forEach { note ->
                        SettingsItemSurface(false, SettingsGroupPosition.Single) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(displayVersion(note.tag), style = MaterialTheme.typography.titleMedium)
                                SelectionContainer {
                                    Text(note.notes.ifBlank { stringResource(R.string.about_update_no_notes) },
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                if (release.apkUrl != null) {
                    TextButton(onClick = { openUrl(context, release.url) }) {
                        Text(stringResource(R.string.about_open_release))
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (download.busy) {
                        val progress = download.total?.let { download.downloaded.toFloat() / it }
                        if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        val amount = String.format(Locale.ROOT, "%.1f MB", download.downloaded / (1024f * 1024f))
                        Text(stringResource(R.string.update_downloading, amount), style = MaterialTheme.typography.bodySmall)
                    }
                    download.failureMessage?.let { Text(it,
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = onAction, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(when {
                            download.busy -> R.string.update_cancel_download
                            release.apkUrl == null -> R.string.about_open_release
                            download.file != null -> R.string.update_install
                            else -> R.string.update_download_install
                        }))
                    }
                }
            }
        }
    }
}
