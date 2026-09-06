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
import com.astrawave.app.data.ProfileSafetyStore
import com.astrawave.app.data.SportsGuideItem
import com.astrawave.app.data.SportsGuideRepository
import com.astrawave.app.data.SportsGuideSnapshot
import com.astrawave.app.data.SportsPreferenceStore
import com.astrawave.app.data.StreamHealthChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private sealed interface SportsLoadState {
    data object Loading : SportsLoadState
    data class Ready(val snapshot: SportsGuideSnapshot) : SportsLoadState
    data class Error(val message: String) : SportsLoadState
}

private enum class SportsSection { FOR_YOU, LIVE, UPCOMING, FINAL, ALL }

@Composable
fun AstraWaveSportsScreen(
    sources: List<IptvSource>,
    multiviewCount: Int = 0,
    onAddToMultiview: (MultiviewPane) -> Unit = {},
    onOpenMultiview: () -> Unit = {},
    profileId: String = "default",
    repository: SportsGuideRepository = remember { SportsGuideRepository() },
) {
    val context = LocalContext.current
    val safety = remember(profileId) { ProfileSafetyStore(context).load(profileId) }
    if (safety.kids.enabled && !safety.kids.allowSports) {
        Column(Modifier.fillMaxSize().background(AstraWaveColors.Background).padding(24.dp)) {
            AstraWavePageHeader(
                title = "Sports",
                subtitle = "Sports is disabled for this kids profile.",
            )
            Spacer(Modifier.height(18.dp))
            AstraWaveStatePanel(
                title = "Restricted by profile settings",
                message = "A household administrator can enable Sports for this profile from Privacy & Parental Controls.",
            )
        }
        return
    }

    val scope = rememberCoroutineScope()
    val preferences = remember { SportsPreferenceStore(context) }
    var state by remember(sources) { mutableStateOf<SportsLoadState>(SportsLoadState.Loading) }
    var selectedDate by remember { mutableStateOf(LocalDate.now(ZoneOffset.UTC)) }
    var selectedLeague by remember { mutableStateOf<String?>(null) }
    var selectedSection by remember { mutableStateOf(SportsSection.FOR_YOU) }
    var favoriteTeams by remember(profileId) { mutableStateOf(preferences.teams(profileId)) }
    var reminders by remember(profileId) { mutableStateOf(preferences.reminders(profileId)) }

    LaunchedEffect(sources, selectedDate) {
        state = SportsLoadState.Loading
        state = try {
            SportsLoadState.Ready(
                withContext(Dispatchers.IO) {
                    repository.load(date = selectedDate, sources = sources)
                },
            )
        } catch (error: Exception) {
            SportsLoadState.Error(error.message ?: "Unable to load sports schedule")
        }
    }

    fun play(item: SportsGuideItem) {
        val urls = item.resolution?.candidates?.map { it.streamUrl }?.distinct().orEmpty()
        if (urls.isEmpty()) {
            Toast.makeText(context, "No matched authorized channel is available for this event.", Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            val healthy = withContext(Dispatchers.IO) {
                urls.filter { url -> runCatching { StreamHealthChecker.check(url).reachable }.getOrDefault(false) }
            }
            if (healthy.isEmpty()) {
                Toast.makeText(context, "Matched channels are not reachable right now.", Toast.LENGTH_LONG).show()
                return@launch
            }
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_URL, healthy.first())
                    .putStringArrayListExtra(PlayerActivity.EXTRA_URLS, ArrayList(healthy))
                    .putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true),
            )
        }
    }

    fun addMosaic(item: SportsGuideItem) {
        val candidate = item.watchCandidate ?: return
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

    fun toggleReminder(item: SportsGuideItem) {
        val existing = reminders.firstOrNull { it.eventId == item.event.id }
        if (existing != null) {
            preferences.removeReminder(profileId, existing.id)
            Toast.makeText(context, "Reminder removed", Toast.LENGTH_SHORT).show()
        } else {
            val reminder = SportsPreferenceStore.Reminder(
                id = "sports:${item.event.id}",
                eventId = item.event.id,
                title = item.event.name,
                startTime = listOfNotNull(item.event.date, item.event.time).joinToString(" "),
            )
            preferences.saveReminder(profileId, reminder)
            Toast.makeText(context, "Game reminder saved", Toast.LENGTH_SHORT).show()
        }
        reminders = preferences.reminders(profileId)
    }

    fun toggleTeam(name: String?, league: String?) {
        val clean = name?.trim().orEmpty()
        if (clean.isBlank()) return
        val id = clean.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        preferences.toggleTeam(profileId, SportsPreferenceStore.FavoriteTeam(id, clean, league))
        favoriteTeams = preferences.teams(profileId)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background)
            .padding(horizontal = 28.dp, vertical = 22.dp),
    ) {
        SportsHeroHeader(multiviewCount, onOpenMultiview)
        Spacer(Modifier.height(18.dp))
        SportsDateRail(selectedDate) { selectedDate = it }
        Spacer(Modifier.height(18.dp))

        when (val current = state) {
            SportsLoadState.Loading -> AstraWaveLoadingState(
                title = "Building Game Day",
                message = "Loading events, broadcasters and matched channels.",
            )

            is SportsLoadState.Error -> AstraWaveErrorState(
                title = "Sports unavailable",
                message = current.message,
                retryLabel = "Try again",
                onRetry = { selectedDate = selectedDate.plusDays(0) },
            )

            is SportsLoadState.Ready -> {
                val snapshot = current.snapshot
                val favorites = favoriteTeams.map { it.name.lowercase() }.toSet()
                val forYou = snapshot.events.filter { item ->
                    listOfNotNull(item.event.homeTeam, item.event.awayTeam).any { it.lowercase() in favorites }
                }
                val live = snapshot.events.filter { it.event.isLive }
                val final = snapshot.events.filter { it.event.isFinal }
                val upcoming = snapshot.events.filterNot { it.event.isLive || it.event.isFinal }
                val playableCount = snapshot.events.count { it.watchCandidate != null }

                SportsSummaryRail(
                    games = snapshot.events.size,
                    live = live.size,
                    ready = playableCount,
                    favorites = favoriteTeams.size,
                )
                Spacer(Modifier.height(16.dp))

                val sections = buildList {
                    if (forYou.isNotEmpty()) add(SportsSection.FOR_YOU)
                    if (live.isNotEmpty()) add(SportsSection.LIVE)
                    if (upcoming.isNotEmpty()) add(SportsSection.UPCOMING)
                    if (final.isNotEmpty()) add(SportsSection.FINAL)
                    add(SportsSection.ALL)
                }.distinct()
                if (selectedSection !in sections) selectedSection = sections.first()

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sections.forEach { section ->
                        val count = when (section) {
                            SportsSection.FOR_YOU -> forYou.size
                            SportsSection.LIVE -> live.size
                            SportsSection.UPCOMING -> upcoming.size
                            SportsSection.FINAL -> final.size
                            SportsSection.ALL -> snapshot.events.size
                        }
                        SportsNavChip(
                            label = "${sectionLabel(section)} $count",
                            selected = selectedSection == section,
                        ) { selectedSection = section }
                    }
                }
                Spacer(Modifier.height(16.dp))

                val leagues = snapshot.events.mapNotNull { it.event.league?.takeIf(String::isNotBlank) }.distinct().sorted()
                if (leagues.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SportsNavChip("All leagues", selectedLeague == null) { selectedLeague = null }
                        leagues.take(18).forEach { league ->
                            SportsNavChip(league, selectedLeague == league) {
                                selectedLeague = if (selectedLeague == league) null else league
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }

                val baseEvents = when (selectedSection) {
                    SportsSection.FOR_YOU -> forYou
                    SportsSection.LIVE -> live
                    SportsSection.UPCOMING -> upcoming
                    SportsSection.FINAL -> final
                    SportsSection.ALL -> snapshot.events
                }
                val visible = selectedLeague?.let { league -> baseEvents.filter { it.event.league == league } } ?: baseEvents
                val featured = visible.firstOrNull { it.event.isLive && it.watchCandidate != null }
                    ?: visible.firstOrNull { it.watchCandidate != null }
                    ?: visible.firstOrNull()

                if (featured != null) {
                    PremiumSportsFeatured(
                        item = featured,
                        reminderActive = reminders.any { it.eventId == featured.event.id },
                        multiviewCount = multiviewCount,
                        isFavoriteHome = favoriteTeams.any { it.name.equals(featured.event.homeTeam, true) },
                        isFavoriteAway = favoriteTeams.any { it.name.equals(featured.event.awayTeam, true) },
                        onWatch = { play(featured) },
                        onReminder = { toggleReminder(featured) },
                        onMosaic = { addMosaic(featured) },
                        onToggleHome = { toggleTeam(featured.event.homeTeam, featured.event.league) },
                        onToggleAway = { toggleTeam(featured.event.awayTeam, featured.event.league) },
                    )
                    Spacer(Modifier.height(24.dp))
                }

                AstraWaveSectionHeader(
                    title = when (selectedSection) {
                        SportsSection.FOR_YOU -> "Your Teams"
                        SportsSection.LIVE -> "Live Now"
                        SportsSection.UPCOMING -> "Coming Up"
                        SportsSection.FINAL -> "Final Scores"
                        SportsSection.ALL -> "Full Sports Guide"
                    },
                    subtitle = "${visible.size} events • ${snapshot.combinedChannelGroups} merged channel groups available for broadcaster matching",
                )
                Spacer(Modifier.height(12.dp))

                if (visible.isEmpty()) {
                    AstraWaveEmptyState(
                        title = if (selectedSection == SportsSection.FOR_YOU) "Pick your teams" else "No events here",
                        message = if (selectedSection == SportsSection.FOR_YOU) {
                            "Favorite a team from any event and AstraWave will build a personal sports guide around it."
                        } else {
                            "Try another date, league or sports section."
                        },
                    )
                } else {
                    visible.take(120).forEach { item ->
                        PremiumSportsRow(
                            item = item,
                            reminderActive = reminders.any { it.eventId == item.event.id },
                            multiviewCount = multiviewCount,
                            onWatch = { play(item) },
                            onReminder = { toggleReminder(item) },
                            onMosaic = { addMosaic(item) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun SportsHeroHeader(multiviewCount: Int, onOpenMultiview: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text("ASTRAWAVE SPORTS", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(3.dp))
            Text("Game Day", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(5.dp))
            Text(
                "Games first. Broadcasters matched automatically. One click to watch, remind, or build a six-screen Sports Mosaic.",
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (multiviewCount > 0) {
            AstraWavePrimaryButton(
                label = "Sports Mosaic $multiviewCount/6",
                onClick = onOpenMultiview,
            )
        }
    }
}

@Composable
private fun SportsDateRail(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now(ZoneOffset.UTC)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (-1L..5L).forEach { offset ->
            val date = today.plusDays(offset)
            val label = when (offset) {
                -1L -> "Yesterday"
                0L -> "Today"
                1L -> "Tomorrow"
                else -> date.format(DateTimeFormatter.ofPattern("EEE M/d"))
            }
            SportsNavChip(label, selected == date) { onSelect(date) }
        }
    }
}

@Composable
private fun SportsSummaryRail(games: Int, live: Int, ready: Int, favorites: Int) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SportsMetric("GAMES", games.toString())
        SportsMetric("LIVE", live.toString(), live > 0)
        SportsMetric("WATCH READY", ready.toString())
        SportsMetric("MY TEAMS", favorites.toString())
    }
}

@Composable
private fun SportsMetric(label: String, value: String, live: Boolean = false) {
    Column(
        Modifier
            .background(if (live) AstraWaveColors.SurfaceFocus else AstraWaveColors.SurfaceRaised, MaterialTheme.shapes.large)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(label, color = if (live) AstraWaveColors.Live else AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
        Text(value, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun PremiumSportsFeatured(
    item: SportsGuideItem,
    reminderActive: Boolean,
    multiviewCount: Int,
    isFavoriteHome: Boolean,
    isFavoriteAway: Boolean,
    onWatch: () -> Unit,
    onReminder: () -> Unit,
    onMosaic: () -> Unit,
    onToggleHome: () -> Unit,
    onToggleAway: () -> Unit,
) {
    AstraWaveFocusableCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    eventBadge(item),
                    color = if (item.event.isLive) AstraWaveColors.Live else if (item.watchCandidate != null) AstraWaveColors.Success else AstraWaveColors.Accent,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(eventTime(item), color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(14.dp))
            Text(item.event.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(item.event.league, item.event.venue).joinToString(" • ").ifBlank { "Sports event" },
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (item.event.homeScore != null && item.event.awayScore != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "${item.event.awayTeam ?: "Away"} ${item.event.awayScore}   •   ${item.event.homeTeam ?: "Home"} ${item.event.homeScore}",
                    color = AstraWaveColors.PrimaryText,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(Modifier.height(12.dp))
            SourceLine(item)
            Spacer(Modifier.height(15.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (item.watchCandidate != null) AstraWavePrimaryButton("▶ Watch", onWatch)
                AstraWaveSecondaryButton(if (reminderActive) "✓ Reminded" else "＋ Reminder", onReminder)
                if (item.watchCandidate != null) {
                    AstraWaveSecondaryButton(if (multiviewCount >= 6) "Mosaic full" else "＋ Mosaic", onMosaic, enabled = multiviewCount < 6)
                }
                item.event.homeTeam?.let { AstraWaveSecondaryButton(if (isFavoriteHome) "★ $it" else "☆ $it", onToggleHome) }
                item.event.awayTeam?.let { AstraWaveSecondaryButton(if (isFavoriteAway) "★ $it" else "☆ $it", onToggleAway) }
            }
        }
    }
}

@Composable
private fun PremiumSportsRow(
    item: SportsGuideItem,
    reminderActive: Boolean,
    multiviewCount: Int,
    onWatch: () -> Unit,
    onReminder: () -> Unit,
    onMosaic: () -> Unit,
) {
    AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.width(118.dp)) {
                Text(eventTime(item), color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(eventBadge(item), color = if (item.event.isLive) AstraWaveColors.Live else AstraWaveColors.Accent, style = MaterialTheme.typography.labelMedium)
            }
            Column(Modifier.weight(1f)) {
                Text(item.event.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                Text(
                    listOfNotNull(item.event.league, item.event.sport).joinToString(" • "),
                    color = AstraWaveColors.SecondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(5.dp))
                SourceLine(item)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.watchCandidate != null) AstraWavePrimaryButton("Watch", onWatch)
                AstraWaveSecondaryButton(if (reminderActive) "✓" else "Remind", onReminder)
                if (item.watchCandidate != null) AstraWaveSecondaryButton("Mosaic", onMosaic, enabled = multiviewCount < 6)
            }
        }
    }
}

@Composable
private fun SourceLine(item: SportsGuideItem) {
    val candidate = item.watchCandidate
    when {
        candidate != null -> {
            val backups = (item.resolution?.candidates?.size ?: 1) - 1
            Text(
                "${candidate.channelName} • ${candidate.source}${if (backups > 0) " • $backups backups" else ""}",
                color = AstraWaveColors.Success,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        item.broadcasterNames.isNotEmpty() -> Text(
            "Broadcast: ${item.broadcasterNames.joinToString()} • no matched playable channel yet",
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.labelMedium,
        )
        else -> Text("Broadcast data pending", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SportsNavChip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) AstraWavePrimaryButton(label, onClick) else AstraWaveSecondaryButton(label, onClick)
}

private fun sectionLabel(section: SportsSection): String = when (section) {
    SportsSection.FOR_YOU -> "For You"
    SportsSection.LIVE -> "Live"
    SportsSection.UPCOMING -> "Upcoming"
    SportsSection.FINAL -> "Final"
    SportsSection.ALL -> "All"
}

private fun eventTime(item: SportsGuideItem): String =
    item.event.time?.takeIf(String::isNotBlank) ?: item.event.date?.takeIf(String::isNotBlank) ?: "Scheduled"

private fun eventBadge(item: SportsGuideItem): String = when {
    item.event.isLive -> "● LIVE"
    item.event.isFinal -> "FINAL"
    item.watchCandidate != null -> "WATCH READY"
    else -> "UPCOMING"
}
