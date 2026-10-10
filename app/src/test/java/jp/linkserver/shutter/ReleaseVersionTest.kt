package jp.linkserver.shutter

import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionTest {
    @Test fun comparesVersionPartsNumerically() {
        assertTrue(compareReleaseVersions("v1.10.0", "1.9.9")!! > 0)
        assertTrue(compareReleaseVersions("v2.0", "10.0")!! < 0)
        assertEquals(0, compareReleaseVersions("v1.0.0", "1.0"))
        assertTrue(compareReleaseVersions("v100", "99")!! > 0)
    }

    @Test fun releaseSupersedesPrereleaseAtSameVersion() {
        assertTrue(compareReleaseVersions("v1.0", "1.0-Beta")!! > 0)
        assertTrue(compareReleaseVersions("v1.0-Beta", "1.0")!! < 0)
        assertTrue(compareReleaseVersions("v1.0-rc.2", "1.0-beta.10")!! > 0)
        assertTrue(compareReleaseVersions("v1.0-beta.10", "1.0-beta.2")!! > 0)
        assertTrue(compareReleaseVersions("v1.0-Beta", "1.0-IntDev")!! > 0)
        assertEquals(0, compareReleaseVersions("v1.0-Release", "1.0-Stable"))
    }

    @Test fun buildMetadataDoesNotTriggerAnUpdate() {
        assertEquals(0, compareReleaseVersions("v1.0+build.10", "1.0+build.2"))
    }

    @Test fun unrecognizedVersionsAreNotReportedAsUpToDate() {
        assertNull(compareReleaseVersions("nightly", "1.0"))
        assertNull(compareReleaseVersions("1.1", "development"))
        assertNull(compareReleaseVersions("v99999999999999999999999", "1.0"))
    }
}
