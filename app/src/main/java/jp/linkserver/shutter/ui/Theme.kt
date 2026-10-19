package jp.linkserver.shutter.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import jp.linkserver.pyrite.theme.PyriteTheme

private val DarkColorScheme = darkColorScheme()
private val LightColorScheme = lightColorScheme()
private val ShutterShapes = Shapes()

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
    // Supply Pyrite's style/dark context while retaining ShutUp's existing app-wide
    // colors, typography and original Material shapes (including Home/download).
    PyriteTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        androidx.compose.material3.MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = ShutterShapes,
            content = content
        )
    }
}

