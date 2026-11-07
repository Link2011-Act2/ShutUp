package jp.linkserver.shutter.update

import jp.linkserver.shutter.*
import org.junit.Assert.*
import org.junit.Test

class GoldyMigrationTest {
    /** Frozen pre-migration selector: compare candidate, notes, ties, channel and developer preview. */
    private fun legacySelect(releases: List<GitHubRelease>, installed: String, preview: Boolean): GitHubRelease? {
        if (preview && isIntDevUpdateBuild(installed)) return releases.filter { acceptsRelease(it, installed) }
            .maxByOrNull { it.publishedAt }?.copy(intermediateNotes = emptyList())
        require(compareReleaseVersions(installed, installed) != null)
        val candidates = releases.filter { acceptsRelease(it, installed) &&
            (compareReleaseVersions(it.tag, installed) ?: -1) > 0 }
            .sortedWith { a, b -> compareReleaseVersions(b.tag, a.tag) ?: 0 }
        return candidates.firstOrNull()?.copy(intermediateNotes = candidates.drop(1).map { ReleaseNote(it.tag, it.notes) })
    }

    @Test fun goldyMatchesLegacyCandidateAndHistoryAcrossChannelsAndPreview() {
        val tags = listOf("v0.9.0", "1.0.0", "v1.0.0-Release", "1.0.0-Stable", "1.0.0-Beta2",
            "1.0.0-Beta10", "1.0.0-IntDev_rev2", "1.0.0-IntDev_rev10", "1.1.0-Internal",
            "1.1.0-Beta", "1.2.0", "v1.2.0", "1.2.0-RC1", "nightly", "broken", "1.0-Alpha")
        val releases = tags.flatMapIndexed { index, tag -> listOf(false, true).map { prerelease ->
            GitHubRelease(tag, "notes-$index-$prerelease", "${AppLinks.REPOSITORY}/releases/tag/$tag",
                prerelease, publishedAt = (index % 5).toLong())
        } }
        val catalogs = listOf(emptyList(), releases, releases.reversed()) + releases.chunked(3) + releases.map { listOf(it) }
        var comparisons = 0
        for (installed in tags + listOf("2.0.0", "1.0.0-IntDev", "", "unknown")) {
            for (preview in listOf(false, true)) for (catalog in catalogs) {
                val before = runCatching { legacySelect(catalog, installed, preview) }
                val after = runCatching { selectWithGoldy(catalog, installed, preview) }
                assertEquals("$installed / preview=$preview / $catalog", before.getOrNull(), after.getOrNull())
                assertEquals(before.exceptionOrNull()?.javaClass, after.exceptionOrNull()?.javaClass)
                comparisons++
            }
        }
        assertEquals(1840, comparisons)
    }

    @Test fun hostPresentationRetainsChosenApkDigestAndIntermediateNotes() {
        val release = GitHubRelease("v2.0.0", "Latest", "${AppLinks.REPOSITORY}/releases/tag/v2.0.0",
            apkName = "ShutUp.apk", apkUrl = "${AppLinks.REPOSITORY}/releases/download/v2.0.0/ShutUp.apk",
            apkSha256 = "a".repeat(64), intermediateNotes = listOf(ReleaseNote("v1.5.0", "Earlier")))
        val candidate = release.toGoldyCandidate()
        assertEquals(release.apkUrl, candidate.release.assets.single().downloadUrl)
        assertEquals("sha256:${release.apkSha256}", candidate.release.assets.single().digest)
        assertEquals("Earlier", candidate.intermediateReleases.single().notes)
        assertEquals(release.copy(intermediateNotes = emptyList()), candidate.release.toShutUpRelease())
    }

    @Test fun legacyApkChoiceKeepsFirstPreferredNameEvenWhenAmbiguous() {
        val source = jp.linkserver.goldy.GitHubRelease(42, "v2.0.0", url = "${AppLinks.REPOSITORY}/releases/tag/v2.0.0",
            assets = listOf("other.apk", "ShutUp-arm.apk", "shutup-x86.apk").mapIndexed { index, name ->
                jp.linkserver.goldy.GitHubAsset(index.toLong() + 1, name,
                    "${AppLinks.REPOSITORY}/releases/download/v2.0.0/$name", 1, null)
            })
        assertEquals("ShutUp-arm.apk", source.toShutUpRelease().apkName)
    }
}
