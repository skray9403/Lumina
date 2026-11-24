package org.fossify.gallery.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Stable
class DockScrollState(
    private val density: Float,
    private val velocityThresholdDpPerSec: Float = 600f,
    private val minTravelDp: Float = 18f,
    private val cooldownMillis: Long = 220L
) {
    private data class DeltaSample(val timeNanos: Long, val dyPx: Float)

    var isCollapsed by mutableStateOf(false)
        private set

    private val windowNanos = 50_000_000L
    private val minWindowNanos = 20_000_000L
    private val samples = ArrayDeque<DeltaSample>(16)
    private var lastTriggerMillis = 0L

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            if (available.y == 0f) return Offset.Zero

            val nowNanos = System.nanoTime()
            // Clear window immediately on direction reversal to avoid stale deltas
            if (samples.isNotEmpty() && (samples.last().dyPx * available.y) < 0f) {
                samples.clear()
            }
            samples.addLast(DeltaSample(nowNanos, available.y))

            val cutoff = nowNanos - windowNanos
            while (samples.isNotEmpty() && samples.first().timeNanos < cutoff) {
                samples.removeFirst()
            }

            if (samples.size < 2) return Offset.Zero

            val elapsedNanos = nowNanos - samples.first().timeNanos
            if (elapsedNanos < minWindowNanos) return Offset.Zero

            var sumDyPx = 0f
            for (sample in samples) {
                sumDyPx += sample.dyPx
            }

            val travelDp = abs(sumDyPx) / density
            if (travelDp < minTravelDp) return Offset.Zero

            val elapsedSec = elapsedNanos / 1_000_000_000f
            val velocityDpPerSec = (sumDyPx / density) / elapsedSec

            val nowMillis = nowNanos / 1_000_000L
            if (nowMillis - lastTriggerMillis >= cooldownMillis &&
                abs(velocityDpPerSec) >= velocityThresholdDpPerSec
            ) {
                val targetCollapsed = velocityDpPerSec < 0f
                if (targetCollapsed != isCollapsed) {
                    lastTriggerMillis = nowMillis
                    isCollapsed = targetCollapsed
                    samples.clear()
                }
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            samples.clear()
            return Velocity.Zero
        }
    }

    fun expand() {
        samples.clear()
        isCollapsed = false
    }
}

@Composable
fun rememberDockScrollState(
    expandThreshold: Dp = 32.dp,
    collapseVelocityDpPerSec: Dp = 600.dp
): DockScrollState {
    val density = LocalDensity.current.density
    val thresholdValue = collapseVelocityDpPerSec.value
    return remember(density, thresholdValue) {
        DockScrollState(
            density = density,
            velocityThresholdDpPerSec = thresholdValue
        )
    }
}
