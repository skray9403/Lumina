package org.fossify.gallery.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Immutable
data class LuminaPillTokens(
    val containerColor: Color,
    val contentColor: Color,
    val secondaryContentColor: Color,
    val border: BorderStroke,
    val shadowElevation: Dp
)

@Composable
fun rememberLuminaPillTokens(
    darkOverride: Boolean? = null,
    colorSchemeOverride: ColorScheme? = null
): LuminaPillTokens {
    val isDark = darkOverride ?: isSystemInDarkTheme()
    val scheme = colorSchemeOverride ?: MaterialTheme.colorScheme
    return if (isDark) {
        LuminaPillTokens(
            containerColor = scheme.surfaceContainerHigh.copy(alpha = 0.96f),
            contentColor = scheme.onSurface,
            secondaryContentColor = scheme.onSurfaceVariant,
            border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.11f)),
            shadowElevation = 6.dp
        )
    } else {
        LuminaPillTokens(
            containerColor = scheme.surfaceContainerLowest.copy(alpha = 0.97f),
            contentColor = scheme.onSurface,
            secondaryContentColor = scheme.onSurfaceVariant,
            border = BorderStroke(0.75.dp, scheme.outlineVariant.copy(alpha = 0.28f)),
            shadowElevation = 3.dp
        )
    }
}

/**
 * Status-bar-aware smooth feather scrim.
 * Anchors high opacity across the status bar + upper half of the floating date pill so row 1's
 * top edge melts seamlessly into the background with zero harsh cutoff, then feathers past the pill.
 */
@Composable
fun TopEdgeFeatherFade(
    modifier: Modifier = Modifier,
    statusBarHeight: Dp = 0.dp,
    height: Dp = statusBarHeight + 96.dp,
    surfaceColor: Color = MaterialTheme.colorScheme.background
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawWithCache {
                val h = size.height.coerceAtLeast(1f)
                val sb = statusBarHeight.toPx()
                val s0 = ((sb + 8.dp.toPx()) / h).coerceIn(0.05f, 0.45f)
                val s1 = ((sb + 30.dp.toPx()) / h).coerceIn(s0 + 0.05f, 0.65f)
                val s2 = ((sb + 52.dp.toPx()) / h).coerceIn(s1 + 0.05f, 0.80f)
                val s3 = ((sb + 74.dp.toPx()) / h).coerceIn(s2 + 0.05f, 0.94f)

                val brush = Brush.verticalGradient(
                    0.0f to surfaceColor,
                    s0 to surfaceColor.copy(alpha = 0.97f),
                    s1 to surfaceColor.copy(alpha = 0.88f),
                    s2 to surfaceColor.copy(alpha = 0.44f),
                    s3 to surfaceColor.copy(alpha = 0.12f),
                    1.0f to Color.Transparent
                )
                onDrawBehind {
                    drawRect(brush = brush)
                }
            }
    )
}

@Composable
fun BottomEdgeFeatherFade(
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
                    0.0f to Color.Transparent,
                    0.35f to surfaceColor.copy(alpha = 0.36f),
                    0.75f to surfaceColor.copy(alpha = 0.84f),
                    1.0f to surfaceColor.copy(alpha = 0.98f)
                )
                onDrawBehind {
                    drawRect(brush = brush)
                }
            }
    )
}

fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.94f
): Modifier = this then PressScaleElement(interactionSource, pressedScale)

private data class PressScaleElement(
    val interactionSource: InteractionSource,
    val pressedScale: Float
) : ModifierNodeElement<PressScaleNode>() {
    override fun create(): PressScaleNode = PressScaleNode(interactionSource, pressedScale)

    override fun update(node: PressScaleNode) {
        node.updateConfig(interactionSource, pressedScale)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "pressScale"
        properties["pressedScale"] = pressedScale
    }
}

private class PressScaleNode(
    private var interactionSource: InteractionSource,
    private var pressedScale: Float
) : Modifier.Node(), LayoutModifierNode {

    private val scaleAnimatable = Animatable(1f)
    private var collectJob: Job? = null
    private var animJob: Job? = null

    override fun onAttach() {
        startCollecting()
    }

    override fun onDetach() {
        collectJob?.cancel()
        animJob?.cancel()
        collectJob = null
        animJob = null
    }

    fun updateConfig(source: InteractionSource, targetScale: Float) {
        pressedScale = targetScale
        if (interactionSource !== source) {
            interactionSource = source
            if (isAttached) {
                startCollecting()
            }
        }
    }

    private fun startCollecting() {
        collectJob?.cancel()
        collectJob = coroutineScope.launch {
            var pressCount = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressCount++
                    is PressInteraction.Release,
                    is PressInteraction.Cancel -> pressCount = (pressCount - 1).coerceAtLeast(0)
                }
                val target = if (pressCount > 0) pressedScale else 1f
                animJob?.cancel()
                animJob = coroutineScope.launch {
                    scaleAnimatable.animateTo(target, LuminaMotion.pressScale)
                }
            }
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val s = scaleAnimatable.value
                scaleX = s
                scaleY = s
            }
        }
    }
}
