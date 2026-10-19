package jp.linkserver.shutter.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.shutter.R
import jp.linkserver.shutter.ShutterUiState
import org.junit.Rule
import org.junit.Test
import java.io.File

/** The same fixture is captured before/after migration; it never connects to Shizuku or the network. */
class PyriteMigrationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int) = compose.activity.getString(id)

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val phase = InstrumentationRegistry.getArguments().getString("migrationPhase", "after")
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "pyrite-migration/$phase").apply { mkdirs() }
        // PixelCopy of the Compose root waits for its draw; an OS screenshot can
        // return the outgoing Surface while the render thread is still catching up.
        val bitmap = if (name.endsWith("-channel")) {
            compose.onAllNodes(isRoot()).onLast().captureToImage()
            instrumentation.uiAutomation.takeScreenshot()
        } else compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun verify(dark: Boolean, dynamic: Boolean, fontScale: Float = 1f) {
        val prefix = "${if (dark) "dark" else "light"}-${if (dynamic) "dynamic" else "static"}${if (fontScale > 1f) "-large" else ""}"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ShutterTheme(darkTheme = dark, dynamicColor = dynamic) {
                    ShutterNavigation(ShutterUiState(), {}, {}, {}, {})
                }
            }
        }
        capture("$prefix-home")
        compose.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        compose.onNodeWithText(text(R.string.settings_about_title)).performScrollTo().assertIsDisplayed()
        capture("$prefix-settings")
        compose.onNodeWithText(text(R.string.settings_about_title)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.about_screen_title)).assertIsDisplayed()
        compose.onNodeWithText("soralis0912/ShutterSoundToggle", substring = true).assertExists()
        capture("$prefix-about")
        compose.onNodeWithText(text(R.string.about_channel)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.about_channel_release_description)).assertIsDisplayed()
        capture("$prefix-channel")
        compose.onNodeWithText(text(android.R.string.ok)).performClick()
        compose.onNodeWithText(text(R.string.about_version)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.about_version_details_title)).assertIsDisplayed()
        compose.onNodeWithText(text(android.R.string.ok)).performClick()
        compose.onNodeWithText(text(R.string.about_check_updates)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.about_update_latest)).assertExists()
        capture("$prefix-update-result")
        compose.onNodeWithText(text(R.string.about_oss_open)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.about_licenses_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        compose.onNodeWithText(text(R.string.about_update_latest)).assertExists()
        compose.onNodeWithText(text(R.string.about_oss_open)).assertIsDisplayed()
    }

    @Test fun lightDynamic() = verify(false, true)
    @Test fun darkDynamic() = verify(true, true)
    @Test fun lightStatic() = verify(false, false)
    @Test fun darkStatic() = verify(true, false)
    @Test fun largeText() = verify(false, true, 1.5f)
}
