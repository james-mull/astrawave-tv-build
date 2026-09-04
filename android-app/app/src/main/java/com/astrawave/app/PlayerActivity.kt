package com.astrawave.app

import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
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
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.astrawave.app.core.LibraryItemRef
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.data.FirebaseCloudRepository
import com.astrawave.app.data.LocalLibraryStore
import com.astrawave.app.data.SeriesPlaybackCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var quickControls: LinearLayout? = null
    private var sourceButton: Button? = null
    private var skipIntroButton: Button? = null
    private var skipRecapButton: Button? = null
    private var upNextOverlay: LinearLayout? = null
    private var upNextText: TextView? = null
    private var upNextButton: Button? = null

    private var streamUrls: List<String> = emptyList()
    private var streamIndex = 0
    private var retryCountForCurrentStream = 0
    private var resumePositionMs = 0L
    private var lastKnownPositionMs = 0L
    private var historyRecorded = false
    private var libraryStore: LocalLibraryStore? = null
    private var cloudStore: FirebaseCloudRepository? = null
    private var libraryItem: LibraryItemRef? = null
    private var profileId = DEFAULT_PROFILE_ID
    private var networkLost = false
    private var networkCallbackRegistered = false
    private var lastCloudSyncAtMs = 0L
    private var lastLocalSyncAtMs = 0L
    private var playbackSpeed = 1f
    private var introEndMs = 0L
    private var recapEndMs = 0L

    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var nextPlan: SeriesPlaybackCoordinator.NextEpisodePlan? = null
    private var nextPlanLoading = false
    private var autoplayCancelled = false
    private var countdownStartedAtMs = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val progressTicker = object : Runnable {
        override fun run() {
            val active = player
            persistProgress(active, allowCloud = shouldCloudSync())
            updateSeriesControls(active)
            handler.postDelayed(this, UI_TICK_MS)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            networkLost = !hasValidatedNetwork()
            if (networkLost) {
                player?.let { lastKnownPositionMs = maxOf(lastKnownPositionMs, it.currentPosition.coerceAtLeast(0L)) }
                runOnUiThread { Toast.makeText(this@PlayerActivity, "Network lost. Playback will resume automatically.", Toast.LENGTH_SHORT).show() }
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
            if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && networkLost) onAvailable(network)
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
        introEndMs = intent.getLongExtra(EXTRA_INTRO_END_MS, 0L).coerceAtLeast(0L)
        recapEndMs = intent.getLongExtra(EXTRA_RECAP_END_MS, 0L).coerceAtLeast(0L)
        libraryItem = intentLibraryItem()
        if (libraryItem != null) {
            libraryStore = LocalLibraryStore(this)
            cloudStore = FirebaseCloudRepository(this)
            resumePositionMs = resumeFor(libraryItem)
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
            if (browserIntent.resolveActivity(packageManager) != null) startActivity(browserIntent)
            else Toast.makeText(this, "No browser is available to open this official stream.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        configurePictureInPicture()
        registerNetworkCallback()
        buildPlayerSurface()
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView?.player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    lastKnownPositionMs = maxOf(lastKnownPositionMs, exo.currentPosition.coerceAtLeast(0L))
                    persistProgress(exo, allowCloud = true)
                    if (networkLost || !hasValidatedNetwork()) {
                        networkLost = true
                        Toast.makeText(this@PlayerActivity, "Waiting for network…", Toast.LENGTH_SHORT).show()
                        return
                    }
                    when {
                        retryCountForCurrentStream < MAX_RETRIES_PER_STREAM -> {
                            retryCountForCurrentStream++
                            Toast.makeText(this@PlayerActivity, "Stream interrupted. Reconnecting…", Toast.LENGTH_SHORT).show()
                            handler.postDelayed({ player?.let { playCurrent(it, lastKnownPositionMs) } }, RETRY_DELAY_MS)
                        }
                        streamIndex + 1 < streamUrls.size -> {
                            streamIndex++
                            retryCountForCurrentStream = 0
                            updateSourceButton()
                            Toast.makeText(this@PlayerActivity, "Switching to backup ${streamIndex + 1} of ${streamUrls.size}…", Toast.LENGTH_SHORT).show()
                            handler.postDelayed({ player?.let { playCurrent(it, lastKnownPositionMs) } }, BACKUP_FAILOVER_DELAY_MS)
                        }
                        else -> Toast.makeText(this@PlayerActivity, "All available streams failed.", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        retryCountForCurrentStream = 0
                        exo.setPlaybackSpeed(playbackSpeed)
                        if (!historyRecorded) {
                            libraryItem?.let { libraryStore?.recordHistory(profileId, it) }
                            historyRecorded = true
                        }
                        prepareNextEpisode()
                    } else if (playbackState == Player.STATE_ENDED) {
                        persistProgress(exo, allowCloud = true)
                        if (!autoplayCancelled && nextPlan != null) playNextEpisode()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) lastKnownPositionMs = maxOf(lastKnownPositionMs, exo.currentPosition.coerceAtLeast(0L))
                }
            })
            playCurrent(exo, resumePositionMs)
        }
        handler.postDelayed(progressTicker, UI_TICK_MS)
    }

    private fun buildPlayerSurface() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            useController = true
            controllerShowTimeoutMs = 4_500
            controllerAutoShow = true
        }
        root.addView(playerView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        quickControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10, 10, 10, 10)
            setBackgroundColor(0x99000000.toInt())
        }
        sourceButton = quickButton(sourceLabel()) { showSourcePicker() }.also { quickControls?.addView(it) }
        quickControls?.addView(quickButton("Speed") { showSpeedPicker() })
        quickControls?.addView(quickButton("Audio") { showTrackPicker(C.TRACK_TYPE_AUDIO) })
        quickControls?.addView(quickButton("Subtitles") { showTrackPicker(C.TRACK_TYPE_TEXT) })
        skipRecapButton = quickButton("Skip Recap") { player?.seekTo(recapEndMs) }.also { it.visibility = View.GONE; quickControls?.addView(it) }
        skipIntroButton = quickButton("Skip Intro") { player?.seekTo(introEndMs) }.also { it.visibility = View.GONE; quickControls?.addView(it) }
        quickControls?.addView(quickButton("Stats") { showDiagnostics() })
        root.addView(quickControls, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.END; topMargin = 18; rightMargin = 18
        })

        upNextOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(28, 20, 28, 20)
            setBackgroundColor(0xDD11131A.toInt())
            visibility = View.GONE
        }
        upNextText = TextView(this).apply { setTextColor(Color.WHITE); textSize = 16f }
        upNextButton = quickButton("Play Next") { playNextEpisode() }
        upNextOverlay?.addView(upNextText)
        upNextOverlay?.addView(upNextButton)
        upNextOverlay?.addView(quickButton("Cancel Autoplay") {
            autoplayCancelled = true
            upNextOverlay?.visibility = View.GONE
        })
        root.addView(upNextOverlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.END; rightMargin = 28; bottomMargin = 86
        })
        setContentView(root)
    }

    private fun quickButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label; isAllCaps = false; setOnClickListener { action() }; setPadding(20, 8, 20, 8); minimumWidth = 0; minWidth = 0
    }

    private fun prepareNextEpisode() {
        val item = libraryItem ?: return
        if (item.type != LibraryMediaType.EPISODE || nextPlanLoading || nextPlan != null) return
        val sourceId = item.sourceId ?: return
        nextPlanLoading = true
        playbackScope.launch {
            val plan = runCatching { SeriesPlaybackCoordinator(this@PlayerActivity).prepareNext(sourceId, profileId) }.getOrNull()
            withContext(Dispatchers.Main) {
                nextPlan = plan
                nextPlanLoading = false
            }
        }
    }

    private fun updateSeriesControls(exo: ExoPlayer?) {
        val active = exo ?: return
        val position = active.currentPosition.coerceAtLeast(0L)
        skipRecapButton?.visibility = if (recapEndMs > position) View.VISIBLE else View.GONE
        skipIntroButton?.visibility = if (introEndMs > position) View.VISIBLE else View.GONE

        val plan = nextPlan ?: return
        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return
        val remaining = (duration - position).coerceAtLeast(0L)
        if (remaining <= UP_NEXT_WINDOW_MS && !autoplayCancelled) {
            if (countdownStartedAtMs == 0L) countdownStartedAtMs = System.currentTimeMillis()
            val seconds = ((remaining + 999L) / 1000L).coerceIn(1L, UP_NEXT_WINDOW_MS / 1000L)
            upNextText?.text = "Up Next • S${plan.episode.season}E${plan.episode.episode} ${plan.episode.title}\nPlaying automatically in ${seconds}s"
            upNextOverlay?.visibility = View.VISIBLE
        } else upNextOverlay?.visibility = View.GONE
    }

    private fun playNextEpisode() {
        val plan = nextPlan ?: return
        val exo = player ?: return
        persistProgress(exo, allowCloud = true)
        val previous = libraryItem
        val baseId = previous?.id?.replace(Regex(":s\\d+e\\d+$", RegexOption.IGNORE_CASE), "") ?: "series:${plan.seriesId}"
        libraryItem = LibraryItemRef(
            id = "$baseId:s${plan.episode.season}e${plan.episode.episode}",
            type = LibraryMediaType.EPISODE,
            title = "S${plan.episode.season}E${plan.episode.episode} ${plan.episode.title}",
            posterUrl = plan.episode.thumbnail ?: previous?.posterUrl,
            sourceId = plan.episode.id,
        )
        streamUrls = plan.urls
        streamIndex = 0
        retryCountForCurrentStream = 0
        historyRecorded = false
        autoplayCancelled = false
        countdownStartedAtMs = 0L
        nextPlan = null
        nextPlanLoading = false
        lastKnownPositionMs = resumeFor(libraryItem)
        upNextOverlay?.visibility = View.GONE
        Toast.makeText(this, "Now playing S${plan.episode.season}E${plan.episode.episode} • ${plan.episode.title}", Toast.LENGTH_SHORT).show()
        playCurrent(exo, lastKnownPositionMs)
    }

    private fun resumeFor(item: LibraryItemRef?): Long = item?.let { target ->
        libraryStore?.progress(profileId)?.firstOrNull { it.item.id == target.id }?.takeIf { !it.completed }?.positionMs
    } ?: 0L

    private fun playCurrent(exo: ExoPlayer, positionMs: Long = 0L) {
        if (streamUrls.isEmpty() || streamIndex !in streamUrls.indices) return
        exo.stop(); exo.clearMediaItems(); exo.setMediaItem(MediaItem.fromUri(streamUrls[streamIndex]))
        if (positionMs > 0L) exo.seekTo(positionMs)
        exo.prepare(); exo.playWhenReady = true; updateSourceButton()
    }

    private fun sourceLabel(): String = if (streamUrls.size <= 1) "Source" else "Source ${streamIndex + 1}/${streamUrls.size}"
    private fun updateSourceButton() { sourceButton?.text = sourceLabel() }

    private fun showSourcePicker() {
        if (streamUrls.size <= 1) { Toast.makeText(this, "Only one source is available.", Toast.LENGTH_SHORT).show(); return }
        val labels = streamUrls.mapIndexed { index, url ->
            val host = runCatching { Uri.parse(url).host }.getOrNull().orEmpty().ifBlank { "Stream ${index + 1}" }
            "${if (index == streamIndex) "✓ " else ""}Source ${index + 1} • $host"
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Playback source").setItems(labels) { _, which ->
            if (which == streamIndex) return@setItems
            val exo = player ?: return@setItems
            lastKnownPositionMs = maxOf(lastKnownPositionMs, exo.currentPosition.coerceAtLeast(0L)); streamIndex = which; retryCountForCurrentStream = 0
            playCurrent(exo, lastKnownPositionMs)
        }.setNegativeButton("Cancel", null).show()
    }

    private fun showSpeedPicker() {
        val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        val labels = speeds.map { if (it == 1f) "1.0× • Normal" else "${it}×" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Playback speed").setSingleChoiceItems(labels, speeds.indexOfFirst { it == playbackSpeed }.coerceAtLeast(2)) { dialog, which ->
            playbackSpeed = speeds[which]; player?.setPlaybackSpeed(playbackSpeed); dialog.dismiss()
        }.setNegativeButton("Cancel", null).show()
    }

    private data class TrackChoice(val label: String, val language: String?)
    private fun showTrackPicker(trackType: Int) {
        val exo = player ?: return
        val choices = mutableListOf<TrackChoice>()
        if (trackType == C.TRACK_TYPE_TEXT) choices += TrackChoice("Off", null)
        exo.currentTracks.groups.filter { it.type == trackType }.forEach { group ->
            for (index in 0 until group.length) {
                val format = group.getTrackFormat(index); val language = format.language
                val label = format.label?.takeIf { it.isNotBlank() } ?: language?.uppercase() ?: if (trackType == C.TRACK_TYPE_AUDIO) "Audio ${choices.size + 1}" else "Subtitle ${choices.size + 1}"
                if (choices.none { it.label == label && it.language == language }) choices += TrackChoice(label, language)
            }
        }
        if (choices.isEmpty() || (trackType == C.TRACK_TYPE_TEXT && choices.size == 1)) {
            Toast.makeText(this, if (trackType == C.TRACK_TYPE_AUDIO) "No alternate audio tracks detected." else "No subtitle tracks detected.", Toast.LENGTH_SHORT).show(); return
        }
        AlertDialog.Builder(this).setTitle(if (trackType == C.TRACK_TYPE_AUDIO) "Audio track" else "Subtitles").setItems(choices.map { it.label }.toTypedArray()) { _, which ->
            val selected = choices[which]; val builder = exo.trackSelectionParameters.buildUpon()
            if (trackType == C.TRACK_TYPE_AUDIO) builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false).setPreferredAudioLanguage(selected.language)
            else if (selected.language == null) builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            else builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setPreferredTextLanguage(selected.language)
            exo.trackSelectionParameters = builder.build()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun showDiagnostics() {
        val exo = player ?: return
        val video = exo.videoFormat; val audio = exo.audioFormat; val bufferedMs = (exo.bufferedPosition - exo.currentPosition).coerceAtLeast(0L)
        val host = streamUrls.getOrNull(streamIndex)?.let { runCatching { Uri.parse(it).host }.getOrNull() }.orEmpty().ifBlank { "Unknown" }
        val next = nextPlan?.episode?.let { "S${it.season}E${it.episode} ${it.title}" } ?: if (nextPlanLoading) "Loading…" else "None"
        val message = buildString {
            appendLine("Source: ${streamIndex + 1}/${streamUrls.size} • $host")
            appendLine("Network: ${if (hasValidatedNetwork()) "Validated" else "Unavailable / unvalidated"}")
            appendLine("State: ${playbackStateLabel(exo.playbackState)}${if (exo.isPlaying) " • Playing" else ""}")
            appendLine("Speed: ${playbackSpeed}×")
            appendLine("Buffered: ${bufferedMs / 1000}s")
            appendLine("Video: ${video?.let(::formatVideo) ?: "No video format reported"}")
            appendLine("Audio: ${audio?.let(::formatAudio) ?: "No audio format reported"}")
            appendLine("Up Next: $next")
            append("Retries: $retryCountForCurrentStream")
        }
        AlertDialog.Builder(this).setTitle("Playback diagnostics").setMessage(message).setPositiveButton("Close", null)
            .setNeutralButton("Retry source") { _, _ -> lastKnownPositionMs = maxOf(lastKnownPositionMs, exo.currentPosition.coerceAtLeast(0L)); retryCountForCurrentStream = 0; playCurrent(exo, lastKnownPositionMs) }.show()
    }

    private fun formatVideo(format: Format): String {
        val size = if (format.width > 0 && format.height > 0) "${format.width}×${format.height}" else "adaptive"
        val bitrate = format.bitrate.takeIf { it > 0 }?.let { " • ${(it / 1000f).roundToInt()} kbps" }.orEmpty()
        return "$size • ${format.sampleMimeType?.substringAfter('/') ?: "video"}$bitrate"
    }
    private fun formatAudio(format: Format): String {
        val channels = format.channelCount.takeIf { it > 0 }?.let { " • ${it}ch" }.orEmpty()
        return "${format.language?.uppercase() ?: "default"} • ${format.sampleMimeType?.substringAfter('/') ?: "audio"}$channels"
    }
    private fun playbackStateLabel(state: Int): String = when (state) { Player.STATE_IDLE -> "Idle"; Player.STATE_BUFFERING -> "Buffering"; Player.STATE_READY -> "Ready"; Player.STATE_ENDED -> "Ended"; else -> "Unknown" }

    private fun persistProgress(exo: ExoPlayer?, allowCloud: Boolean = false) {
        val item = libraryItem ?: return; val active = exo ?: return
        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return
        val position = maxOf(lastKnownPositionMs, active.currentPosition.coerceAtLeast(0L)); if (position <= 0L) return
        val savedPosition = position.coerceAtMost(duration); lastKnownPositionMs = savedPosition; val now = System.currentTimeMillis()
        if (now - lastLocalSyncAtMs >= MIN_LOCAL_PROGRESS_WRITE_MS || savedPosition >= duration) { libraryStore?.saveProgress(profileId, item, savedPosition, duration); lastLocalSyncAtMs = now }
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
    private fun shouldCloudSync(): Boolean = System.currentTimeMillis() - lastCloudSyncAtMs >= CLOUD_PROGRESS_INTERVAL_MS

    private fun registerNetworkCallback() {
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager; networkLost = !hasValidatedNetwork()
        runCatching { connectivity.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), networkCallback); networkCallbackRegistered = true }
    }
    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager; runCatching { connectivity.unregisterNetworkCallback(networkCallback) }; networkCallbackRegistered = false
    }
    private fun hasValidatedNetwork(): Boolean {
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager; val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun configurePictureInPicture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { builder.setAutoEnterEnabled(true); builder.setSeamlessResizeEnabled(true) }
        setPictureInPictureParams(builder.build())
    }
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPictureInPictureMode && player?.isPlaying == true) runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerView?.useController = !isInPictureInPictureMode; quickControls?.visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE; upNextOverlay?.visibility = View.GONE
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

    override fun onPause() { persistProgress(player, allowCloud = shouldCloudSync()); super.onPause() }
    override fun onStop() { persistProgress(player, allowCloud = true); super.onStop(); if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isInPictureInPictureMode) releasePlayer() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); persistProgress(player, allowCloud = true); releasePlayer(); unregisterNetworkCallback(); playbackScope.cancel(); super.onDestroy() }
    private fun releasePlayer() { playerView?.player = null; player?.release(); player = null }

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
        const val EXTRA_INTRO_END_MS = "intro_end_ms"
        const val EXTRA_RECAP_END_MS = "recap_end_ms"
        private const val DEFAULT_PROFILE_ID = "default"
        private const val MAX_RETRIES_PER_STREAM = 1
        private const val RETRY_DELAY_MS = 700L
        private const val BACKUP_FAILOVER_DELAY_MS = 250L
        private const val UI_TICK_MS = 1_000L
        private const val MIN_LOCAL_PROGRESS_WRITE_MS = 10_000L
        private const val CLOUD_PROGRESS_INTERVAL_MS = 60_000L
        private const val UP_NEXT_WINDOW_MS = 45_000L

        fun isDirectMediaUrl(url: String): Boolean {
            val normalized = url.substringBefore('?').substringBefore('#').lowercase()
            return normalized.endsWith(".m3u8") || normalized.endsWith(".mpd") || normalized.endsWith(".mp4") || normalized.endsWith(".m4v") || normalized.endsWith(".webm") || normalized.endsWith(".mp3") || normalized.endsWith(".aac") || normalized.endsWith(".m4a") || normalized.endsWith(".ogg") || normalized.endsWith(".ts")
        }
    }
}
