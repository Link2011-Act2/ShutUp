package jp.linkserver.shutter.ui

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import jp.linkserver.shutter.R

/** Matches Scheduler's 14sp Markwon rendering without loading remote images or HTML. */
@Composable
internal fun ReleaseNotesMarkdown(markdown: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context).usePlugin(TablePlugin.create(context)).build()
    }
    val content = markdown.ifBlank { stringResource(R.string.about_update_no_notes) }
    val rendered = remember(markwon, content) { markwon.toMarkdown(content) }
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                includeFontPadding = true
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
                textSize = 14f
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.setLinkTextColor(linkColor)
            textView.textSize = 14f
            if (textView.tag !== rendered) {
                markwon.setParsedMarkdown(textView, rendered)
                textView.tag = rendered
            }
        }
    )
}
