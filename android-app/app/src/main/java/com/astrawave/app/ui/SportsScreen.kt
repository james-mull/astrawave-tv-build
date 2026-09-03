package com.astrawave.app.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.astrawave.app.data.SportsGuideItem
import com.astrawave.app.data.SportsGuideRepository
import com.astrawave.app.data.SportsGuideSnapshot
import com.astrawave.app.data.StreamHealthChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface SportsLoadState {
    data object Loading : SportsLoadState
    data class Ready(val snapshot: SportsGuideSnapshot) : SportsLoadState
    data class Error(val message: String) : SportsLoadState
}

@Composable
fun AstraWaveSportsScreen(
    sources: List<IptvSource>,
    multiviewCount: Int = 0,
    onAddToMultiview: (MultiviewPane) -> Unit = {},
    onOpenMultiview: () -> Unit = {},
    repository: SportsGuideRepository = remember { SportsGuideRepository() },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember(sources) { mutableStateOf<SportsLoadState>(SportsLoadState.Loading) }
    var selectedLeague by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sources) {
        state = SportsLoadState.Loading
        state = try {
            SportsLoadState.Ready(withContext(Dispatchers.IO) { repository.load(sources = sources) })
        } catch (error: Exception) {
            SportsLoadState.Error(error.message ?: "Unable to load sports schedule")
        }
    }

    fun play(url: String) {
        scope.launch {
            val healthy = withContext(Dispatchers.IO) { StreamHealthChecker.check(url).reachable }
            if (!healthy) {
                Toast.makeText(context, "This sports stream is not reachable right now.", Toast.LENGTH_LONG).show()
                return@launch
            }
            context.startActivity(Intent(context, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_URL, url))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background)
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        SportsHeader(multiviewCount = multiviewCount, onOpenMultiview = onOpenMultiview)
        Spacer(Modifier.height(22.dp))

        when (val current = state) {
            SportsLoadState.Loading -> SportsInfoCard("Building Game Day…", "Loading today's schedule and matching available channels.", true)
            is SportsLoadState.Error -> SportsInfoCard("Sports unavailable", current.message)
            is SportsLoadState.Ready -> {
                val snapshot = current.snapshot
                val playableCount = snapshot.events.count { it.watchCandidate != null }
                val leagues = snapshot.events.mapNotNull { it.event.league?.takeIf(String::isNotBlank) }.distinct().take(12)
                val visibleEvents = selectedLeague?.let { league -> snapshot.events.filter { it.event.league == league } } ?: snapshot.events

                SportsSummaryRail(
                    date = snapshot.date,
                    eventCount = snapshot.events.size,
                    playableCount = playableCount,
                    sourceGroups = snapshot.combinedChannelGroups,
                )
                Spacer(Modifier.height(18.dp))

                if (leagues.isNotEmpty()) {
                    LeagueFilterRail(
                        leagues = leagues,
                        selectedLeague = selectedLeague,
                        onSelect = { selectedLeague = it },
                    )
                    Spacer(Modifier.height(20.dp))
                }

                val featured = visibleEvents.firstOrNull { it.watchCandidate != null } ?: visibleEvents.firstOrNull()
                if (featured != null) {
                    FeaturedGameCard(
                        item = featured,
                        multiviewCount = multiviewCount,
                        onPlay = ::play,
                        onAddToMultiview = onAddToMultiview,
                    )
                    Spacer(Modifier.height(24.dp))
                }

                Text("Today's Schedule", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    selectedLeague ?: "All leagues",
                    color = AstraWaveColors.SecondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))

                if (visibleEvents.isEmpty()) {
                    SportsInfoCard("No events", "No events match the selected league for this date.")
                } else {
                    visibleEvents.take(100).forEach { item ->
                        SportsScheduleCard(
                            item = item,
                            multiviewCount = multiviewCount,
                            onPlay = ::play,
                            onAddToMultiview = onAddToMultiview,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SportsHeader(multiviewCount: Int, onOpenMultiview: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text("ASTRAWAVE SPORTS", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(5.dp))
            Text("Game Day", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Your premium schedule, broadcaster matches and watch options in one place.",
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (multiviewCount > 0) {
            Text(
                "Multiview  $multiviewCount/4",
                color = AstraWaveColors.Accent,
                modifier = Modifier.clickable(onClick = onOpenMultiview).padding(10.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SportsSummaryRail(date: String, eventCount: Int, playableCount: Int, sourceGroups: Int) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SportsStatPill("TODAY", date)
        SportsStatPill("EVENTS", eventCount.toString())
        SportsStatPill("WATCHABLE", playableCount.toString())
        SportsStatPill("CHANNEL GROUPS", sourceGroups.toString())
    }
}

@Composable
private fun SportsStatPill(label: String, value: String) {
    Column(
        Modifier
            .background(AstraWaveColors.SurfaceRaised, MaterialTheme.shapes.large)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(label, color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(2.dp))
        Text(value, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun LeagueFilterRail(
    leagues: List<String>,
    selectedLeague: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LeagueChip("All", selectedLeague == null) { onSelect(null) }
        leagues.forEach { league ->
            LeagueChip(league, selectedLeague == league) { onSelect(league) }
        }
    }
}

@Composable
private fun LeagueChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) AstraWaveColors.PrimaryText else AstraWaveColors.SecondaryText,
        modifier = Modifier
            .background(
                if (selected) AstraWaveColors.Accent else AstraWaveColors.Surface,
                MaterialTheme.shapes.large,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun FeaturedGameCard(
    item: SportsGuideItem,
    multiviewCount: Int,
    onPlay: (String) -> Unit,
    onAddToMultiview: (MultiviewPane) -> Unit,
) {
    val candidate = item.watchCandidate
    AstraWaveFocusableCard(Modifier.fillMaxWidth()) {
        Column(Modifier.background(AstraWaveColors.SurfaceRaised, MaterialTheme.shapes.large).padding(22.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("FEATURED MATCHUP", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
                Text(
                    item.event.time ?: item.event.date ?: "Scheduled",
                    color = AstraWaveColors.SecondaryText,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(item.event.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(7.dp))
            Text(
                listOfNotNull(item.event.league, item.event.sport).joinToString(" • ").ifBlank { "Sports event" },
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(14.dp))
            SportsSourceStatus(item)
            if (candidate != null) {
                Spacer(Modifier.height(18.dp))
                SportsActions(item, multiviewCount, onPlay, onAddToMultiview)
            }
        }
    }
}

@Composable
private fun SportsScheduleCard(
    item: SportsGuideItem,
    multiviewCount: Int,
    onPlay: (String) -> Unit,
    onAddToMultiview: (MultiviewPane) -> Unit,
) {
    AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.event.league ?: item.event.sport ?: "SPORTS",
                        color = AstraWaveColors.Accent,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(item.event.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    item.event.time ?: "Scheduled",
                    color = AstraWaveColors.PrimaryText,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(10.dp))
            SportsSourceStatus(item)
            if (item.watchCandidate != null) {
                Spacer(Modifier.height(12.dp))
                SportsActions(item, multiviewCount, onPlay, onAddToMultiview)
            }
        }
    }
}

@Composable
private fun SportsSourceStatus(item: SportsGuideItem) {
    val candidate = item.watchCandidate
    when {
        candidate != null -> {
            Text("MATCHED CHANNEL", color = AstraWaveColors.Success, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(3.dp))
            Text(
                "${candidate.channelName} • ${candidate.source}",
                color = AstraWaveColors.PrimaryText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item.broadcasterNames.isNotEmpty() -> {
            Text("BROADCASTER", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(3.dp))
            Text(
                "${item.broadcasterNames.joinToString()} • no matching playable channel found",
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        else -> Text(
            "Schedule available • broadcaster data not supplied",
            color = AstraWaveColors.TertiaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SportsActions(
    item: SportsGuideItem,
    multiviewCount: Int,
    onPlay: (String) -> Unit,
    onAddToMultiview: (MultiviewPane) -> Unit,
) {
    val candidate = item.watchCandidate ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(
            "▶ Watch",
            color = AstraWaveColors.PrimaryText,
            modifier = Modifier
                .background(AstraWaveColors.Accent, MaterialTheme.shapes.medium)
                .clickable { onPlay(candidate.streamUrl) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            if (multiviewCount >= 4) "Multiview full" else "+ Multiview",
            color = if (multiviewCount >= 4) AstraWaveColors.TertiaryText else AstraWaveColors.Accent,
            modifier = Modifier
                .clickable(enabled = multiviewCount < 4) {
                    onAddToMultiview(
                        MultiviewPane(
                            id = "sports:${item.event.id}",
                            title = item.event.name,
                            streamUrl = candidate.streamUrl,
                            sourceName = candidate.source,
                            eventId = item.event.id,
                        ),
                    )
                }
                .padding(horizontal = 6.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun SportsInfoCard(title: String, message: String, loading: Boolean = false) {
    Row(Modifier.fillMaxWidth().background(AstraWaveColors.Surface, MaterialTheme.shapes.medium).padding(18.dp)) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.width(22.dp), color = AstraWaveColors.Accent, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column {
            Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(message, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
