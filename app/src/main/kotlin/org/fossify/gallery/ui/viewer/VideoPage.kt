package org.fossify.gallery.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import org.fossify.gallery.engine.video.PlayerPool
import org.fossify.gallery.engine.video.VideoGestureOverlay
import org.fossify.gallery.models.Medium

/**
 * Pure Compose native video playback page.
 *
 * Employs androidx.media3.ui.compose.PlayerSurface with SURFACE_TYPE_TEXTURE_VIEW,
 * bound to the lifecycle-aware PlayerPool, with Compose gesture overlay for scrub/volume/brightness.
 */
@Composable
fun VideoPage(
    medium: Medium,
    isCurrentPage: Boolean,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val playerPool = remember { PlayerPool(context) }

    val player = remember(medium.uri, isCurrentPage) {
        if (isCurrentPage) {
            playerPool.acquirePlayer(medium.uri, lifecycleOwner)
        } else {
            null
        }
    }

    DisposableEffect(isCurrentPage) {
        onDispose {
            if (!isCurrentPage) {
                playerPool.pause()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerPool.release()
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
        }
    }
}
