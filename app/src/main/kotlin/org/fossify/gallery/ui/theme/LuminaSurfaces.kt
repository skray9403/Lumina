package org.fossify.gallery.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
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

/**
 * Full-bleed top-edge feather fade. Non-interactive; content scrolls underneath.
 * Allocates the vertical Brush only when size or scrimColor changes.
 */
@Composable
fun TopEdgeFeatherFade(
    modifier: Modifier = Modifier,
    height: Dp = 104.dp,
    scrimColor: Color = Color.Black,
    topAlpha: Float = 0.52f
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawWithCache {
                val brush = Brush.verticalGradient(
                    0.0f to scrimColor.copy(alpha = topAlpha),
                    0.55f to scrimColor.copy(alpha = topAlpha * 0.45f),
                    1.0f to Color.Transparent
                )
                onDrawBehind {
                    drawRect(brush = brush)
                }
            }
    )
}

/**
 * Zero-subcomposition touch-down tactile compression executed strictly in graphicsLayer.
 */
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
