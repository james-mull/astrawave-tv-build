package com.astrawave.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val streamUrl = intent.getStringExtra(EXTRA_URL)
        val playerView = PlayerView(this).apply { useController = true }
        setContentView(playerView)

        if (streamUrl.isNullOrBlank()) {
            Toast.makeText(this, "No playable stream was provided.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(this@PlayerActivity, "This stream could not be played.", Toast.LENGTH_LONG).show()
                }
            })
            exo.setMediaItem(MediaItem.fromUri(streamUrl))
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_URL = "stream_url"
    }
}
