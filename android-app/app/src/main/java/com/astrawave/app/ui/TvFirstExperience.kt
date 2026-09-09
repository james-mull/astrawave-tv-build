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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.astrawave.app.data.CombinedLiveTvRepository
import com.astrawave.app.data.GuideChannelRow
import com.astrawave.app.data.GuideRepository
import com.astrawave.app.data.GuideSnapshot
import com.astrawave.app.data.LiveChannelGroup
import com.astrawave.app.data.SportsGuideItem
import com.astrawave.app.data.SportsGuideRepository
import com.astrawave.app.data.SportsGuideSnapshot
import com.astrawave.app.data.SourceFusionPlaybackPlanner
import com.astrawave.app.data.StremioLiveCatalogDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private sealed interface TvFirstLoad<out T> {
    data object Loading : TvFirstLoad<Nothing>
    data class Ready<T>(val value: T) : TvFirstLoad<T>
    data class Error(val message: String) : TvFirstLoad<Nothing>
}

@Composable
fun PremiumLiveTvScreen(
    sources: List<IptvSource>,
    onSourcesChanged: (List<IptvSource>) -> Unit,
    profileId: String = "default",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { CombinedLiveTvRepository() }
    val planner = remember { SourceFusionPlaybackPlanner(context) }
    var state by remember(sources, profileId) { mutableStateOf<TvFirstLoad<List<LiveChannelGroup>>>(TvFirstLoad.Loading) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var showSources by remember { mutableStateOf(false) }

    LaunchedEffect(sources, profileId) {
        state = try {
            val snapshot = withContext(Dispatchers.IO) {
                repository.load(sources, includeEpg = false, expandedPublicInventory = false)
            }
            TvFirstLoad.Ready(snapshot.groups)
        } catch (error: Exception) {
            TvFirstLoad.Error(error.message ?: "Unable to load Live TV")
        }
    }

    fun play(group: LiveChannelGroup) {
        scope.launch {
            val inputs = group.candidates.mapIndexed { index, candidate ->
                SourceFusionPlaybackPlanner.Input(
                    sourceKey = "live:${candidate.source}:${candidate.normalizedName}",
                    url = candidate.url,
                    provider = candidate.source,
                    priority = candidate.priority + index,
                )
            }
            val plan = withContext(Dispatchers.IO) { planner.plan(inputs, probeUnknown = false) }
            val urls = plan?.orderedUrls.orEmpty()
            if (urls.isEmpty()) {
                Toast.makeText(context, "No stream is available for this channel.", Toast.LENGTH_LONG).show()
                return@launch
            }
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_URL, urls.first())
                    .putStringArrayListExtra(PlayerActivity.EXTRA_URLS, ArrayList(urls))
                    .putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true),
            )
        }
    }

    Column(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
        TvFirstTopBar(
            title = "Live TV",
            subtitle = "Channels",
            actions = {
                AstraWaveSecondaryButton(if (showSources) "Back to TV" else "Sources") { showSources = !showSources }
            },
        )
        if (showSources) {
            MyIptvScreen(sources = sources, onSourcesChanged = onSourcesChanged)
            return@Column
        }

        when (val current = state) {
            TvFirstLoad.Loading -> Box(Modifier.fillMaxSize().padding(24.dp)) {
                AstraWaveLoadingState("Loading channels", "Preparing your live lineup.")
            }
            is TvFirstLoad.Error -> Box(Modifier.fillMaxSize().padding(24.dp)) {
                AstraWaveErrorState("Live TV unavailable", current.message)
            }
            is TvFirstLoad.Ready -> {
                val all = current.value
                val groups = all.map { it.bestCandidate?.group ?: "Other" }.distinct().sorted()
                val normalized = query.trim().lowercase()
                val filtered = all.filter { channel ->
                    (selectedGroup == null || (channel.bestCandidate?.group ?: "Other") == selectedGroup) &&
                        (normalized.isBlank() || channel.displayName.lowercase().contains(normalized))
                }
                if (selectedId == null || filtered.none { it.canonicalName == selectedId }) {
                    selectedId = filtered.firstOrNull()?.canonicalName
                }
                val selected = filtered.firstOrNull { it.canonicalName == selectedId } ?: filtered.firstOrNull()

                Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(
                        Modifier.width(190.dp).fillMaxHeight()
                            .background(AstraWaveColors.BackgroundRaised, MaterialTheme.shapes.large)
                            .padding(10.dp),
                    ) {
                        Text("GROUPS", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(8.dp))
                        TvRailRow("All Channels", selectedGroup == null) { selectedGroup = null }
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(Modifier.weight(1f)) {
                            items(groups) { group ->
                                TvRailRow(group, selectedGroup == group) { selectedGroup = group }
                            }
                        }
                        Text("${all.size} channels", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
                    }

                    Column(
                        Modifier.width(330.dp).fillMaxHeight()
                            .background(AstraWaveColors.BackgroundRaised, MaterialTheme.shapes.large)
                            .padding(10.dp),
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Search channels") },
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(Modifier.weight(1f)) {
                            items(filtered, key = { it.canonicalName }) { channel ->
                                val selectedRow = channel.canonicalName == selected?.canonicalName
                                Column(
                                    Modifier.fillMaxWidth()
                                        .background(if (selectedRow) AstraWaveColors.SurfaceRaised else AstraWaveColors.Surface, MaterialTheme.shapes.medium)
                                        .clickable { selectedId = channel.canonicalName }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    Text(channel.displayName, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    Text(
                                        channel.bestCandidate?.source ?: "Live TV",
                                        color = AstraWaveColors.TertiaryText,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }

                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .background(AstraWaveColors.BackgroundRaised, MaterialTheme.shapes.large)
                            .padding(22.dp),
                    ) {
                        if (selected == null) {
                            AstraWaveEmptyState("Choose a channel", "Select a channel from the list.")
                        } else {
                            Text("LIVE", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(6.dp))
                            Text(selected.displayName, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                selected.bestCandidate?.group ?: "Live TV",
                                color = AstraWaveColors.SecondaryText,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(18.dp))
                            AstraWavePrimaryButton("Watch") { play(selected) }
                            Spacer(Modifier.height(16.dp))
                            Text("Available feeds", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(6.dp))
                            selected.candidates.take(5).forEach { candidate ->
                                Text("• ${candidate.source}", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumGuideScreen(
    sources: List<IptvSource>,
    profileId: String = "default",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { GuideRepository() }
    val planner = remember { SourceFusionPlaybackPlanner(context) }
    var state by remember(sources, profileId) { mutableStateOf<TvFirstLoad<GuideSnapshot>>(TvFirstLoad.Loading) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(sources, profileId) {
        state = try {
            TvFirstLoad.Ready(withContext(Dispatchers.IO) { repository.load(sources) })
        } catch (error: Exception) {
            TvFirstLoad.Error(error.message ?: "Unable to load guide")
        }
    }

    fun play(row: GuideChannelRow) {
        if (row.playableUrls.isEmpty()) {
            row.externalUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
            return
        }
        scope.launch {
            val inputs = row.playableUrls.mapIndexed { index, url ->
                SourceFusionPlaybackPlanner.Input("guide:${row.id}:$index", url, row.preferredSource ?: "Live TV", index)
            }
            val plan = withContext(Dispatchers.IO) { planner.plan(inputs, probeUnknown = false) }
            val urls = plan?.orderedUrls.orEmpty()
            if (urls.isEmpty()) return@launch
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_URL, urls.first())
                    .putStringArrayListExtra(PlayerActivity.EXTRA_URLS, ArrayList(urls))
                    .putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true),
            )
        }
    }

    Column(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
        TvFirstTopBar(title = "Guide", subtitle = "Now & Next")
        when (val current = state) {
            TvFirstLoad.Loading -> Box(Modifier.fillMaxSize().padding(24.dp)) {
                AstraWaveLoadingState("Loading guide", "Matching channels with current programming.")
            }
            is TvFirstLoad.Error -> Box(Modifier.fillMaxSize().padding(24.dp)) {
                AstraWaveErrorState("Guide unavailable", current.message)
            }
            is TvFirstLoad.Ready -> {
                val snapshot = current.value
                val groups = snapshot.rows.map { it.group ?: "Other" }.distinct().sorted()
                val normalized = query.trim().lowercase()
                val rows = snapshot.rows.filter { row ->
                    (selectedGroup == null || (row.group ?: "Other") == selectedGroup) &&
                        (normalized.isBlank() || row.name.lowercase().contains(normalized) ||
                            row.now?.title.orEmpty().lowercase().contains(normalized) ||
                            row.next?.title.orEmpty().lowercase().contains(normalized))
                }

                Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.width(300.dp),
                            singleLine = true,
                            placeholder = { Text("Search guide") },
                        )
                        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GuidePill("All", selectedGroup == null) { selectedGroup = null }
                            groups.forEach { group -> GuidePill(group, selectedGroup == group) { selectedGroup = group } }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().background(AstraWaveColors.BackgroundRaised).padding(vertical = 8.dp),
                    ) {
                        GuideHeaderCell("CHANNEL", 220)
                        GuideHeaderCell("NOW", 360)
                        GuideHeaderCell("NEXT", 360)
                        Text("", modifier = Modifier.weight(1f))
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(rows, key = { it.id }) { row ->
                            GuideGridRow(row = row, onWatch = { play(row) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumSportsScreen(
    sources: List<IptvSource>,
    profileId: String = "default",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SportsGuideRepository() }
    val addonLive = remember { StremioLiveCatalogDiscovery(context) }
    val planner = remember { SourceFusionPlaybackPlanner(context) }
    var selectedDate by remember { mutableStateOf(LocalDate.now(ZoneOffset.UTC)) }
    var selectedLeague by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var state by remember(sources, selectedDate, profileId) { mutableStateOf<TvFirstLoad<SportsGuideSnapshot>>(TvFirstLoad.Loading) }

    LaunchedEffect(sources, selectedDate, profileId) {
        state = try {
            val snapshot = withContext(Dispatchers.IO) {
                val names = runCatching { addonLive.sportsChannelNames(profileId) }.getOrDefault(emptyList())
                repository.load(selectedDate, sources, addonSportsChannelNames = names)
            }
            TvFirstLoad.Ready(snapshot)
        } catch (error: Exception) {
            TvFirstLoad.Error(error.message ?: "Unable to load sports")
        }
    }

    fun play(item: SportsGuideItem) {
        val candidates = item.resolution?.candidates.orEmpty()
        if (candidates.isEmpty()) {
            Toast.makeText(context, "No matched channel is available for this event.", Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            val inputs = candidates.mapIndexed { index, candidate ->
                SourceFusionPlaybackPlanner.Input(
                    sourceKey = "sports:${item.event.id}:$index",
                    url = candidate.streamUrl,
                    provider = candidate.source,
                    priority = candidate.priority,
                )
            }
            val plan = withContext(Dispatchers.IO) { planner.plan(inputs, probeUnknown = false) }
            val urls = plan?.orderedUrls.orEmpty()
            if (urls.isEmpty()) return@launch
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_URL, urls.first())
                    .putStringArrayListExtra(PlayerActivity.EXTRA_URLS, ArrayList(urls))
                    .putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true),
            )
        }
    }

    Column(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
        TvFirstTopBar(title = "Sports", subtitle = selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d")))
        when (val current = state) {
            TvFirstLoad.Loading -> Box(Modifier.fillMaxSize().padding(24.dp)) {
                AstraWaveLoadingState("Loading sports", "Getting today's events and broadcaster matches.")
            }
            is TvFirstLoad.Error -> Box(Modifier.fillMaxSize().padding(24.dp)) {
                AstraWaveErrorState("Sports unavailable", current.message)
            }
            is TvFirstLoad.Ready -> {
                val snapshot = current.value
                val leagues = snapshot.events.mapNotNull { it.event.league?.takeIf(String::isNotBlank) }.distinct().sorted()
                val events = selectedLeague?.let { league -> snapshot.events.filter { it.event.league == league } } ?: snapshot.events
                if (selectedId == null || events.none { it.event.id == selectedId }) {
                    selectedId = events.firstOrNull { it.event.isLive }?.event?.id ?: events.firstOrNull()?.event?.id
                }
                val selected = events.firstOrNull { it.event.id == selectedId }

                Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DatePill("Yesterday", selectedDate == LocalDate.now(ZoneOffset.UTC).minusDays(1)) { selectedDate = LocalDate.now(ZoneOffset.UTC).minusDays(1) }
                        DatePill("Today", selectedDate == LocalDate.now(ZoneOffset.UTC)) { selectedDate = LocalDate.now(ZoneOffset.UTC) }
                        DatePill("Tomorrow", selectedDate == LocalDate.now(ZoneOffset.UTC).plusDays(1)) { selectedDate = LocalDate.now(ZoneOffset.UTC).plusDays(1) }
                        Spacer(Modifier.width(10.dp))
                        DatePill("All leagues", selectedLeague == null) { selectedLeague = null }
                        leagues.forEach { league -> DatePill(league, selectedLeague == league) { selectedLeague = league } }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(
                            Modifier.width(520.dp).fillMaxHeight()
                                .background(AstraWaveColors.BackgroundRaised, MaterialTheme.shapes.large)
                                .padding(10.dp),
                        ) {
                            Text("EVENTS", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(8.dp))
                            if (events.isEmpty()) {
                                AstraWaveEmptyState("No events", "Try another day or league.")
                            } else {
                                LazyColumn(Modifier.weight(1f)) {
                                    items(events, key = { it.event.id }) { item ->
                                        SportsEventRow(
                                            item = item,
                                            selected = item.event.id == selectedId,
                                            onClick = { selectedId = item.event.id },
                                        )
                                    }
                                }
                            }
                        }
                        Column(
                            Modifier.weight(1f).fillMaxHeight()
                                .background(AstraWaveColors.BackgroundRaised, MaterialTheme.shapes.large)
                                .padding(22.dp),
                        ) {
                            if (selected == null) {
                                AstraWaveEmptyState("Choose an event", "Select a game to see broadcast details.")
                            } else {
                                Text(if (selected.event.isLive) "LIVE NOW" else "GAME", color = if (selected.event.isLive) AstraWaveColors.Live else AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(6.dp))
                                Text(selected.event.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineMedium)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    listOfNotNull(selected.event.league, selected.event.time).joinToString(" • "),
                                    color = AstraWaveColors.SecondaryText,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.height(16.dp))
                                val broadcast = selected.broadcasterNames.joinToString(" • ").ifBlank { "Broadcast not announced" }
                                Text("Broadcast", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
                                Text(broadcast, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(16.dp))
                                if (selected.resolution?.candidates.orEmpty().isNotEmpty()) {
                                    AstraWavePrimaryButton("Watch") { play(selected) }
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        "${selected.resolution?.candidates?.size ?: 0} matched channel option${if ((selected.resolution?.candidates?.size ?: 0) == 1) "" else "s"}",
                                        color = AstraWaveColors.Success,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                } else {
                                    Text("No matched channel yet", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvFirstTopBar(
    title: String,
    subtitle: String,
    actions: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().background(AstraWaveColors.BackgroundRaised).padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelMedium)
        }
        actions()
    }
}

@Composable
private fun TvRailRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) AstraWaveColors.PrimaryText else AstraWaveColors.SecondaryText,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth()
            .background(if (selected) AstraWaveColors.SurfaceRaised else AstraWaveColors.BackgroundRaised, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        maxLines = 1,
    )
}

@Composable
private fun GuidePill(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) AstraWavePrimaryButton(label, onClick) else AstraWaveSecondaryButton(label, onClick)
}

@Composable
private fun DatePill(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) AstraWavePrimaryButton(label, onClick) else AstraWaveSecondaryButton(label, onClick)
}

@Composable
private fun GuideHeaderCell(label: String, width: Int) {
    Text(
        label,
        color = AstraWaveColors.TertiaryText,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.width(width.dp).padding(horizontal = 12.dp),
    )
}

@Composable
private fun GuideGridRow(row: GuideChannelRow, onWatch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .background(AstraWaveColors.Surface, MaterialTheme.shapes.medium)
            .clickable(enabled = row.playableUrls.isNotEmpty() || row.externalUrl != null, onClick = onWatch)
            .padding(vertical = 10.dp),
    ) {
        Column(Modifier.width(220.dp).padding(horizontal = 12.dp)) {
            Text(row.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(row.group ?: "Live TV", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        ProgramCell(row.now?.title ?: "No guide data", row.now?.start, 360)
        ProgramCell(row.next?.title ?: "No upcoming data", row.next?.start, 360)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            if (row.playableUrls.isNotEmpty() || row.externalUrl != null) {
                Text("WATCH", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ProgramCell(title: String, time: String?, width: Int) {
    Column(Modifier.width(width.dp).padding(horizontal = 12.dp)) {
        Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        if (!time.isNullOrBlank()) Text(time.take(12), color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun SportsEventRow(item: SportsGuideItem, selected: Boolean, onClick: () -> Unit) {
    val status = when {
        item.event.isLive -> "LIVE"
        item.event.isFinal -> "FINAL"
        else -> item.event.time?.take(5) ?: "UPCOMING"
    }
    Column(
        Modifier.fillMaxWidth()
            .background(if (selected) AstraWaveColors.SurfaceRaised else AstraWaveColors.Surface, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(status, color = if (item.event.isLive) AstraWaveColors.Live else AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelMedium)
            if (item.resolution?.candidates.orEmpty().isNotEmpty()) {
                Text("WATCH", color = AstraWaveColors.Success, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(item.event.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
        Text(item.event.league ?: "Sports", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
    Spacer(Modifier.height(5.dp))
}
