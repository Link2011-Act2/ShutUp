package jp.linkserver.shutter.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph

/** Rotate the surrounding Material shape while keeping the status symbol upright. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeStatusEmblem(
    released: Boolean, icon: Int, containerColor: Color, iconColor: Color
) {
    val morph = remember { Morph(MaterialShapes.SoftBurst, MaterialShapes.Cookie9Sided) }
    val path = remember { Path() }
    val transform = remember { Matrix() }
    val progress = animateFloatAsState(
        if (released) 1f else 0f,
        tween(700, easing = FastOutSlowInEasing), label = "shutter status shape")
    val rotation = rememberInfiniteTransition(label = "shutter status rotation").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(30_000, easing = LinearEasing), RepeatMode.Restart),
        label = "shutter status angle")

    Box(Modifier.size(92.dp).testTag("home_status_emblem"), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize().graphicsLayer { rotationZ = rotation.value }) {
            morph.toPath(progress.value, path)
            // Leave room for the outline to rotate without touching nearby text.
            // Expand the path itself: scaling a unit path while drawing can break on API 28.
            transform.reset()
            transform.translate(size.width * 0.05f, size.height * 0.05f)
            transform.scale(size.width * 0.9f, size.height * 0.9f)
            path.transform(transform)
            drawPath(path, containerColor)
        }
        Crossfade(icon, animationSpec = tween(220), label = "shutter status symbol") { symbol ->
            Icon(painterResource(symbol), null, Modifier.size(52.dp), tint = iconColor)
        }
    }
}
