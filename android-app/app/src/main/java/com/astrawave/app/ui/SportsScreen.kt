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
import com.astrawave.app.data.SourceFusionPlaybackPlanner
import com.astrawave.app.data.SportsAvailabilityReason
import com.astrawave.app.data.SportsGuideItem
import com.astrawave.app.data.SportsGuideRepository
import com.astrawave.app.data.SportsGuideSnapshot
import com.astrawave.app.data.SportsPreferenceStore
import com.astrawave.app.data.StremioLiveCatalogDiscovery
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
    val device = LocalAstraWaveDeviceClass.current
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
    val playbackPlanner = remember { SourceFusionPlaybackPlanner(context) }
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
                    repository.load(date = selectedDate, sources = sources, addonSportsChannelNames = addonSportsChannels)
                },
            )
        } catch (error: Exception) {
            SportsLoadState.Error(error.message ?: "Unable to load sports schedule")
        }
    }

    fun play(item: SportsGuideItem) {
        val candidates = item.resolution?.candidates.orEmpty()
        if (candidates.isEmpty()) {
            Toast.makeText(context, sportsAvailabilityMessage(item), Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            val plan = withContext(Dispatchers.IO) {
                playbackPlanner.plan(
                    candidates.mapIndexed { index, candidate ->
                        SourceFusionPlaybackPlanner.Input(
                            sourceKey = "sports:${item.event.id}:$index:${candidate.source}",
                            url = candidate.streamUrl,
                            provider = candidate.source,
                            priority = index,
                        )
                    },
                )
            }
            val urls = plan?.orderedUrls.orEmpty()
            if (urls.isEmpty()) {
                Toast.makeText(context, "Matched channels are not reachable right now.", Toast.LENGTH_LONG).show()
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
            Modifier.fillMaxWidth().padding(horizontal = if (device == AstraWaveDeviceClass.PHONE) 16.dp else 24.dp, vertical = if (device == AstraWaveDeviceClass.PHONE) 9.dp else 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                if (device == AstraWaveDeviceClass.PHONE) {
                    Text("Sports", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
                } else {
                    Text("SPORTS", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelLarge)
                    Text("Game Day", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
                    Text("Live now, starting soon and watch-ready games from your authorized sources.", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
            }
            if (multiviewCount > 0 && device != AstraWaveDeviceClass.PHONE) {
                AstraWavePrimaryButton("Mosaic $multiviewCount/6", onOpenMultiview)
            }
        }

        SportsDateRail(selectedDate) { selectedDate = it }
        Spacer(Modifier.height(8.dp))

        when (val current = state) {
            SportsLoadState.Loading -> Column(Modifier.fillMaxSize().padding(24.dp)) {
                AstraWaveLoadingState("Loading Game Day", "Finding schedules and watch-ready channels.")
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
                val featured = live.firstOrNull { it.watchCandidate != null }
                    ?: upcoming.firstOrNull { it.watchCandidate != null }
                    ?: live.firstOrNull()
                    ?: upcoming.firstOrNull()

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

                if (device == AstraWaveDeviceClass.PHONE) {
                    PhoneSportsContent(
                        snapshot = snapshot,
                        featured = featured,
                        sections = sections,
                        selectedSection = selectedSection,
                        onSection = { selectedSection = it },
                        leagues = leagues,
                        selectedLeague = selectedLeague,
                        onLeague = { selectedLeague = it },
                        visible = visible,
                        selectedEventId = selectedEventId,
                        onSelect = { selectedEventId = if (selectedEventId == it.event.id) null else it.event.id },
                        favoriteTeams = favoriteTeams.map { it.name }.toSet(),
                        reminders = reminders.map { it.eventId }.toSet(),
                        multiviewCount = multiviewCount,
                        onPlay = ::play,
                        onReminder = ::toggleReminder,
                        onFavorite = ::toggleTeam,
                        onMosaic = ::addMosaic,
                    )
                } else {
                    LargeSportsContent(
                        snapshot = snapshot,
                        featured = featured,
                        sections = sections,
                        selectedSection = selectedSection,
                        onSection = { selectedSection = it },
                        leagues = leagues,
                        selectedLeague = selectedLeague,
                        onLeague = { selectedLeague = it },
                        visible = visible,
                        selected = selected,
                        onSelect = { selectedEventId = it.event.id },
                        favoriteTeams = favoriteTeams.map { it.name }.toSet(),
                        reminders = reminders.map { it.eventId }.toSet(),
                        multiviewCount = multiviewCount,
                        onPlay = ::play,
                        onReminder = ::toggleReminder,
                        onFavorite = ::toggleTeam,
                        onMosaic = ::addMosaic,
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneSportsContent(
    snapshot: SportsGuideSnapshot,
    featured: SportsGuideItem?,
    sections: List<SportsSection>,
    selectedSection: SportsSection,
    onSection: (SportsSection) -> Unit,
    leagues: List<String>,
    selectedLeague: String?,
    onLeague: (String?) -> Unit,
    visible: List<SportsGuideItem>,
    selectedEventId: String?,
    onSelect: (SportsGuideItem) -> Unit,
    favoriteTeams: Set<String>,
    reminders: Set<String>,
    multiviewCount: Int,
    onPlay: (SportsGuideItem) -> Unit,
    onReminder: (SportsGuideItem) -> Unit,
    onFavorite: (String?, String?) -> Unit,
    onMosaic: (SportsGuideItem) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        featured?.let {
            SportsFeaturedHero(it, onPlay)
            Spacer(Modifier.height(14.dp))
        }
        SportsFilterRails(sections, selectedSection, onSection, leagues, selectedLeague, onLeague)
        Spacer(Modifier.height(14.dp))
        AstraWaveSectionHeader(
            title = sectionLabel(selectedSection),
            subtitle = "${visible.size} games",
        )
        Spacer(Modifier.height(8.dp))

        if (visible.isEmpty()) {
            AstraWaveEmptyState(
                if (selectedSection == SportsSection.FOR_YOU) "Pick your teams" else "No games here",
                if (selectedSection == SportsSection.FOR_YOU) "Favorite a team from any game to build your personal feed." else "Try another date, league or section.",
            )
        } else {
            visible.take(120).forEach { item ->
                val expanded = item.event.id == selectedEventId
                SportsEventCard(item = item, selected = expanded, onClick = { onSelect(item) })
                if (expanded) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                        SportsActions(
                            item = item,
                            favoriteTeams = favoriteTeams,
                            reminderActive = item.event.id in reminders,
                            multiviewCount = multiviewCount,
                            onPlay = onPlay,
                            onReminder = onReminder,
                            onFavorite = onFavorite,
                            onMosaic = onMosaic,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LargeSportsContent(
    snapshot: SportsGuideSnapshot,
    featured: SportsGuideItem?,
    sections: List<SportsSection>,
    selectedSection: SportsSection,
    onSection: (SportsSection) -> Unit,
    leagues: List<String>,
    selectedLeague: String?,
    onLeague: (String?) -> Unit,
    visible: List<SportsGuideItem>,
    selected: SportsGuideItem?,
    onSelect: (SportsGuideItem) -> Unit,
    favoriteTeams: Set<String>,
    reminders: Set<String>,
    multiviewCount: Int,
    onPlay: (SportsGuideItem) -> Unit,
    onReminder: (SportsGuideItem) -> Unit,
    onFavorite: (String?, String?) -> Unit,
    onMosaic: (SportsGuideItem) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 6.dp)) {
        featured?.let {
            SportsFeaturedHero(it, onPlay)
            Spacer(Modifier.height(16.dp))
        }

        SportsFilterRails(sections, selectedSection, onSection, leagues, selectedLeague, onLeague)
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1.05f)) {
                AstraWaveSectionHeader(
                    title = sectionLabel(selectedSection),
                    subtitle = "${visible.size} games • ${visible.count { it.watchCandidate != null }} ready to watch • ${snapshot.combinedChannelGroups} matched channel groups",
                )
                Spacer(Modifier.height(8.dp))
                if (visible.isEmpty()) {
                    AstraWaveEmptyState("No games here", "Try another date, league or section.")
                } else {
                    visible.take(80).forEach { item ->
                        SportsEventCard(item = item, selected = item.event.id == selected?.event?.id, onClick = { onSelect(item) })
                        Spacer(Modifier.height(7.dp))
                    }
                }
            }

            Column(
                Modifier.weight(0.95f).background(AstraWaveColors.BackgroundRaised, MaterialTheme.shapes.large).padding(18.dp),
            ) {
                if (selected == null) {
                    AstraWaveEmptyState("Select a game", "Choose an event to see scores, source status and actions.")
                } else {
                    Text(eventBadge(selected), color = eventBadgeColor(selected), style = MaterialTheme.typography.labelLarge)
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
                    SportsActions(
                        item = selected,
                        favoriteTeams = favoriteTeams,
                        reminderActive = selected.event.id in reminders,
                        multiviewCount = multiviewCount,
                        onPlay = onPlay,
                        onReminder = onReminder,
                        onFavorite = onFavorite,
                        onMosaic = onMosaic,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SportsFeaturedHero(item: SportsGuideItem, onPlay: (SportsGuideItem) -> Unit) {
    AstraWaveFocusableCard(Modifier.fillMaxWidth().clickable(enabled = item.watchCandidate != null) { onPlay(item) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                when {
                    item.event.isLive -> "● LIVE NOW"
                    item.watchCandidate != null -> "STARTING SOON • WATCH READY"
                    else -> "STARTING SOON"
                },
                color = if (item.event.isLive) AstraWaveColors.Live else AstraWaveColors.AccentStrong,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(7.dp))
            Text(item.event.name, color = AstraWaveColors.PrimaryText, style = if (LocalAstraWaveDeviceClass.current == AstraWaveDeviceClass.PHONE) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge, maxLines = 2)
            Spacer(Modifier.height(5.dp))
            Text(
                listOfNotNull(item.event.league, eventTime(item), item.broadcasterNames.firstOrNull()).joinToString(" • "),
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
            )
            Spacer(Modifier.height(10.dp))
            SourceLine(item)
            if (item.watchCandidate != null) {
                Spacer(Modifier.height(8.dp))
                Text("Watch", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SportsFilterRails(
    sections: List<SportsSection>,
    selectedSection: SportsSection,
    onSection: (SportsSection) -> Unit,
    leagues: List<String>,
    selectedLeague: String?,
    onLeague: (String?) -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        sections.forEach { section ->
            if (section == selectedSection) AstraWavePrimaryButton(sectionLabel(section), { onSection(section) })
            else AstraWaveSecondaryButton(sectionLabel(section), { onSection(section) })
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        if (selectedLeague == null) AstraWavePrimaryButton("All leagues", { onLeague(null) })
        else AstraWaveSecondaryButton("All leagues", { onLeague(null) })
        leagues.take(24).forEach { league ->
            if (league == selectedLeague) AstraWavePrimaryButton(league, { onLeague(league) })
            else AstraWaveSecondaryButton(league, { onLeague(league) })
        }
    }
}

@Composable
private fun SportsEventCard(item: SportsGuideItem, selected: Boolean, onClick: () -> Unit) {
    AstraWaveFocusableCard(
        Modifier.fillMaxWidth()
            .background(if (selected) AstraWaveColors.SurfaceRaised else AstraWaveColors.Surface, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(eventTime(item), color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.labelLarge)
                Text(eventBadge(item), color = eventBadgeColor(item), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(item.event.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(
                listOfNotNull(item.event.league, item.broadcasterNames.firstOrNull()).joinToString(" • ").ifBlank { "Broadcast details pending" },
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
            Spacer(Modifier.height(6.dp))
            SourceLine(item)
        }
    }
}

@Composable
private fun SportsActions(
    item: SportsGuideItem,
    favoriteTeams: Set<String>,
    reminderActive: Boolean,
    multiviewCount: Int,
    onPlay: (SportsGuideItem) -> Unit,
    onReminder: (SportsGuideItem) -> Unit,
    onFavorite: (String?, String?) -> Unit,
    onMosaic: (SportsGuideItem) -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (item.watchCandidate != null) AstraWavePrimaryButton("▶ Watch", { onPlay(item) })
        AstraWaveSecondaryButton(if (reminderActive) "✓ Reminded" else "＋ Reminder", { onReminder(item) })
        if (item.watchCandidate != null) {
            AstraWaveSecondaryButton(if (multiviewCount >= 6) "Mosaic Full" else "＋ Mosaic", { onMosaic(item) }, enabled = multiviewCount < 6)
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        item.event.homeTeam?.let { team ->
            AstraWaveSecondaryButton(if (favoriteTeams.any { it.equals(team, true) }) "★ $team" else "☆ $team", { onFavorite(team, item.event.league) })
        }
        item.event.awayTeam?.let { team ->
            AstraWaveSecondaryButton(if (favoriteTeams.any { it.equals(team, true) }) "★ $team" else "☆ $team", { onFavorite(team, item.event.league) })
        }
    }
}

@Composable
private fun SportsDateRail(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now(ZoneOffset.UTC)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
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
                "Watch on ${candidate.channelName}${if (backups > 0) " • $backups backup${if (backups == 1) "" else "s"}" else ""}",
                color = AstraWaveColors.Success,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        item.addonCatalogMatches.isNotEmpty() -> Text(
            "Game found • watch source not connected",
            color = AstraWaveColors.Accent,
            style = MaterialTheme.typography.labelMedium,
        )
        else -> Text(sportsAvailabilityMessage(item), color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
    }
}

private fun sportsAvailabilityMessage(item: SportsGuideItem): String = when (item.availabilityReason) {
    SportsAvailabilityReason.READY -> {
        val candidate = item.watchCandidate
        if (candidate == null) "Watch source is being resolved." else "${candidate.channelName} is available from an authorized source."
    }
    SportsAvailabilityReason.NO_BROADCAST_METADATA -> "Broadcaster details have not been announced yet."
    SportsAvailabilityReason.NO_CHANNEL_MATCH -> if (item.broadcasterNames.isEmpty()) "Broadcaster details are not available yet." else "No connected channel currently matches ${item.broadcasterNames.joinToString()}."
    SportsAvailabilityReason.NO_HEALTHY_CANDIDATE -> "A channel match exists, but no healthy authorized stream is available right now."
}

private fun sectionLabel(section: SportsSection): String = when (section) {
    SportsSection.FOR_YOU -> "For You"
    SportsSection.LIVE -> "Live Now"
    SportsSection.UPCOMING -> "Starting Soon"
    SportsSection.FINAL -> "Final"
    SportsSection.ALL -> "Full Schedule"
}

private fun eventTime(item: SportsGuideItem): String =
    item.event.time?.takeIf(String::isNotBlank) ?: item.event.date?.takeIf(String::isNotBlank) ?: "Scheduled"

private fun eventBadge(item: SportsGuideItem): String = when {
    item.event.isLive -> "● LIVE"
    item.event.isFinal -> "FINAL"
    item.watchCandidate != null -> "WATCH READY"
    item.availabilityReason == SportsAvailabilityReason.NO_HEALTHY_CANDIDATE -> "SOURCE DOWN"
    item.availabilityReason == SportsAvailabilityReason.NO_CHANNEL_MATCH -> "NO MATCH"
    else -> "UPCOMING"
}

private fun eventBadgeColor(item: SportsGuideItem) = when {
    item.event.isLive -> AstraWaveColors.Live
    item.watchCandidate != null -> AstraWaveColors.Success
    item.availabilityReason == SportsAvailabilityReason.NO_HEALTHY_CANDIDATE -> AstraWaveColors.Warning
    else -> AstraWaveColors.TertiaryText
}
