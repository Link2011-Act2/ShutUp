package jp.linkserver.shutter.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.shutter.*
import jp.linkserver.shutter.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GalaxyControlSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test fun enablingRequiresConfirmationAndPersistsWhileOtherDevicesIgnoreTheOverride() {
        val prefs = context.getSharedPreferences("galaxy_control_test", 0)
        prefs.edit().clear().commit()
        val store = GalaxyControlPreferences(context, prefs, samsung = { true })
        var settings by mutableStateOf(store.load())
        try {
            compose.setContent { ShutterTheme {
                ShutterNavigation(ShutterUiState(), {}, {}, {}, {}, galaxySettings = settings,
                    onGalaxyAudioPolicyChange = { store.save(it); settings = store.load() })
            } }
            compose.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
            compose.onNodeWithText(text(R.string.galaxy_force_audio_title)).performScrollTo().assertIsOff().performClick()
            compose.onNodeWithText(text(R.string.galaxy_force_audio_confirmation_title)).assertIsDisplayed()
            assertFalse(store.load().forceAudioPolicy)
            compose.onNodeWithText(text(R.string.samsung_user_confirmation_cancel)).performClick()
            compose.onNodeWithText(text(R.string.galaxy_force_audio_title)).assertIsOff().performClick()
            compose.onNodeWithText(text(R.string.galaxy_force_audio_enable)).performClick()
            compose.onNodeWithText(text(R.string.galaxy_force_audio_title)).assertIsOn()
            assertTrue(GalaxyControlPreferences(context, prefs, samsung = { true }).load().forceAudioPolicy)
            compose.onNodeWithText(text(R.string.galaxy_force_audio_title)).performClick().assertIsOff()
            assertFalse(store.load().forceAudioPolicy)
            store.save(true)
            val otherDevice = GalaxyControlPreferences(context, prefs, samsung = { false })
            assertEquals(GalaxyControlSettings(), otherDevice.load())
            otherDevice.save(false)
            assertTrue(store.load().forceAudioPolicy)
            compose.runOnIdle { settings = otherDevice.load() }
            compose.onNodeWithText(text(R.string.galaxy_settings_title)).assertDoesNotExist()
        } finally { context.deleteSharedPreferences("galaxy_control_test") }
    }

    @Test fun anExistingJournalBlocksSwitchingAndOffersRestorationInSettings() {
        var state by mutableStateOf(ShutterUiState(ShutterSound.Status.READY, 11, hasChange = true))
        var changes = 0
        var restores = 0
        compose.setContent { ShutterTheme {
            ShutterNavigation(state, {}, {}, { restores++; state = state.copy(hasChange = false) }, {},
                galaxySettings = GalaxyControlSettings(true), onGalaxyAudioPolicyChange = { changes++ })
        } }
        compose.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        compose.onNodeWithText(text(R.string.galaxy_force_audio_title)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.galaxy_method_restore_first)).performScrollTo().assertIsDisplayed()
        assertEquals(0, changes)
        compose.onNodeWithText(text(R.string.restore_previous)).performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, restores)
        compose.onNodeWithText(text(R.string.galaxy_force_audio_title)).performScrollTo().assertIsEnabled()
    }

    @Test fun confirmedAbsentKeyDoesNotDisplayAudioPolicyOrOfferUnnecessaryWrites() {
        val snapshot = ShutterSnapshot(ShutterMethod.GALAXY_CSC, Enforcement.RELEASED, null, null,
            false, currentKnown = true, cscAbsent = true)
        val state = ShutterUiState(ShutterSound.Status.READY, 0).withSnapshot(snapshot)
        assertEquals(0, state.defaultValue)
        assertFalse(state.canRelease)
        assertFalse(state.canRestore)
        compose.setContent { ShutterTheme { HomeScreen(state, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText(text(R.string.galaxy_csc_absent_value)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_enforced)).assertDoesNotExist()
        compose.onNodeWithText("(CSC=0)").assertDoesNotExist()
        compose.onNodeWithText("(FOR_SYSTEM=11)").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.action_release)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.home_restore)).performScrollTo().assertIsNotEnabled()
    }

    @Test fun unknownCscDoesNotGuessAValueAndOldAudioJournalsOfferRestore() {
        val unknown = ShutterSnapshot(ShutterMethod.GALAXY_CSC, Enforcement.UNKNOWN, null, null, false)
        var state by mutableStateOf(ShutterUiState(ShutterSound.Status.READY, 11).withSnapshot(unknown))
        var restores = 0
        compose.setContent { ShutterTheme { HomeScreen(state, {}, {}, {}, {}, { restores++ }, {}) } }
        compose.onAllNodesWithText(text(R.string.home_unknown)).onFirst().assertIsDisplayed()
        compose.onNodeWithText("(CSC=?)").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_refresh)).assertIsEnabled()
        compose.runOnIdle {
            state = ShutterUiState(ShutterSound.Status.READY, 11).withSnapshot(ShutterSnapshot(
                ShutterMethod.AUDIO_POLICY, Enforcement.ENFORCED, 11, 11, true, releaseAllowed = false))
        }
        compose.onNodeWithText(text(R.string.galaxy_legacy_restore_pending)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_enforced)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.action_release)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.home_restore)).performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, restores)
    }
}
