// Frozen legacy/reference adapter; tests only.
package jp.linkserver.shutter.update

import jp.linkserver.goldy.CheckOutcome
import jp.linkserver.goldy.DeveloperOptions
import jp.linkserver.goldy.GitHubAsset
import jp.linkserver.goldy.GitHubRepository
import jp.linkserver.goldy.GoldyConfig
import jp.linkserver.goldy.GoldyState
import jp.linkserver.goldy.GoldyError
import jp.linkserver.goldy.InstalledVersion
import jp.linkserver.goldy.ReleaseCatalog
import jp.linkserver.goldy.ReleaseSelector
import jp.linkserver.goldy.RuntimeOptions
import jp.linkserver.goldy.UpdateCandidate
import jp.linkserver.goldy.UpdatePolicies
import jp.linkserver.shutter.GitHubRelease
import jp.linkserver.shutter.ReleaseNote
import jp.linkserver.shutter.compareReleaseVersions
import java.time.Instant
import java.net.URLEncoder
import jp.linkserver.goldy.GitHubRelease as GoldRelease

internal fun shutUpGoldyConfig(installed: String) = GoldyConfig(
    GitHubRepository("Link2011-Act2", "ShutUp"), UpdatePolicies.shutUp(),
    userAgent = "ShutUp/$installed"
)

/** IDs below identify host presentation metadata only, never a verified APK or install request. */
internal fun GitHubRelease.toGoldyRelease(id: Long = 1) = GoldRelease(
    id, tag, notes = notes, url = url, prerelease = prerelease,
    publishedAt = Instant.ofEpochMilli(publishedAt),
    assets = if (apkUrl != null) listOf(GitHubAsset(id, apkName ?: "ShutUp.apk", apkUrl,
        null, apkSha256?.let { "sha256:$it" })) else emptyList()
)

internal fun GitHubRelease.toGoldyCandidate() = UpdateCandidate(toGoldyRelease(),
    intermediateNotes.mapIndexed { index, note ->
        GoldRelease(index.toLong() + 2, note.tag, notes = note.notes,
            url = "https://github.com/Link2011-Act2/ShutUp/releases/tag/" +
                URLEncoder.encode(note.tag, "UTF-8").replace("+", "%20"))
    })

internal fun AppUpdateState.toGoldyState() = GoldyState(
    availableUpdate?.toGoldyCandidate(), notificationUpdate?.toGoldyCandidate(), showBanner,
    checking = checking, lastOutcome = if (checkFailed) CheckOutcome.Failure(GoldyError.Network) else null
)

/** Retain ShutUp's first matching APK choice until the transfer/security migration is reviewed. */
internal fun GoldRelease.toShutUpRelease(): GitHubRelease {
    val apk = assets.filter { it.name.endsWith(".apk", true) }
        .maxByOrNull { if (it.name.contains("shutup", true)) 1 else 0 }
    return GitHubRelease(tag, notes, url, prerelease, apk?.name, apk?.downloadUrl,
        apk?.digest?.takeIf { it.matches(Regex("sha256:[0-9a-fA-F]{64}")) }?.substringAfter(':'),
        publishedAt = publishedAt?.toEpochMilli() ?: 0)
}

internal fun selectWithGoldy(releases: List<GitHubRelease>, installed: String,
    ignoreVersionComparison: Boolean): GitHubRelease? {
    val preview = ignoreVersionComparison && isIntDevUpdateBuild(installed)
    if (!preview) require(compareReleaseVersions(installed, installed) != null) { "Unrecognized installed version" }
    // ShutUp deliberately checks the first 100 releases; keep that selection window.
    val result = ReleaseSelector.select(
        ReleaseCatalog(releases.mapIndexed { index, release -> release.toGoldyRelease(index.toLong() + 1) }),
        InstalledVersion(installed, 0), UpdatePolicies.shutUp(),
        RuntimeOptions(developer = DeveloperOptions(ignoreVersionComparison = preview)))
    val candidate = (result as? CheckOutcome.Available)?.update ?: return null
    return releases[candidate.release.id.toInt() - 1].copy(intermediateNotes =
        candidate.intermediateReleases.map { ReleaseNote(it.tag, it.notes) })
}
