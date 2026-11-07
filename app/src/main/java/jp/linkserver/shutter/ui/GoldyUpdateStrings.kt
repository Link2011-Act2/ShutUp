package jp.linkserver.shutter.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import jp.linkserver.goldy.compose.UpdateNotificationStrings
import jp.linkserver.goldy.compose.UpdateStrings
import jp.linkserver.shutter.R

@Composable
internal fun goldyNotificationStrings() = UpdateNotificationStrings(
    title = stringResource(R.string.update_screen_title),
    available = stringResource(R.string.goldy_update_available),
    openDetails = stringResource(R.string.update_confirm),
    hideBanner = stringResource(R.string.update_banner_dismiss),
    ignoreVersion = stringResource(R.string.update_ignore),
    close = stringResource(android.R.string.cancel),
    iconDescription = stringResource(R.string.update_action_description),
    check = stringResource(R.string.about_check_updates), retry = stringResource(R.string.goldy_update_retry),
    checking = stringResource(R.string.about_update_checking), idle = stringResource(R.string.about_update_not_checked),
    upToDate = stringResource(R.string.about_update_latest), noReleases = stringResource(R.string.about_update_no_releases),
    noEligible = stringResource(R.string.about_update_latest), unsupported = stringResource(R.string.about_update_failed),
    disabled = stringResource(R.string.about_update_not_checked), failure = stringResource(R.string.about_update_failed),
    throttled = stringResource(R.string.about_update_failed), incomplete = stringResource(R.string.goldy_update_incomplete),
    cached = stringResource(R.string.goldy_update_cached)
)

@Composable
internal fun goldyUpdateStrings() = UpdateStrings(
    title = stringResource(R.string.update_screen_title), back = stringResource(R.string.action_back),
    releaseNotes = stringResource(R.string.update_notes_title), noNotes = stringResource(R.string.about_update_no_notes),
    history = stringResource(R.string.update_intermediate_notes_title),
    expand = stringResource(R.string.update_notes_expanded), collapse = stringResource(R.string.update_notes_collapsed),
    openRelease = stringResource(R.string.about_open_release),
    download = stringResource(R.string.update_download_install), cancel = stringResource(R.string.update_cancel_download),
    retry = stringResource(R.string.goldy_update_retry), install = stringResource(R.string.update_install),
    allowInstall = stringResource(R.string.goldy_update_allow_install), permissionRequired = stringResource(R.string.update_permission_required),
    checking = stringResource(R.string.about_update_checking), noUpdate = stringResource(R.string.about_update_latest),
    noApk = stringResource(R.string.goldy_update_no_apk), downloading = stringResource(R.string.goldy_update_downloading),
    verifying = stringResource(R.string.goldy_update_verifying), preparing = stringResource(R.string.goldy_update_preparing),
    ready = stringResource(R.string.goldy_update_ready), handedOff = stringResource(R.string.goldy_update_handed_off),
    cancelled = stringResource(R.string.goldy_update_cancelled), incompleteHistory = stringResource(R.string.goldy_update_incomplete)
)
