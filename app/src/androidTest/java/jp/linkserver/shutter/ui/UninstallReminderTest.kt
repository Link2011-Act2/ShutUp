package jp.linkserver.shutter.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.shutter.ForceUse
import jp.linkserver.shutter.R
import jp.linkserver.shutter.ShutterSound
import jp.linkserver.shutter.ShutterUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UninstallReminderTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test fun restoreRequiresATapAndReportsTheConfirmedDefault() {
        var state by mutableStateOf(ShutterUiState(ShutterSound.Status.READY, ForceUse.FORCE_NONE))
        var visible by mutableStateOf(true)
        var restores = 0
        compose.setContent {
            ShutterTheme {
                if (visible) UninstallReminderDialog(state, { visible = false }, {
                    restores++
                    state = state.copy(busy = true)
                }, {}, {}, {}, deviceDefault = ForceUse.FORCE_SYSTEM_ENFORCED)
            }
        }
        compose.onNodeWithText(text(R.string.home_uninstall_note)).assertIsDisplayed()
        assertEquals(0, restores)
        compose.onNodeWithText(text(R.string.home_restore)).assertIsEnabled().performClick()
        compose.onNodeWithText(text(R.string.home_working)).assertIsNotEnabled()
        assertEquals(1, restores)
        compose.runOnIdle {
            state = state.copy(current = ForceUse.FORCE_SYSTEM_ENFORCED, busy = false)
        }
        compose.onNodeWithText(text(R.string.uninstall_reminder_already_default)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.uninstall_reminder_ready_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_uninstall_note)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.home_restore)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.action_close)).performClick()
        compose.onNodeWithText(text(R.string.uninstall_reminder_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.uninstall_reminder_ready_title)).assertDoesNotExist()
    }

    @Test fun missingShizukuPermissionAndUnknownStateHaveTheirOwnActions() {
        var state by mutableStateOf(ShutterUiState())
        var opens = 0
        var authorizations = 0
        var refreshes = 0
        var restores = 0
        compose.setContent {
            ShutterTheme {
                UninstallReminderDialog(state, {}, { restores++ }, { opens++ },
                    { authorizations++ }, { refreshes++ }, deviceDefault = ForceUse.FORCE_SYSTEM_ENFORCED)
            }
        }
        compose.onNodeWithText(text(R.string.home_open_shizuku)).performClick()
        assertEquals(1, opens)
        compose.runOnIdle { state = state.copy(status = ShutterSound.Status.NOT_AUTHORIZED) }
        compose.onNodeWithText(text(R.string.action_authorize)).performClick()
        assertEquals(1, authorizations)
        compose.runOnIdle { state = state.copy(status = ShutterSound.Status.READY) }
        compose.onNodeWithText(text(R.string.action_refresh)).performClick()
        assertEquals(1, refreshes)
        compose.runOnIdle { state = state.copy(error = "Could not query") }
        compose.onNodeWithText(text(R.string.uninstall_reminder_error)).assertIsDisplayed()
        assertEquals(0, restores)
    }

    @Test fun unknownDeviceDefaultBlocksRestorationAndKeepsTheReminderVisible() {
        compose.setContent {
            ShutterTheme {
                UninstallReminderDialog(ShutterUiState(ShutterSound.Status.READY, ForceUse.FORCE_NONE),
                    {}, {}, {}, {}, {}, deviceDefault = null)
            }
        }
        compose.onNodeWithText(text(R.string.home_restore)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.device_default_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.uninstall_reminder_ready_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.uninstall_reminder_already_default)).assertDoesNotExist()
    }
}
