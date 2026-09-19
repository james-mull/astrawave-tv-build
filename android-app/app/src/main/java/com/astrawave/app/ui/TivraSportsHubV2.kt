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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import com.astrawave.app.PlayerActivity
import com.astrawave.app.core.IptvSource
import com.astrawave.app.data.SportsEvent
import com.astrawave.app.data.SportsGuideItem
import com.astrawave.app.data.SportsGuideRepository
import com.astrawave.app.data.SportsGuideSnapshot
import com.astrawave.app.data.SportsDetailRepository
import com.astrawave.app.data.SportsEventDetail
import com.astrawave.app.data.StremioLiveCatalogDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class TivraSportsV2Tab { SCORES, MY_TEAMS, CHANNELS }

private sealed interface TivraSportsV2Load {
    data object Loading : TivraSportsV2Load
    data class Ready(val snapshot: SportsGuideSnapshot) : TivraSportsV2Load
    data class Error(val message: String) : TivraSportsV2Load
}

@Composable
fun TivraSportsHubV2(
    sources: List<IptvSource>,
    preview: LivePreviewController,
    profileId: String,
    previewData: Boolean = false,
) {
    val context = LocalContext.current
    val device = LocalAstraWaveDeviceClass.current
    val phone = device == AstraWaveDeviceClass.PHONE
    val repository = remember { SportsGuideRepository() }
    val detailRepository = remember { SportsDetailRepository() }
    val addonLive = remember { StremioLiveCatalogDiscovery(context) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedTab by remember { mutableStateOf(TivraSportsV2Tab.SCORES) }
    var selectedSport by remember { mutableStateOf<String?>(null) }
    var hideScores by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedDetail by remember { mutableStateOf<SportsEventDetail?>(null) }
    var load by remember(sources, selectedDate, profileId) { mutableStateOf<TivraSportsV2Load>(TivraSportsV2Load.Loading) }

    LaunchedEffect(sources, selectedDate, profileId) {
        load = if (previewData) {
            TivraSportsV2Load.Ready(reviewSportsSnapshot(selectedDate))
        } else {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    val addonNames = runCatching { addonLive.sportsChannelNames(profileId) }.getOrDefault(emptyList())
                    repository.load(selectedDate, sources, addonSportsChannelNames = addonNames)
                }
                TivraSportsV2Load.Ready(snapshot)
            } catch (error: Exception) {
                TivraSportsV2Load.Error(error.message ?: "Unable to load sports")
            }
        }
    }

    fun select(item: SportsGuideItem) {
        val urls = item.resolution?.candidates.orEmpty().map { it.streamUrl }.filter(String::isNotBlank)
        preview.select(
            contentId = "sports:${item.event.id}",
            title = item.event.name,
            subtitle = listOfNotNull(item.event.league, item.broadcasterNames.firstOrNull()).joinToString("  •  "),
            urls = urls,
            provider = item.watchCandidate?.source,
        )
        selectedId = item.event.id
    }

    LaunchedEffect(selectedId, previewData) {
        selectedDetail = if (previewData || selectedId.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { runCatching { detailRepository.load(selectedId.orEmpty()) }.getOrNull() }
        }
    }

    Column(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
        SportsV2Header(selectedDate, selectedTab, hideScores, onTab = { selectedTab = it }, onToggleScores = { hideScores = !hideScores })
        SportsV2DateStrip(selectedDate) { selectedDate = it }

        when (val current = load) {
            TivraSportsV2Load.Loading -> Box(Modifier.fillMaxSize().padding(24.dp)) {
                AstraWaveLoadingState("Loading Sports Hub", "Building scores, channels and matchups.")
            }
            is TivraSportsV2Load.Error -> Box(Modifier.fillMaxSize().padding(24.dp)) {
                AstraWaveErrorState("Sports Hub unavailable", current.message)
            }
            is TivraSportsV2Load.Ready -> {
                val allEvents = current.snapshot.events
                val tabEvents = when (selectedTab) {
                    TivraSportsV2Tab.SCORES -> allEvents
                    TivraSportsV2Tab.MY_TEAMS -> allEvents.filter { it.event.isLive || it.watchCandidate != null }
                    TivraSportsV2Tab.CHANNELS -> allEvents.filter { it.watchCandidate != null }
                }
                val sports = tabEvents.mapNotNull { it.event.sport?.takeIf(String::isNotBlank) }.distinct().sorted()
                if (selectedSport != null && selectedSport !in sports) selectedSport = null
                SportsV2SportStrip(sports, selectedSport) { selectedSport = it }
                val visibleEvents = tabEvents.filter { selectedSport == null || it.event.sport == selectedSport }
                if (selectedId == null || visibleEvents.none { it.event.id == selectedId }) {
                    selectedId = visibleEvents.firstOrNull { it.event.isLive && it.watchCandidate != null }?.event?.id
                        ?: visibleEvents.firstOrNull { it.watchCandidate != null }?.event?.id
                        ?: visibleEvents.firstOrNull()?.event?.id
                }
                val selected = visibleEvents.firstOrNull { it.event.id == selectedId }
                selected?.let { item ->
                    if (preview.state.contentId != "sports:${item.event.id}") select(item)
                }

                if (phone) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        item("hero") {
                            SportsV2HeroPhone(selected, preview, context, hideScores, selectedDetail)
                        }
                        sportsLeagueSections(visibleEvents, selectedId, ::select, compact = true, hideScores = hideScores)
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().height(if (device == AstraWaveDeviceClass.TV) 318.dp else 272.dp)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            SportsPreviewV2(
                                preview = preview,
                                modifier = Modifier.weight(1.18f).fillMaxHeight(),
                                onFullScreen = { openSportsFullScreen(context, preview.state.urls) },
                            )
                            SportsV2MatchupPanel(selected, Modifier.weight(0.82f).fillMaxHeight(), context, hideScores, selectedDetail)
                        }
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
                            sportsLeagueSections(visibleEvents, selectedId, ::select, compact = false, hideScores = hideScores)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SportsV2Header(
    date: LocalDate,
    selectedTab: TivraSportsV2Tab,
    hideScores: Boolean,
    onTab: (TivraSportsV2Tab) -> Unit,
    onToggleScores: () -> Unit,
) {
    val phone = LocalAstraWaveDeviceClass.current == AstraWaveDeviceClass.PHONE
    Column(Modifier.fillMaxWidth().padding(horizontal = if (phone) 14.dp else 18.dp, vertical = if (phone) 8.dp else 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text("Sports", color = AstraWaveColors.PrimaryText, style = if (phone) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")).uppercase(), color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("LIVE HUB", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelMedium)
                Text(
                    if (hideScores) "SHOW SCORES" else "HIDE SCORES",
                    color = if (hideScores) AstraWaveColors.AccentStrong else AstraWaveColors.TertiaryText,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clickable(onClick = onToggleScores).padding(vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(if (phone) 16.dp else 20.dp)) {
            TivraSportsV2Tab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Column(Modifier.clickable { onTab(tab) }.padding(vertical = 4.dp)) {
                    Text(tab.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, color = if (selected) AstraWaveColors.PrimaryText else AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.width(if (selected) 28.dp else 0.dp).height(2.dp).background(AstraWaveColors.FocusRing))
                }
            }
        }
    }
}

@Composable
private fun SportsV2SportStrip(sports: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    if (sports.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val allActive = selected == null
        Column(
            Modifier.clickable { onSelect(null) }.padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "ALL SPORTS",
                color = if (allActive) AstraWaveColors.PrimaryText else AstraWaveColors.TertiaryText,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.width(if (allActive) 28.dp else 0.dp).height(2.dp)
                    .background(if (allActive) AstraWaveColors.FocusRing else Color.Transparent),
            )
        }
        sports.forEach { sport ->
            val active = sport == selected
            Column(
                Modifier.clickable { onSelect(sport) }.padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    sport.uppercase(),
                    color = if (active) AstraWaveColors.PrimaryText else AstraWaveColors.TertiaryText,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.width(if (active) 28.dp else 0.dp).height(2.dp)
                        .background(if (active) AstraWaveColors.FocusRing else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun SportsV2DateStrip(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (-2L..5L).forEach { offset ->
            val date = LocalDate.now().plusDays(offset)
            val active = date == selected
            val label = when (offset) {
                -1L -> "YESTERDAY"
                0L -> "TODAY"
                1L -> "TOMORROW"
                else -> date.format(DateTimeFormatter.ofPattern("EEE d")).uppercase()
            }
            Column(
                Modifier.clickable { onSelect(date) }.padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    color = if (active) AstraWaveColors.PrimaryText else AstraWaveColors.TertiaryText,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.width(if (active) 28.dp else 0.dp).height(2.dp)
                        .background(if (active) AstraWaveColors.FocusRing else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun SportsV2HeroPhone(item: SportsGuideItem?, preview: LivePreviewController, context: Context, hideScores: Boolean, detail: SportsEventDetail?) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        SportsPreviewV2(
            preview = preview,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            onFullScreen = { openSportsFullScreen(context, preview.state.urls) },
        )
        SportsV2MatchupPanel(item, Modifier.fillMaxWidth(), context, hideScores, detail)
    }
}

@Composable
private fun SportsV2MatchupPanel(item: SportsGuideItem?, modifier: Modifier, context: Context, hideScores: Boolean, detail: SportsEventDetail?) {
    val event = item?.event
    Column(modifier.background(AstraWaveColors.Background).padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                when {
                    event?.isLive == true -> "● LIVE"
                    event?.isFinal == true -> "FINAL"
                    else -> event?.time ?: "UPCOMING"
                },
                color = if (event?.isLive == true) AstraWaveColors.Live else AstraWaveColors.TertiaryText,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(event?.league?.uppercase() ?: "SPORTS", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        }
        if (event == null) {
            Text("Select a game", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineMedium)
            Text("Choose a matchup below to see scores, network and channel availability.", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            BroadcastTeamScore(event.awayTeam ?: event.name.substringBefore(" at ").substringBefore(" vs "), if (hideScores) null else event.awayScore, hideScores, Modifier.weight(1f))
            Text("—", color = AstraWaveColors.Divider, style = MaterialTheme.typography.headlineMedium)
            BroadcastTeamScore(event.homeTeam ?: event.name.substringAfter(" at ", event.name.substringAfter(" vs ", "")), if (hideScores) null else event.homeScore, hideScores, Modifier.weight(1f))
        }

        if (event.homeTeam == null && event.awayTeam == null) {
            Text(event.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge, maxLines = 2)
        }
        val network = item.broadcasterNames.joinToString("  •  ").ifBlank { event.network ?: "Broadcast pending" }
        Text(network, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        event.venue?.takeIf(String::isNotBlank)?.let { Text(it, color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.bodySmall) }

        detail?.let { loaded ->
            if (!hideScores && loaded.lines.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().background(AstraWaveColors.BackgroundRaised).padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    loaded.lines.take(2).forEach { line ->
                        Column(Modifier.weight(1f)) {
                            Text(line.abbreviation ?: line.team, color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            Text(line.score?.toString() ?: "—", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                            if (line.periods.isNotEmpty()) {
                                Text(
                                    line.periods.joinToString("  ") { (label, value) -> "$label ${value ?: "—"}" },
                                    color = AstraWaveColors.SecondaryText,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
            val leaders = loaded.leaders.take(2)
            if (leaders.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    leaders.forEach { leader ->
                        Column(Modifier.weight(1f)) {
                            Text(leader.category.uppercase(), color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            Text(leader.athlete, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(leader.value, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
            loaded.venue?.let { venue ->
                Text(venue, color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }

        if (item.watchCandidate != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ON YOUR CHANNELS", color = AstraWaveColors.Success, style = MaterialTheme.typography.labelSmall)
                    Text(item.watchCandidate.channelName, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
                Text(
                    "WATCH  ▶",
                    color = Color.Black,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.background(AstraWaveColors.AccentStrong).clickable {
                        openSportsFullScreen(context, item.resolution?.candidates.orEmpty().map { it.streamUrl }.filter(String::isNotBlank))
                    }.padding(horizontal = 16.dp, vertical = 11.dp),
                )
            }
        } else {
            Text("NO MATCHED CHANNEL YET", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun BroadcastTeamScore(team: String, score: Int?, hidden: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(team.ifBlank { "Team" }, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(
            if (hidden) "—" else score?.toString() ?: "—",
            color = AstraWaveColors.PrimaryText,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun TeamScoreLine(team: String, score: Int?, hidden: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(team.ifBlank { "Team" }, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.weight(1f))
        Text(if (hidden) "HIDDEN" else score?.toString() ?: "—", color = if (hidden) AstraWaveColors.TertiaryText else AstraWaveColors.PrimaryText, style = if (hidden) MaterialTheme.typography.labelMedium else MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sportsLeagueSections(
    events: List<SportsGuideItem>,
    selectedId: String?,
    onSelect: (SportsGuideItem) -> Unit,
    compact: Boolean,
    hideScores: Boolean,
) {
    val liveNow = events.filter { it.event.isLive }
    if (liveNow.isNotEmpty()) {
        item("sports-v2-header:live-now") {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("LIVE NOW", color = AstraWaveColors.Live, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val watchable = liveNow.count { it.watchCandidate != null }
                if (watchable > 0) Text("$watchable ON YOUR CHANNELS", color = AstraWaveColors.Success, style = MaterialTheme.typography.labelSmall)
            }
        }
        item("sports-v2-row:live-now") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 12.dp),
            ) {
                items(liveNow, key = { "live:" + it.event.id }) { game ->
                    SportsScoreCardV2(game, game.event.id == selectedId, compact, hideScores) { onSelect(game) }
                }
            }
        }
    }
    val leagues = events.groupBy { it.event.league?.takeIf(String::isNotBlank) ?: "TODAY'S GAMES" }
    leagues.forEach { (league, games) ->
        item("sports-v2-header:$league") {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(league.uppercase(), color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val ready = games.count { it.watchCandidate != null }
                if (ready > 0) Text("$ready WATCHABLE", color = AstraWaveColors.Success, style = MaterialTheme.typography.labelSmall)
            }
        }
        item("sports-v2-row:$league") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 12.dp),
            ) {
                items(games, key = { it.event.id }) { game ->
                    SportsScoreCardV2(game, game.event.id == selectedId, compact, hideScores) { onSelect(game) }
                }
            }
        }
    }
    if (events.isEmpty()) {
        item("sports-v2-empty") {
            Box(Modifier.fillMaxWidth().padding(28.dp)) { AstraWaveEmptyState("No games", "Try another day or tab.") }
        }
    }
}

@Composable
private fun SportsScoreCardV2(item: SportsGuideItem, selected: Boolean, compact: Boolean, hideScores: Boolean, onClick: () -> Unit) {
    val event = item.event
    val width = if (compact) 268.dp else 294.dp
    Column(
        Modifier.width(width).height(if (compact) 154.dp else 166.dp)
            .background(if (selected) AstraWaveColors.SurfaceFocus else AstraWaveColors.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) AstraWaveColors.FocusRing else AstraWaveColors.Divider)
            .clickable(onClick = onClick).padding(13.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                when {
                    event.isLive -> "● LIVE"
                    event.isFinal -> "FINAL"
                    else -> event.time ?: "UPCOMING"
                },
                color = if (event.isLive) AstraWaveColors.Live else AstraWaveColors.TertiaryText,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(item.broadcasterNames.firstOrNull() ?: event.network ?: "TBD", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        ScoreCardTeam(event.awayTeam ?: event.name.substringBefore(" at ").substringBefore(" vs "), if (hideScores) null else event.awayScore, hideScores)
        ScoreCardTeam(event.homeTeam ?: event.name.substringAfter(" at ", event.name.substringAfter(" vs ", "")), if (hideScores) null else event.homeScore, hideScores)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(event.sport?.uppercase() ?: event.league?.uppercase() ?: "SPORTS", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
            Text(if (item.watchCandidate != null) "WATCH" else "SCHEDULE", color = if (item.watchCandidate != null) AstraWaveColors.Success else AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ScoreCardTeam(team: String, score: Int?, hidden: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(team.ifBlank { "Team" }, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
        Text(if (hidden) "•••" else score?.toString() ?: "—", color = if (hidden) AstraWaveColors.TertiaryText else AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SportsPreviewV2(preview: LivePreviewController, modifier: Modifier, onFullScreen: () -> Unit) {
    val context = LocalContext.current
    val state = preview.state
    val player = remember { ExoPlayer.Builder(context).build().apply { volume = 0.65f } }
    LaunchedEffect(state.activeUrl) {
        val url = state.activeUrl
        if (url.isNullOrBlank()) {
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
        if (!state.activeUrl.isNullOrBlank()) {
            AndroidView(
                factory = { PlayerView(it).apply { useController = false; this.player = player } },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize().clickable(onClick = onFullScreen),
            )
        } else {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LIVE PREVIEW", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Text(state.title.ifBlank { "Select a watchable game" }, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge, maxLines = 2)
            }
        }
        if (state.title.isNotBlank()) {
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.74f)).padding(14.dp)) {
                Text(state.provider?.uppercase() ?: "SPORTS HUB", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelSmall)
                Text(state.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(state.subtitle, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}

private fun openSportsFullScreen(context: Context, urls: List<String>) {
    val playable = urls.filter(String::isNotBlank).distinct()
    if (playable.isEmpty()) return
    context.startActivity(
        Intent(context, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_URL, playable.first())
            .putStringArrayListExtra(PlayerActivity.EXTRA_URLS, ArrayList(playable))
            .putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true),
    )
}
