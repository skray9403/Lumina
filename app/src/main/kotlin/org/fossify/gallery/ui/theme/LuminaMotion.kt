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
 * Critically-damped / zero-overshoot motion tokens tuned for 120Hz.
 */
object LuminaMotion {
    fun <T> spatialStandard() = spring<T>(
        dampingRatio = 0.96f,
        stiffness = 680f
    )

    fun <T> standardSpring() = spatialStandard<T>()

    fun <T> responsiveSpring() = spring<T>(
        dampingRatio = 0.98f,
        stiffness = 850f
    )

    fun <T> gentleSpring() = spring<T>(
        dampingRatio = 0.94f,
        stiffness = 560f
    )

    val viewerMorph = spring<Float>(
        dampingRatio = 0.96f,
        stiffness = 640f
    )

    val dockSizeSpec = spring<IntSize>(
        dampingRatio = 0.96f,
        stiffness = 720f
    )

    val pressScale = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 1400f
    )

    val chromeSlide = spring<Float>(
        dampingRatio = 0.98f,
        stiffness = 780f
    )

    val colorTransition = tween<Color>(
        durationMillis = 150,
        easing = FastOutSlowInEasing
    )

    /** Through-zero mode swap: outgoing hits 0f before incoming starts. */
    val throughZeroExit = tween<Float>(
        durationMillis = 85,
        easing = FastOutSlowInEasing
    )

    val throughZeroEnter = tween<Float>(
        durationMillis = 135,
        delayMillis = 85,
        easing = LinearOutSlowInEasing
    )

    val contentEnter = tween<Float>(
        durationMillis = 160,
        easing = LinearOutSlowInEasing
    )

    val contentExit = tween<Float>(
        durationMillis = 110,
        easing = FastOutSlowInEasing
    )

    val chromeOffset = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 760f
    )
}
