package org.fossify.gallery.ui.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewThumbnail = rememberThumbnail(medium, sizePx = 360)

    val player = remember(medium.uri, isCurrentPage, playerPool) {
        if (isCurrentPage) {
            playerPool.acquirePlayer(medium.uri, lifecycleOwner)
        } else {
            null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (player != null) {
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
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
