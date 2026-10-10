package jp.linkserver.shutter.update

import org.junit.Assert.*
import org.junit.Test

class UpdateDeveloperSettingsTest {
    @Test fun intDevSettingsDefaultToOffAndCanBeEnabledIndependently() {
        assertEquals(UpdateDeveloperSettings(available = true),
            resolveUpdateDeveloperSettings("1.0.0-IntDev_rev0", false, false))
        assertEquals(UpdateDeveloperSettings(true, true, false),
            resolveUpdateDeveloperSettings("1.0.0-IntDev_rev0", true, false))
        assertEquals(UpdateDeveloperSettings(true, false, true),
            resolveUpdateDeveloperSettings("1.0.0-IntDev", false, true))
        assertEquals(UpdateDeveloperSettings(true, true, true),
            resolveUpdateDeveloperSettings("1.0.0-intdev", true, true))
    }

    @Test fun nonIntDevBuildsDisableEveryCombinationOfSavedSettings() {
        for (version in listOf("1.0.0", "1.0.0-Release", "1.0.0-Stable", "1.0.0-Beta_rev2", "1.0.0-RC", "")) {
            for (comparison in listOf(false, true)) for (interval in listOf(false, true)) {
                assertEquals(version, UpdateDeveloperSettings(), resolveUpdateDeveloperSettings(version, comparison, interval))
            }
        }
    }
}
