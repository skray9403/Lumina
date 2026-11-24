package org.fossify.gallery.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

val M3StandardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

object LuminaMotion {
    /** Expressive spatial spring for dock morphs, search expansion, and pill glides. */
    fun <T> expressiveSpatial() = spring<T>(
        dampingRatio = 0.78f,
        stiffness = 520f
    )

    /** Expressive lively spring for press feedback, active icon pop, and FAB menu pills. */
    fun <T> expressiveBouncy() = spring<T>(
        dampingRatio = 0.68f,
        stiffness = 780f
    )

    /** Snappy spring for corner-radius morphs and hover focus. */
    fun <T> expressiveSnappy() = spring<T>(
        dampingRatio = 0.75f,
        stiffness = 1100f
    )

    /** Zero-overshoot high-stiffness spring for color & tonal role shifts. */
    fun <T> noBounceColor() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 1400f
    )

    fun <T> spatialStandard() = spring<T>(
        dampingRatio = 0.85f,
        stiffness = 560f
    )

    fun <T> standardSpring() = spatialStandard<T>()

    fun <T> responsiveSpring() = spring<T>(
        dampingRatio = 0.88f,
        stiffness = 780f
    )

    fun <T> gentleSpring() = spring<T>(
        dampingRatio = 0.82f,
        stiffness = 440f
    )

    val viewerMorph = spring<Float>(
        dampingRatio = 0.90f,
        stiffness = 620f
    )

    val dockSizeSpec = spring<IntSize>(
        dampingRatio = 0.78f,
        stiffness = 520f
    )

    val pressScale = spring<Float>(
        dampingRatio = 0.68f,
        stiffness = 950f
    )

    val chromeSlide = spring<Float>(
        dampingRatio = 0.88f,
        stiffness = 700f
    )

    val colorTransition = spring<Color>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 1400f
    )

    val tabSlideSpec = tween<IntOffset>(
        durationMillis = 200,
        easing = M3StandardEasing
    )

    val tabFadeIn = tween<Float>(
        durationMillis = 170,
        easing = LinearOutSlowInEasing
    )

    val tabFadeOut = tween<Float>(
        durationMillis = 120,
        easing = FastOutSlowInEasing
    )

    /** Through-zero mode swap matching the morphing bar demo (80ms out, 90ms delay + 170ms in). */
    val throughZeroExit = tween<Float>(
        durationMillis = 80,
        easing = LinearEasing
    )

    val throughZeroEnter = tween<Float>(
        durationMillis = 170,
        delayMillis = 90,
        easing = LinearEasing
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
        dampingRatio = 0.88f,
        stiffness = 720f
    )
}
