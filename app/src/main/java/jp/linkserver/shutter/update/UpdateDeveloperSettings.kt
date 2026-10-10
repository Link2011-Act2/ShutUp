package jp.linkserver.shutter.update

import jp.linkserver.shutter.detectReleaseChannel

internal data class UpdateDeveloperSettings(
    val available: Boolean = false,
    val ignoreVersionComparison: Boolean = false,
    val ignoreInterval: Boolean = false
)

internal fun isIntDevUpdateBuild(version: String) = detectReleaseChannel(version) == "IntDev"

internal fun resolveUpdateDeveloperSettings(
    installedVersion: String, ignoreVersionComparison: Boolean, ignoreInterval: Boolean
): UpdateDeveloperSettings = if (isIntDevUpdateBuild(installedVersion)) {
    UpdateDeveloperSettings(true, ignoreVersionComparison, ignoreInterval)
} else UpdateDeveloperSettings()
