package org.fossify.gallery.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Critically-damped / zero-overshoot motion tokens for Lumina (120Hz tuned).
 */
object LuminaMotion {
    /** Spatial morphs (dock resize, sheet settles, layout shifts). */
    fun <T> spatialStandard() = spring<T>(
        dampingRatio = 0.96f,
        stiffness = 680f
    )

    /** Backward compatibility alias for spatialStandard */
    fun <T> standardSpring() = spatialStandard<T>()

    fun <T> responsiveSpring() = spring<T>(
        dampingRatio = 0.98f,
        stiffness = 800f
    )

    fun <T> gentleSpring() = spring<T>(
        dampingRatio = 0.92f,
        stiffness = 550f
    )

    /** Dock size morph specifically typed to avoid inference noise. */
    val dockSizeSpec = spring<IntSize>(
        dampingRatio = 0.96f,
        stiffness = 700f
    )

    /** Immediate touch-down tactile compression (strictly no overshoot). */
    val pressScale = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 1400f
    )

    /** Dock hide/show translation on scroll. */
    val chromeSlide = spring<Float>(
        dampingRatio = 0.98f,
        stiffness = 750f
    )

    /** Zero-overshoot color transitions. */
    val colorTransition = tween<Color>(
        durationMillis = 150,
        easing = FastOutSlowInEasing
    )

    val contentEnter = tween<Float>(
        durationMillis = 180,
        easing = LinearOutSlowInEasing
    )

    val contentExit = tween<Float>(
        durationMillis = 120,
        easing = FastOutSlowInEasing
    )

    val chromeOffset = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 750f
    )

    fun screenSlide() = tween<IntOffset>(
        durationMillis = 280,
        easing = FastOutSlowInEasing
    )
}
