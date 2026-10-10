package jp.linkserver.shutter

import java.util.Locale

/** Channel markers from glyphvisualizer, resolved from the version suffix. */
internal fun detectReleaseChannel(versionName: String): String {
    val suffix = versionName.substringAfter('-', missingDelimiterValue = "").lowercase(Locale.US)
    return when {
        "intdev" in suffix || "internal" in suffix -> "IntDev"
        "beta" in suffix -> "Beta"
        "stable" in suffix -> "Stable"
        "release" in suffix -> "Release"
        else -> "Unknown"
    }
}

/** UI formatting only; package metadata and release comparisons retain the raw version. */
internal fun displayVersion(versionName: String): String =
    "v" + versionName.trim().replaceFirst(Regex("^v", RegexOption.IGNORE_CASE), "")
