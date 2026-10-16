package jp.linkserver.shutter.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.shutter.*
import jp.linkserver.shutter.R
import jp.linkserver.shutter.maintain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SamsungUserWarningUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    private inner class Fixture {
        val prefs = context.getSharedPreferences("samsung_user_warning_ui_test", 0).apply { edit().clear().commit() }
        val warning = SamsungUserWarning(AppUserEnvironment.fromAppUid("Samsung", 9_510_263),
            SamsungUserWarningPreferences(context, prefs))
        var csc = 1
        val writes = mutableListOf<Int?>()
        var journal = ShutterJournal(true, ShutterChange(ShutterMethod.GALAXY_CSC, 1))
        val store = object : ShutterChangeStore {
            override fun load() = journal
            override fun save(journal: ShutterJournal) { this@Fixture.journal = journal }
        }
        val backend = ShutterBackend(object : ShutterDevice {
            override fun readAudio() = 11
            override fun writeAudio(value: Int) { fail("This fixture must keep the recorded CSC method") }
            override fun readCsc() = csc.toString()
            override fun writeCsc(value: Int?) { writes += value; csc = value!! }
            override fun canWriteCsc() = true
        }, store, samsung = true, deviceDefault = 11, bootCount = 1)
        val controller = AutoMaintainController(AutoMaintainPreferences(context, prefs),
            AudioPolicyConnector { BackendPolicyConnection(backend, store) {} }, releaseAuthorization = warning)
        var state by mutableStateOf(ShutterUiState(ShutterSound.Status.READY, 11, nonMainUserWarning = true)
            .withSnapshot(backend.inspect()))

        fun perform(action: ManualAction, permit: ReleasePermit? = null) {
            val result = runBlocking { controller.performManual(controller.requestManual(action, permit), 11) }
            state = state.copy(current = result, nonMainUserConfirmed = warning.confirmed,
                releaseConfirmationPending = false).withSnapshot(backend.inspect())
        }
        fun show() {
            compose.setContent {
                ShutterTheme {
                    ShutterNavigation(state, {}, {
                        if (warning.confirmationRequired) state = state.copy(releaseConfirmationPending = true)
                        else perform(ManualAction.RELEASE)
                    }, { perform(ManualAction.RESTORE) }, {})
                    if (state.releaseConfirmationPending) SamsungUserWarningDialog(
                        { state = state.copy(releaseConfirmationPending = false) },
                        { remember -> perform(ManualAction.RELEASE, warning.confirm(remember)) })
                }
            }
        }
        fun close() { context.deleteSharedPreferences("samsung_user_warning_ui_test") }
    }

    @Test fun cancelDoesNotChangeSettingsConsentOrTheExistingRestorationRecord() {
        val f = Fixture()
        try {
            f.show()
            val journal = f.journal
            compose.onNodeWithText(text(R.string.samsung_user_warning_title)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.action_release)).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.samsung_user_confirmation_title)).assertIsDisplayed()
            compose.onNode(hasText(text(R.string.samsung_user_confirmation_remember)) and isToggleable()).assertIsOn()
            compose.onNodeWithText(text(R.string.samsung_user_confirmation_cancel)).performClick()
            compose.onNodeWithText(text(R.string.samsung_user_confirmation_title)).assertDoesNotExist()
            compose.onNodeWithText(text(R.string.samsung_user_warning_title)).assertIsDisplayed()
            assertEquals(1, f.csc)
            assertTrue(f.writes.isEmpty())
            assertEquals(journal, f.journal)
            assertFalse(f.warning.confirmed)
            assertFalse(f.controller.state.value.enabled)
        } finally { f.close() }
    }

    @Test fun rememberedContinueReleasesAndKeepsTheWarningWithoutClaimingActualSilence() {
        val f = Fixture()
        try {
            f.show()
            compose.onNodeWithText(text(R.string.action_release)).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.samsung_user_confirmation_continue)).performClick()
            compose.onNodeWithText(text(R.string.samsung_user_warning_title)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.samsung_user_release_setting)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.home_not_enforced)).assertDoesNotExist()
            compose.onNodeWithText(text(R.string.home_released_hint)).assertDoesNotExist()
            assertEquals(0, f.csc)
            assertFalse(f.controller.state.value.enabled)
            assertFalse(f.warning.automaticAllowed)
            assertFalse(SamsungUserWarning(f.warning.environment, SamsungUserWarningPreferences(context, f.prefs)).confirmationRequired)
            compose.onNodeWithText(text(R.string.restore_previous)).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.action_release)).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.samsung_user_confirmation_title)).assertDoesNotExist()
            assertEquals(listOf(0, 1, 0), f.writes)
        } finally { f.close() }
    }

    @Test fun uncheckedRememberWarnsAgainButRestorationNeedsNoConfirmation() {
        val f = Fixture()
        try {
            f.show()
            compose.onNodeWithText(text(R.string.action_release)).performScrollTo().performClick()
            compose.onNode(hasText(text(R.string.samsung_user_confirmation_remember)) and isToggleable()).performClick().assertIsOff()
            compose.onNodeWithText(text(R.string.samsung_user_confirmation_continue)).performClick()
            assertEquals(0, f.csc)
            assertTrue(f.warning.confirmationRequired)
            compose.onNodeWithText(text(R.string.restore_previous)).performScrollTo().performClick()
            assertEquals(1, f.csc)
            compose.onNodeWithText(text(R.string.action_release)).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.samsung_user_confirmation_title)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.samsung_user_confirmation_cancel)).performClick()
            assertEquals(listOf(0, 1), f.writes)
        } finally { f.close() }
    }

    @Test fun mainSamsungUserAndOtherManufacturersDoNotShowTheWarningOrChangeExistingLabels() {
        val state = mutableStateOf(ShutterUiState(ShutterSound.Status.READY, 0, defaultValue = 11))
        compose.setContent { ShutterTheme { HomeScreen(state.value, {}, {}, {}, {}, {}, {}) } }
        for (environment in listOf(AppUserEnvironment("samsung", 0), AppUserEnvironment("Google", 95))) {
            compose.runOnIdle { state.value = state.value.copy(nonMainUserWarning = environment.needsSamsungUserWarning) }
            compose.onNodeWithText(text(R.string.samsung_user_warning_title)).assertDoesNotExist()
            compose.onNodeWithText(text(R.string.home_not_enforced)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.samsung_user_confirmation_title)).assertDoesNotExist()
        }
    }
}
