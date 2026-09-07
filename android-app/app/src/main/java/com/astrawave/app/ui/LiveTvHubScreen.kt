package com.astrawave.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    val device = LocalAstraWaveDeviceClass.current
    val isPhone = device == AstraWaveDeviceClass.PHONE
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
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var selectedChannelId by remember { mutableStateOf<String?>(null) }
    var favoriteIds by remember { mutableStateOf(preferences.favoriteIds()) }
    var recentIds by remember { mutableStateOf(preferences.recentIds()) }
    var lastChannel by remember { mutableStateOf(playbackStore.last()) }
    var refreshKey by remember { mutableStateOf(0) }
    var state by remember(sources, profileId) { mutableStateOf<LiveTvLoadState>(LiveTvLoadState.Loading) }

    DisposableEffect(channelStore, profileId) {
        val registration = channelStore.addChangeListener(profileId) { refreshKey += 1 }
        onDispose { registration.close() }
    }

    LaunchedEffect(sources, profileId, refreshKey) {
        state = LiveTvLoadState.Loading
        state = try {
            val edits = channelStore.load(profileId)
            val epgOverrides = edits.mapNotNull { edit -> edit.epgIdOverride?.takeIf(String::isNotBlank)?.let { edit.channelId to it } }.toMap()
            val snapshot = withContext(Dispatchers.IO) { repository.load(sources, epgOverrides) }
            LiveTvLoadState.Ready(snapshot, channelProjection.live(profileId, snapshot.groups), edits.size, epgOverrides.size)
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
            val channel = LastLiveChannel(group.canonicalName, displayName, plan.bestProvider ?: group.bestCandidate?.source.orEmpty(), plan.urls, System.currentTimeMillis())
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
            val refreshed = channel.copy(source = plan.bestProvider ?: channel.source, urls = plan.urls, watchedAtEpochMs = System.currentTimeMillis())
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
        Column(Modifier.fillMaxWidth().padding(horizontal = if (isPhone) 16.dp else 22.dp, vertical = 14.dp)) {
            Text("ASTRAWAVE LIVE", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
            Text("Live TV", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
            Text(
                if (isPhone) "Watch live channels fast. AstraWave picks the healthiest eligible source and keeps backups ready."
                else "Fast TV-first browsing with Smart Source Fusion, profile channel maps, catch-up-ready guide data and six-screen Multiview.",
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveModeButton("Watch", mode == LiveTvMode.CHANNELS) { mode = LiveTvMode.CHANNELS }
                if (!isPhone) LiveModeButton("Sources & Editor", mode == LiveTvMode.SOURCES) { mode = LiveTvMode.SOURCES }
                lastChannel?.let { channel -> LiveModeButton("▶ ${channel.name}", false, ::resumeLast) }
                if (multiviewCount > 0) LiveModeButton("Mosaic $multiviewCount/6", false, onOpenMultiview)
                if (isPhone) LiveModeButton("Sources", mode == LiveTvMode.SOURCES) { mode = LiveTvMode.SOURCES }
            }
        }

        when (mode) {
            LiveTvMode.SOURCES -> MyIptvScreen(sources = sources, onSourcesChanged = onSourcesChanged)
            LiveTvMode.CHANNELS -> when (val current = state) {
                LiveTvLoadState.Loading -> Box(Modifier.fillMaxSize().padding(24.dp)) { AstraWaveLoadingState("Getting Live TV ready", "Loading channels, guide mappings and healthy source candidates.") }
                is LiveTvLoadState.Error -> Box(Modifier.fillMaxSize().padding(24.dp)) { AstraWaveErrorState("Live TV unavailable", current.message, retryLabel = "Refresh", onRetry = { refreshKey += 1 }) }
                is LiveTvLoadState.Ready -> {
                    val normalizedQuery = query.trim().lowercase()
                    val groups = current.presentations.mapNotNull { it.displayGroup }.distinct().sorted()
                    val filtered = current.presentations
                        .filter { p -> selectedGroup == null || p.displayGroup == selectedGroup }
                        .filter { p -> normalizedQuery.isBlank() || p.displayName.lowercase().contains(normalizedQuery) || p.displayGroup.orEmpty().lowercase().contains(normalizedQuery) || p.group.candidates.any { it.source.lowercase().contains(normalizedQuery) } }
                        .filter { p -> when (filter) { LiveTvFilter.ALL -> true; LiveTvFilter.FAVORITES -> p.channelId in favoriteIds; LiveTvFilter.RECENTS -> p.channelId in recentIds } }
                        .let { rows -> if (filter == LiveTvFilter.RECENTS) rows.sortedBy { recentIds.indexOf(it.channelId).let { i -> if (i < 0) Int.MAX_VALUE else i } } else rows }

                    LaunchedEffect(filtered, selectedChannelId) {
                        if (selectedChannelId == null || filtered.none { it.channelId == selectedChannelId }) selectedChannelId = filtered.firstOrNull()?.channelId
                    }
                    val selected = filtered.firstOrNull { it.channelId == selectedChannelId } ?: filtered.firstOrNull()

                    if (isPhone) {
                        PhoneLiveChannels(
                            current = current,
                            filtered = filtered,
                            groups = groups,
                            query = query,
                            onQuery = { query = it },
                            selectedGroup = selectedGroup,
                            filter = filter,
                            onAll = { selectedGroup = null; filter = LiveTvFilter.ALL },
                            onFavorites = { selectedGroup = null; filter = LiveTvFilter.FAVORITES },
                            onRecents = { selectedGroup = null; filter = LiveTvFilter.RECENTS },
                            onGroup = { selectedGroup = it; filter = LiveTvFilter.ALL },
                            favoriteIds = favoriteIds,
                            onFavorite = { favoriteIds = preferences.toggleFavorite(it) },
                            selectedChannelId = selectedChannelId,
                            onSelect = { selectedChannelId = it },
                            onPlay = { p -> play(p.group, p.displayName) },
                            multiviewCount = multiviewCount,
                            onAddToMultiview = onAddToMultiview,
                        )
                    } else {
                        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.width(205.dp).fillMaxSize().background(AstraWaveColors.BackgroundRaised, MaterialTheme.shapes.large).padding(10.dp)) {
                                Text("GROUPS", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.height(8.dp))
                                LiveGroupRow("All Channels", selectedGroup == null && filter == LiveTvFilter.ALL) { selectedGroup = null; filter = LiveTvFilter.ALL }
                                LiveGroupRow("★ Favorites", filter == LiveTvFilter.FAVORITES) { selectedGroup = null; filter = LiveTvFilter.FAVORITES }
                                LiveGroupRow("Recent", filter == LiveTvFilter.RECENTS) { selectedGroup = null; filter = LiveTvFilter.RECENTS }
                                Spacer(Modifier.height(8.dp))
                                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                    groups.take(80).forEach { group -> LiveGroupRow(group, selectedGroup == group && filter == LiveTvFilter.ALL) { selectedGroup = group; filter = LiveTvFilter.ALL } }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text("${current.presentations.size} channels", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
                                Text("${current.customizedCount} customized • ${current.epgOverrideCount} EPG maps", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
                            }

                            ChannelListPane(filtered, selected, query, { query = it }, favoriteIds, { favoriteIds = preferences.toggleFavorite(it) }, { selectedChannelId = it })

                            Column(Modifier.weight(1f).fillMaxSize().background(AstraWaveColors.BackgroundRaised, MaterialTheme.shapes.large).padding(18.dp)) {
                                if (selected == null) AstraWaveEmptyState("Select a channel", "Choose a channel to see program details, source health and quick actions.")
                                else ChannelDetails(selected, current.snapshot, fusion, favoriteIds, { favoriteIds = preferences.toggleFavorite(it) }, { play(selected.group, selected.displayName) }, multiviewCount, onAddToMultiview, ::openOfficial)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneLiveChannels(
    current: LiveTvLoadState.Ready,
    filtered: List<ChannelCustomizationProjection.LivePresentation>,
    groups: List<String>,
    query: String,
    onQuery: (String) -> Unit,
    selectedGroup: String?,
    filter: LiveTvFilter,
    onAll: () -> Unit,
    onFavorites: () -> Unit,
    onRecents: () -> Unit,
    onGroup: (String) -> Unit,
    favoriteIds: Set<String>,
    onFavorite: (String) -> Unit,
    selectedChannelId: String?,
    onSelect: (String) -> Unit,
    onPlay: (ChannelCustomizationProjection.LivePresentation) -> Unit,
    multiviewCount: Int,
    onAddToMultiview: (MultiviewPane) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp)) {
        OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search channels") })
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            LiveModeButton("All", selectedGroup == null && filter == LiveTvFilter.ALL, onAll)
            LiveModeButton("Favorites", filter == LiveTvFilter.FAVORITES, onFavorites)
            LiveModeButton("Recent", filter == LiveTvFilter.RECENTS, onRecents)
            groups.take(30).forEach { group -> LiveModeButton(group, selectedGroup == group && filter == LiveTvFilter.ALL) { onGroup(group) } }
        }
        Spacer(Modifier.height(8.dp))
        Text("${filtered.size} channels", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            AstraWaveEmptyState("No channels match", "Try another search or filter, or check Sources if this lineup is empty.")
        } else {
            filtered.take(400).forEach { p ->
                val selected = p.channelId == selectedChannelId
                val candidate = p.group.bestCandidate
                val favorite = p.channelId in favoriteIds
                Column(
                    Modifier.fillMaxWidth().background(if (selected) AstraWaveColors.SurfaceRaised else AstraWaveColors.Surface, MaterialTheme.shapes.large)
                        .clickable { onSelect(p.channelId) }.padding(14.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(listOfNotNull(p.channelNumber?.toString(), p.displayName).joinToString("  "), color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            p.group.currentProgram?.title?.let { Text(it, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 1) }
                        }
                        Text(if (favorite) "★" else "☆", color = if (favorite) AstraWaveColors.Accent else AstraWaveColors.TertiaryText, modifier = Modifier.clickable { onFavorite(p.channelId) }.padding(4.dp))
                    }
                    Text(
                        candidate?.source ?: "No healthy source available",
                        color = if (candidate != null) AstraWaveColors.Success else AstraWaveColors.Warning,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (selected) {
                        Spacer(Modifier.height(10.dp))
                        p.group.nextProgram?.let { Text("Next • ${it.title}", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (candidate != null) AstraWavePrimaryButton("Watch", { onPlay(p) })
                            AstraWaveSecondaryButton(if (favorite) "★ Favorite" else "☆ Favorite", { onFavorite(p.channelId) })
                            if (candidate != null && PlayerActivity.isDirectMediaUrl(candidate.url)) {
                                AstraWaveSecondaryButton(if (multiviewCount >= 6) "Mosaic Full" else "+ Mosaic", {
                                    onAddToMultiview(MultiviewPane("live:${p.channelId}", p.displayName, candidate.url, candidate.source, channelId = candidate.id))
                                }, enabled = multiviewCount < 6)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("${current.customizedCount} customized • ${current.epgOverrideCount} EPG maps", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ChannelListPane(
    filtered: List<ChannelCustomizationProjection.LivePresentation>,
    selected: ChannelCustomizationProjection.LivePresentation?,
    query: String,
    onQuery: (String) -> Unit,
    favoriteIds: Set<String>,
    onFavorite: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.width(430.dp).fillMaxSize().background(AstraWaveColors.Surface, MaterialTheme.shapes.large).padding(10.dp)) {
        OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search channels") })
        Spacer(Modifier.height(8.dp))
        Text("${filtered.size} visible", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) AstraWaveEmptyState("No channels match", "Change the group/search or unhide channels in Channel Editor.")
        else Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            filtered.take(700).forEach { p ->
                val isSelected = p.channelId == selected?.channelId
                val favorite = p.channelId in favoriteIds
                Column(Modifier.fillMaxWidth().background(if (isSelected) AstraWaveColors.SurfaceRaised else AstraWaveColors.Surface, MaterialTheme.shapes.medium).clickable { onSelect(p.channelId) }.padding(horizontal = 10.dp, vertical = 9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(listOfNotNull(p.channelNumber?.toString(), p.displayName).joinToString("  "), color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(if (favorite) "★" else "☆", color = if (favorite) AstraWaveColors.Accent else AstraWaveColors.TertiaryText, modifier = Modifier.clickable { onFavorite(p.channelId) })
                    }
                    p.group.currentProgram?.title?.let { Text(it, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                    Text(p.group.bestCandidate?.source ?: "Source unavailable", color = if (isSelected) AstraWaveColors.Accent else AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

@Composable
private fun ChannelDetails(
    selected: ChannelCustomizationProjection.LivePresentation,
    snapshot: CombinedLiveTvSnapshot,
    fusion: SourceFusionRepository,
    favoriteIds: Set<String>,
    onFavorite: (String) -> Unit,
    onPlay: () -> Unit,
    multiviewCount: Int,
    onAddToMultiview: (MultiviewPane) -> Unit,
    openOfficial: (String) -> Unit,
) {
    val group = selected.group
    val candidate = group.bestCandidate
    val health = candidate?.let { fusion.score("live:${it.source}:${it.normalizedName}") }
    Text(selected.displayGroup ?: "LIVE TV", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(5.dp))
    Text(listOfNotNull(selected.channelNumber?.toString(), selected.displayName).joinToString("  "), color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineMedium, maxLines = 2)
    Spacer(Modifier.height(12.dp))
    group.currentProgram?.let { now ->
        Text("NOW", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        Text(now.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge, maxLines = 3)
    } ?: Text("No current guide data", color = AstraWaveColors.SecondaryText)
    group.nextProgram?.let { next -> Spacer(Modifier.height(8.dp)); Text("NEXT • ${next.title}", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 2) }
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (candidate != null) {
            AstraWavePrimaryButton("Watch", onPlay)
            if (PlayerActivity.isDirectMediaUrl(candidate.url)) AstraWaveSecondaryButton(if (multiviewCount >= 6) "Mosaic Full" else "+ Mosaic", {
                onAddToMultiview(MultiviewPane("live:${selected.channelId}", selected.displayName, candidate.url, candidate.source, channelId = candidate.id))
            }, enabled = multiviewCount < 6)
        }
        AstraWaveSecondaryButton(if (selected.channelId in favoriteIds) "★ Favorite" else "☆ Favorite", { onFavorite(selected.channelId) })
    }
    Spacer(Modifier.height(18.dp))
    AstraWaveStatePanel(
        title = if (health != null && health.samples > 0) "Smart Source ${health.score}/100" else if (candidate != null) "Smart Source learning" else "No healthy source",
        message = buildString {
            append(candidate?.source ?: "AstraWave could not find a currently healthy eligible stream")
            append(" • ${group.candidates.size} source${if (group.candidates.size == 1) "" else "s"}")
            if (selected.customized) append(" • Custom channel")
            if (selected.epgIdOverride != null) append(" • Manual EPG map")
            if (health != null && health.samples > 0) append(" • ${health.uptimePercent}% uptime")
        },
        tone = if (candidate == null) AstraWaveStateTone.WARNING else AstraWaveStateTone.SUCCESS,
    )
    if (snapshot.handoffs.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        Text("OFFICIAL OPTIONS", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        snapshot.handoffs.take(4).forEach { handoff -> Text(handoff.name, color = AstraWaveColors.Accent, modifier = Modifier.clickable { openOfficial(handoff.actionUrl) }.padding(vertical = 5.dp), style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun LiveGroupRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) AstraWaveColors.PrimaryText else AstraWaveColors.SecondaryText,
        modifier = Modifier.fillMaxWidth().background(if (selected) AstraWaveColors.Accent else AstraWaveColors.BackgroundRaised, MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
    )
    Spacer(Modifier.height(3.dp))
}

@Composable
private fun LiveModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) AstraWaveColors.Accent else AstraWaveColors.SurfaceRaised, contentColor = AstraWaveColors.PrimaryText),
        shape = MaterialTheme.shapes.large,
    ) { Text(label, maxLines = 1) }
}
