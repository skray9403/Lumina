package org.fossify.gallery.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * Native, non-bouncy motion tokens for Lumina.
 *
 * Adheres strictly to critically damped / low-overshoot spring characteristics
 * (dampingRatio = 0.92f..1.0f, stiffness = 550f..800f) to avoid floaty or bouncy animations.
 */
object LuminaMotion {
    fun <T> standardSpring() = spring<T>(
        dampingRatio = 0.95f,
        stiffness = 650f
    )

    fun <T> responsiveSpring() = spring<T>(
        dampingRatio = 0.98f,
        stiffness = 800f
    )

    fun <T> gentleSpring() = spring<T>(
        dampingRatio = 0.92f,
        stiffness = 550f
    )

    val contentEnter = tween<Float>(
        durationMillis = 200,
        easing = LinearOutSlowInEasing
    )

    val contentExit = tween<Float>(
        durationMillis = 150,
        easing = FastOutSlowInEasing
    )

    fun screenSlide() = tween<IntOffset>(
        durationMillis = 280,
        easing = FastOutSlowInEasing
    )
}
