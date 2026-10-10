package jp.linkserver.shutter

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseChannelTest {
    @Test fun detectsChannelFromVersionSuffix() {
        assertEquals("IntDev", detectReleaseChannel("1.0.0-IntDev"))
        assertEquals("IntDev", detectReleaseChannel("1.0.0-IntDev_rev0"))
        assertEquals("IntDev", detectReleaseChannel("1.0.0-internal"))
        assertEquals("Beta", detectReleaseChannel("1.0.0-BETA_rev2"))
        assertEquals("Stable", detectReleaseChannel("1.0.0-Stable"))
        assertEquals("Release", detectReleaseChannel("1.0.0-Release"))
    }

    @Test fun missingOrUnrecognizedSuffixIsUnknown() {
        assertEquals("Unknown", detectReleaseChannel("1.0.0"))
        assertEquals("Unknown", detectReleaseChannel("1.0.0-Preview"))
        assertEquals("Unknown", detectReleaseChannel("1.0.0+beta"))
    }

    @Test fun displayAddsOneVersionPrefix() {
        assertEquals("v1.0.0-IntDev", displayVersion("1.0.0-IntDev"))
        assertEquals("v1.0.0-Beta", displayVersion("v1.0.0-Beta"))
        assertEquals("v1.0.0-Release", displayVersion("V1.0.0-Release"))
    }
}
