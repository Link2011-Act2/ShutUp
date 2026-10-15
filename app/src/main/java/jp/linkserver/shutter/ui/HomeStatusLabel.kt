package jp.linkserver.shutter.ui

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Prefer one line, but keep all text readable by wrapping at the minimum size. */
private object HomeStatusAutoSize : TextAutoSize {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)

    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        for (size in 48 downTo 33) {
            val layout = performLayout(constraints, text, size.sp)
            if (layout.lineCount == 1 && !layout.hasVisualOverflow) return size.sp
        }
        return 32.sp
    }
}

@Composable
internal fun HomeStatusLabel(text: String, modifier: Modifier = Modifier,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null) {
    Text(text, modifier, style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold, lineHeight = (56f / 48f).em,
        autoSize = HomeStatusAutoSize, onTextLayout = onTextLayout)
}
