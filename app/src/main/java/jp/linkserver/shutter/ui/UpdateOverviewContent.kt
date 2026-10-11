package jp.linkserver.shutter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.linkserver.shutter.GitHubRelease
import jp.linkserver.shutter.R
import jp.linkserver.shutter.ReleaseNote
import jp.linkserver.shutter.detectReleaseChannel
import jp.linkserver.shutter.displayVersion
import jp.linkserver.shutter.update.UpdateDownloadState
import java.util.Locale

/** Layout ported from NITTC Scheduler's UpdateOverviewScreen; download ownership stays in the ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdateOverviewContent(
    release: GitHubRelease,
    download: UpdateDownloadState,
    onBack: () -> Unit,
    feedback: String? = null,
    onAction: () -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.update_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                }
            )
        },
        bottomBar = { UpdateDownloadBar(release, download, feedback, onAction) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 840.dp).fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(displayVersion(release.tag), Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    ReleaseChannelChip(release.tag, release.prerelease)
                }
                Text(stringResource(R.string.update_notes_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().testTag("latest-release-notes")
                ) {
                    ReleaseNotesMarkdown(release.notes, Modifier.padding(16.dp))
                }
                if (release.intermediateNotes.isNotEmpty()) {
                    IntermediateReleaseNotes(release.tag, release.intermediateNotes)
                }
                // Retain ShutUp's direct link to the release, alongside the reference layout.
                if (release.apkUrl != null) {
                    TextButton(onClick = { openUrl(context, release.url) }) {
                        Text(stringResource(R.string.about_open_release))
                    }
                }
                Spacer(Modifier.height(84.dp))
            }
        }
    }
}

@Composable
private fun ReleaseChannelChip(tag: String, prerelease: Boolean = false) {
    val label = when (detectReleaseChannel(tag)) {
        "IntDev" -> R.string.about_channel_development
        "Beta" -> R.string.about_channel_beta
        "Stable", "Release" -> R.string.about_channel_release
        else -> if (prerelease) R.string.about_channel_unknown else R.string.about_channel_release
    }
    AssistChip(onClick = {}, label = { Text(stringResource(label)) })
}

@Composable
private fun IntermediateReleaseNotes(tag: String, notes: List<ReleaseNote>) {
    var expanded by rememberSaveable(tag) { mutableStateOf(false) }
    val stateLabel = stringResource(if (expanded) R.string.update_notes_expanded else R.string.update_notes_collapsed)
    Surface(shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().semantics { stateDescription = stateLabel }
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.update_intermediate_notes_title),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(pluralStringResource(R.plurals.update_intermediate_notes_count, notes.size, notes.size),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more), null)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    notes.forEach { note ->
                        Surface(shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth().testTag("release-note-${note.tag}")) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(displayVersion(note.tag), Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    ReleaseChannelChip(note.tag)
                                }
                                ReleaseNotesMarkdown(note.notes)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun UpdateDownloadBar(release: GitHubRelease, download: UpdateDownloadState,
    feedback: String?, onAction: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth(),
                enabled = download.busy || release.apkUrl != null || release.url.isNotBlank()) {
                if (download.busy) {
                    LoadingIndicator(Modifier.padding(end = 8.dp).size(16.dp))
                } else {
                    Icon(painterResource(R.drawable.ic_download), null, Modifier.padding(end = 8.dp))
                }
                Text(stringResource(when {
                    download.busy -> R.string.update_cancel_download
                    release.apkUrl == null -> R.string.about_open_release
                    download.file != null -> R.string.update_install
                    else -> R.string.update_download_install
                }))
            }
            if (download.busy) {
                val downloadedMb = String.format(Locale.ROOT, "%.1f", download.downloaded / (1024f * 1024f))
                val total = download.total?.takeIf { it > 0 }
                val status = if (total != null) {
                    stringResource(R.string.update_downloading_progress,
                        (download.downloaded.toDouble() * 100 / total).toInt().coerceIn(0, 100),
                        downloadedMb, String.format(Locale.ROOT, "%.1f", total / (1024f * 1024f)))
                } else stringResource(R.string.update_downloading_progress_unknown_total, downloadedMb)
                Text(status, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            download.failureMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            feedback?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            release.apkName?.let {
                Text(stringResource(R.string.update_asset_name, it), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
