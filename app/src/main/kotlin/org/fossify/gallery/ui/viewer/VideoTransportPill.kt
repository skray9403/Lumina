package org.fossify.gallery.ui.viewer

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.fossify.gallery.ui.theme.LuminaMotion
import org.fossify.gallery.ui.theme.pressScale
import java.util.Locale

@Composable
fun VideoTransportPill(
    player: ExoPlayer,
    onScrubbingChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
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
                val d = player.duration
                if (d > 0L) durationMs = d
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }

            override fun onVolumeChanged(volume: Float) {
                isMuted = volume == 0f
            }
        }
        player.addListener(listener)
        onDispose {
            onScrubbingChange(false)
            player.removeListener(listener)
        }
    }

    LaunchedEffect(player, isPlaying, isScrubbing) {
        while (isActive) {
            if (!isScrubbing) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                val d = player.duration
                if (d > 0L) durationMs = d
            }
            delay(if (isPlaying) 64L else 300L)
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

    val handleHeightDp by animateDpAsState(
        targetValue = if (isScrubbing) 34.dp else 26.dp,
        animationSpec = LuminaMotion.expressiveSnappy(),
        label = "seekbar_handle_height"
    )
    val trackHeightDp by animateDpAsState(
        targetValue = if (isScrubbing) 6.dp else 4.5.dp,
        animationSpec = LuminaMotion.expressiveSnappy(),
        label = "seekbar_track_height"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            val playInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp)
                    .pressScale(playInteraction, pressedScale = 0.86f)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = playInteraction,
                        indication = ripple(bounded = true, color = Color.White),
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
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            val timeText = buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium)) {
                    append(formatVideoTime(displayedPosMs))
                }
                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.62f), fontWeight = FontWeight.Normal)) {
                    append(" / ")
                    append(formatVideoTime(durationMs))
                }
            }
            Text(
                text = timeText,
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.align(Alignment.Center)
            )

            val muteInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(44.dp)
                    .pressScale(muteInteraction, pressedScale = 0.86f)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = muteInteraction,
                        indication = ripple(bounded = true, color = Color.White),
                        role = Role.Button,
                        onClick = { player.volume = if (isMuted) 1f else 0f }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .height(38.dp)
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
                                onScrubbingChange(true)
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
                            onScrubbingChange(false)
                        },
                        onDragCancel = {
                            isScrubbing = false
                            onScrubbingChange(false)
                        }
                    )
                }
        ) {
            val handleW = 4.dp.toPx()
            val handleH = handleHeightDp.coerceAtLeast(16.dp).toPx()
            val trackH = trackHeightDp.coerceAtLeast(3.dp).toPx()
            val gap = 6.dp.toPx()
            val cy = size.height / 2f
            val trackRadius = CornerRadius(trackH / 2f, trackH / 2f)

            val handleX = (handleW / 2f + displayedFraction * (size.width - handleW))
                .coerceIn(handleW / 2f, size.width - handleW / 2f)

            val activeEnd = (handleX - handleW / 2f - gap).coerceAtLeast(0f)
            if (activeEnd > 1f) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(0f, cy - trackH / 2f),
                    size = Size(activeEnd, trackH),
                    cornerRadius = trackRadius
                )
            }

            val inactiveStart = (handleX + handleW / 2f + gap).coerceAtMost(size.width)
            val inactiveW = (size.width - inactiveStart).coerceAtLeast(0f)
            if (inactiveW > 1f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.28f),
                    topLeft = Offset(inactiveStart, cy - trackH / 2f),
                    size = Size(inactiveW, trackH),
                    cornerRadius = trackRadius
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.78f),
                    radius = trackH * 0.42f,
                    center = Offset(size.width - trackH / 2f, cy)
                )
            }

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(handleX - handleW / 2f, cy - handleH / 2f),
                size = Size(handleW, handleH),
                cornerRadius = CornerRadius(handleW / 2f, handleW / 2f)
            )
        }
    }
}

private fun formatVideoTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hrs = totalSec / 3600L
    val min = (totalSec % 3600L) / 60L
    val sec = totalSec % 60L
    return if (hrs > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, min, sec)
    } else {
        String.format(Locale.US, "%d:%02d", min, sec)
    }
}
