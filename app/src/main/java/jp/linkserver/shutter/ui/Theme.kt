package jp.linkserver.shutter.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NightAccent,
    onPrimary = Color(0xFF4D2100),
    primaryContainer = Color(0xFF723507),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFE2C1FF),
    onSecondary = Color(0xFF3E2456),
    secondaryContainer = Color(0xFF563B6E),
    onSecondaryContainer = Color(0xFFF5DEFF),
    tertiary = Color(0xFFFFCF88),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6B3D00),
    onTertiaryContainer = Color(0xFFFFE1B4),
    background = CocoaSurface,
    surface = CocoaSurface,
    surfaceContainer = Color(0xFF1E1613),
    surfaceContainerHigh = CocoaSurfaceHigh,
    surfaceContainerHighest = CocoaSurfaceHighest,
    surfaceDim = Color(0xFF130D0B),
    onSurface = Color(0xFFF8E7DE),
    onSurfaceVariant = Color(0xFFD9C4BC),
    outline = Color(0xFF8E7B74),
    outlineVariant = Color(0xFF4A3D38)
)

private val LightColorScheme = lightColorScheme(
    primary = CoralPrimary,
    onPrimary = Color(0xFFFFF8F6),
    primaryContainer = Color(0xFFFFDBD1),
    onPrimaryContainer = Color(0xFF3D0F05),
    secondary = PlumAccent,
    onSecondary = Color(0xFFFFF7FD),
    secondaryContainer = Color(0xFFF2DAFF),
    onSecondaryContainer = Color(0xFF2D173F),
    tertiary = AmberAccent,
    onTertiary = Color(0xFF442300),
    tertiaryContainer = Color(0xFFFFE1B7),
    onTertiaryContainer = Color(0xFF2E1700),
    background = MistSurface,
    surface = MistSurface,
    surfaceContainer = MistContainer,
    surfaceContainerHigh = Color(0xFFFCECE5),
    surfaceContainerHighest = Color(0xFFF7E0D8),
    surfaceDim = Color(0xFFE7D3CB),
    onSurface = Ink,
    onSurfaceVariant = Slate,
    outline = Color(0xFFA68E84),
    outlineVariant = Color(0xFFE7D2CA)
)

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
    androidx.compose.material3.MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

