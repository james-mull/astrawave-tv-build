package com.astrawave.app.ui

import android.content.Context
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.astrawave.app.PlayerActivity
import com.astrawave.app.core.IptvSource
import com.astrawave.app.data.CombinedLiveTvRepository
import com.astrawave.app.data.GuideChannelRow
import com.astrawave.app.data.GuideRepository
import com.astrawave.app.data.GuideSnapshot
import com.astrawave.app.data.LiveChannel
import com.astrawave.app.data.LiveChannelGroup
import com.astrawave.app.data.LiveTvRepository
import com.astrawave.app.data.XmlTvProgramme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private sealed interface TivraV2Load<out T> {
    data object Loading : TivraV2Load<Nothing>
    data class Ready<T>(val value: T) : TivraV2Load<T>
    data class Error(val message: String) : TivraV2Load<Nothing>
}

@Composable
fun TivraLiveTvScreenV2(
    sources: List<IptvSource>,
    onSourcesChanged: (List<IptvSource>) -> Unit,
    preview: LivePreviewController,
    profileId: String,
    previewData: Boolean = false,
) {
    val context = LocalContext.current
    val phone = LocalAstraWaveDeviceClass.current == AstraWaveDeviceClass.PHONE
    val repository = remember { CombinedLiveTvRepository() }
    var state by remember(sources, profileId, previewData) { mutableStateOf<TivraV2Load<List<LiveChannelGroup>>>(TivraV2Load.Loading) }
    var category by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showSources by remember { mutableStateOf(false) }

    LaunchedEffect(sources, profileId, previewData) {
        state = if (previewData) {
            TivraV2Load.Ready(v2DemoLiveGroups())
        } else {
            try {
                val snapshot = withContext(Dispatchers.IO) { repository.load(sources, includeEpg = true, includeBundledFreeTv = false) }
                TivraV2Load.Ready(snapshot.groups)
            } catch (error: Exception) {
                TivraV2Load.Error(error.message ?: "Unable to load Live TV")
            }
        }
    }

    fun select(group: LiveChannelGroup) {
        preview.select(
            contentId = group.canonicalName,
            title = group.displayName,
            subtitle = group.currentProgram?.title ?: group.bestCandidate?.group ?: "Live TV",
            urls = group.candidates.map { it.url },
            provider = group.bestCandidate?.source,
        )
        selectedId = group.canonicalName
    }

    Column(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
        V2Header("LIVE TV", "WATCH NOW", if (showSources) "DONE" else "MANAGE") { showSources = !showSources }
        if (showSources) {
            MyIptvScreen(sources, onSourcesChanged)
            return@Column
        }
        when (val current = state) {
            TivraV2Load.Loading -> AstraWaveLoadingState("Loading Live TV", "Preparing channels and guide data.")
            is TivraV2Load.Error -> AstraWaveErrorState("Live TV unavailable", current.message)
            is TivraV2Load.Ready -> {
                if (!previewData && sources.none { it.enabled }) {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                        Text("CONNECT YOUR TV", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("Live television, your way", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("Add an M3U or Xtream provider to unlock channels, guide data, and matched sports.", color = AstraWaveColors.SecondaryText)
                        Spacer(Modifier.height(18.dp))
                        AstraWavePrimaryButton("Add TV provider") { showSources = true }
                    }
                    return@Column
                }
                val groups = current.value
                val categories = groups.map { it.bestCandidate?.group ?: "Other" }.distinct().sorted()
                val filtered = groups.filter { category == null || (it.bestCandidate?.group ?: "Other") == category }
                if (selectedId == null && filtered.isNotEmpty()) select(filtered.first())
                val selected = filtered.firstOrNull { it.canonicalName == selectedId } ?: filtered.firstOrNull()

                if (phone) {
                    Column(Modifier.fillMaxSize()) {
                        V2PreviewPane(preview, Modifier.fillMaxWidth().aspectRatio(16f / 8.6f))
                        selected?.let { V2NowStrip(it) }
                        V2CategoryRail(categories, category) { category = it }
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(filtered, key = { it.canonicalName }) { channel ->
                                V2ChannelRow(channel, channel.canonicalName == selected?.canonicalName) { select(channel) }
                            }
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(
                            Modifier.width(170.dp).fillMaxHeight()
                                .background(AstraWaveColors.BackgroundRaised)
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                "BROWSE",
                                color = AstraWaveColors.TertiaryText,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                            V2SideItem("All channels", category == null) { category = null }
                            categories.forEach { V2SideItem(it, category == it) { category = it } }
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            V2PreviewPane(preview, Modifier.fillMaxWidth().weight(1f))
                            selected?.let { V2NowNextPanel(it) }
                        }
                        LazyColumn(
                            Modifier.width(350.dp).fillMaxHeight()
                                .background(AstraWaveColors.BackgroundRaised),
                        ) {
                            items(filtered, key = { it.canonicalName }) { channel ->
                                V2ChannelRow(channel, channel.canonicalName == selected?.canonicalName) { select(channel) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TivraGuideScreenV2(
    sources: List<IptvSource>,
    preview: LivePreviewController,
    profileId: String,
    previewData: Boolean = false,
) {
    val repository = remember { GuideRepository() }
    val context = LocalContext.current
    val phone = LocalAstraWaveDeviceClass.current == AstraWaveDeviceClass.PHONE
    var state by remember(sources, profileId, previewData) { mutableStateOf<TivraV2Load<GuideSnapshot>>(TivraV2Load.Loading) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var dayOffset by remember { mutableStateOf(0) }
    val timelineScroll = rememberScrollState()

    LaunchedEffect(sources, profileId, previewData) {
        state = if (previewData) TivraV2Load.Ready(v2DemoGuide()) else {
            try { TivraV2Load.Ready(withContext(Dispatchers.IO) { repository.load(sources) }) }
            catch (error: Exception) { TivraV2Load.Error(error.message ?: "Unable to load guide") }
        }
    }

    fun select(row: GuideChannelRow) {
        preview.select(row.id, row.name, row.now?.title ?: "No programme information", row.playableUrls, row.preferredSource)
        selectedId = row.id
    }

    Column(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
        V2Header("GUIDE", "PROGRAM GRID", "NOW") { dayOffset = 0 }
        when (val current = state) {
            TivraV2Load.Loading -> AstraWaveLoadingState("Loading guide", "Building your program grid.")
            is TivraV2Load.Error -> AstraWaveErrorState("Guide unavailable", current.message)
            is TivraV2Load.Ready -> {
                if (!previewData && sources.none { it.enabled }) {
                    AstraWaveEmptyState("No TV provider", "Connect a Live TV provider to populate the guide.")
                    return@Column
                }
                val rows = current.value.rows.filter { !it.id.startsWith("handoff:") }
                if (selectedId == null && rows.isNotEmpty()) select(rows.first())
                val selected = rows.firstOrNull { it.id == selectedId } ?: rows.firstOrNull()

                if (phone) {
                    selected?.let { V2GuideSelectionBanner(it) }
                } else {
                    Row(Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        V2PreviewPane(preview, Modifier.width(200.dp).fillMaxHeight())
                        selected?.let { V2GuideSelectionBanner(it, Modifier.weight(1f).fillMaxHeight()) }
                    }
                }
                V2DayRail(dayOffset) { dayOffset = it }
                V2GuideGrid(rows, selectedId, dayOffset, timelineScroll, ::select, phone)
            }
        }
    }
}

@Composable
private fun V2Header(title: String, subtitle: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.lowercase().replaceFirstChar { it.uppercase() }, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Text(subtitle, color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        }
        Text(action, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable(onClick = onAction).padding(10.dp))
    }
}

@Composable
private fun V2PreviewPane(controller: LivePreviewController, modifier: Modifier) {
    val context = LocalContext.current
    val state = controller.state
    val player = remember { ExoPlayer.Builder(context).build().apply { volume = 0.65f } }
    LaunchedEffect(state.activeUrl) {
        val url = state.activeUrl
        if (url.isNullOrBlank()) { player.stop(); player.clearMediaItems() }
        else { player.setMediaItem(MediaItem.fromUri(url)); player.prepare(); player.playWhenReady = true }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(modifier.background(Color.Black)) {
        if (!state.activeUrl.isNullOrBlank()) {
            AndroidView(factory = { PlayerView(it).apply { useController = false; this.player = player } }, update = { it.player = player }, modifier = Modifier.fillMaxSize().clickable { openV2FullScreen(context, state.urls) })
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.66f)).padding(14.dp)) {
            Text(state.provider?.uppercase() ?: "LIVE PREVIEW", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelSmall)
            Text(state.title, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(state.subtitle, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

@Composable
private fun V2NowStrip(channel: LiveChannelGroup) {
    val now = channel.currentProgram
    val progress = programmeProgress(now)
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(channel.displayName, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                Text(now?.title ?: "Live programming", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
            Text("LIVE", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(7.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp), color = AstraWaveColors.PrimaryText, trackColor = AstraWaveColors.SurfaceRaised)
    }
}

@Composable
private fun V2CategoryRail(categories: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        item { V2Chip("ALL", selected == null) { onSelect(null) } }
        items(categories) { item -> V2Chip(item.uppercase(), selected == item) { onSelect(item) } }
    }
}

@Composable
private fun V2Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, color = if (selected) Color.Black else AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.background(if (selected) AstraWaveColors.AccentStrong else AstraWaveColors.Surface).clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 8.dp))
}

@Composable
private fun V2ChannelRow(channel: LiveChannelGroup, selected: Boolean, onClick: () -> Unit) {
    val progress = programmeProgress(channel.currentProgram)
    Row(Modifier.fillMaxWidth().height(82.dp).background(if (selected) AstraWaveColors.SurfaceFocus else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(48.dp).height(48.dp).background(AstraWaveColors.SurfaceRaised), contentAlignment = Alignment.Center) {
            Text(channel.displayName.take(2).uppercase(), color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(channel.displayName, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(if (channel.currentProgram != null) "NOW" else "LIVE", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelSmall)
            }
            Text(channel.currentProgram?.title ?: "Live programming", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp), color = if (selected) AstraWaveColors.AccentStrong else AstraWaveColors.TertiaryText, trackColor = AstraWaveColors.SurfaceRaised)
        }
    }
}

@Composable
private fun V2SideItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, color = if (selected) Color.Black else AstraWaveColors.SecondaryText, maxLines = 1,
        modifier = Modifier.fillMaxWidth().background(if (selected) AstraWaveColors.AccentStrong else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp))
}

@Composable
private fun V2NowNextPanel(channel: LiveChannelGroup) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text("ON NOW", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelSmall)
        Text(channel.currentProgram?.title ?: "Live programming", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text("UP NEXT", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        Text(channel.nextProgram?.title ?: "Schedule unavailable", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun V2DayRail(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        (-1..5).forEach { offset ->
            val date = LocalDate.now().plusDays(offset.toLong())
            val label = when (offset) { -1 -> "YESTERDAY"; 0 -> "TODAY"; 1 -> "TOMORROW"; else -> date.format(DateTimeFormatter.ofPattern("EEE d")) }
            V2Chip(label, offset == selected) { onSelect(offset) }
        }
    }
}

@Composable
private fun V2GuideSelectionBanner(row: GuideChannelRow, modifier: Modifier = Modifier.fillMaxWidth()) {
    Column(modifier.background(AstraWaveColors.BackgroundRaised).padding(horizontal = 14.dp, vertical = 11.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(row.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
            Text("ON NOW", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelSmall)
        }
        Text(row.now?.title ?: "No programme information", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Text("Next  ${row.next?.title ?: "Schedule unavailable"}", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

@Composable
private fun V2GuideGrid(
    rows: List<GuideChannelRow>,
    selectedId: String?,
    dayOffset: Int,
    timelineScroll: androidx.compose.foundation.ScrollState,
    onSelect: (GuideChannelRow) -> Unit,
    phone: Boolean,
) {
    val channelWidth = if (phone) 112.dp else 168.dp
    val slotWidth = if (phone) 116.dp else 126.dp
    val start = v2GuideWindowStart(dayOffset)
    val end = start + 6L * 60L * 60L * 1000L
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(38.dp).background(AstraWaveColors.BackgroundRaised), verticalAlignment = Alignment.CenterVertically) {
            Text("CHANNEL", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(channelWidth).padding(start = 10.dp))
            Row(Modifier.weight(1f).horizontalScroll(timelineScroll)) {
                repeat(12) { index -> Text(v2FormatTime(start + index * 30L * 60L * 1000L), color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(slotWidth)) }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = { it.id }) { row ->
                val programmes = row.programmes.mapNotNull { p ->
                    val ps = LiveTvRepository.parseXmlTvEpochMs(p.start) ?: return@mapNotNull null
                    val pe = LiveTvRepository.parseXmlTvEpochMs(p.stop) ?: return@mapNotNull null
                    if (pe <= start || ps >= end) null else Triple(p, ps.coerceAtLeast(start), pe.coerceAtMost(end))
                }
                Row(Modifier.fillMaxWidth().height(if (phone) 68.dp else 60.dp).border(0.5.dp, AstraWaveColors.Divider)) {
                    Row(
                        Modifier.width(channelWidth).fillMaxHeight()
                            .background(if (row.id == selectedId) AstraWaveColors.SurfaceFocus else AstraWaveColors.BackgroundRaised)
                            .border(if (row.id == selectedId) 1.dp else 0.dp, if (row.id == selectedId) AstraWaveColors.FocusRing else Color.Transparent)
                            .clickable { onSelect(row) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!row.logo.isNullOrBlank()) {
                            Box(Modifier.width(34.dp).height(34.dp), contentAlignment = Alignment.Center) {
                                AsyncImage(
                                    model = row.logo,
                                    contentDescription = row.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                            Text(row.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(row.group ?: row.preferredSource ?: "Live TV", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                    Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(timelineScroll)) {
                        if (programmes.isEmpty()) {
                            Text("No guide information", color = AstraWaveColors.TertiaryText, modifier = Modifier.width(slotWidth * 12).fillMaxHeight().padding(16.dp))
                        } else {
                            programmes.forEach { (programme, ps, pe) ->
                                val width = (((pe - ps).toFloat() / (30L * 60L * 1000L)) * slotWidth.value).coerceAtLeast(72f).dp
                                val currentProgramme = programme == row.now
                                Column(
                                    Modifier.width(width).fillMaxHeight()
                                        .background(if (currentProgramme) AstraWaveColors.GuideNow else AstraWaveColors.GuideFuture)
                                        .border(if (currentProgramme) 1.dp else 0.dp, if (currentProgramme) AstraWaveColors.FocusRing else Color.Transparent)
                                        .clickable { onSelect(row) }
                                        .padding(8.dp),
                                ) {
                                    if (currentProgramme) {
                                        Text("NOW", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(programme.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    Text(v2FormatTime(ps), color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun programmeProgress(programme: XmlTvProgramme?): Float {
    if (programme == null) return 0f
    val start = LiveTvRepository.parseXmlTvEpochMs(programme.start) ?: return 0f
    val stop = LiveTvRepository.parseXmlTvEpochMs(programme.stop) ?: return 0f
    if (stop <= start) return 0f
    return ((System.currentTimeMillis() - start).toFloat() / (stop - start).toFloat()).coerceIn(0f, 1f)
}

private fun v2GuideWindowStart(dayOffset: Int): Long {
    val halfHour = 30L * 60L * 1000L
    val now = System.currentTimeMillis()
    return now - (now % halfHour) + dayOffset * 24L * 60L * 60L * 1000L
}

private fun v2FormatTime(epoch: Long): String = SimpleDateFormat("h:mm a", Locale.US).format(Date(epoch))

private fun openV2FullScreen(context: Context, urls: List<String>) {
    if (urls.isEmpty()) return
    context.startActivity(Intent(context, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_URL, urls.first()).putStringArrayListExtra(PlayerActivity.EXTRA_URLS, ArrayList(urls)).putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true))
}

private fun v2DemoLiveGroups(): List<LiveChannelGroup> {
    val now = System.currentTimeMillis()
    val slot = 30L * 60L * 1000L
    val base = now - now % slot
    val data = listOf(
        Triple("ESPN", "Sports", listOf("SportsCenter", "NBA Countdown")),
        Triple("TNT", "Sports", listOf("NBA Tip-Off", "Inside the NBA")),
        Triple("CBS", "Networks", listOf("Local News", "Evening Report")),
        Triple("NBC", "Networks", listOf("Today", "Nightly News")),
        Triple("Discovery", "Entertainment", listOf("Expedition Unknown", "Deadliest Catch")),
        Triple("FX", "Entertainment", listOf("Movie Premiere", "Series Encore")),
    )
    return data.mapIndexed { index, (name, group, titles) ->
        val id = name.lowercase(Locale.US)
        val nowP = v2Programme(id, titles[0], base, base + slot)
        val nextP = v2Programme(id, titles[1], base + slot, base + slot * 2)
        LiveChannelGroup(id, name, listOf(LiveChannel(id, name, id, "", group, null, id, "Demo TV", index)), nowP, nextP, listOf(nowP, nextP))
    }
}

private fun v2DemoGuide(): GuideSnapshot {
    val groups = v2DemoLiveGroups()
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
      preferredSource = "Demo TV",
      recordingSource = null,
      playableUrl = null,
      playableUrls = emptyList(),
  )
    }
    return GuideSnapshot(rows, 1, 0, 0, rows.size, rows.size, rows.sumOf { it.programmes.size })
}

private fun v2Programme(channelId: String, title: String, start: Long, stop: Long): XmlTvProgramme {
    val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    return XmlTvProgramme(channelId, title, format.format(Date(start)), format.format(Date(stop)))
}
