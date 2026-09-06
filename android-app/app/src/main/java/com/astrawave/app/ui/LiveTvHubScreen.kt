package com.astrawave.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.PlayerActivity
import com.astrawave.app.core.IptvSource
import com.astrawave.app.core.MultiviewPane
import com.astrawave.app.core.ProfileSafetyPolicy
import com.astrawave.app.data.ChannelCustomizationProjection
import com.astrawave.app.data.ChannelCustomizationStore
import com.astrawave.app.data.CombinedLiveTvRepository
import com.astrawave.app.data.CombinedLiveTvSnapshot
import com.astrawave.app.data.LastLiveChannel
import com.astrawave.app.data.LiveChannelGroup
import com.astrawave.app.data.LivePlaybackStore
import com.astrawave.app.data.LiveTvPreferenceStore
import com.astrawave.app.data.ProfileSafetyStore
import com.astrawave.app.data.SourceFusionPlaybackPlanner
import com.astrawave.app.data.SourceFusionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LiveTvMode { CHANNELS, SOURCES }
private enum class LiveTvFilter { ALL, FAVORITES, RECENTS }

private sealed interface LiveTvLoadState {
    data object Loading : LiveTvLoadState
    data class Ready(
        val snapshot: CombinedLiveTvSnapshot,
        val presentations: List<ChannelCustomizationProjection.LivePresentation>,
        val customizedCount: Int,
        val epgOverrideCount: Int,
    ) : LiveTvLoadState
    data class Error(val message: String) : LiveTvLoadState
}

@Composable
fun LiveTvHubScreen(
    sources: List<IptvSource>,
    onSourcesChanged: (List<IptvSource>) -> Unit,
    multiviewCount: Int = 0,
    onAddToMultiview: (MultiviewPane) -> Unit = {},
    onOpenMultiview: () -> Unit = {},
    profileId: String = "default",
    repository: CombinedLiveTvRepository = remember { CombinedLiveTvRepository() },
) {
    val context = LocalContext.current
    val safety = remember(profileId) { ProfileSafetyStore(context).load(profileId) }
    if (!ProfileSafetyPolicy.liveTvAllowed(safety)) {
        Column(Modifier.fillMaxSize().background(AstraWaveColors.Background).padding(24.dp)) {
            AstraWavePageHeader("Live TV", "Live TV is disabled for this kids profile.")
            Spacer(Modifier.height(18.dp))
            AstraWaveStatePanel("Restricted by profile settings", "A household administrator can enable Live TV for this profile from Privacy & Parental Controls.")
        }
        return
    }

    val scope = rememberCoroutineScope()
    val preferences = remember { LiveTvPreferenceStore(context) }
    val playbackStore = remember { LivePlaybackStore(context) }
    val playbackPlanner = remember { SourceFusionPlaybackPlanner(context) }
    val fusion = remember { SourceFusionRepository(context) }
    val channelStore = remember { ChannelCustomizationStore(context) }
    val channelProjection = remember { ChannelCustomizationProjection(context) }
    var mode by remember { mutableStateOf(LiveTvMode.CHANNELS) }
    var filter by remember { mutableStateOf(LiveTvFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var favoriteIds by remember { mutableStateOf(preferences.favoriteIds()) }
    var recentIds by remember { mutableStateOf(preferences.recentIds()) }
    var lastChannel by remember { mutableStateOf(playbackStore.last()) }
    var state by remember(sources, profileId) { mutableStateOf<LiveTvLoadState>(LiveTvLoadState.Loading) }

    LaunchedEffect(sources, profileId) {
        state = LiveTvLoadState.Loading
        state = try {
            val edits = channelStore.load(profileId)
            val epgOverrides = edits.mapNotNull { edit ->
                edit.epgIdOverride?.takeIf(String::isNotBlank)?.let { edit.channelId to it }
            }.toMap()
            val snapshot = withContext(Dispatchers.IO) { repository.load(sources, epgOverrides) }
            LiveTvLoadState.Ready(
                snapshot = snapshot,
                presentations = channelProjection.live(profileId, snapshot.groups),
                customizedCount = edits.size,
                epgOverrideCount = epgOverrides.size,
            )
        } catch (error: Exception) {
            LiveTvLoadState.Error(error.message ?: "Unable to load Live TV")
        }
    }

    fun launchPlayer(channel: LastLiveChannel) {
        val urls = ArrayList(channel.urls.distinct())
        if (urls.isEmpty()) return
        context.startActivity(
            Intent(context, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_URL, urls.first())
                .putStringArrayListExtra(PlayerActivity.EXTRA_URLS, urls)
                .putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true),
        )
    }

    fun play(group: LiveChannelGroup, displayName: String) {
        scope.launch {
            val plan = withContext(Dispatchers.IO) { playbackPlanner.live(group) }
            if (plan.urls.isEmpty()) {
                Toast.makeText(context, "No healthy stream is available for this channel right now.", Toast.LENGTH_LONG).show()
                return@launch
            }
            recentIds = preferences.markWatched(group.canonicalName)
            val channel = LastLiveChannel(
                id = group.canonicalName,
                name = displayName,
                source = plan.bestProvider ?: group.bestCandidate?.source.orEmpty(),
                urls = plan.urls,
                watchedAtEpochMs = System.currentTimeMillis(),
            )
            playbackStore.save(channel)
            lastChannel = channel
            launchPlayer(channel)
        }
    }

    fun resumeLast() {
        val channel = lastChannel ?: return
        scope.launch {
            val plan = withContext(Dispatchers.IO) { playbackPlanner.urls(channel.urls, channel.source.ifBlank { "Live TV" }) }
            if (plan.urls.isEmpty()) {
                Toast.makeText(context, "Your last channel is unavailable right now.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val refreshed = channel.copy(
                source = plan.bestProvider ?: channel.source,
                urls = plan.urls,
                watchedAtEpochMs = System.currentTimeMillis(),
            )
            playbackStore.save(refreshed)
            lastChannel = refreshed
            launchPlayer(refreshed)
        }
    }

    fun openOfficial(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(context, "No app is available to open this official provider.", Toast.LENGTH_LONG).show() }
    }

    Column(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
            AstraWavePageHeader(
                title = "Live TV",
                subtitle = "TiviMate-style control with AstraWave Smart Source Fusion, personalized channels and automatic backup streams.",
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveModeButton("Watch", mode == LiveTvMode.CHANNELS) { mode = LiveTvMode.CHANNELS }
                LiveModeButton("My Sources & Editor", mode == LiveTvMode.SOURCES) { mode = LiveTvMode.SOURCES }
                lastChannel?.let { channel -> LiveModeButton("▶ Resume ${channel.name}", false, ::resumeLast) }
                if (multiviewCount > 0) LiveModeButton("Multiview $multiviewCount/6", false, onOpenMultiview)
            }
        }

        when (mode) {
            LiveTvMode.SOURCES -> MyIptvScreen(sources = sources, onSourcesChanged = onSourcesChanged)
            LiveTvMode.CHANNELS -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 4.dp),
            ) {
                when (val current = state) {
                    LiveTvLoadState.Loading -> AstraWaveLoadingState("Getting Live TV ready", "Loading merged channels, guide mappings, profile edits and provider candidates.")
                    is LiveTvLoadState.Error -> AstraWaveErrorState("Live TV unavailable", current.message, retryLabel = null)
                    is LiveTvLoadState.Ready -> {
                        val snapshot = current.snapshot
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LiveMetric("CHANNELS", current.presentations.size.toString())
                            LiveMetric("MY SOURCES", sources.count { it.enabled }.toString())
                            LiveMetric("CUSTOM", current.customizedCount.toString())
                            LiveMetric("EPG MAPS", current.epgOverrideCount.toString())
                            LiveMetric("OFFICIAL", snapshot.handoffCount.toString())
                        }
                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Search channels, groups or sources") },
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LiveFilterButton("All", filter == LiveTvFilter.ALL) { filter = LiveTvFilter.ALL }
                            LiveFilterButton("★ Favorites ${favoriteIds.size}", filter == LiveTvFilter.FAVORITES) { filter = LiveTvFilter.FAVORITES }
                            LiveFilterButton("Recent ${recentIds.size}", filter == LiveTvFilter.RECENTS) { filter = LiveTvFilter.RECENTS }
                        }
                        Spacer(Modifier.height(16.dp))

                        val normalizedQuery = query.trim().lowercase()
                        val visible = current.presentations
                            .filter { presentation ->
                                normalizedQuery.isBlank() ||
                                    presentation.displayName.lowercase().contains(normalizedQuery) ||
                                    presentation.displayGroup.orEmpty().lowercase().contains(normalizedQuery) ||
                                    presentation.group.candidates.any { it.source.lowercase().contains(normalizedQuery) }
                            }
                            .filter { presentation ->
                                when (filter) {
                                    LiveTvFilter.ALL -> true
                                    LiveTvFilter.FAVORITES -> presentation.channelId in favoriteIds
                                    LiveTvFilter.RECENTS -> presentation.channelId in recentIds
                                }
                            }
                            .let { rows ->
                                if (filter == LiveTvFilter.RECENTS) {
                                    rows.sortedBy { recentIds.indexOf(it.channelId).let { index -> if (index < 0) Int.MAX_VALUE else index } }
                                } else rows
                            }

                        if (visible.isEmpty()) {
                            AstraWaveEmptyState(
                                title = "No channels match",
                                message = when (filter) {
                                    LiveTvFilter.FAVORITES -> "Favorite channels with the star, or switch back to All."
                                    LiveTvFilter.RECENTS -> "Channels you watch will appear here automatically."
                                    LiveTvFilter.ALL -> "Try another search, unhide channels in Channel Editor, or add an authorized provider."
                                },
                            )
                        } else {
                            visible.take(600).forEach { presentation ->
                                val group = presentation.group
                                val candidate = group.bestCandidate
                                val isFavorite = presentation.channelId in favoriteIds
                                val health = candidate?.let {
                                    fusion.score("live:${it.source}:${it.normalizedName}")
                                }
                                AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Column {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(Modifier.weight(1f)) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(
                                                        if (isFavorite) "★" else "☆",
                                                        color = if (isFavorite) AstraWaveColors.Accent else AstraWaveColors.TertiaryText,
                                                        modifier = Modifier.clickable { favoriteIds = preferences.toggleFavorite(presentation.channelId) },
                                                        style = MaterialTheme.typography.titleMedium,
                                                    )
                                                    Text(
                                                        listOfNotNull(presentation.channelNumber?.let { "$it" }, presentation.displayName).joinToString("  "),
                                                        color = AstraWaveColors.PrimaryText,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        maxLines = 2,
                                                    )
                                                }
                                                Spacer(Modifier.height(3.dp))
                                                Text(
                                                    listOfNotNull(presentation.displayGroup, candidate?.source).joinToString(" • ").ifBlank { "Source unavailable" },
                                                    color = AstraWaveColors.Accent,
                                                    style = MaterialTheme.typography.labelMedium,
                                                )
                                            }
                                            if (candidate != null) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    if (health != null && health.samples > 0) {
                                                        Text(
                                                            "SMART ${health.score}",
                                                            color = if (health.score >= 70) AstraWaveColors.Success else AstraWaveColors.Warning,
                                                            style = MaterialTheme.typography.labelLarge,
                                                        )
                                                    }
                                                    Text(
                                                        "Watch",
                                                        color = AstraWaveColors.Success,
                                                        modifier = Modifier.clickable { play(group, presentation.displayName) },
                                                        style = MaterialTheme.typography.labelLarge,
                                                    )
                                                    if (PlayerActivity.isDirectMediaUrl(candidate.url)) {
                                                        Text(
                                                            if (multiviewCount >= 6) "Mosaic full" else "+ Multiview",
                                                            color = if (multiviewCount >= 6) AstraWaveColors.TertiaryText else AstraWaveColors.PrimaryText,
                                                            modifier = Modifier.clickable(enabled = multiviewCount < 6) {
                                                                onAddToMultiview(
                                                                    MultiviewPane(
                                                                        id = "live:${presentation.channelId}",
                                                                        title = presentation.displayName,
                                                                        streamUrl = candidate.url,
                                                                        sourceName = candidate.source,
                                                                        channelId = candidate.id,
                                                                    ),
                                                                )
                                                            },
                                                            style = MaterialTheme.typography.labelLarge,
                                                        )
                                                    }
                                                }
                                            } else {
                                                Text("Unavailable", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "${group.candidates.size} source${if (group.candidates.size == 1) "" else "s"}${if (presentation.customized) " • CUSTOM" else ""}${if (presentation.epgIdOverride != null) " • EPG OVERRIDE" else ""}",
                                            color = AstraWaveColors.TertiaryText,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                        group.currentProgram?.title?.let { now ->
                                            Spacer(Modifier.height(7.dp))
                                            Text("Now • $now", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        }
                                        group.nextProgram?.title?.let { next -> Text("Next • $next", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                                    }
                                }
                            }
                        }

                        if (snapshot.handoffs.isNotEmpty() && filter == LiveTvFilter.ALL && normalizedQuery.isBlank()) {
                            Spacer(Modifier.height(24.dp))
                            AstraWaveSectionHeader(
                                title = "Official Free Watch Options",
                                subtitle = "Official provider destinations stay separate when native playback is not authorized.",
                            )
                            Spacer(Modifier.height(10.dp))
                            snapshot.handoffs.take(40).forEach { handoff ->
                                AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f)) {
                                            Text(handoff.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                                            Text(handoff.provider ?: handoff.group, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
                                        }
                                        Text("Open Official", color = AstraWaveColors.Accent, modifier = Modifier.clickable { openOfficial(handoff.actionUrl) }, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun LiveMetric(label: String, value: String) {
    Column(Modifier.background(AstraWaveColors.SurfaceRaised, MaterialTheme.shapes.large).padding(horizontal = 17.dp, vertical = 11.dp)) {
        Text(label, color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        Text(value, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun LiveModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) AstraWaveColors.Accent else AstraWaveColors.SurfaceRaised,
            contentColor = AstraWaveColors.PrimaryText,
        ),
        shape = MaterialTheme.shapes.large,
    ) { Text(label, maxLines = 1) }
}

@Composable
private fun LiveFilterButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) AstraWaveColors.Accent else AstraWaveColors.Surface,
            contentColor = AstraWaveColors.PrimaryText,
        ),
        shape = MaterialTheme.shapes.large,
    ) { Text(label, maxLines = 1) }
}
