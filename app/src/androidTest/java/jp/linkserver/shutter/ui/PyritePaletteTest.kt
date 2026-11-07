package jp.linkserver.shutter.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.pyrite.theme.PyriteTheme
import jp.linkserver.goldy.CheckOutcome
import jp.linkserver.goldy.GoldyState
import jp.linkserver.goldy.compose.LocalUpdateCardStyle
import jp.linkserver.shutter.R
import jp.linkserver.shutter.ShutterUiState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** All destinations inherit the published Pyrite theme, including both sides of a transition. */
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
        lateinit var actual: ColorScheme
        lateinit var reference: ColorScheme
        lateinit var referenceTypography: Typography
        lateinit var referenceShapes: Shapes
        lateinit var referenceMotion: Any
        val release = testUpdate("v2.0.0-Beta2", withApk = true)
        compose.setContent {
            // Resolve the published library's complete palette on this same device/wallpaper.
            PyriteTheme(darkTheme = dark, dynamicColor = dynamic) {
                val colors = MaterialTheme.colorScheme
                val typography = MaterialTheme.typography
                val shapes = MaterialTheme.shapes
                val motion = MaterialTheme.motionScheme
                SideEffect {
                    reference = colors; referenceTypography = typography
                    referenceShapes = shapes; referenceMotion = motion
                }
            }
            ShutterTheme(darkTheme = dark, dynamicColor = dynamic) {
                val colors = MaterialTheme.colorScheme
                val typography = MaterialTheme.typography
                val shapes = MaterialTheme.shapes
                val motion = MaterialTheme.motionScheme
                val card = LocalUpdateCardStyle.current
                SideEffect {
                    actual = colors
                    assertEquals("$prefix complete scheme", reference.toString(), colors.toString())
                    assertSame("Pyrite typography", referenceTypography, typography)
                    assertSame("Pyrite shapes", referenceShapes, shapes)
                    assertEquals("Pyrite motion", referenceMotion.javaClass, motion.javaClass)
                    assertEquals(if (dark) reference.surfaceContainerHigh else reference.surfaceContainerLowest, card.containerColor)
                    assertSame(referenceShapes.large, card.shape)
                }
                ShutterNavigation(ShutterUiState(), {}, {}, {}, {},
                    updateState = GoldyState(release, release, true), onCheckUpdates = { CheckOutcome.Available(release) })
            }
        }
        compose.waitForIdle()
        val expected = reference
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
        capture("home", expected, true)
        compose.onNodeWithTag("goldy-notification-banner").assertIsDisplayed()
        compose.onNodeWithTag("goldy-notification-icon").performClick()
        compose.onNodeWithTag("goldy-update-check-dialog").assertIsDisplayed()
        compose.onNodeWithTag("goldy-notification-details").assertIsEnabled()
        compose.onNodeWithText(text(android.R.string.cancel)).performClick()
        // Sample the actual transition with both outgoing and entering screens retained.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        for (step in listOf(16L, 64L, 100L, 140L)) {
            compose.mainClock.advanceTimeBy(step)
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            assertEquals("$prefix transition canvas", expected.surface.toArgb(), image.getPixel(0, image.height / 2))
            image.recycle()
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(text(R.string.settings_about_title)).performScrollTo()
        capture("settings", expected, true)
        compose.onNodeWithText(text(R.string.settings_about_title)).performClick()
        compose.onNodeWithText(text(R.string.about_screen_title)).assertIsDisplayed()
        capture("about", expected, true)
        compose.onNodeWithText(text(R.string.about_check_updates)).performScrollTo().performClick()
        compose.onNodeWithText("v2.0.0-Beta2").assertIsDisplayed()
        capture("update", expected, true)
        compose.onNodeWithTag("latest-release-notes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("history-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("history-2").performScrollTo().assertIsDisplayed()
        capture("update-history", expected, true)
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        compose.onNodeWithText(text(R.string.about_screen_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        compose.onNodeWithText(text(R.string.settings_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        capture("home-return", expected)
        fun roles(colors: ColorScheme) = JSONObject().apply {
            put("surface", "#%06X".format(colors.surface.toArgb() and 0xffffff))
            put("background", "#%06X".format(colors.background.toArgb() and 0xffffff))
            put("card", "#%06X".format((if (dark) colors.surfaceContainerHigh else colors.surfaceContainerLowest).toArgb() and 0xffffff))
            put("primary", "#%06X".format(colors.primary.toArgb() and 0xffffff))
        }
        File(directory, "$prefix.json").writeText(JSONObject().apply {
            put("app", roles(actual)); put("pyrite", roles(reference))
            put("allDestinationsMatch", true); put("transitionCanvasMatches", true)
        }.toString(2))
    }

    @Test fun lightDynamic() = verify(false, true)
    @Test fun lightStatic() = verify(false, false)
    @Test fun darkDynamic() = verify(true, true)
    @Test fun darkStatic() = verify(true, false)
}
