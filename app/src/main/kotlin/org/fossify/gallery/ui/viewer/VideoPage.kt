package org.fossify.gallery.ui.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import org.fossify.gallery.engine.image.rememberThumbnail
import org.fossify.gallery.engine.video.PlayerPool
import org.fossify.gallery.engine.video.VideoGestureOverlay
import org.fossify.gallery.models.Medium

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPage(
    medium: Medium,
    isCurrentPage: Boolean,
    playerPool: PlayerPool,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {}
) {
    val previewThumbnail = rememberThumbnail(medium)
    var activePlayer by remember(medium.uri) { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(medium.uri, isCurrentPage) {
        activePlayer = if (isCurrentPage) {
            playerPool.acquirePlayer(medium.uri)
        } else {
            null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val player = activePlayer
        if (player != null) {
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                modifier = Modifier.fillMaxSize()
            )
            VideoGestureOverlay(
                player = player,
                onSingleTap = onSingleTap
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
