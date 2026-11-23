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

/**
 * Velocity-gated dock collapse state (600 dp/s downward collapses to compact capsule;
 * upward scroll expands). Uses primitive accumulators to avoid 120Hz snapshot churn.
 */
@Stable
class DockScrollState(
    private val expandThresholdPx: Float,
    private val collapseVelocityPxPerSec: Float
) {
    var isCollapsed by mutableStateOf(false)
        private set

    private var accumulatedUpPx: Float = 0f
    private var accumulatedDownPx: Float = 0f
    private var lastEventTimeMs: Long = 0L

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy == 0f) return Offset.Zero

            val now = SystemClock.uptimeMillis()
            val dtMs = (now - lastEventTimeMs).coerceIn(4L, 100L)
            lastEventTimeMs = now

            if (dy < 0f) {
                // Scrolling down
                accumulatedUpPx = 0f
                accumulatedDownPx += -dy
                val instantVelPxPerSec = (-dy / dtMs) * 1000f
                if (!isCollapsed &&
                    (instantVelPxPerSec >= collapseVelocityPxPerSec || accumulatedDownPx >= expandThresholdPx * 3f)
                ) {
                    isCollapsed = true
                    accumulatedDownPx = 0f
                }
            } else {
                // Scrolling up
                accumulatedDownPx = 0f
                accumulatedUpPx += dy
                if (isCollapsed && accumulatedUpPx >= expandThresholdPx) {
                    isCollapsed = false
                    accumulatedUpPx = 0f
                }
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (available.y <= -collapseVelocityPxPerSec && !isCollapsed) {
                isCollapsed = true
            } else if (available.y >= collapseVelocityPxPerSec * 0.6f && isCollapsed) {
                isCollapsed = false
            }
            return Velocity.Zero
        }
    }

    fun expand() {
        accumulatedUpPx = 0f
        accumulatedDownPx = 0f
        isCollapsed = false
    }
}

@Composable
fun rememberDockScrollState(
    expandThreshold: Dp = 28.dp,
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
