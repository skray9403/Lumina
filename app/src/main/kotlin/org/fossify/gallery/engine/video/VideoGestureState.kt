package org.fossify.gallery.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

/**
 * Pure Compose gesture overlay for video playback (no brightness or volume hijacking).
 * - Single tap: toggle viewer chrome.
 * - Double tap left/right: seek -10s / +10s.
 * - Vertical drag down: dismiss viewer.
 * - Vertical swipe up: open details sheet.
 */
@Composable
fun VideoGestureOverlay(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {},
    onVerticalDismissDrag: (Float) -> Unit = {},
    onVerticalDismissEnd: (Float) -> Unit = {},
    onSwipeUpDetails: () -> Unit = {}
) {
    var pillText by remember { mutableStateOf<String?>(null) }
    var pillNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(pillNonce) {
        if (pillText != null) {
            delay(700)
            pillText = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(player) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { tapOffset ->
                        val isLeft = tapOffset.x < size.width / 2f
                        val seekDelta = if (isLeft) -10_000L else 10_000L
                        val duration = player.duration.coerceAtLeast(0L)
                        val newPos = (player.currentPosition + seekDelta).coerceIn(0L, duration)
                        player.seekTo(newPos)
                        pillText = if (isLeft) "-10s" else "+10s"
                        pillNonce++
                    }
                )
            }
            .pointerInput(player) {
                var totalDragY = 0f
                var dismissingDown = false
                var swipedUp = false

                detectVerticalDragGestures(
                    onDragStart = {
                        totalDragY = 0f
                        dismissingDown = false
                        swipedUp = false
                    },
                    onDragEnd = {
                        if (dismissingDown) {
                            onVerticalDismissEnd(totalDragY)
                        }
                    },
                    onDragCancel = {
                        if (dismissingDown) {
                            onVerticalDismissEnd(0f)
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        totalDragY += dragAmount
                        if (!dismissingDown && !swipedUp) {
                            if (totalDragY > viewConfiguration.touchSlop) {
                                dismissingDown = true
                            } else if (totalDragY < -viewConfiguration.touchSlop * 3f) {
                                swipedUp = true
                                onSwipeUpDetails()
                            }
                        }
                        if (dismissingDown) {
                            change.consume()
                            onVerticalDismissDrag(totalDragY)
                        }
                    }
                )
            }
    ) {
        pillText?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
