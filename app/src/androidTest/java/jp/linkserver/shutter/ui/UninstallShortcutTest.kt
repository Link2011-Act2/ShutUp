package jp.linkserver.shutter.ui

import android.content.Intent
import android.content.pm.ShortcutManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.shutter.MainActivity
import jp.linkserver.shutter.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UninstallShortcutTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun publishedShortcutHandlesColdWarmAndRecreatedActivities() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val shortcut = context.getSystemService(ShortcutManager::class.java)
            .manifestShortcuts.single { it.id == "prepare_uninstall" }
        val intent = shortcut.intent!!
        assertEquals(MainActivity.ACTION_PREPARE_UNINSTALL, intent.action)
        assertEquals(MainActivity::class.java.name, intent.component!!.className)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // Activity resources include Android's per-app language override.
            var title = ""
            var close = ""
            scenario.onActivity { activity ->
                title = activity.getString(R.string.uninstall_reminder_title)
                close = activity.getString(R.string.action_close)
            }
            compose.onNodeWithText(title).assertIsDisplayed()
            compose.onNodeWithText(close).performClick()
            compose.onNodeWithText(title).assertDoesNotExist()
            scenario.recreate()
            compose.onNodeWithText(title).assertDoesNotExist()
            scenario.onActivity { activity ->
                activity.startActivity(Intent(activity, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_PREPARE_UNINSTALL)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            compose.onNodeWithText(title).assertIsDisplayed()
            scenario.recreate()
            compose.onNodeWithText(title).assertIsDisplayed()
            compose.onNodeWithText(close).performClick()
        }
    }
}
