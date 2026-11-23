package org.fossify.gallery.engine.video

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.abs

/**
 * Pure Compose gesture overlay for video playback.
 *
 * Left half vertical drag: Window screen brightness
 * Right half vertical drag: AudioManager media volume
 * Horizontal drag: Timeline scrub with floating pill
 * Double tap left/right: Seek -10s / +10s
 */
@Composable
fun VideoGestureOverlay(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubTimeMs by remember { mutableLongStateOf(0L) }
    var pillText by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(player) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { tapOffset ->
                        val isLeft = tapOffset.x < size.width / 2f
                        val seekDelta = if (isLeft) -10_000L else 10_000L
                        val newPos = (player.currentPosition + seekDelta).coerceIn(0L, player.duration.coerceAtLeast(0L))
                        player.seekTo(newPos)
                        pillText = if (isLeft) "-10s" else "+10s"
                    }
                )
            }
            .pointerInput(player) {
                var totalDragX = 0f
                var totalDragY = 0f
                var dragType = 0 // 1 = scrub, 2 = brightness, 3 = volume

                detectDragGestures(
                    onDragStart = { startOffset ->
                        totalDragX = 0f
                        totalDragY = 0f
                        dragType = 0
                        isScrubbing = false
                    },
                    onDragEnd = {
                        if (dragType == 1 && isScrubbing) {
                            player.seekTo(scrubTimeMs)
                            player.play()
                        }
                        isScrubbing = false
                        dragType = 0
                        pillText = null
                    },
                    onDragCancel = {
                        isScrubbing = false
                        dragType = 0
                        pillText = null
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y

                        if (dragType == 0) {
                            if (abs(totalDragX) > abs(totalDragY) && abs(totalDragX) > 20f) {
                                dragType = 1 // horizontal scrub
                                isScrubbing = true
                                player.pause()
                            } else if (abs(totalDragY) > 20f) {
                                val isLeft = change.position.x < size.width / 2f
                                dragType = if (isLeft) 2 else 3 // 2: brightness, 3: volume
                            }
                        }

                        when (dragType) {
                            1 -> { // Scrub
                                val duration = player.duration.coerceAtLeast(1L)
                                val deltaFraction = dragAmount.x / size.width
                                val newTarget = (player.currentPosition + (deltaFraction * duration).toLong())
                                    .coerceIn(0L, duration)
                                scrubTimeMs = newTarget
                                pillText = "${formatTime(newTarget)} / ${formatTime(duration)}"
                            }
                            2 -> { // Brightness (left)
                                activity?.let { act ->
                                    val currentBrightness = act.window.attributes.screenBrightness.let {
                                        if (it < 0f) 0.5f else it
                                    }
                                    val delta = -dragAmount.y / size.height
                                    val newBrightness = (currentBrightness + delta).coerceIn(0.01f, 1f)
                                    val lp = act.window.attributes
                                    lp.screenBrightness = newBrightness
                                    act.window.attributes = lp
                                    pillText = "Brightness: ${(newBrightness * 100).toInt()}%"
                                }
                            }
                            3 -> { // Volume (right)
                                audioManager?.let { am ->
                                    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val delta = if (dragAmount.y < -15f) 1 else if (dragAmount.y > 15f) -1 else 0
                                    if (delta != 0) {
                                        val newVol = (currentVol + delta).coerceIn(0, maxVol)
                                        am.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                        pillText = "Volume: ${(newVol.toFloat() / maxVol * 100).toInt()}%"
                                    }
                                }
                            }
                        }
                    }
                )
            }
    ) {
        pillText?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 15.sp,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
