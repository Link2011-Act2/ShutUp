package jp.linkserver.shutter.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.linkserver.shutter.*
import jp.linkserver.shutter.R
import jp.linkserver.shutter.update.UpdateDownloadState
import jp.linkserver.shutter.update.UpdateDownloadViewModel

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
