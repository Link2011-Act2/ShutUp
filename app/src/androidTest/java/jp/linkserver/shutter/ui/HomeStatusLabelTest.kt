package jp.linkserver.shutter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HomeStatusLabelTest {
    @get:Rule val compose = createComposeRule()
    private val released = "\u89e3\u9664\u6e08\u307f"

    private var lastLayout: TextLayoutResult? = null

    private fun layout(text: String): TextLayoutResult {
        lateinit var result: TextLayoutResult
        // Compose semantics report the original style; the callback contains the
        // actual auto-sized layout used for drawing.
        compose.runOnIdle { result = checkNotNull(lastLayout) }
        assertEquals(text, result.layoutInput.text.text)
        return result
    }

    @Test fun narrowWidthsShrinkAndWideningOrShorterTextRestoresTheOriginalSize() {
        val width = mutableStateOf(240.dp)
        val label = mutableStateOf(released)
        compose.setContent { ShutterTheme { Box(Modifier.width(width.value)) { HomeStatusLabel(label.value, onTextLayout = { lastLayout = it }) } } }
        assertEquals(48f, layout(released).layoutInput.style.fontSize.value, 0.01f)
        compose.runOnIdle { width.value = 144.dp }
        val narrow = layout(released)
        assertEquals(1, narrow.lineCount)
        assertTrue(narrow.layoutInput.style.fontSize.value in 32f..47f)
        assertFalse(narrow.hasVisualOverflow)
        compose.runOnIdle { width.value = 240.dp }
        assertEquals(48f, layout(released).layoutInput.style.fontSize.value, 0.01f)
        compose.runOnIdle { width.value = 144.dp; label.value = "\u4e0d\u660e" }
        assertEquals(48f, layout(label.value).layoutInput.style.fontSize.value, 0.01f)
    }

    @Test fun veryNarrowWidthsWrapTheFullTextAtTheMinimumSize() {
        compose.setContent { ShutterTheme { Box(Modifier.width(80.dp)) { HomeStatusLabel(released, onTextLayout = { lastLayout = it }) } } }
        val result = layout(released)
        assertEquals(32f, result.layoutInput.style.fontSize.value, 0.01f)
        assertTrue(result.lineCount > 1)
        assertEquals(released.length, result.getLineEnd(result.lineCount - 1))
        assertFalse(result.hasVisualOverflow)
    }

    @Test fun englishStatusAlsoShrinksWithoutTruncation() {
        compose.setContent { ShutterTheme { Box(Modifier.width(160.dp)) { HomeStatusLabel("Released", onTextLayout = { lastLayout = it }) } } }
        val result = layout("Released")
        assertEquals(1, result.lineCount)
        assertTrue(result.layoutInput.style.fontSize.value in 32f..47f)
        assertEquals(8, result.getLineEnd(0))
        assertFalse(result.hasVisualOverflow)
    }

    @Test fun largeSystemFontsKeepTheirScaleAndCanWrapAtTheMinimumSize() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                ShutterTheme { Box(Modifier.width(80.dp)) { HomeStatusLabel(released, onTextLayout = { lastLayout = it }) } }
            }
        }
        val result = layout(released)
        assertEquals(1.5f, result.layoutInput.density.fontScale, 0.01f)
        assertEquals(32f, result.layoutInput.style.fontSize.value, 0.01f)
        assertTrue(result.lineCount > 1)
        assertEquals(released.length, result.getLineEnd(result.lineCount - 1))
        assertFalse(result.hasVisualOverflow)
    }
}
