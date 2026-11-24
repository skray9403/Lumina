package org.fossify.gallery.ui.navigation

import android.os.SystemClock
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

@Stable
class DockScrollState(
    private val expandThresholdPx: Float,
    private val collapseVelocityPxPerSec: Float
) {
    var isCollapsed by mutableStateOf(false)
        private set

    private var windowStartMs: Long = 0L
    private var windowDy: Float = 0f
    private var accumulatedUpPx: Float = 0f
    private var accumulatedDownPx: Float = 0f
    private var lastToggleMs: Long = 0L

    private val windowDurationMs = 50L
    private val toggleCooldownMs = 180L

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy == 0f) return Offset.Zero

            val now = SystemClock.uptimeMillis()
            if (windowStartMs == 0L || (now - windowStartMs) > windowDurationMs || (dy * windowDy < 0f)) {
                windowStartMs = now
                windowDy = 0f
            }
            windowDy += dy

            val elapsed = (now - windowStartMs).coerceAtLeast(16L)
            val canToggle = (now - lastToggleMs) >= toggleCooldownMs

            if (dy < 0f) {
                accumulatedUpPx = 0f
                accumulatedDownPx += -dy
                val windowVelPxPerSec = (-windowDy / elapsed) * 1000f
                if (!isCollapsed && canToggle &&
                    (windowVelPxPerSec >= collapseVelocityPxPerSec || accumulatedDownPx >= expandThresholdPx * 3.5f)
                ) {
                    isCollapsed = true
                    lastToggleMs = now
                    accumulatedDownPx = 0f
                    windowDy = 0f
                }
            } else {
                accumulatedDownPx = 0f
                accumulatedUpPx += dy
                if (isCollapsed && canToggle && accumulatedUpPx >= expandThresholdPx) {
                    isCollapsed = false
                    lastToggleMs = now
                    accumulatedUpPx = 0f
                    windowDy = 0f
                }
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            val now = SystemClock.uptimeMillis()
            if ((now - lastToggleMs) < toggleCooldownMs) return Velocity.Zero

            if (available.y <= -collapseVelocityPxPerSec && !isCollapsed) {
                isCollapsed = true
                lastToggleMs = now
            } else if (available.y >= collapseVelocityPxPerSec * 0.55f && isCollapsed) {
                isCollapsed = false
                lastToggleMs = now
            }
            return Velocity.Zero
        }
    }

    fun expand() {
        windowStartMs = 0L
        windowDy = 0f
        accumulatedUpPx = 0f
        accumulatedDownPx = 0f
        isCollapsed = false
    }
}

@Composable
fun rememberDockScrollState(
    expandThreshold: Dp = 32.dp,
    collapseVelocityDpPerSec: Dp = 600.dp
): DockScrollState {
    val density = LocalDensity.current
    val expandPx = with(density) { expandThreshold.toPx() }
    val velocityPx = with(density) { collapseVelocityDpPerSec.toPx() }
    return remember(expandPx, velocityPx) {
        DockScrollState(
            expandThresholdPx = expandPx,
            collapseVelocityPxPerSec = velocityPx
        )
    }
}
