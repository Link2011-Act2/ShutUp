package jp.linkserver.shutter

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class SamsungUserWarningPreferencesTest {
    @Test fun confirmationAndExplicitAutomaticChoiceSurviveReloadWithoutChangingExistingPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "samsung_user_warning_preferences_test"
        val prefs = context.getSharedPreferences(name, 0)
        prefs.edit().clear().putBoolean("autoMaintainEnabled", true).putBoolean("autoMaintainArmed", true).commit()
        try {
            val environment = AppUserEnvironment("samsung", 95)
            val first = SamsungUserWarning(environment, SamsungUserWarningPreferences(context, prefs))
            first.confirm(remember = false)
            val reloaded = SamsungUserWarning(environment, SamsungUserWarningPreferences(context, prefs))
            assertTrue(reloaded.confirmed)
            assertTrue(reloaded.confirmationRequired)
            assertFalse(reloaded.automaticAllowed)
            assertTrue(prefs.getBoolean("autoMaintainEnabled", false))
            assertTrue(prefs.getBoolean("autoMaintainArmed", false))
            reloaded.enableAutomatic()
            assertTrue(SamsungUserWarning(environment, SamsungUserWarningPreferences(context, prefs)).automaticAllowed)
            reloaded.confirm(remember = true)
            assertFalse(SamsungUserWarning(environment, SamsungUserWarningPreferences(context, prefs)).confirmationRequired)
            assertTrue(SamsungUserWarning(AppUserEnvironment("samsung", 150),
                SamsungUserWarningPreferences(context, prefs)).confirmationRequired)
        } finally { context.deleteSharedPreferences(name) }
    }

    @Test fun currentUserIsDerivedFromTheTargetApplicationIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(context.applicationInfo.uid / 100_000, AppUserEnvironment.current().userId)
    }
}
