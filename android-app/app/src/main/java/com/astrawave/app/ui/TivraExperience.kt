package com.astrawave.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.astrawave.app.PlayerActivity
import com.astrawave.app.core.IptvSource
import com.astrawave.app.data.CombinedLiveTvRepository
import com.astrawave.app.data.GuideChannelRow
import com.astrawave.app.data.GuideRepository
import com.astrawave.app.data.GuideSnapshot
import com.astrawave.app.data.LiveChannelGroup
import com.astrawave.app.data.LiveChannel
import com.astrawave.app.data.LiveTvRepository
import com.astrawave.app.data.SportsEvent
import com.astrawave.app.data.SportsGuideItem
import com.astrawave.app.data.SportsGuideRepository
import com.astrawave.app.data.SportsGuideSnapshot
import com.astrawave.app.data.SportsGuideEvent
import com.astrawave.app.data.SportsResolution
import com.astrawave.app.data.SportsWatchCandidate
import com.astrawave.app.data.StremioLiveCatalogDiscovery
import com.astrawave.app.data.XmlTvProgramme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private sealed interface TivraLoad<out T> {
    data object Loading : TivraLoad<Nothing>
    data class Ready<T>(val value: T) : TivraLoad<T>
    data class Error(val message: String) : TivraLoad<Nothing>
}

private enum class SportsHubTab { SCORES, MY_TEAMS, CHANNELS }

@Composable
fun TivraLiveTvScreen(
    sources: List<IptvSource>,
    onSourcesChanged: (List<IptvSource>) -> Unit,
    preview: LivePreviewController,
    profileId: String,
    previewData: Boolean = false,
) {
    val context = LocalContext.current
    val phone = LocalAstraWaveDeviceClass.current == AstraWaveDeviceClass.PHONE
    val repository = remember { CombinedLiveTvRepository() }
    var state by remember(sources, profileId) { mutableStateOf<TivraLoad<List<LiveChannelGroup>>>(TivraLoad.Loading) }
    var category by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(preview.state.contentId) }
    var showSources by remember { mutableStateOf(false) }

    LaunchedEffect(sources, profileId) {
        if (previewData) {
            state = TivraLoad.Ready(reviewLiveGroups())
            return@LaunchedEffect
        }
        state = try {
            val snapshot = withContext(Dispatchers.IO) {
                repository.load(sources, includeEpg = true, includeBundledFreeTv = false)
            }
            TivraLoad.Ready(snapshot.groups)
        } catch (error: Exception) {
            TivraLoad.Error(error.message ?: "Unable to load Live TV")
        }
    }

    fun select(group: LiveChannelGroup) {
        val repeated = preview.select(
            contentId = group.canonicalName,
            title = group.displayName,
            subtitle = group.currentProgram?.title ?: group.bestCandidate?.group ?: "Live TV",
            urls = group.candidates.map { it.url },
            provider = group.bestCandidate?.source,
        )
        selectedId = group.canonicalName
        if (repeated) openFullScreen(context, preview.state.urls)
    }

    Column(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
        ExperienceTopBar("Live TV", "Channels", if (showSources) "BACK TO TV" else "SOURCES") { showSources = !showSources }
        if (showSources) {
            MyIptvScreen(sources, onSourcesChanged)
            return@Column
        }
        when (val current = state) {
            TivraLoad.Loading -> AstraWaveLoadingState("Loading channels", "Preparing your providers and guide.")
            is TivraLoad.Error -> AstraWaveErrorState("Live TV unavailable", current.message)
            is TivraLoad.Ready -> {
                if (!previewData && sources.none { it.enabled }) {
                    ProviderSetupState { showSources = true }
                    return@Column
                }
                val categories = current.value.map { it.bestCandidate?.group ?: "Other" }.distinct().sorted()
                val channels = current.value.filter { category == null || (it.bestCandidate?.group ?: "Other") == category }
                if (selectedId == null) channels.firstOrNull()?.let(::select)
                if (phone) {
                    Column(Modifier.fillMaxSize()) {
                        PreviewPane(preview, Modifier.fillMaxWidth().aspectRatio(16f / 9f), ::openPreviewFullScreen)
                        CategoryRail(categories, category) { category = it }
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(channels, key = { it.canonicalName }) { channel ->
                                ChannelLedgerRow(channel, channel.canonicalName == selectedId) { select(channel) }
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CategoryLedger(categories, category, Modifier.width(188.dp)) { category = it }
                        LazyColumn(Modifier.width(430.dp).fillMaxHeight()) {
                            items(channels, key = { it.canonicalName }) { channel ->
                                ChannelLedgerRow(channel, channel.canonicalName == selectedId) { select(channel) }
                            }
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            PreviewPane(preview, Modifier.fillMaxWidth().aspectRatio(16f / 9f), ::openPreviewFullScreen)
                            NowNextPanel(channels.firstOrNull { it.canonicalName == selectedId })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TivraGuideScreen(
    sources: List<IptvSource>,
    preview: LivePreviewController,
    profileId: String,
    previewData: Boolean = false,
) {
    val context = LocalContext.current
    val phone = LocalAstraWaveDeviceClass.current == AstraWaveDeviceClass.PHONE
    val repository = remember { GuideRepository() }
    var state by remember(sources, profileId) { mutableStateOf<TivraLoad<GuideSnapshot>>(TivraLoad.Loading) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var dayOffset by remember { mutableStateOf(0) }
    val timeline = rememberScrollState()

    LaunchedEffect(sources, profileId) {
        if (previewData) {
            state = TivraLoad.Ready(reviewGuideSnapshot())
            return@LaunchedEffect
        }
        state = try {
            TivraLoad.Ready(withContext(Dispatchers.IO) { repository.load(sources) })
        } catch (error: Exception) {
            TivraLoad.Error(error.message ?: "Unable to load guide")
        }
    }

    fun select(row: GuideChannelRow) {
        val repeated = preview.select(
            row.id,
            row.name,
            row.now?.title ?: "No programme information",
            row.playableUrls,
            row.preferredSource,
        )
        selectedId = row.id
        if (repeated) openFullScreen(context, row.playableUrls)
    }

    Column(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
        ExperienceTopBar("Guide", "Full program guide", "NOW") {
            dayOffset = 0
        }
        when (val current = state) {
            TivraLoad.Loading -> AstraWaveLoadingState("Loading guide", "Matching channels with XMLTV schedules.")
            is TivraLoad.Error -> AstraWaveErrorState("Guide unavailable", current.message)
            is TivraLoad.Ready -> {
                if (!previewData && sources.none { it.enabled }) {
                    ProviderSetupState()
                    return@Column
                }
                val rows = current.value.rows.filter { !it.id.startsWith("handoff:") }
                if (selectedId == null) rows.firstOrNull()?.let(::select)
                if (phone) {
                    Column(Modifier.fillMaxSize()) {
                        PreviewPane(preview, Modifier.fillMaxWidth().aspectRatio(16f / 9f), ::openPreviewFullScreen)
                        DayRail(dayOffset) { dayOffset = it }
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(rows, key = { it.id }) { row -> PhoneGuideLedgerRow(row, row.id == selectedId) { select(row) } }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
                        Row(Modifier.fillMaxWidth().height(230.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            PreviewPane(preview, Modifier.width(390.dp).fillMaxHeight(), ::openPreviewFullScreen)
                            GuideSelectionDetails(rows.firstOrNull { it.id == selectedId }, Modifier.weight(1f))
                        }
                        DayRail(dayOffset) { dayOffset = it }
                        GuideTimelineHeader(dayOffset, timeline)
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(rows, key = { it.id }) { row ->
                                TivraGuideTimelineRow(row, dayOffset, timeline, row.id == selectedId) { select(row) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TivraSportsScreen(
    sources: List<IptvSource>,
    preview: LivePreviewController,
    profileId: String,
    previewData: Boolean = false,
) {
    val context = LocalContext.current
    val phone = LocalAstraWaveDeviceClass.current == AstraWaveDeviceClass.PHONE
    val repository = remember { SportsGuideRepository() }
    val addonLive = remember { StremioLiveCatalogDiscovery(context) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var tab by remember { mutableStateOf(SportsHubTab.SCORES) }
    var state by remember(sources, date, profileId) { mutableStateOf<TivraLoad<SportsGuideSnapshot>>(TivraLoad.Loading) }
    var selectedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sources, date, profileId) {
        if (previewData) {
            state = TivraLoad.Ready(reviewSportsSnapshot(date))
            return@LaunchedEffect
        }
        state = try {
            val snapshot = withContext(Dispatchers.IO) {
                val names = runCatching { addonLive.sportsChannelNames(profileId) }.getOrDefault(emptyList())
                repository.load(date, sources, addonSportsChannelNames = names)
            }
            TivraLoad.Ready(snapshot)
        } catch (error: Exception) {
            TivraLoad.Error(error.message ?: "Unable to load sports")
        }
    }

    fun select(item: SportsGuideItem) {
        val urls = item.resolution?.candidates.orEmpty().map { it.streamUrl }
        val repeated = preview.select(
            "sports:${item.event.id}",
            item.event.name,
            listOfNotNull(item.event.league, item.broadcasterNames.firstOrNull()).joinToString("  |  "),
            urls,
            item.watchCandidate?.source,
        )
        selectedId = item.event.id
        if (repeated) openFullScreen(context, urls)
    }

    Column(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
        ExperienceTopBar("Sports", date.format(DateTimeFormatter.ofPattern("EEE, MMM d")), "MULTIVIEW") { }
        SportsTabs(tab) { tab = it }
        when (val current = state) {
            TivraLoad.Loading -> AstraWaveLoadingState("Loading sports", "Matching games to your channels.")
            is TivraLoad.Error -> AstraWaveErrorState("Sports unavailable", current.message)
            is TivraLoad.Ready -> {
                val all = current.value.events
                val events = when (tab) {
                    SportsHubTab.SCORES -> all
                    SportsHubTab.MY_TEAMS -> all.filter { it.event.isLive || it.watchCandidate != null }
                    SportsHubTab.CHANNELS -> all.filter { it.watchCandidate != null }
                }
                if (selectedId == null) events.firstOrNull()?.let(::select)
                val selected = events.firstOrNull { it.event.id == selectedId }
                if (phone) {
                    Column(Modifier.fillMaxSize()) {
                        PreviewPane(preview, Modifier.fillMaxWidth().aspectRatio(16f / 9f), ::openPreviewFullScreen)
                        SportsDateRail(date) { date = it }
                        LeagueRows(events, selectedId, ::select, Modifier.fillMaxSize())
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
                        Row(Modifier.fillMaxWidth().height(250.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            PreviewPane(preview, Modifier.width(430.dp).fillMaxHeight(), ::openPreviewFullScreen)
                            MatchupPanel(selected, Modifier.weight(1f))
                        }
                        SportsDateRail(date) { date = it }
                        LeagueRows(events, selectedId, ::select, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewPane(
    controller: LivePreviewController,
    modifier: Modifier,
    onFullScreen: (android.content.Context, List<String>) -> Unit,
) {
    val context = LocalContext.current
    val state = controller.state
    val player = remember { ExoPlayer.Builder(context).build().apply { volume = 0.65f } }
    LaunchedEffect(state.activeUrl) {
        val url = state.activeUrl
        if (url == null) {
            player.stop()
            player.clearMediaItems()
        } else {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(modifier.background(Color.Black).border(1.dp, AstraWaveColors.Divider)) {
        if (state.activeUrl != null) {
            AndroidView(
                factory = { PlayerView(it).apply { useController = false; this.player = player } },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize().clickable { onFullScreen(context, state.urls) },
            )
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f)).padding(14.dp),
        ) {
            Text(state.provider?.uppercase() ?: "ASTRAWAVE PREVIEW", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelSmall)
            Text(state.title, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(state.subtitle, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

private fun openPreviewFullScreen(context: android.content.Context, urls: List<String>) = openFullScreen(context, urls)

private fun openFullScreen(context: android.content.Context, urls: List<String>) {
    if (urls.isEmpty()) return
    context.startActivity(
        Intent(context, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_URL, urls.first())
            .putStringArrayListExtra(PlayerActivity.EXTRA_URLS, ArrayList(urls))
            .putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true),
    )
}

@Composable
private fun ExperienceTopBar(title: String, subtitle: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle.uppercase(), color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        }
        Text(action, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable(onClick = onAction).padding(12.dp))
    }
}

@Composable
private fun ProviderSetupState(onAdd: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("BRING YOUR OWN TV", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(10.dp))
        Text("Connect a television provider", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Add an M3U playlist or Xtream Codes account to unlock Live TV, the guide, and channel-matched sports.", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(20.dp))
        AstraWavePrimaryButton("Add provider", onAdd)
    }
}

@Composable
private fun CategoryRail(categories: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item { CompactTab("ALL", selected == null) { onSelect(null) } }
        items(categories) { CompactTab(it.uppercase(), selected == it) { onSelect(it) } }
    }
}

@Composable
private fun CategoryLedger(categories: List<String>, selected: String?, modifier: Modifier, onSelect: (String?) -> Unit) {
    Column(modifier.background(AstraWaveColors.BackgroundRaised).padding(8.dp)) {
        Text("CHANNELS", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(10.dp))
        CompactLedgerItem("All channels", selected == null) { onSelect(null) }
        CompactLedgerItem("Favorites", false) { }
        CompactLedgerItem("Recent", false) { }
        LazyColumn(Modifier.weight(1f)) {
            items(categories) { CompactLedgerItem(it, selected == it) { onSelect(it) } }
        }
    }
}

@Composable
private fun CompactLedgerItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else AstraWaveColors.SecondaryText,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth().background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
    )
}

@Composable
private fun CompactTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else AstraWaveColors.SecondaryText,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.background(if (selected) Color.White else AstraWaveColors.Surface)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun ChannelLedgerRow(channel: LiveChannelGroup, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(74.dp)
            .background(if (selected) AstraWaveColors.SurfaceFocus else Color.Transparent)
            .border(if (selected) 1.dp else 0.dp, if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(42.dp).height(42.dp).background(AstraWaveColors.SurfaceRaised), contentAlignment = Alignment.Center) {
            Text(channel.displayName.take(2).uppercase(), color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.displayName, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(channel.currentProgram?.title ?: "No guide information", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        Text(if (channel.currentProgram != null) "NOW" else "LIVE", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun NowNextPanel(channel: LiveChannelGroup?) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text("NOW", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelSmall)
        Text(channel?.currentProgram?.title ?: "Program information unavailable", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text("NEXT", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        Text(channel?.nextProgram?.title ?: "No upcoming program", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DayRail(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (-1..5).forEach { offset ->
            val label = when (offset) { -1 -> "YESTERDAY"; 0 -> "TODAY"; 1 -> "TOMORROW"; else -> LocalDate.now().plusDays(offset.toLong()).format(DateTimeFormatter.ofPattern("EEE d")) }
            CompactTab(label, selected == offset) { onSelect(offset) }
        }
    }
}

@Composable
private fun PhoneGuideLedgerRow(row: GuideChannelRow, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(if (selected) AstraWaveColors.SurfaceFocus else Color.Transparent)
            .clickable(onClick = onClick).padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(row.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
            Text(if (row.hasRealEpg) "GUIDE" else "LIVE", color = if (row.hasRealEpg) AstraWaveColors.Success else AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        }
        Text(row.now?.title ?: "No program information", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Text("Next  ${row.next?.title ?: "Not available"}", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

@Composable
private fun GuideSelectionDetails(row: GuideChannelRow?, modifier: Modifier) {
    Column(modifier.fillMaxHeight().padding(18.dp)) {
        Text("ON NOW", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelSmall)
        Text(row?.now?.title ?: "Select a program", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineMedium, maxLines = 2)
        Spacer(Modifier.height(8.dp))
        Text(row?.name ?: "Browse the timeline below", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(14.dp))
        Text("Program details and the upcoming schedule remain visible while the preview continues.", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
    }
}

@Composable
private fun GuideTimelineHeader(dayOffset: Int, scroll: androidx.compose.foundation.ScrollState) {
    val start = guideWindowStart(dayOffset)
    Row(Modifier.fillMaxWidth().height(40.dp).background(AstraWaveColors.BackgroundRaised), verticalAlignment = Alignment.CenterVertically) {
        Text("CHANNEL", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(190.dp).padding(start = 12.dp))
        Row(Modifier.weight(1f).horizontalScroll(scroll)) {
            repeat(12) { index ->
                Text(formatTime(start + index * 30L * 60L * 1000L), color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(130.dp))
            }
        }
    }
}

@Composable
private fun TivraGuideTimelineRow(row: GuideChannelRow, dayOffset: Int, scroll: androidx.compose.foundation.ScrollState, selected: Boolean, onClick: () -> Unit) {
    val start = guideWindowStart(dayOffset)
    val end = start + 6L * 60L * 60L * 1000L
    val programs = row.programmes.mapNotNull { programme ->
        val pStart = LiveTvRepository.parseXmlTvEpochMs(programme.start) ?: return@mapNotNull null
        val pEnd = LiveTvRepository.parseXmlTvEpochMs(programme.stop) ?: return@mapNotNull null
        if (pEnd <= start || pStart >= end) null else Triple(programme, pStart.coerceAtLeast(start), pEnd.coerceAtMost(end))
    }
    Row(Modifier.fillMaxWidth().height(64.dp).border(0.5.dp, AstraWaveColors.Divider)) {
        Column(
            Modifier.width(190.dp).fillMaxHeight().background(if (selected) AstraWaveColors.SurfaceFocus else AstraWaveColors.BackgroundRaised)
                .clickable(onClick = onClick).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(row.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(row.preferredSource ?: "Live TV", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
            if (programs.isEmpty()) {
                Text("No guide information", color = AstraWaveColors.TertiaryText, modifier = Modifier.width(1560.dp).fillMaxHeight().padding(18.dp))
            } else programs.forEach { (programme, pStart, pEnd) ->
                val width = (((pEnd - pStart).toFloat() / (30L * 60L * 1000L)) * 130f).coerceAtLeast(72f).dp
                Column(
                    Modifier.width(width).fillMaxHeight().background(if (programme == row.now) AstraWaveColors.GuideNow else AstraWaveColors.GuideFuture)
                        .border(1.dp, AstraWaveColors.Background).clickable(onClick = onClick).padding(9.dp),
                ) {
                    Text(programme.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(formatTime(pStart), color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun SportsTabs(selected: SportsHubTab, onSelect: (SportsHubTab) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SportsHubTab.entries.forEach { CompactTab(it.name.replace('_', ' '), selected == it) { onSelect(it) } }
    }
}

@Composable
private fun SportsDateRail(date: LocalDate, onSelect: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (-1L..5L).forEach { offset ->
            val candidate = LocalDate.now().plusDays(offset)
            val label = when (offset) { -1L -> "YESTERDAY"; 0L -> "TODAY"; 1L -> "TOMORROW"; else -> candidate.format(DateTimeFormatter.ofPattern("EEE d")) }
            CompactTab(label, candidate == date) { onSelect(candidate) }
        }
    }
}

@Composable
private fun LeagueRows(events: List<SportsGuideItem>, selectedId: String?, onSelect: (SportsGuideItem) -> Unit, modifier: Modifier) {
    val leagues = events.groupBy { it.event.league?.takeIf(String::isNotBlank) ?: "TODAY'S GAMES" }
    LazyColumn(modifier) {
        leagues.forEach { (league, games) ->
            item("header:$league") {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(league.uppercase(), color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                    Text("${games.count { it.watchCandidate != null }} ON YOUR CHANNELS", color = AstraWaveColors.Success, style = MaterialTheme.typography.labelSmall)
                }
            }
            item("row:$league") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(games, key = { it.event.id }) { game -> SportsGameTile(game, game.event.id == selectedId) { onSelect(game) } }
                }
            }
        }
    }
}

@Composable
private fun SportsGameTile(item: SportsGuideItem, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(250.dp).height(126.dp)
            .background(if (selected) AstraWaveColors.SurfaceFocus else AstraWaveColors.Surface)
            .border(if (selected) 1.dp else 0.dp, if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick).padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (item.event.isLive) "LIVE" else item.event.time?.take(5) ?: "UPCOMING", color = if (item.event.isLive) AstraWaveColors.Live else AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
            if (item.watchCandidate != null) Text("ON YOUR CHANNELS", color = AstraWaveColors.Success, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(8.dp))
        Text(item.event.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
        Spacer(Modifier.height(4.dp))
        Text(item.broadcasterNames.firstOrNull() ?: "Broadcast pending", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

@Composable
private fun MatchupPanel(item: SportsGuideItem?, modifier: Modifier) {
    Column(modifier.fillMaxHeight().padding(18.dp)) {
        Text(if (item?.event?.isLive == true) "LIVE MATCHUP" else "MATCHUP", color = if (item?.event?.isLive == true) AstraWaveColors.Live else AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        Text(item?.event?.name ?: "Select a game", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineMedium, maxLines = 2)
        Spacer(Modifier.height(10.dp))
        if (item?.event?.homeScore != null && item.event.awayScore != null) {
            Text("${item.event.awayTeam}  ${item.event.awayScore}   -   ${item.event.homeScore}  ${item.event.homeTeam}", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        Text(item?.broadcasterNames?.joinToString("  |  ") ?: "Broadcast information pending", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyLarge)
        Text(if (item?.watchCandidate != null) "Matched to ${item.watchCandidate.channelName}" else "No matching channel in your providers", color = if (item?.watchCandidate != null) AstraWaveColors.Success else AstraWaveColors.TertiaryText, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun guideWindowStart(dayOffset: Int): Long {
    val now = System.currentTimeMillis()
    val halfHour = 30L * 60L * 1000L
    return now - (now % halfHour) + dayOffset * 24L * 60L * 60L * 1000L
}

private fun formatTime(epochMs: Long): String = SimpleDateFormat("h:mm a", Locale.US).format(Date(epochMs))

private fun reviewLiveGroups(): List<LiveChannelGroup> {
    val now = System.currentTimeMillis()
    val slot = 30L * 60L * 1000L
    val starts = now - now % slot
    val channels = listOf(
        Triple("Metro News", "News", listOf("Morning Briefing", "Market Watch")),
        Triple("Arena Sports", "Sports", listOf("Matchday Live", "Postgame Report")),
        Triple("Cinema One", "Entertainment", listOf("The Last Signal", "Behind the Scenes")),
        Triple("Local 8", "Regional", listOf("Colorado Today", "Local Headlines")),
        Triple("World Docs", "Documentary", listOf("Wild Frontiers", "Deep Ocean")),
    )
    return channels.mapIndexed { index, (name, group, titles) ->
        val id = name.lowercase().replace(' ', '-')
        val current = reviewProgramme(id, titles[0], starts, starts + slot)
        val next = reviewProgramme(id, titles[1], starts + slot, starts + slot * 2)
        LiveChannelGroup(
            canonicalName = id,
            displayName = name,
            candidates = listOf(LiveChannel(id, name, id, "", group, null, id, "Demo Provider", index)),
            currentProgram = current,
            nextProgram = next,
            schedule = listOf(current, next),
        )
    }
}

private fun reviewGuideSnapshot(): GuideSnapshot {
    val groups = reviewLiveGroups()
    val rows = groups.map { group ->
        GuideChannelRow(
            id = group.canonicalName,
            name = group.displayName,
            logo = null,
            group = group.bestCandidate?.group,
            now = group.currentProgram,
            next = group.nextProgram,
            programmes = group.schedule,
            playableCandidateCount = 1,
            preferredSource = "Demo Provider",
            playableUrl = null,
        )
    }
    return GuideSnapshot(rows, 1, 0, 0, rows.size, rows.size, rows.sumOf { it.programmes.size })
}

private fun reviewProgramme(channelId: String, title: String, start: Long, stop: Long): XmlTvProgramme {
    val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    return XmlTvProgramme(channelId, title, format.format(Date(start)), format.format(Date(stop)))
}

internal fun reviewSportsSnapshot(date: LocalDate): SportsGuideSnapshot {
    val games = listOf(
        listOf("nba-1", "Lakers at Nuggets", "NBA", "Basketball", "Lakers", "Nuggets", "ESPN", "Q3 04:18", "82", "88"),
        listOf("nba-2", "Celtics at Knicks", "NBA", "Basketball", "Celtics", "Knicks", "TNT", "8:30 PM", "", ""),
        listOf("nhl-1", "Avalanche at Wild", "NHL", "Hockey", "Avalanche", "Wild", "SportsNet", "7:00 PM", "", ""),
        listOf("mls-1", "Seattle at Colorado", "MLS", "Soccer", "Seattle", "Colorado", "Apple TV", "9:00 PM", "", ""),
    ).mapIndexed { index, item ->
        val event = SportsEvent(
            id = item[0], name = item[1], league = item[2], sport = item[3], date = date.toString(),
            time = item[7], homeTeam = item[5], awayTeam = item[4],
            homeScore = item[9].toIntOrNull(), awayScore = item[8].toIntOrNull(),
            status = if (item[9].isNotBlank()) item[7] else "Scheduled", network = item[6],
        )
        val candidate = if (index != 3) SportsWatchCandidate(event.id, item[6], "Demo Provider", "", 100, index) else null
        val resolution = SportsResolution(
            SportsGuideEvent(event.id, item[2], item[5], item[4], 0L, listOf(item[6])),
            listOfNotNull(candidate),
        )
        SportsGuideItem(event, listOf(item[6]), resolution, watchCandidate = candidate)
    }
    return SportsGuideSnapshot(date.toString(), games, 5)
}
