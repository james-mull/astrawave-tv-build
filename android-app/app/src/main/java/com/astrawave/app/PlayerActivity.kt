package com.astrawave.app

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
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
import com.astrawave.app.data.FirebaseCloudRepository
import com.astrawave.app.data.LocalLibraryStore

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var streamUrls: List<String> = emptyList()
    private var streamIndex: Int = 0
    private var retryCountForCurrentStream: Int = 0
    private var resumePositionMs: Long = 0L
    private var lastKnownPositionMs: Long = 0L
    private var historyRecorded = false
    private var libraryStore: LocalLibraryStore? = null
    private var cloudStore: FirebaseCloudRepository? = null
    private var libraryItem: LibraryItemRef? = null
    private var profileId: String = DEFAULT_PROFILE_ID
    private var networkLost = false
    private var networkCallbackRegistered = false
    private var lastCloudSyncAtMs = 0L
    private var lastLocalSyncAtMs = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val progressTicker = object : Runnable {
        override fun run() {
            persistProgress(player, allowCloud = shouldCloudSync())
            handler.postDelayed(this, LOCAL_PROGRESS_INTERVAL_MS)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            networkLost = !hasValidatedNetwork()
            if (networkLost) {
                player?.let { lastKnownPositionMs = maxOf(lastKnownPositionMs, it.currentPosition.coerceAtLeast(0L)) }
                runOnUiThread {
                    Toast.makeText(this@PlayerActivity, "Network lost. Playback will resume automatically.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onAvailable(network: Network) {
            if (!networkLost) return
            networkLost = false
            runOnUiThread {
                val active = player ?: return@runOnUiThread
                Toast.makeText(this@PlayerActivity, "Network restored. Reconnecting…", Toast.LENGTH_SHORT).show()
                retryCountForCurrentStream = 0
                playCurrent(active, lastKnownPositionMs)
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && networkLost) {
                onAvailable(network)
            }
        }
    }

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
            cloudStore = FirebaseCloudRepository(this)
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

        configurePictureInPicture()
        registerNetworkCallback()

        playerView = PlayerView(this).apply { useController = true }
        setContentView(playerView)

        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView?.player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    lastKnownPositionMs = maxOf(lastKnownPositionMs, exo.currentPosition.coerceAtLeast(0L))
                    persistProgress(exo, allowCloud = shouldCloudSync(force = true))

                    if (networkLost || !hasValidatedNetwork()) {
                        networkLost = true
                        Toast.makeText(this@PlayerActivity, "Waiting for network…", Toast.LENGTH_SHORT).show()
                        return
                    }

                    when {
                        retryCountForCurrentStream < MAX_RETRIES_PER_STREAM -> {
                            retryCountForCurrentStream += 1
                            Toast.makeText(this@PlayerActivity, "Stream interrupted. Reconnecting…", Toast.LENGTH_SHORT).show()
                            handler.postDelayed({
                                player?.let { playCurrent(it, lastKnownPositionMs) }
                            }, RETRY_DELAY_MS)
                        }
                        streamIndex + 1 < streamUrls.size -> {
                            streamIndex += 1
                            retryCountForCurrentStream = 0
                            Toast.makeText(
                                this@PlayerActivity,
                                "Switching to backup ${streamIndex + 1} of ${streamUrls.size}…",
                                Toast.LENGTH_SHORT,
                            ).show()
                            handler.postDelayed({
                                player?.let { playCurrent(it, lastKnownPositionMs) }
                            }, BACKUP_FAILOVER_DELAY_MS)
                        }
                        else -> {
                            Toast.makeText(this@PlayerActivity, "All available streams failed.", Toast.LENGTH_LONG).show()
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
                    } else if (playbackState == Player.STATE_ENDED) {
                        persistProgress(exo, allowCloud = true)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        lastKnownPositionMs = maxOf(lastKnownPositionMs, exo.currentPosition.coerceAtLeast(0L))
                    }
                }
            })
            playCurrent(exo, resumePositionMs)
        }
        handler.postDelayed(progressTicker, LOCAL_PROGRESS_INTERVAL_MS)
    }

    private fun playCurrent(exo: ExoPlayer, positionMs: Long = 0L) {
        if (streamUrls.isEmpty() || streamIndex !in streamUrls.indices) return
        exo.stop()
        exo.clearMediaItems()
        exo.setMediaItem(MediaItem.fromUri(streamUrls[streamIndex]))
        if (positionMs > 0L) exo.seekTo(positionMs)
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun persistProgress(exo: ExoPlayer?, allowCloud: Boolean = false) {
        val item = libraryItem ?: return
        val active = exo ?: return
        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return
        val position = maxOf(lastKnownPositionMs, active.currentPosition.coerceAtLeast(0L))
        if (position <= 0L) return
        val savedPosition = position.coerceAtMost(duration)
        lastKnownPositionMs = savedPosition

        val now = System.currentTimeMillis()
        if (now - lastLocalSyncAtMs >= MIN_LOCAL_PROGRESS_WRITE_MS || savedPosition >= duration) {
            libraryStore?.saveProgress(profileId, item, savedPosition, duration)
            lastLocalSyncAtMs = now
        }

        if (allowCloud) {
            cloudStore?.takeIf { it.signedIn }?.saveProgress(
                mediaId = item.id,
                kind = item.type.name,
                title = item.title,
                positionMs = savedPosition,
                durationMs = duration,
                profileId = profileId,
            )
            lastCloudSyncAtMs = now
        }
    }

    private fun shouldCloudSync(force: Boolean = false): Boolean {
        if (force) return true
        return System.currentTimeMillis() - lastCloudSyncAtMs >= CLOUD_PROGRESS_INTERVAL_MS
    }

    private fun registerNetworkCallback() {
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkLost = !hasValidatedNetwork()
        runCatching {
            connectivity.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                networkCallback,
            )
            networkCallbackRegistered = true
        }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        networkCallbackRegistered = false
    }

    private fun hasValidatedNetwork(): Boolean {
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun configurePictureInPicture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
            builder.setSeamlessResizeEnabled(true)
        }
        setPictureInPictureParams(builder.build())
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode && player?.isPlaying == true) {
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerView?.useController = !isInPictureInPictureMode
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

    override fun onPause() {
        persistProgress(player, allowCloud = shouldCloudSync())
        super.onPause()
    }

    override fun onStop() {
        persistProgress(player, allowCloud = shouldCloudSync(force = true))
        super.onStop()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isInPictureInPictureMode) {
            releasePlayer()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        persistProgress(player, allowCloud = true)
        releasePlayer()
        unregisterNetworkCallback()
        super.onDestroy()
    }

    private fun releasePlayer() {
        playerView?.player = null
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
        private const val RETRY_DELAY_MS = 700L
        private const val BACKUP_FAILOVER_DELAY_MS = 250L
        private const val LOCAL_PROGRESS_INTERVAL_MS = 15_000L
        private const val MIN_LOCAL_PROGRESS_WRITE_MS = 10_000L
        private const val CLOUD_PROGRESS_INTERVAL_MS = 60_000L

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
