package com.astrawave.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.astrawave.app.core.LibraryItemRef
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.data.LocalLibraryStore

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var streamUrls: List<String> = emptyList()
    private var streamIndex: Int = 0
    private var retryCountForCurrentStream: Int = 0
    private var resumePositionMs: Long = 0L
    private var lastKnownPositionMs: Long = 0L
    private var historyRecorded = false
    private var libraryStore: LocalLibraryStore? = null
    private var libraryItem: LibraryItemRef? = null
    private var profileId: String = DEFAULT_PROFILE_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val primary = intent.getStringExtra(EXTRA_URL)
        val alternates = intent.getStringArrayListExtra(EXTRA_URLS).orEmpty()
        val trustedDirect = intent.getBooleanExtra(EXTRA_TRUSTED_DIRECT, false)
        streamUrls = (listOfNotNull(primary) + alternates).filter { it.isNotBlank() }.distinct()
        profileId = intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty().ifBlank { DEFAULT_PROFILE_ID }
        libraryItem = intentLibraryItem()
        if (libraryItem != null) {
            libraryStore = LocalLibraryStore(this)
            resumePositionMs = libraryStore
                ?.progress(profileId)
                ?.firstOrNull { it.item.id == libraryItem?.id }
                ?.takeIf { !it.completed }
                ?.positionMs
                ?: 0L
            lastKnownPositionMs = resumePositionMs
        }

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
                    lastKnownPositionMs = maxOf(lastKnownPositionMs, exo.currentPosition.coerceAtLeast(0L))
                    when {
                        retryCountForCurrentStream < MAX_RETRIES_PER_STREAM -> {
                            retryCountForCurrentStream += 1
                            Toast.makeText(
                                this@PlayerActivity,
                                "Stream interrupted. Reconnecting…",
                                Toast.LENGTH_SHORT,
                            ).show()
                            playCurrent(exo, lastKnownPositionMs)
                        }
                        streamIndex + 1 < streamUrls.size -> {
                            streamIndex += 1
                            retryCountForCurrentStream = 0
                            Toast.makeText(
                                this@PlayerActivity,
                                "Trying backup stream ${streamIndex + 1} of ${streamUrls.size}…",
                                Toast.LENGTH_SHORT,
                            ).show()
                            playCurrent(exo, lastKnownPositionMs)
                        }
                        else -> {
                            persistProgress(exo)
                            Toast.makeText(
                                this@PlayerActivity,
                                "All available streams for this channel failed.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        retryCountForCurrentStream = 0
                        if (!historyRecorded) {
                            libraryItem?.let { item -> libraryStore?.recordHistory(profileId, item) }
                            historyRecorded = true
                        }
                    }
                }
            })
            playCurrent(exo, resumePositionMs)
        }
    }

    private fun playCurrent(exo: ExoPlayer, positionMs: Long = 0L) {
        exo.stop()
        exo.clearMediaItems()
        exo.setMediaItem(MediaItem.fromUri(streamUrls[streamIndex]))
        if (positionMs > 0L) exo.seekTo(positionMs)
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun persistProgress(exo: ExoPlayer?) {
        val item = libraryItem ?: return
        val active = exo ?: return
        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return
        val position = maxOf(lastKnownPositionMs, active.currentPosition.coerceAtLeast(0L))
        if (position <= 0L) return
        libraryStore?.saveProgress(profileId, item, position.coerceAtMost(duration), duration)
    }

    private fun intentLibraryItem(): LibraryItemRef? {
        val id = intent.getStringExtra(EXTRA_LIBRARY_ID)?.takeIf { it.isNotBlank() } ?: return null
        val title = intent.getStringExtra(EXTRA_LIBRARY_TITLE)?.takeIf { it.isNotBlank() } ?: return null
        val type = intent.getStringExtra(EXTRA_LIBRARY_TYPE)
            ?.let { raw -> runCatching { LibraryMediaType.valueOf(raw) }.getOrNull() }
            ?: LibraryMediaType.MOVIE
        return LibraryItemRef(
            id = id,
            type = type,
            title = title,
            posterUrl = intent.getStringExtra(EXTRA_LIBRARY_POSTER)?.takeIf { it.isNotBlank() },
            sourceId = intent.getStringExtra(EXTRA_LIBRARY_SOURCE_ID)?.takeIf { it.isNotBlank() },
        )
    }

    override fun onStop() {
        persistProgress(player)
        super.onStop()
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_URL = "stream_url"
        const val EXTRA_URLS = "stream_urls"
        const val EXTRA_TRUSTED_DIRECT = "trusted_direct"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_LIBRARY_ID = "library_id"
        const val EXTRA_LIBRARY_TITLE = "library_title"
        const val EXTRA_LIBRARY_TYPE = "library_type"
        const val EXTRA_LIBRARY_POSTER = "library_poster"
        const val EXTRA_LIBRARY_SOURCE_ID = "library_source_id"
        private const val DEFAULT_PROFILE_ID = "default"
        private const val MAX_RETRIES_PER_STREAM = 1

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
