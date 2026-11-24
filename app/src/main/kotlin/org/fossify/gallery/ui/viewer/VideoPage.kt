package org.fossify.gallery.ui.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import org.fossify.gallery.engine.image.rememberThumbnail
import org.fossify.gallery.engine.video.PlayerPool
import org.fossify.gallery.engine.video.VideoGestureOverlay
import org.fossify.gallery.models.Medium
import kotlin.math.roundToInt

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPage(
    medium: Medium,
    isCurrentPage: Boolean,
    playerPool: PlayerPool,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {},
    onVerticalDismissDrag: (Float) -> Unit = {},
    onVerticalDismissEnd: (Float) -> Unit = {},
    onSwipeUpDetails: () -> Unit = {}
) {
    val previewThumbnail = rememberThumbnail(medium)
    var activePlayer by remember(medium.uri) { mutableStateOf<ExoPlayer?>(null) }
    var hasRenderedFirstFrame by remember(medium.uri) { mutableStateOf(false) }

    // Seed aspect ratio synchronously from MediaStore metadata so frame 1 never stretches
    var videoAspectRatio by remember(medium.uri) {
        mutableFloatStateOf(computeMetadataAspectRatio(medium))
    }

    LaunchedEffect(medium.uri, isCurrentPage) {
        if (isCurrentPage) {
            hasRenderedFirstFrame = false
            activePlayer = playerPool.acquirePlayer(medium.uri)
        } else {
            activePlayer = null
        }
    }

    val player = activePlayer
    DisposableEffect(player, medium.uri) {
        if (player == null) return@DisposableEffect onDispose {}

        // Apply immediately if ExoPlayer already knows videoSize
        computePlayerAspectRatio(player.videoSize)?.let { videoAspectRatio = it }

        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                computePlayerAspectRatio(videoSize)?.let { videoAspectRatio = it }
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (player != null) {
            // Exact ContentScale.Fit sizing around PlayerSurface (zero stretching, zero subcomposition)
            VideoAspectFitBox(
                aspectRatio = videoAspectRatio,
                modifier = Modifier.fillMaxSize()
            ) {
                PlayerSurface(
                    player = player,
                    surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Cover SurfaceView with cached thumbnail until first decoded frame renders
            if (!hasRenderedFirstFrame && previewThumbnail != null) {
                Image(
                    bitmap = previewThumbnail,
                    contentDescription = medium.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Gesture overlay spans full viewport (including letterbox area)
            VideoGestureOverlay(
                player = player,
                onSingleTap = onSingleTap,
                onVerticalDismissDrag = onVerticalDismissDrag,
                onVerticalDismissEnd = onVerticalDismissEnd,
                onSwipeUpDetails = onSwipeUpDetails
            )
        } else if (previewThumbnail != null) {
            Image(
                bitmap = previewThumbnail,
                contentDescription = medium.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun VideoAspectFitBox(
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val safeRatio = aspectRatio.coerceIn(0.2f, 5f)
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val maxW = constraints.maxWidth.coerceAtLeast(1)
        val maxH = constraints.maxHeight.coerceAtLeast(1)
        val containerRatio = maxW.toFloat() / maxH.toFloat()

        val fitW: Int
        val fitH: Int
        if (containerRatio > safeRatio) {
            fitH = maxH
            fitW = (fitH * safeRatio).roundToInt().coerceIn(1, maxW)
        } else {
            fitW = maxW
            fitH = (fitW / safeRatio).roundToInt().coerceIn(1, maxH)
        }

        val placeable = measurables.first().measure(Constraints.fixed(fitW, fitH))
        val offsetX = (maxW - fitW) / 2
        val offsetY = (maxH - fitH) / 2
        layout(maxW, maxH) {
            placeable.placeRelative(offsetX, offsetY)
        }
    }
}

private fun computeMetadataAspectRatio(medium: Medium): Float {
    if (medium.width <= 0 || medium.height <= 0) return 16f / 9f
    val rot = ((medium.orientation % 360) + 360) % 360
    val w = if (rot == 90 || rot == 270) medium.height.toFloat() else medium.width.toFloat()
    val h = if (rot == 90 || rot == 270) medium.width.toFloat() else medium.height.toFloat()
    return (w / h).coerceIn(0.2f, 5f)
}

private fun computePlayerAspectRatio(videoSize: VideoSize): Float? {
    if (videoSize.width <= 0 || videoSize.height <= 0) return null
    val par = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1f
    val unapplied = ((videoSize.unappliedRotationDegrees % 360) + 360) % 360
    val rawW = if (unapplied == 90 || unapplied == 270) videoSize.height.toFloat() else videoSize.width.toFloat()
    val rawH = if (unapplied == 90 || unapplied == 270) videoSize.width.toFloat() else videoSize.height.toFloat()
    return ((rawW * par) / rawH).coerceIn(0.2f, 5f)
}
