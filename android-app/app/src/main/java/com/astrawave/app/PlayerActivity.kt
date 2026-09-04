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
    private var streamUrls: List<String> = emptyList()
    private var streamIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val primary = intent.getStringExtra(EXTRA_URL)
        val alternates = intent.getStringArrayListExtra(EXTRA_URLS).orEmpty()
        val trustedDirect = intent.getBooleanExtra(EXTRA_TRUSTED_DIRECT, false)
        streamUrls = (listOfNotNull(primary) + alternates).filter { it.isNotBlank() }.distinct()

        if (streamUrls.isEmpty()) {
            Toast.makeText(this, "No playable stream was provided.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val first = streamUrls.first()
        if (!trustedDirect && !isDirectMediaUrl(first)) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(first))
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
                    if (streamIndex + 1 < streamUrls.size) {
                        streamIndex += 1
                        Toast.makeText(
                            this@PlayerActivity,
                            "Stream failed. Trying alternate ${streamIndex + 1} of ${streamUrls.size}…",
                            Toast.LENGTH_SHORT,
                        ).show()
                        playCurrent(exo)
                    } else {
                        Toast.makeText(this@PlayerActivity, "All available streams for this channel failed.", Toast.LENGTH_LONG).show()
                    }
                }
            })
            playCurrent(exo)
        }
    }

    private fun playCurrent(exo: ExoPlayer) {
        exo.stop()
        exo.clearMediaItems()
        exo.setMediaItem(MediaItem.fromUri(streamUrls[streamIndex]))
        exo.prepare()
        exo.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_URL = "stream_url"
        const val EXTRA_URLS = "stream_urls"
        const val EXTRA_TRUSTED_DIRECT = "trusted_direct"

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
