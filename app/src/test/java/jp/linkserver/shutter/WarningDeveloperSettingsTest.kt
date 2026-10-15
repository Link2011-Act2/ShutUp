package jp.linkserver.shutter

import org.junit.Assert.*
import org.junit.Test

class WarningDeveloperSettingsTest {
    @Test fun overridesAreIndependentAndOnlyBothMakeAGoogleMainUserAffected() {
        val actual = AppUserEnvironment("Google", 0)
        for (samsung in listOf(false, true)) for (nonMain in listOf(false, true)) {
            val settings = WarningDeveloperSettings(true, samsung, nonMain)
            val resolved = settings.environment(actual).recognized
            assertEquals(samsung && nonMain, resolved.needsSamsungUserWarning)
            assertEquals(samsung, resolved.samsung)
            assertEquals(if (nonMain) 1 else 0, resolved.userId)
            assertEquals(actual.userId, settings.environment(actual).settingsUserId)
            assertEquals(actual, settings.environment(actual).actual)
        }
        assertEquals(AppUserEnvironment("Google", 0), actual)
    }

    @Test fun offUsesActualIdentityAndOverridesCannotHideARealAffectedEnvironment() {
        val actual = AppUserEnvironment("SaMsUnG", 95)
        for (samsung in listOf(false, true)) for (nonMain in listOf(false, true)) {
            val resolved = WarningDeveloperSettings(true, samsung, nonMain).environment(actual).recognized
            assertTrue(resolved.needsSamsungUserWarning)
            assertEquals(95, resolved.userId)
            assertEquals(95, WarningDeveloperSettings(true, samsung, nonMain).environment(actual).settingsUserId)
        }
        assertEquals(actual, WarningDeveloperSettings(available = true).environment(actual).recognized)
    }

    @Test fun unavailableOverridesAreIgnoredEvenWithStoredFlags() {
        val actual = AppUserEnvironment("Google", 0)
        val settings = WarningDeveloperSettings(false, true, true)
        assertFalse(settings.simulated)
        assertEquals(actual, settings.environment(actual).recognized)
    }
}
