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
import com.astrawave.app.data.StremioLiveCatalogDiscovery
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
            AstraWavePageHeader("Sports", "Sports is disabled for this kids profile.")
            Spacer(Modifier.height(18.dp))
            AstraWaveStatePanel("Restricted by profile settings", "A household administrator can enable Sports for this profile from Privacy & Parental Controls.")
        }
        return
    }

    val scope = rememberCoroutineScope()
    val preferences = remember { SportsPreferenceStore(context) }
    val addonLiveDiscovery = remember { StremioLiveCatalogDiscovery(context) }
    var state by remember(sources, profileId) { mutableStateOf<SportsLoadState>(SportsLoadState.Loading) }
    var selectedDate by remember { mutableStateOf(LocalDate.now(ZoneOffset.UTC)) }
    var selectedLeague by remember { mutableStateOf<String?>(null) }
    var selectedSection by remember { mutableStateOf(SportsSection.FOR_YOU) }
    var selectedEventId by remember { mutableStateOf<String?>(null) }
    var favoriteTeams by remember(profileId) { mutableStateOf(preferences.teams(profileId)) }
    var reminders by remember(profileId) { mutableStateOf(preferences.reminders(profileId)) }

    LaunchedEffect(sources, selectedDate, profileId) {
        state = SportsLoadState.Loading
        state = try {
            SportsLoadState.Ready(
                withContext(Dispatchers.IO) {
                    val addonSportsChannels = runCatching { addonLiveDiscovery.sportsChannelNames(profileId) }.getOrDefault(emptyList())
                    repository.load(
                        date = selectedDate,
                        sources = sources,
                        addonSportsChannelNames = addonSportsChannels,
                    )
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
            preferences.saveReminder(
                profileId,
                SportsPreferenceStore.Reminder(
                    id = "sports:${item.event.id}",
                    eventId = item.event.id,
                    title = item.event.name,
                    startTime = listOfNotNull(item.event.date, item.event.time).joinToString(" "),
                ),
            )
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

    Column(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("ASTRAWAVE SPORTS", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelLarge)
                Text("Game Day", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Events first: favorite teams, live scores, reminders, matched broadcasters, hardcoded channel catalogs and six-screen Sports Mosaic.",
                    color = AstraWaveColors.SecondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
            }
            if (multiviewCount > 0) {
                AstraWavePrimaryButton("Sports Mosaic $multiviewCount/6", onOpenMultiview)
            }
        }

        SportsDateRail(selectedDate) { selectedDate = it }
        Spacer(Modifier.height(8.dp))

        when (val current = state) {
            SportsLoadState.Loading -> Column(Modifier.fillMaxSize().padding(24.dp)) {
                AstraWaveLoadingState("Building Game Day", "Loading events, broadcasters, hardcoded addon channel metadata and matched sources.")
            }
            is SportsLoadState.Error -> Column(Modifier.fillMaxSize().padding(24.dp)) {
                AstraWaveErrorState("Sports unavailable", current.message, retryLabel = "Try again", onRetry = { selectedDate = selectedDate.plusDays(0) })
            }
            is SportsLoadState.Ready -> {
                val snapshot = current.snapshot
                val favorites = favoriteTeams.map { it.name.lowercase() }.toSet()
                val forYou = snapshot.events.filter { item ->
                    listOfNotNull(item.event.homeTeam, item.event.awayTeam).any { it.lowercase() in favorites }
                }
                val live = snapshot.events.filter { it.event.isLive }
                val final = snapshot.events.filter { it.event.isFinal }
                val upcoming = snapshot.events.filterNot { it.event.isLive || it.event.isFinal }
                val sections = buildList {
                    if (forYou.isNotEmpty()) add(SportsSection.FOR_YOU)
                    if (live.isNotEmpty()) add(SportsSection.LIVE)
                    if (upcoming.isNotEmpty()) add(SportsSection.UPCOMING)
                    if (final.isNotEmpty()) add(SportsSection.FINAL)
                    add(SportsSection.ALL)
                }.distinct()
                if (selectedSection !in sections) selectedSection = sections.first()
                val leagues = snapshot.events.mapNotNull { it.event.league?.takeIf(String::isNotBlank) }.distinct().sorted()
                val baseEvents = when (selectedSection) {
                    SportsSection.FOR_YOU -> forYou
                    SportsSection.LIVE -> live
                    SportsSection.UPCOMING -> upcoming
                    SportsSection.FINAL -> final
                    SportsSection.ALL -> snapshot.events
                }
                val visible = selectedLeague?.let { league -> baseEvents.filter { it.event.league == league } } ?: baseEvents

                LaunchedEffect(visible, selectedEventId) {
                    if (selectedEventId == null || visible.none { it.event.id == selectedEventId }) {
                        selectedEventId = visible.firstOrNull { it.event.isLive && it.watchCandidate != null }?.event?.id
                            ?: visible.firstOrNull { it.watchCandidate != null }?.event?.id
                            ?: visible.firstOrNull()?.event?.id
                    }
                }
                val selected = visible.firstOrNull { it.event.id == selectedEventId } ?: visible.firstOrNull()

                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(
                        Modifier.width(220.dp).fillMaxSize().background(AstraWaveColors.BackgroundRaised, MaterialTheme.shapes.large).padding(10.dp),
                    ) {
                        Text("GAME DAY", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(8.dp))
                        sections.forEach { section ->
                            val count = when (section) {
                                SportsSection.FOR_YOU -> forYou.size
                                SportsSection.LIVE -> live.size
                                SportsSection.UPCOMING -> upcoming.size
                                SportsSection.FINAL -> final.size
                                SportsSection.ALL -> snapshot.events.size
                            }
                            SportsSideRow("${sectionLabel(section)}  $count", selectedSection == section) { selectedSection = section }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("LEAGUES", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(6.dp))
                        SportsSideRow("All leagues", selectedLeague == null) { selectedLeague = null }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            leagues.take(40).forEach { league -> SportsSideRow(league, selectedLeague == league) { selectedLeague = league } }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("${favoriteTeams.size} favorite teams", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
                        Text("${reminders.size} reminders", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
                        Text("${snapshot.addonSportsChannelCount} addon sports channels", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
                    }

                    Column(
                        Modifier.width(500.dp).fillMaxSize().background(AstraWaveColors.Surface, MaterialTheme.shapes.large).padding(10.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(sectionLabel(selectedSection), color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                                Text("${visible.size} events", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall)
                            }
                            Text(
                                "${snapshot.combinedChannelGroups} playable groups • ${snapshot.addonSportsChannelCount} addon catalog channels",
                                color = AstraWaveColors.TertiaryText,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (visible.isEmpty()) {
                            AstraWaveEmptyState(
                                if (selectedSection == SportsSection.FOR_YOU) "Pick your teams" else "No events here",
                                if (selectedSection == SportsSection.FOR_YOU) "Favorite a team from an event to build your personal sports guide." else "Try another date, league or section.",
                            )
                        } else {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                visible.take(180).forEach { item ->
                                    val active = item.event.id == selected?.event?.id
                                    Column(
                                        Modifier.fillMaxWidth()
                                            .background(if (active) AstraWaveColors.SurfaceRaised else AstraWaveColors.Surface, MaterialTheme.shapes.medium)
                                            .clickable { selectedEventId = item.event.id }
                                            .padding(horizontal = 11.dp, vertical = 10.dp),
                                    ) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(eventTime(item), color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.labelLarge)
                                            Text(
                                                eventBadge(item),
                                                color = if (item.event.isLive) AstraWaveColors.Live else if (item.watchCandidate != null) AstraWaveColors.Success else AstraWaveColors.TertiaryText,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                        Text(item.event.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                        Text(
                                            listOfNotNull(item.event.league, item.broadcasterNames.firstOrNull()).joinToString(" • "),
                                            color = AstraWaveColors.SecondaryText,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                        )
                                        if (item.addonCatalogMatches.isNotEmpty()) {
                                            Text("Addon match • ${item.addonCatalogMatches.joinToString()}", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                        }
                                    }
                                    Spacer(Modifier.height(3.dp))
                                }
                            }
                        }
                    }

                    Column(
                        Modifier.weight(1f).fillMaxSize().background(AstraWaveColors.BackgroundRaised, MaterialTheme.shapes.large).padding(18.dp),
                    ) {
                        if (selected == null) {
                            AstraWaveEmptyState("Select a game", "Choose an event to see broadcaster matching, favorite-team controls and Watch/Mosaic actions.")
                        } else {
                            val reminderActive = reminders.any { it.eventId == selected.event.id }
                            val isFavoriteHome = favoriteTeams.any { it.name.equals(selected.event.homeTeam, true) }
                            val isFavoriteAway = favoriteTeams.any { it.name.equals(selected.event.awayTeam, true) }
                            Text(eventBadge(selected), color = if (selected.event.isLive) AstraWaveColors.Live else AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(5.dp))
                            Text(selected.event.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineMedium, maxLines = 3)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                listOfNotNull(selected.event.league, selected.event.venue).joinToString(" • ").ifBlank { "Sports event" },
                                color = AstraWaveColors.SecondaryText,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (selected.event.homeScore != null && selected.event.awayScore != null) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "${selected.event.awayTeam ?: "Away"} ${selected.event.awayScore}   •   ${selected.event.homeTeam ?: "Home"} ${selected.event.homeScore}",
                                    color = AstraWaveColors.PrimaryText,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            SourceLine(selected)
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (selected.watchCandidate != null) AstraWavePrimaryButton("▶ Watch", { play(selected) })
                                AstraWaveSecondaryButton(label = if (reminderActive) "✓ Reminded" else "＋ Reminder", onClick = { toggleReminder(selected) })
                                if (selected.watchCandidate != null) {
                                    AstraWaveSecondaryButton(
                                        label = if (multiviewCount >= 6) "Mosaic Full" else "＋ Mosaic",
                                        onClick = { addMosaic(selected) },
                                        enabled = multiviewCount < 6,
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            selected.event.homeTeam?.let { team ->
                                AstraWaveSecondaryButton(label = if (isFavoriteHome) "★ $team" else "☆ $team", onClick = { toggleTeam(team, selected.event.league) })
                                Spacer(Modifier.height(6.dp))
                            }
                            selected.event.awayTeam?.let { team ->
                                AstraWaveSecondaryButton(label = if (isFavoriteAway) "★ $team" else "☆ $team", onClick = { toggleTeam(team, selected.event.league) })
                            }
                            Spacer(Modifier.height(16.dp))
                            AstraWaveStatePanel(
                                title = if (selected.watchCandidate != null) "Watch Ready" else if (selected.addonCatalogMatches.isNotEmpty()) "Catalog broadcaster match" else "Broadcaster match pending",
                                message = buildString {
                                    if (selected.watchCandidate != null) {
                                        append(selected.watchCandidate.channelName)
                                        append(" • ${selected.watchCandidate.source}")
                                        val backups = (selected.resolution?.candidates?.size ?: 1) - 1
                                        if (backups > 0) append(" • $backups backup${if (backups == 1) "" else "s"}")
                                    } else if (selected.addonCatalogMatches.isNotEmpty()) {
                                        append("Hardcoded addon catalog match: ${selected.addonCatalogMatches.joinToString()}. Metadata match only; playback still requires an eligible authorized source.")
                                    } else if (selected.broadcasterNames.isNotEmpty()) {
                                        append("Broadcast: ${selected.broadcasterNames.joinToString()}")
                                    } else append("Broadcast data pending")
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SportsSideRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) AstraWaveColors.PrimaryText else AstraWaveColors.SecondaryText,
        modifier = Modifier.fillMaxWidth()
            .background(if (selected) AstraWaveColors.Accent else AstraWaveColors.BackgroundRaised, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
    )
    Spacer(Modifier.height(3.dp))
}

@Composable
private fun SportsDateRail(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now(ZoneOffset.UTC)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
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
            if (selected == date) AstraWavePrimaryButton(label, { onSelect(date) })
            else AstraWaveSecondaryButton(label, { onSelect(date) })
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
            if (item.addonCatalogMatches.isNotEmpty()) {
                Text("Also found in addon catalog: ${item.addonCatalogMatches.joinToString()}", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelSmall, maxLines = 2)
            }
        }
        item.addonCatalogMatches.isNotEmpty() -> Text(
            "Addon catalog match: ${item.addonCatalogMatches.joinToString()} • metadata only until an authorized source is matched",
            color = AstraWaveColors.Accent,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
        )
        item.broadcasterNames.isNotEmpty() -> Text(
            "Broadcast: ${item.broadcasterNames.joinToString()} • no matched playable channel yet",
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.labelMedium,
        )
        else -> Text("Broadcast data pending", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelMedium)
    }
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
    item.addonCatalogMatches.isNotEmpty() -> "CATALOG MATCH"
    else -> "UPCOMING"
}
