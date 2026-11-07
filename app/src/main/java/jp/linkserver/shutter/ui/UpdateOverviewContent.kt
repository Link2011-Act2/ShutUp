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
    EliefOverviewScreen(
        state = UpdateUiState(candidate, apkAvailable = release?.let { ApkSelectionPolicies.ShutUp.select(it) != null } == true,
            transfer = transfer, message = feedback ?: download.message.takeUnless {
                active && download.transfer is UpdateTransferState.Failed
            }),
        onBack = onBack, onOpenUrl = onOpenUrl ?: { openUrl(context, it) }, onAction = onAction,
        strings = goldyUpdateStrings(), channelLabel = release?.let { updateChannelLabel(it.tag, it.prerelease) }.orEmpty(),
        header = {
            header()
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
