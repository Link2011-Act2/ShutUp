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

class AutoMaintainOfferUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = compose.activity.getString(id)

    private fun checkChoice(maintain: Boolean) {
        val prefs = context.getSharedPreferences("auto_maintain_offer_ui_test", 0)
        prefs.edit().clear().commit()
        try {
            var policy = 11
            val controller = AutoMaintainController(AutoMaintainPreferences(context, prefs), AudioPolicyConnector {
                object : AudioPolicyConnection {
                    override fun read() = policy
                    override fun write(value: Int) { policy = value }
                }
            })
            val offer = AutoMaintainOffer(AutoMaintainOfferPreferences(context, prefs))
            var current by mutableStateOf(11)
            val release = {
                val result = runCatching {
                    runBlocking { controller.performManual(controller.requestManual(ManualAction.RELEASE), 11) }
                }
                offer.manualFinished(ManualAction.RELEASE, result, 11, controller.state.value)
                current = result.getOrThrow()
            }
            compose.setContent {
                val settings by controller.state.collectAsState()
                val pending by offer.pending.collectAsState()
                ShutterTheme {
                    ShutterNavigation(ShutterUiState(ShutterSound.Status.READY, current), {}, release, {}, {},
                        autoMaintainState = settings,
                        onAutoMaintainChange = { controller.setEnabled(it); offer.finish() },
                        autoMaintainOfferPending = pending,
                        onAutoMaintainOfferChoice = { choice -> offer.respond(choice) { controller.setEnabled(true) } })
                }
            }
            compose.onNodeWithText(text(R.string.auto_maintain_offer_title)).assertDoesNotExist()
            compose.onNodeWithText(text(R.string.action_release)).performClick()
            compose.onNodeWithText(text(R.string.auto_maintain_offer_title)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.auto_maintain_offer_description)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.auto_maintain_offer_later)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.auto_maintain_offer_enable)).assertIsDisplayed()
            capture(if (maintain) "maintain" else "not-now")
            compose.onNodeWithText(text(if (maintain) R.string.auto_maintain_offer_enable else R.string.auto_maintain_offer_later)).performClick()
            compose.onNodeWithText(text(R.string.auto_maintain_offer_title)).assertDoesNotExist()
            assertEquals(0, policy)
            assertEquals(AutoMaintainState(maintain, true), AutoMaintainPreferences(context, prefs).load())
            assertFalse(AutoMaintainOffer(AutoMaintainOfferPreferences(context, prefs)).pending.value)
            compose.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
            val toggle = compose.onNodeWithText(text(R.string.auto_maintain_title)).performScrollTo()
            if (maintain) {
                toggle.assertIsOn()
                compose.onNodeWithText(text(R.string.auto_maintain_state_active)).assertIsDisplayed()
            } else {
                toggle.assertIsOff()
                compose.onNodeWithText(text(R.string.auto_maintain_state_active)).assertDoesNotExist()
                compose.runOnIdle { release() }
                compose.onNodeWithText(text(R.string.auto_maintain_offer_title)).assertDoesNotExist()
            }
        } finally { prefs.edit().clear().commit() }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory = File(context.getExternalFilesDir(null), "maintain-offer").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun maintainEnablesTheExistingPersistentSettingsToggle() = checkChoice(true)
    @Test fun notNowLeavesSettingsOffAndDoesNotAskAgain() = checkChoice(false)
}
