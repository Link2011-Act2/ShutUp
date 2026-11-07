package jp.linkserver.shutter.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import jp.linkserver.goldy.compose.LocalUpdateCardStyle
import jp.linkserver.goldy.compose.UpdateCardStyle
import jp.linkserver.pyrite.theme.PyriteTheme

/** One published Pyrite theme for every destination, transition and dialog. */
@Composable
fun ShutterTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    PyriteTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        val colors = MaterialTheme.colorScheme
        CompositionLocalProvider(LocalUpdateCardStyle provides UpdateCardStyle(
            containerColor = if (darkTheme) colors.surfaceContainerHigh else colors.surfaceContainerLowest,
            shape = MaterialTheme.shapes.large
        ), content = content)
    }
}

