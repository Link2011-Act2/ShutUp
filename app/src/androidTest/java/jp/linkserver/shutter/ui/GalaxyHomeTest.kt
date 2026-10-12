package jp.linkserver.shutter.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.shutter.R
import jp.linkserver.shutter.ShutterSound
import jp.linkserver.shutter.ShutterUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UI fixture only: no Samsung settings or audio policy are modified. */
class GalaxyHomeTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun sameHomeActionsUseTheSavedBaselineAndExplainPersistence() {
        val state = mutableStateOf(ShutterUiState(ShutterSound.Status.READY, current = 11,
            defaultValue = 11, persistent = true))
        var releases = 0
        var restores = 0
        compose.setContent {
            ShutterTheme {
                HomeScreen(state.value, {}, {}, {}, {
                    releases++
                    state.value = state.value.copy(current = 0, hasChange = true)
                }, {
                    restores++
                    state.value = state.value.copy(current = 11, hasChange = false)
                }, {})
            }
        }
        compose.onNodeWithText(text(R.string.action_release)).performClick()
        compose.onNodeWithText(text(R.string.home_not_enforced)).assertIsDisplayed()
        compose.onNodeWithText("(FOR_SYSTEM=0)").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.home_restart_note)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.home_persistent_note)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.restore_previous)).performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText(text(R.string.action_release)).assertIsEnabled()
        assertEquals(1, releases)
        assertEquals(1, restores)
    }

    @Test fun anUncertainPersistentSettingCannotClaimUninstallIsSafe() {
        compose.setContent {
            ShutterTheme {
                UninstallReminderDialog(ShutterUiState(ShutterSound.Status.READY,
                    current = 11, defaultValue = 11, persistent = true, hasChange = true),
                    {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText(text(R.string.uninstall_reminder_ready_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.restore_previous)).assertIsEnabled()
    }
}
