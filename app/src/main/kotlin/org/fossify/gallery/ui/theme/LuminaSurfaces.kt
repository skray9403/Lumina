package org.fossify.gallery.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Full-bleed top-edge feather fade. Non-interactive; content scrolls seamlessly underneath.
 * Allocates the vertical Brush only when size or surface color changes.
 */
@Composable
fun TopEdgeFeatherFade(
    modifier: Modifier = Modifier,
    height: Dp = 112.dp,
    surfaceColor: Color = MaterialTheme.colorScheme.background
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawWithCache {
                val brush = Brush.verticalGradient(
                    0.0f to surfaceColor.copy(alpha = 0.96f),
                    0.55f to surfaceColor.copy(alpha = 0.72f),
                    0.85f to surfaceColor.copy(alpha = 0.22f),
                    1.0f to Color.Transparent
                )
                onDrawBehind {
                    drawRect(brush = brush)
                }
            }
    )
}

/**
 * Immediate touch-down tactile response executed strictly in the graphicsLayer draw phase.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.94f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = LuminaMotion.pressScale,
        label = "press_scale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
