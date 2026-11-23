package org.fossify.gallery.engine.video

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

/**
 * Pure Compose gesture overlay for video playback.
 * - Horizontal swipes pass straight through to HorizontalPager.
 * - Left vertical drag: window brightness (restored to system default on exit).
 * - Right vertical drag: media volume.
 * - Double-tap left/right: seek -10s / +10s with auto-dismissing pill.
 */
@Composable
fun VideoGestureOverlay(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    var pillText by remember { mutableStateOf<String?>(null) }
    var pillNonce by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }

    // Restore system brightness when leaving the video page
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { window ->
                val lp = window.attributes
                if (lp.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.attributes = lp
                }
            }
        }
    }

    // Auto-clear pill after tap seek
    LaunchedEffect(pillNonce, isDragging) {
        if (pillText != null && !isDragging) {
            delay(750)
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
                var isLeftZone = false
                var accumulatedVolPx = 0f

                detectVerticalDragGestures(
                    onDragStart = { startOffset ->
                        isLeftZone = startOffset.x < size.width / 2f
                        accumulatedVolPx = 0f
                        isDragging = true
                    },
                    onDragEnd = {
                        isDragging = false
                        pillText = null
                    },
                    onDragCancel = {
                        isDragging = false
                        pillText = null
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (isLeftZone) {
                            activity?.window?.let { window ->
                                val lp = window.attributes
                                val current = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                val delta = -dragAmount / size.height.coerceAtLeast(1)
                                val updated = (current + delta).coerceIn(0.01f, 1f)
                                lp.screenBrightness = updated
                                window.attributes = lp
                                pillText = "Brightness ${(updated * 100).toInt()}%"
                            }
                        } else {
                            audioManager?.let { am ->
                                accumulatedVolPx += dragAmount
                                val stepPx = (size.height / 18f).coerceAtLeast(24f)
                                if (kotlin.math.abs(accumulatedVolPx) >= stepPx) {
                                    val steps = (-accumulatedVolPx / stepPx).toInt()
                                    accumulatedVolPx %= stepPx
                                    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val nextVol = (curVol + steps).coerceIn(0, maxVol)
                                    am.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                                    pillText = "Volume ${(nextVol * 100 / maxVol)}%"
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
