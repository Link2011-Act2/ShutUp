package jp.linkserver.shutter.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.shutter.*
import jp.linkserver.shutter.R
import jp.linkserver.shutter.maintain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AutoMaintainSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = compose.activity.getString(id)
    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory = File(context.getExternalFilesDir(null), "auto-maintain").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun togglePersistencePausedPresentationAndResumeMatchTheSharedController() {
        val prefs = context.getSharedPreferences("auto_maintain_ui_test", 0)
        prefs.edit().clear().commit()
        try {
            val store = AutoMaintainPreferences(context, prefs)
            var policy = 11
            var writes = 0
            val controller = AutoMaintainController(store, AudioPolicyConnector {
                object : AudioPolicyConnection {
                    override fun read() = policy
                    override fun write(value: Int) { policy = value; writes++ }
                }
            })
            compose.setContent {
                val settings by controller.state.collectAsState()
                ShutterTheme {
                    ShutterNavigation(ShutterUiState(ShutterSound.Status.READY, 11), {}, {}, {}, {},
                        autoMaintainState = settings, onAutoMaintainChange = { controller.setEnabled(it) })
                }
            }
            compose.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
            compose.onNodeWithText(text(R.string.auto_maintain_title)).performScrollTo().assertIsOff()
            compose.onNodeWithText(text(R.string.auto_maintain_state_active)).assertDoesNotExist()
            compose.onNodeWithText(text(R.string.auto_maintain_state_paused)).assertDoesNotExist()
            compose.onNodeWithText(text(R.string.auto_maintain_title)).performClick().assertIsOn()
            runBlocking { controller.maintain() }
            assertEquals(0, policy)
            assertEquals(1, writes)
            compose.onNodeWithText(text(R.string.auto_maintain_state_active)).assertIsDisplayed()
            capture("enabled")
            runBlocking { controller.performManual(controller.requestManual(ManualAction.RESTORE), 11) }
            compose.onNodeWithText(text(R.string.auto_maintain_title)).assertIsOn()
            compose.onNodeWithText(text(R.string.auto_maintain_state_paused)).performScrollTo().assertIsDisplayed()
            capture("paused")
            assertEquals(AutoMaintainState(true, false), AutoMaintainPreferences(context, prefs).load())
            runBlocking { assertEquals(MaintainResult.INACTIVE, controller.maintain()) }
            assertEquals(11, policy)
            runBlocking { controller.performManual(controller.requestManual(ManualAction.RELEASE), 11) }
            compose.onNodeWithText(text(R.string.auto_maintain_state_active)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(text(R.string.auto_maintain_title)).performClick().assertIsOff()
            assertEquals(0, policy)
            assertEquals(AutoMaintainState(false, true), AutoMaintainPreferences(context, prefs).load())
            compose.onNodeWithText(text(R.string.auto_maintain_title)).performClick().assertIsOn()
            assertEquals(AutoMaintainState(true, true), AutoMaintainPreferences(context, prefs).load())
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun uninstallAndHomeRestorationCallbacksPauseWithoutTurningTheToggleOff() {
        val prefs = context.getSharedPreferences("auto_maintain_routes_test", 0)
        prefs.edit().clear().commit()
        try {
            var policy = 0
            val controller = AutoMaintainController(AutoMaintainPreferences(context, prefs), AudioPolicyConnector {
                object : AudioPolicyConnection {
                    override fun read() = policy
                    override fun write(value: Int) { policy = value }
                }
            })
            controller.setEnabled(true)
            var homeCurrent by mutableStateOf(0)
            var reminder by mutableStateOf(false)
            val restore = {
                runBlocking { controller.performManual(controller.requestManual(ManualAction.RESTORE), 11) }
                homeCurrent = 11
            }
            compose.setContent {
                ShutterTheme {
                    if (reminder) UninstallReminderDialog(ShutterUiState(ShutterSound.Status.READY, 0), {},
                        restore, {}, {}, {}, deviceDefault = 11)
                    else HomeScreen(ShutterUiState(ShutterSound.Status.READY, homeCurrent), {}, {}, {}, {}, restore, {}, 11)
                }
            }
            compose.onNodeWithText(text(R.string.home_restore)).performClick()
            assertEquals(AutoMaintainState(true, false), controller.state.value)
            controller.setEnabled(false); controller.setEnabled(true)
            policy = 0
            compose.runOnIdle { reminder = true }
            compose.onNodeWithText(text(R.string.home_restore)).performClick()
            assertEquals(AutoMaintainState(true, false), controller.state.value)
            assertEquals(11, policy)
        } finally { prefs.edit().clear().commit() }
    }
}
