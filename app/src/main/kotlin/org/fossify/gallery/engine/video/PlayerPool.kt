package org.fossify.gallery.engine.video

import android.content.Context
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Lifecycle-aware shared ExoPlayer pool for Lumina.
 *
 * Ensures a single ExoPlayer instance is bound strictly to the active settled page
 * in HorizontalPager, auto-pausing on page transitions and lifecycle ON_PAUSE,
 * and releasing on ON_DESTROY.
 */
class PlayerPool(private val context: Context) {
    private var currentPlayer: ExoPlayer? = null
    private var currentUri: Uri? = null

    fun acquirePlayer(uri: Uri, lifecycleOwner: LifecycleOwner): ExoPlayer {
        val player = currentPlayer ?: ExoPlayer.Builder(context).build().also {
            it.repeatMode = Player.REPEAT_MODE_OFF
            currentPlayer = it
        }

        if (currentUri != uri) {
            currentUri = uri
            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    player.pause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    release()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        return player
    }

    fun pause() {
        currentPlayer?.pause()
    }

    fun release() {
        currentPlayer?.stop()
        currentPlayer?.release()
        currentPlayer = null
        currentUri = null
    }
}
