package org.fossify.gallery.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.fossify.gallery.ui.theme.pressScale
import org.fossify.gallery.ui.theme.rememberLuminaDarkColorScheme
import org.fossify.gallery.ui.theme.rememberLuminaPillTokens

@Composable
fun VideoTransportPill(
    player: ExoPlayer,
    modifier: Modifier = Modifier
) {
    val darkScheme = rememberLuminaDarkColorScheme(dynamicColor = true)
    val tokens = rememberLuminaPillTokens(darkOverride = true, colorSchemeOverride = darkScheme)

    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var isMuted by remember(player) { mutableStateOf(player.volume == 0f) }
    var positionMs by remember(player) { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var durationMs by remember(player) { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                durationMs = player.duration.coerceAtLeast(0L)
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }

            override fun onVolumeChanged(volume: Float) {
                isMuted = volume == 0f
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, isPlaying, isScrubbing) {
        while (isActive) {
            if (!isScrubbing) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                val d = player.duration
                if (d > 0L) durationMs = d
            }
            delay(if (isPlaying) 120L else 400L)
        }
    }

    val displayedFraction = if (isScrubbing) {
        scrubFraction
    } else if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val displayedPosMs = if (isScrubbing) {
        (scrubFraction * durationMs).toLong()
    } else {
        positionMs
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = tokens.containerColor,
        border = tokens.border,
        shadowElevation = tokens.shadowElevation,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(44.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Play / Pause
            val playInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .pressScale(playInteraction, pressedScale = 0.88f)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = playInteraction,
                        indication = ripple(bounded = true),
                        role = Role.Button,
                        onClick = {
                            if (player.playbackState == Player.STATE_ENDED) {
                                player.seekTo(0L)
                                player.play()
                            } else if (player.isPlaying) {
                                player.pause()
                            } else {
                                player.play()
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = darkScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = formatVideoTime(displayedPosMs),
                style = MaterialTheme.typography.labelSmall,
                color = darkScheme.onSurface
            )

            // Zero-overhead custom Canvas scrubber
            val activeTrackColor = darkScheme.primary
            val inactiveTrackColor = darkScheme.onSurface.copy(alpha = 0.24f)
            val thumbColor = darkScheme.onSurface

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .pointerInput(player, durationMs) {
                        detectTapGestures { offset ->
                            if (durationMs > 0L && size.width > 0) {
                                val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val target = (frac * durationMs).toLong()
                                positionMs = target
                                player.seekTo(target)
                            }
                        }
                    }
                    .pointerInput(player, durationMs) {
                        detectHorizontalDragGestures(
                            onDragStart = { startOffset ->
                                if (durationMs > 0L && size.width > 0) {
                                    isScrubbing = true
                                    scrubFraction = (startOffset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                }
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                if (durationMs > 0L && size.width > 0) {
                                    scrubFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                }
                            },
                            onDragEnd = {
                                if (isScrubbing && durationMs > 0L) {
                                    val target = (scrubFraction * durationMs).toLong()
                                    positionMs = target
                                    player.seekTo(target)
                                }
                                isScrubbing = false
                            },
                            onDragCancel = {
                                isScrubbing = false
                            }
                        )
                    }
            ) {
                val trackHeight = 4.dp.toPx()
                val cy = size.height / 2f
                val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)

                drawRoundRect(
                    color = inactiveTrackColor,
                    topLeft = Offset(0f, cy - trackHeight / 2f),
                    size = Size(size.width, trackHeight),
                    cornerRadius = corner
                )

                val activeW = (size.width * displayedFraction).coerceIn(0f, size.width)
                drawRoundRect(
                    color = activeTrackColor,
                    topLeft = Offset(0f, cy - trackHeight / 2f),
                    size = Size(activeW, trackHeight),
                    cornerRadius = corner
                )

                drawCircle(
                    color = thumbColor,
                    radius = if (isScrubbing) 7.dp.toPx() else 5.5.dp.toPx(),
                    center = Offset(activeW, cy)
                )
            }

            Text(
                text = formatVideoTime(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = darkScheme.onSurfaceVariant
            )

            // Mute / Unmute
            val muteInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .pressScale(muteInteraction, pressedScale = 0.88f)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = muteInteraction,
                        indication = ripple(bounded = true),
                        role = Role.Button,
                        onClick = {
                            player.volume = if (isMuted) 1f else 0f
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = darkScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatVideoTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hrs = totalSec / 3600L
    val min = (totalSec % 3600L) / 60L
    val sec = totalSec % 60L
    return if (hrs > 0L) {
        String.format("%d:%02d:%02d", hrs, min, sec)
    } else {
        String.format("%d:%02d", min, sec)
    }
}
