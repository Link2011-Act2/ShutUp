package jp.linkserver.shutter.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*
import jp.linkserver.shutter.R
import jp.linkserver.shutter.detectReleaseChannel
import jp.linkserver.shutter.displayVersion
import jp.linkserver.shutter.update.UpdateDownloadState
import jp.linkserver.shutter.update.transferIdentity
import jp.linkserver.goldy.compose.UpdateOverviewScreen as EliefOverviewScreen

@Composable
internal fun UpdateOverviewContent(candidate: UpdateCandidate?, download: UpdateDownloadState,
    onBack: () -> Unit, feedback: String? = null, header: @Composable () -> Unit = {},
    onOpenUrl: ((String) -> Unit)? = null, onAction: (UpdateAction) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val release = candidate?.release
    val active = download.tag == release?.tag && download.releaseIdentity == transferIdentity(candidate)
    val transfer = when {
        download.restoring -> UpdateTransferState.PreparingInstall
        active -> download.transfer
        else -> UpdateTransferState.Idle
    }
    val strings = goldyUpdateStrings().let {
        if (active && download.waiting) it.copy(download = stringResource(R.string.goldy_transfer_restart)) else it
    }
    val prepared = download.preparedRequest.takeIf { candidate == null }
    if (prepared != null) {
        EliefOverviewScreen(preparedApk = prepared.metadata,
            transfer = if (download.restoring) UpdateTransferState.PreparingInstall else download.transfer,
            onBack = onBack, onAction = onAction, strings = strings,
            message = feedback ?: download.message.takeUnless { download.transfer is UpdateTransferState.Failed },
            header = { header() }, presentation = UpdatePresentation(
                backIcon = { Icon(painterResource(R.drawable.ic_arrow_back), null) }))
        return
    }
    EliefOverviewScreen(
        state = UpdateUiState(candidate, apkAvailable = release?.let { ApkSelectionPolicies.ShutUp.select(it) != null } == true,
            transfer = transfer, message = feedback ?: download.message.takeUnless {
                active && download.transfer is UpdateTransferState.Failed
            }),
        onBack = onBack, onOpenUrl = onOpenUrl ?: { openUrl(context, it) }, onAction = onAction,
        strings = strings, channelLabel = release?.let { updateChannelLabel(it.tag, it.prerelease) }.orEmpty(),
        header = {
            header()
            if (active && download.waiting) {
                download.retainedProgress?.let { Text(DownloadProgressFormatter.format(it, resources.configuration.locales[0])) }
                androidx.compose.material3.TextButton(onClick = { onAction(UpdateAction.CancelDownload) }) {
                    Text(stringResource(R.string.update_cancel_download))
                }
            }
            release?.let { ApkSelectionPolicies.ShutUp.select(it) }?.let { asset ->
                Text(stringResource(R.string.update_asset_name, asset.name), style = MaterialTheme.typography.bodySmall)
            }
        },
        presentation = UpdatePresentation(versionLabel = { displayVersion(it.tag) },
            historyChannelLabel = { resources.getString(channelLabelResource(it.tag, it.prerelease)) },
            backIcon = { Icon(painterResource(R.drawable.ic_arrow_back), null) })
    )
}

private fun channelLabelResource(tag: String, prerelease: Boolean) = when (detectReleaseChannel(tag)) {
    "IntDev" -> R.string.about_channel_development
    "Beta" -> R.string.about_channel_beta
    "Stable", "Release" -> R.string.about_channel_release
    else -> if (prerelease) R.string.about_channel_unknown else R.string.about_channel_release
}
@Composable
private fun updateChannelLabel(tag: String, prerelease: Boolean) = stringResource(channelLabelResource(tag, prerelease))
