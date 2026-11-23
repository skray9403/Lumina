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
import androidx.compose.ui.unit.dp

@Stable
class DockScrollState(
    private val thresholdPx: Float
) {
    var isVisible by mutableStateOf(true)
        private set

    // Primitive accumulator avoids snapshot reads/writes on every 120Hz frame
    private var accumulatedDy: Float = 0f

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy == 0f) return Offset.Zero

            // Reset accumulator immediately when scroll direction reverses
            if ((dy < 0f && accumulatedDy > 0f) || (dy > 0f && accumulatedDy < 0f)) {
                accumulatedDy = 0f
            }
            accumulatedDy += dy

            if (accumulatedDy < -thresholdPx && isVisible) {
                isVisible = false
                accumulatedDy = 0f
            } else if (accumulatedDy > thresholdPx && !isVisible) {
                isVisible = true
                accumulatedDy = 0f
            }
            return Offset.Zero
        }
    }

    fun show() {
        accumulatedDy = 0f
        isVisible = true
    }
}

@Composable
fun rememberDockScrollState(threshold: Dp = 28.dp): DockScrollState {
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    return remember(thresholdPx) { DockScrollState(thresholdPx) }
}
