package jp.linkserver.shutter.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import jp.linkserver.shutter.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Rendered-image checks exercise the actual morph and rotation without touching audio. */
class HomeStatusEmblemTest {
    @get:Rule val compose = createComposeRule()

    private fun image() = compose.onNodeWithTag("home_status_emblem").captureToImage()

    private fun difference(a: ImageBitmap, b: ImageBitmap, center: Boolean = false): Float {
        val pixelsA = a.toPixelMap()
        val pixelsB = b.toPixelMap()
        var changed = 0
        var sampled = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val dx = (x + 0.5f) / a.width - 0.5f
            val dy = (y + 0.5f) / a.height - 0.5f
            val radiusSquared = dx * dx + dy * dy
            val included = if (center) radiusSquared < 0.20f * 0.20f
                else radiusSquared > 0.34f * 0.34f
            if (included) {
                sampled++
                if (pixelsA[x, y] != pixelsB[x, y]) changed++
            }
        }
        return changed.toFloat() / sampled
    }

    @Test fun outlineMorphsAndCanReverseFromItsCurrentShape() {
        val released = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            HomeStatusEmblem(released.value, R.drawable.ic_error, Color.Blue, Color.Black)
        }
        compose.mainClock.advanceTimeBy(32)
        val burst = image()
        compose.runOnIdle { released.value = true }
        compose.mainClock.advanceTimeBy(352)
        val middle = image()
        compose.mainClock.advanceTimeBy(800)
        val cookie = image()
        assertTrue("The outline should move away from Soft Burst", difference(burst, middle) > 0.001f)
        assertTrue("An intermediate outline should differ from the final cookie", difference(middle, cookie) > 0.001f)

        compose.runOnIdle { released.value = false }
        compose.mainClock.advanceTimeBy(352)
        val returning = image()
        compose.runOnIdle { released.value = true }
        compose.mainClock.advanceTimeByFrame()
        val reversed = image()
        assertTrue("Reversing must continue from the current outline without snapping",
            difference(returning, reversed) < 0.03f)
        compose.mainClock.advanceTimeBy(800)
    }

    @Test fun rotationChangesTheOutlineButKeepsTheSymbolUpright() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            HomeStatusEmblem(true, R.drawable.ic_check_circle, Color.Blue, Color.Black)
        }
        compose.mainClock.advanceTimeBy(32)
        val start = image()
        compose.mainClock.advanceTimeBy(7_500)
        val rotated = image()
        assertTrue("The surrounding cookie should rotate", difference(start, rotated) > 0.001f)
        assertEquals("The central check symbol must remain upright", 0f,
            difference(start, rotated, center = true), 0f)
    }
}
