package org.fossify.gallery.engine.video

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Single shared ExoPlayer owner for LuminaViewer.
 * Lifecycle observation is managed cleanly via DisposableEffect at the host level.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerPool(context: Context) {
    private val appContext = context.applicationContext
    private var currentPlayer: ExoPlayer? = null
    private var currentUri: Uri? = null

    fun acquirePlayer(uri: Uri): ExoPlayer {
        val player = currentPlayer ?: ExoPlayer.Builder(appContext).build().also {
            it.repeatMode = Player.REPEAT_MODE_OFF
            currentPlayer = it
        }

        if (currentUri != uri) {
            currentUri = uri
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = true
        }
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
