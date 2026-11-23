package org.fossify.gallery.engine.video

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerPool(context: Context) {
    private val appContext = context.applicationContext
    private var currentPlayer: ExoPlayer? = null
    private var currentUri: Uri? = null

    private val _activePlayer = MutableStateFlow<ExoPlayer?>(null)
    val activePlayer: StateFlow<ExoPlayer?> = _activePlayer.asStateFlow()

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
        _activePlayer.value = player
        return player
    }

    fun pause() {
        currentPlayer?.pause()
    }

    fun release() {
        _activePlayer.value = null
        currentPlayer?.stop()
        currentPlayer?.release()
        currentPlayer = null
        currentUri = null
    }
}
