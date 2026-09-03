package com.astrawave.app

import android.content.Intent
import android.net.Uri
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

        if (streamUrl.isNullOrBlank()) {
            Toast.makeText(this, "No playable stream was provided.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (!isDirectMediaUrl(streamUrl)) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(streamUrl))
            if (browserIntent.resolveActivity(packageManager) != null) {
                startActivity(browserIntent)
            } else {
                Toast.makeText(this, "No browser is available to open this official live stream.", Toast.LENGTH_LONG).show()
            }
            finish()
            return
        }

        val playerView = PlayerView(this).apply { useController = true }
        setContentView(playerView)

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

        fun isDirectMediaUrl(url: String): Boolean {
            val normalized = url.substringBefore('?').substringBefore('#').lowercase()
            return normalized.endsWith(".m3u8") ||
                normalized.endsWith(".mpd") ||
                normalized.endsWith(".mp4") ||
                normalized.endsWith(".m4v") ||
                normalized.endsWith(".webm") ||
                normalized.endsWith(".mp3") ||
                normalized.endsWith(".aac") ||
                normalized.endsWith(".m4a") ||
                normalized.endsWith(".ogg") ||
                normalized.endsWith(".ts")
        }
    }
}
