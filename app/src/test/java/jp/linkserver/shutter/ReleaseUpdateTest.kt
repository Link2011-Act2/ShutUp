package jp.linkserver.shutter

import org.junit.Assert.*
import org.junit.Test

class ReleaseUpdateTest {
    private fun release(tag: String, prerelease: Boolean = false) =
        GitHubRelease(tag, tag, "${AppLinks.REPOSITORY}/releases/tag/$tag", prerelease)

    @Test fun selectsByVersionWithinTheInstalledChannel() {
        val releases = listOf(release("v1.2.0"), release("v1.3.0-Beta2", true), release("v1.4.0-IntDev", true))
        assertEquals("v1.2.0", selectReleaseUpdate(releases, "1.0.0-Release")!!.tag)
        assertEquals("v1.3.0-Beta2", selectReleaseUpdate(releases, "1.0.0-Beta")!!.tag)
        assertEquals("v1.4.0-IntDev", selectReleaseUpdate(releases, "1.0.0-IntDev")!!.tag)
        assertNull(selectReleaseUpdate(releases, "2.0.0"))
        assertNull(selectReleaseUpdate(listOf(release("nightly")), "1.0.0"))
    }

    @Test fun sameVersionPromotionAndNumberedBetaOrdering() {
        assertTrue(compareReleaseVersions("v1.0.0-Beta10", "1.0.0-Beta2")!! > 0)
        val release = selectReleaseUpdate(listOf(release("v1.0.0-Release")), "1.0.0-IntDev")
        assertNotNull(release)
        assertNull(selectReleaseUpdate(listOf(release("v1.0.0-Beta3", true)), "1.0.0-Release"))
    }

    @Test fun intermediateNotesAreNewerEligibleVersionsInDescendingOrder() {
        val update = selectReleaseUpdate(listOf(release("v1.1.0"), release("v0.9.0"),
            release("v1.3.0"), release("v1.4.0-Beta", true), release("v1.2.0")), "1.0.0")!!
        assertEquals("v1.3.0", update.tag)
        assertEquals(listOf("v1.2.0", "v1.1.0"), update.intermediateNotes.map { it.tag })
    }

    @Test fun acceptsOnlyRepositoryReleaseAndDownloadUrls() {
        assertTrue(isRepositoryReleaseUrl("${AppLinks.REPOSITORY}/releases/tag/v1.0"))
        assertTrue(isRepositoryReleaseUrl("${AppLinks.REPOSITORY}/releases/download/v1.0/ShutUp.apk", true))
        assertFalse(isRepositoryReleaseUrl("http://github.com/Link2011-Act2/ShutUp/releases/tag/v1"))
        assertFalse(isRepositoryReleaseUrl("https://github.com.evil.example/Link2011-Act2/ShutUp/releases/tag/v1"))
        assertFalse(isRepositoryReleaseUrl("${AppLinks.REPOSITORY}/releases/download/../../../../../other.apk", true))
        assertFalse(isRepositoryReleaseUrl("${AppLinks.REPOSITORY}/releases/tag/v1", true))
    }
}
