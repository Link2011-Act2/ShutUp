package jp.linkserver.shutter.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import jp.linkserver.pyrite.theme.PyriteTheme

private val DarkColorScheme = darkColorScheme()
private val LightColorScheme = lightColorScheme()
private val ShutterShapes = Shapes()
private val LocalSettingsColors = staticCompositionLocalOf<ColorScheme?> { null }

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShutterTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val useDynamicColor = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        useDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        useDynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    // Capture Pyrite's palette before the app-wide theme replaces its colors.
    // Only Settings/About opt into it, and only in light mode.
    PyriteTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        val settingsColors = if (darkTheme) colorScheme else MaterialTheme.colorScheme
        CompositionLocalProvider(LocalSettingsColors provides settingsColors) {
            androidx.compose.material3.MaterialExpressiveTheme(
                colorScheme = colorScheme,
                typography = Typography,
                shapes = ShutterShapes,
                content = content
            )
        }
    }
}

/** Scope colors per animated destination so an outgoing screen keeps its own palette. */
@Composable
internal fun ShutterDestinationTheme(screen: String, content: @Composable () -> Unit) {
    val colors = if (screen == "settings" || screen == "about")
        LocalSettingsColors.current ?: MaterialTheme.colorScheme
    else MaterialTheme.colorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}

