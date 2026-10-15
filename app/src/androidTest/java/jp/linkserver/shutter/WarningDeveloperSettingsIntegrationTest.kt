package jp.linkserver.shutter

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.shutter.maintain.AutoMaintainRuntime
import jp.linkserver.shutter.ui.ShutterApp
import jp.linkserver.shutter.ui.ShutterTheme
import jp.linkserver.shutter.update.AppUpdateViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class WarningDeveloperSettingsIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test fun optionsPersistIndependentlyAndOnlySimulatedConsentIsReset() {
        val prefs = context.getSharedPreferences("warning_simulation_test", 0)
        val realPrefs = context.getSharedPreferences("warning_real_consent_test", 0)
        prefs.edit().clear().commit()
        realPrefs.edit().clear().commit()
        try {
            val store = WarningDeveloperPreferences(context, prefs, "1.1.0-IntDev_rev0")
            val real = SamsungUserWarningPreferences(context, realPrefs)
            val realConsent = SamsungUserConsent("samsung:95", true, true)
            real.save(realConsent)
            assertEquals(WarningDeveloperSettings(available = true), store.load())
            store.save(store.load().copy(forceSamsung = true))
            val restarted = WarningDeveloperPreferences(context, prefs, "1.1.0-IntDev_rev0")
            assertEquals(WarningDeveloperSettings(true, true, false), restarted.load())
            restarted.save(restarted.load().copy(forceNonMainUser = true))
            val warning = SamsungUserWarning(store.load().warningEnvironment(AppUserEnvironment("Google", 0)),
                store.simulationConsent)
            warning.confirm(true)
            warning.enableAutomatic()
            assertFalse(SamsungUserWarning(warning.environment, restarted.simulationConsent).confirmationRequired)
            restarted.save(restarted.load().copy(forceSamsung = false))
            assertEquals(SamsungUserConsent(), store.simulationConsent.load())
            assertEquals(realConsent, real.load())
            assertEquals(WarningDeveloperSettings(true, false, true), store.load())
            for (version in listOf("1.1.0", "1.1.0-Beta", "1.1.0-Stable")) {
                val unavailable = WarningDeveloperPreferences(context, prefs, version)
                assertEquals(WarningDeveloperSettings(), unavailable.load())
                unavailable.save(WarningDeveloperSettings(true, true, false))
                assertEquals(WarningDeveloperSettings(true, false, true), store.load())
            }
        } finally {
            context.deleteSharedPreferences("warning_simulation_test")
            context.deleteSharedPreferences("warning_real_consent_test")
        }
    }

    @Test fun switchesUpdateTheLiveWarningAndResetConsentWithoutRecreatingTheBackend() {
        val runtime = AutoMaintainRuntime.get(context)
        val original = runtime.warningDeveloperSettings.value
        val owner = ViewModelStore()
        val recreatedOwner = ViewModelStore()
        val actual = AppUserEnvironment.current()
        val controller = runtime.controller
        val snapshot = runtime.snapshot.value
        lateinit var model: ShutterViewModel
        lateinit var updates: AppUpdateViewModel
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                ShutterViewModel::class.java -> ShutterViewModel(context.applicationContext as Application,
                    { ShutterSound.Status.READY }, { callback -> callback(Result.success(11)) })
                else -> AppUpdateViewModel(context.applicationContext as Application)
            } as T
        }
        try {
            compose.runOnUiThread {
                runtime.setWarningDeveloperSettings(WarningDeveloperSettings(available = true))
                val provider = ViewModelProvider(owner, factory)
                model = provider[ShutterViewModel::class.java]
                updates = provider[AppUpdateViewModel::class.java]
                model.refresh()
            }
            compose.setContent { ShutterTheme { ShutterApp(model, updates = updates, onRelease = model::release) } }
            compose.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
            compose.onNodeWithText(text(R.string.developer_force_samsung_title)).performScrollTo()
                .assertIsOff().performClick().assertIsOn()
            compose.runOnIdle {
                assertEquals(actual.copy(manufacturer = "samsung").needsSamsungUserWarning, model.state.value.nonMainUserWarning)
            }
            compose.onNodeWithText(text(R.string.developer_force_non_main_user_title)).performScrollTo()
                .assertIsOff().performClick().assertIsOn()
            compose.runOnIdle {
                assertTrue(model.state.value.nonMainUserWarning)
                assertFalse(model.state.value.nonMainUserConfirmed)
                assertFalse(controller.state.value.enabled)
            }
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val directory = File(context.getExternalFilesDir(null), "warning-simulation").apply { mkdirs() }
            File(directory, "settings.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
            compose.onNodeWithText(text(R.string.samsung_user_warning_title)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.action_release)).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.samsung_user_confirmation_title)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.samsung_user_confirmation_cancel)).performClick()
            compose.runOnIdle {
                assertFalse(runtime.userWarning.confirmed)
                model.requestReleaseConfirmation()
                assertNotNull(model.confirmRelease(true))
                assertTrue(runtime.userWarning.confirmed)
            }
            compose.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
            compose.onNodeWithText(text(R.string.developer_force_non_main_user_title)).performScrollTo()
                .performClick().assertIsOff().performClick().assertIsOn()
            compose.runOnIdle {
                assertTrue(runtime.userWarning.confirmationRequired)
                assertFalse(model.state.value.nonMainUserConfirmed)
                val recreated = ViewModelProvider(recreatedOwner, factory)[ShutterViewModel::class.java]
                assertTrue(recreated.state.value.nonMainUserWarning)
                assertTrue(recreated.warningDeveloperSettings.value.forceSamsung)
                assertTrue(recreated.warningDeveloperSettings.value.forceNonMainUser)
                assertSame(controller, runtime.controller)
                assertEquals(actual, AppUserEnvironment.current())
                assertEquals(snapshot, runtime.snapshot.value)
            }
            compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
            compose.onNodeWithText(text(R.string.action_release)).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.samsung_user_confirmation_title)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.samsung_user_confirmation_cancel)).performClick()
            compose.runOnIdle {
                model.setForceSamsung(false)
                model.setForceNonMainUser(false)
            }
            compose.runOnIdle { assertEquals(actual.needsSamsungUserWarning, model.state.value.nonMainUserWarning) }
        } finally {
            compose.runOnUiThread {
                runtime.setWarningDeveloperSettings(original)
                owner.clear()
                recreatedOwner.clear()
            }
        }
    }
}
