package jp.linkserver.shutter.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.pyrite.theme.PyriteTheme
import jp.linkserver.shutter.GitHubRelease
import jp.linkserver.shutter.R
import jp.linkserver.shutter.ShutterUiState
import jp.linkserver.shutter.update.AppUpdateState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual navigation screenshots and palette roles, using a fixed offline update fixture. */
class PyritePaletteTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int) = compose.activity.getString(id)
    private val phase = InstrumentationRegistry.getArguments().getString("palettePhase", "after")
    private val directory get() = File(
        InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
        "pyrite-palette/$phase"
    ).apply { mkdirs() }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    private fun verify(dark: Boolean, dynamic: Boolean) {
        val prefix = "${if (dark) "dark" else "light"}-${if (dynamic) "dynamic" else "static"}"
        lateinit var original: ColorScheme
        lateinit var reference: ColorScheme
        val release = GitHubRelease("v2.0.0-Beta2", "Palette regression fixture.\n更新内容の確認。",
            "https://example.com/release", true)
        compose.setContent {
            // Resolve the published library's complete palette on this same device/wallpaper.
            PyriteTheme(darkTheme = dark, dynamicColor = dynamic) {
                val colors = MaterialTheme.colorScheme
                SideEffect { reference = colors }
            }
            ShutterTheme(darkTheme = dark, dynamicColor = dynamic) {
                val colors = MaterialTheme.colorScheme
                val typography = MaterialTheme.typography
                val shapes = MaterialTheme.shapes
                val motion = MaterialTheme.motionScheme
                SideEffect { original = colors }
                for (destination in listOf("home", "settings", "about", "update", "licenses")) {
                    ShutterDestinationTheme(destination) {
                        val scopedColors = MaterialTheme.colorScheme
                        val scopedTypography = MaterialTheme.typography
                        val scopedShapes = MaterialTheme.shapes
                        val scopedMotion = MaterialTheme.motionScheme
                        SideEffect {
                            val expectedColors = if (!dark && destination in listOf("settings", "about")) reference else colors
                            assertEquals("$prefix/$destination complete scheme", expectedColors.toString(), scopedColors.toString())
                            assertSame("$destination typography", typography, scopedTypography)
                            assertSame("$destination shapes", shapes, scopedShapes)
                            assertSame("$destination motion", motion, scopedMotion)
                        }
                    }
                }
                ShutterNavigation(ShutterUiState(), {}, {}, {}, {},
                    updateState = AppUpdateState(release), onCheckUpdates = { Result.success(release) })
            }
        }
        compose.waitForIdle()
        val expected = if (phase == "before" || dark) original else reference
        fun capture(screen: String, colors: ColorScheme, card: Boolean = false) {
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            assertEquals("$prefix/$screen canvas", colors.surface.toArgb(), bitmap.getPixel(0, bitmap.height / 2))
            if (card) {
                val cardColor = if (dark) colors.surfaceContainerHigh else colors.surfaceContainerLowest
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                assertTrue("$prefix/$screen card fill", pixels.count { it == cardColor.toArgb() } > 1000)
            }
            File(directory, "$prefix-$screen.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        capture("home", original)
        compose.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        compose.onNodeWithText(text(R.string.settings_about_title)).performScrollTo()
        capture("settings", expected, true)
        compose.onNodeWithText(text(R.string.settings_about_title)).performClick()
        compose.onNodeWithText(text(R.string.about_screen_title)).assertIsDisplayed()
        capture("about", expected, true)
        compose.onNodeWithText(text(R.string.about_check_updates)).performScrollTo().performClick()
        compose.onNodeWithText("v2.0.0-Beta2").assertIsDisplayed()
        capture("update", original)
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        compose.onNodeWithText(text(R.string.about_screen_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        compose.onNodeWithText(text(R.string.settings_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        capture("home-return", original)
        fun roles(colors: ColorScheme) = JSONObject().apply {
            put("surface", "#%06X".format(colors.surface.toArgb() and 0xffffff))
            put("background", "#%06X".format(colors.background.toArgb() and 0xffffff))
            put("card", "#%06X".format((if (dark) colors.surfaceContainerHigh else colors.surfaceContainerLowest).toArgb() and 0xffffff))
            put("primary", "#%06X".format(colors.primary.toArgb() and 0xffffff))
        }
        File(directory, "$prefix.json").writeText(JSONObject().apply {
            put("original", roles(original)); put("pyrite", roles(reference)); put("settingsAbout", roles(expected))
        }.toString(2))
    }

    @Test fun lightDynamic() = verify(false, true)
    @Test fun lightStatic() = verify(false, false)
    @Test fun darkDynamic() = verify(true, true)
    @Test fun darkStatic() = verify(true, false)
}
