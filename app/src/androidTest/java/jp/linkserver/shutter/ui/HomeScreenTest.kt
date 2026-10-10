package jp.linkserver.shutter.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import jp.linkserver.shutter.ForceUse
import jp.linkserver.shutter.R
import jp.linkserver.shutter.ShutterSound
import jp.linkserver.shutter.ShutterUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Exercises the home UI with isolated state; no real Shizuku/audio operations. */
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = instrumentation.uiAutomation.takeScreenshot()
        val file = File(context.getExternalFilesDir(null), "home-design/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    @Test fun releaseAndRestoreUseTheDisplayedState() {
        val state = mutableStateOf(ShutterUiState(ShutterSound.Status.READY,
            ForceUse.FORCE_SYSTEM_ENFORCED))
        var releaseCalls = 0
        var restoreCalls = 0
        var refreshCalls = 0
        var settingsCalls = 0
        val dark = InstrumentationRegistry.getArguments().getString("dark") == "true"
        compose.setContent {
            ShutterTheme(darkTheme = dark, dynamicColor = true) {
                HomeScreen(state.value, { settingsCalls++ }, {}, {}, {
                    releaseCalls++
                    state.value = state.value.copy(current = ForceUse.FORCE_NONE)
                }, {
                    restoreCalls++
                    state.value = state.value.copy(current = ForceUse.FORCE_SYSTEM_ENFORCED)
                }, { refreshCalls++ }, deviceDefault = ForceUse.FORCE_SYSTEM_ENFORCED)
            }
        }
        compose.onNodeWithText("(FOR_SYSTEM=11)").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_release)).assertIsEnabled()
        compose.onNodeWithText(text(R.string.home_restore)).assertDoesNotExist()
        screenshot("enforced")
        compose.onNodeWithText(text(R.string.action_release)).performClick()
        compose.onNodeWithText("(FOR_SYSTEM=0)").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.result_already_none)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.action_release)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.home_restore)).assertIsEnabled()
        screenshot("released")
        compose.onNodeWithText(text(R.string.home_restore)).performClick()
        compose.onNodeWithText("(FOR_SYSTEM=11)").assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.action_refresh)).performClick()
        compose.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        assertEquals(1, releaseCalls)
        assertEquals(1, restoreCalls)
        assertEquals(1, refreshCalls)
        assertEquals(1, settingsCalls)
    }

    @Test fun unavailableShizukuAndPermissionHaveTheirOwnActions() {
        val state = mutableStateOf(ShutterUiState())
        var opened = 0
        var authorized = 0
        val dark = InstrumentationRegistry.getArguments().getString("dark") == "true"
        compose.setContent {
            ShutterTheme(darkTheme = dark) {
                HomeScreen(state.value, {}, { opened++ }, { authorized++ }, {}, {}, {})
            }
        }
        compose.onNodeWithText("(FOR_SYSTEM=?)").assertIsDisplayed()
        screenshot("not-running")
        compose.onNodeWithText(text(R.string.home_open_shizuku)).performClick()
        compose.runOnIdle { state.value = state.value.copy(status = ShutterSound.Status.NOT_AUTHORIZED) }
        compose.onNodeWithText(text(R.string.action_authorize)).assertIsEnabled().performClick()
        screenshot("permission")
        assertEquals(1, opened)
        assertEquals(1, authorized)
    }

    @Test fun busyStatePreventsConflictingOperations() {
        compose.setContent {
            ShutterTheme {
                HomeScreen(ShutterUiState(ShutterSound.Status.READY,
                    ForceUse.FORCE_SYSTEM_ENFORCED, busy = true), {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("(FOR_SYSTEM=11)").assertIsDisplayed()
        compose.onNode(hasText(text(R.string.home_working)) and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.home_restore)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.action_refresh)).assertIsNotEnabled()
    }

    @Test fun usageNotesCanBeExpandedAndCollapsed() {
        compose.setContent {
            ShutterTheme { HomeScreen(ShutterUiState(), {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText(text(R.string.about)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.home_before_using)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.about)).assertExists()
        compose.onNodeWithText(text(R.string.home_before_using)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.about)).assertDoesNotExist()
    }

    @Test fun releasedDefaultDoesNotOfferAnUnnecessaryRestore() {
        compose.setContent {
            ShutterTheme {
                HomeScreen(ShutterUiState(ShutterSound.Status.READY, ForceUse.FORCE_NONE),
                    {}, {}, {}, {}, {}, {}, deviceDefault = ForceUse.FORCE_NONE)
            }
        }
        compose.onNodeWithText(text(R.string.home_restore)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.action_release)).assertDoesNotExist()
    }

    @Test fun unknownReadyStateRefreshesWithoutChangingAudioPolicy() {
        var refreshCalls = 0
        var policyCalls = 0
        compose.setContent {
            ShutterTheme {
                HomeScreen(ShutterUiState(ShutterSound.Status.READY), {}, {}, {},
                    { policyCalls++ }, { policyCalls++ }, { refreshCalls++ })
            }
        }
        compose.onNodeWithText(text(R.string.action_refresh)).assertIsEnabled().performClick()
        assertEquals(1, refreshCalls)
        assertEquals(0, policyCalls)
    }
}
