package jp.linkserver.shutter.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import jp.linkserver.shutter.R
import jp.linkserver.shutter.ShutterUiState
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int) = compose.activity.getString(id)

    private fun launch() {
        compose.setContent {
            ShutterTheme {
                ShutterNavigation(ShutterUiState(), {}, {}, {}, {})
            }
        }
    }

    private fun settle() {
        // Interrupted transitions may use a settling spring. Let Compose finish
        // all animations, and keep scroll-to actions on an advancing clock.
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "navigation-design")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }

    @Test fun settingsAboutAndLicensesAnimateAndNavigateBack() {
        launch()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        compose.mainClock.advanceTimeBy(128)
        compose.onNodeWithText(text(R.string.app_name)).assertExists()
        compose.onNodeWithText(text(R.string.settings_title)).assertExists()
        screenshot("forward-in-progress")
        settle()
        compose.onNodeWithText(text(R.string.app_name)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.settings_title)).assertIsDisplayed()
        screenshot("settings")

        compose.onNodeWithText(text(R.string.settings_about_title)).performClick()
        settle()
        compose.onNodeWithText(text(R.string.about_screen_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.about_oss_open)).performScrollTo().performClick()
        settle()
        compose.onNodeWithText(text(R.string.about_licenses_title)).assertIsDisplayed()
        screenshot("licenses")

        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        settle()
        compose.onNodeWithText(text(R.string.about_screen_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.about_oss_open)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        settle()
        compose.onNodeWithText(text(R.string.settings_title)).assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(128)
        compose.onNodeWithText(text(R.string.settings_title)).assertExists()
        compose.onNodeWithText(text(R.string.app_name)).assertExists()
        screenshot("back-in-progress")
        settle()
        compose.onNodeWithText(text(R.string.settings_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.app_name)).assertIsDisplayed()
    }

    @Test fun interruptedTransitionsPreserveHomeState() {
        launch()
        compose.onNodeWithText(text(R.string.home_before_using)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.about)).assertExists()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        compose.onNodeWithText(text(R.string.app_name)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.about)).assertExists()
        screenshot("interrupted-return")
        // Only tap the home action once the home screen is visible again;
        // an outgoing action can otherwise be covered by the incoming surface.
        compose.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        settle()
        compose.onNodeWithText(text(R.string.settings_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        settle()
        compose.onNodeWithText(text(R.string.app_name)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.about)).assertExists()
    }
}
