package com.astrawave.app.ui

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.PlayerActivity
import com.astrawave.app.TitleDetailsActivity
import com.astrawave.app.core.LibraryItemRef
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.data.ArtworkRegistry
import com.astrawave.app.data.AstraWaveMetadataGateway
import com.astrawave.app.data.HomeIntelligenceRepository
import com.astrawave.app.data.IptvSourceStore
import com.astrawave.app.data.LibraryCloudSync
import com.astrawave.app.data.LocalLibraryStore
import com.astrawave.app.data.SportsGuideItem
import com.astrawave.app.data.SportsGuideRepository
import com.astrawave.app.data.SportsPreferenceStore
import com.astrawave.app.data.SourceFusionPlaybackPlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset

private sealed interface HomeDiscoveryState {
    data object Loading : HomeDiscoveryState
    data class Ready(
        val movies: List<AstraWaveMetadataGateway.Item>,
        val series: List<AstraWaveMetadataGateway.Item>,
    ) : HomeDiscoveryState
    data class Error(val message: String) : HomeDiscoveryState
}

private sealed interface HomeSportsState {
    data object Loading : HomeSportsState
    data class Ready(val events: List<SportsGuideItem>) : HomeSportsState
    data object Empty : HomeSportsState
}

@Composable
fun PremiumHomeScreen(profileId: String = "default") {
    val context = LocalContext.current
    val library = remember { LocalLibraryStore(context) }
    val cloudSync = remember { LibraryCloudSync(context) }
    val metadata = remember { AstraWaveMetadataGateway() }
    val intelligence = remember { HomeIntelligenceRepository(context) }
    val sportsRepository = remember { SportsGuideRepository() }
    val sportsPreferences = remember { SportsPreferenceStore(context) }
    val fusion = remember { SourceFusionPlaybackPlanner(context) }

    var libraryRefresh by remember { mutableStateOf(0) }
    var discovery by remember { mutableStateOf<HomeDiscoveryState>(HomeDiscoveryState.Loading) }
    var intelligentRows by remember(profileId) { mutableStateOf<List<HomeIntelligenceRepository.Row>>(emptyList()) }
    var intelligenceLoading by remember(profileId) { mutableStateOf(true) }
    var sports by remember(profileId) { mutableStateOf<HomeSportsState>(HomeSportsState.Loading) }
    var cloudRestoreMessage by remember { mutableStateOf<String?>(null) }

    val allContinue = remember(profileId, libraryRefresh) { library.continueWatching(profileId).take(30) }
    val continueSeries = remember(allContinue) { allContinue.filter { it.item.type == LibraryMediaType.EPISODE }.take(18) }
    val continueWatching = remember(allContinue) { allContinue.filter { it.item.type != LibraryMediaType.EPISODE }.take(18) }
    val heroProgress = remember(continueSeries, continueWatching) { continueSeries.firstOrNull() ?: continueWatching.firstOrNull() }
    val watchlist = remember(profileId, libraryRefresh) { library.watchlist(profileId).take(24) }
    val recent = remember(profileId, libraryRefresh) { library.history(profileId).take(40) }

    LaunchedEffect(profileId) {
        cloudSync.restore(profileId) { result ->
            result.onSuccess { report ->
                val restored = report.watchlistImported + report.favoritesImported + report.listsImported + report.progressImported
                if (restored > 0) {
                    cloudRestoreMessage = "Cloud synced"
                    intelligence.invalidate(profileId)
                    libraryRefresh += 1
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        discovery = try {
            val pair = withContext(Dispatchers.IO) {
                metadata.load(AstraWaveMetadataGateway.Catalog.TRENDING_MOVIES).take(24) to
                    metadata.load(AstraWaveMetadataGateway.Catalog.TRENDING_SERIES).take(24)
            }
            (pair.first + pair.second).forEach { item ->
                ArtworkRegistry.register(item.name, item.posterUrl ?: item.backdropUrl)
            }
            HomeDiscoveryState.Ready(pair.first, pair.second)
        } catch (error: Exception) {
            HomeDiscoveryState.Error(error.message ?: "Discovery is temporarily unavailable")
        }
    }

    LaunchedEffect(profileId, libraryRefresh) {
        intelligenceLoading = true
        intelligentRows = withContext(Dispatchers.IO) {
            runCatching { intelligence.rows(profileId) }.getOrDefault(emptyList())
        }
        intelligentRows.flatMap { it.items }.forEach { item ->
            ArtworkRegistry.register(item.name, item.posterUrl ?: item.backdropUrl)
        }
        intelligenceLoading = false
    }

    LaunchedEffect(profileId) {
        sports = withContext(Dispatchers.IO) {
            val sources = IptvSourceStore(context).load(profileId)
            runCatching {
                val snapshot = sportsRepository.load(
                    date = LocalDate.now(ZoneOffset.UTC),
                    sources = sources,
                )
                val favorites = sportsPreferences.teams(profileId).map { it.name.lowercase() }.toSet()
                val ranked = snapshot.events.sortedWith(
                    compareByDescending<SportsGuideItem> { item -> item.event.isLive }
                        .thenByDescending { item ->
                            listOfNotNull(item.event.homeTeam, item.event.awayTeam).any { it.lowercase() in favorites }
                        }
                        .thenByDescending { item -> item.watchCandidate != null },
                ).take(12)
                if (ranked.isEmpty()) HomeSportsState.Empty else HomeSportsState.Ready(ranked)
            }.getOrDefault(HomeSportsState.Empty)
        }
    }

    fun openItem(item: LibraryItemRef) {
        context.startActivity(
            Intent(context, TitleDetailsActivity::class.java)
                .putExtra(TitleDetailsActivity.EXTRA_TITLE, item.title)
                .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, item.type.name)
                .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, item.sourceId)
                .putExtra(TitleDetailsActivity.EXTRA_PROFILE_ID, profileId),
        )
    }

    fun metadataType(item: AstraWaveMetadataGateway.Item): LibraryMediaType =
        if (item.type.equals("series", true) || item.type.equals("tv", true)) LibraryMediaType.SERIES else LibraryMediaType.MOVIE

    fun openMetadata(item: AstraWaveMetadataGateway.Item) {
        val mediaType = metadataType(item)
        val sourceId = when {
            item.id.startsWith("tt", true) -> "stremio:cinemeta:${if (mediaType == LibraryMediaType.SERIES) "series" else "movie"}:${item.id}"
            item.id.toLongOrNull() != null -> "tmdb:${item.id}"
            else -> null
        }
        context.startActivity(
            Intent(context, TitleDetailsActivity::class.java)
                .putExtra(TitleDetailsActivity.EXTRA_TITLE, item.name)
                .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, mediaType.name)
                .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, sourceId)
                .putExtra(TitleDetailsActivity.EXTRA_PROFILE_ID, profileId),
        )
    }

    fun playSports(item: SportsGuideItem) {
        val candidates = item.resolution?.candidates?.map { it.streamUrl }.orEmpty()
        val plan = fusion.urls(candidates, item.watchCandidate?.source ?: "Sports")
        if (plan.urls.isEmpty()) return
        context.startActivity(
            Intent(context, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_URL, plan.urls.first())
                .putStringArrayListExtra(PlayerActivity.EXTRA_URLS, ArrayList(plan.urls))
                .putExtra(PlayerActivity.EXTRA_TRUSTED_DIRECT, true),
        )
    }

    val liveFeatured = (sports as? HomeSportsState.Ready)?.events?.firstOrNull { it.event.isLive && it.watchCandidate != null }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AstraWaveColors.Background),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item(key = "home-topline") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("ASTRAWAVE", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
                    Text("Your entertainment, already organized.", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineLarge)
                }
                cloudRestoreMessage?.let { Text(it, color = AstraWaveColors.Success, style = MaterialTheme.typography.labelMedium) }
            }
        }

        when {
            liveFeatured != null -> item(key = "live-sports-hero-${liveFeatured.event.id}") {
                HomeSportsHero(liveFeatured, ::playSports)
            }
            heroProgress != null -> item(key = "resume-hero-${heroProgress.item.id}") {
                HomeHero(heroProgress, ::openItem)
            }
            discovery is HomeDiscoveryState.Ready -> {
                val first = (discovery as HomeDiscoveryState.Ready).movies.firstOrNull()
                    ?: (discovery as HomeDiscoveryState.Ready).series.firstOrNull()
                if (first != null) item(key = "discovery-hero-${first.id}") {
                    HomeDiscoveryHero(first, ::openMetadata)
                }
            }
        }

        val sportsReady = sports as? HomeSportsState.Ready
        if (sportsReady != null) {
            item(key = "sports-pulse") {
                HomeSection(
                    title = "Game Day",
                    subtitle = "Live and upcoming events, with your teams and watch-ready matches first",
                ) {
                    HomeSportsRow(sportsReady.events, ::playSports)
                }
            }
        }

        if (continueSeries.isNotEmpty()) {
            item(key = "continue-series") {
                HomeSection("Continue Series", "Resume the exact episode") {
                    ProgressHomeRow(continueSeries, "CONTINUE SERIES", ::openItem)
                }
            }
        }

        if (continueWatching.isNotEmpty()) {
            item(key = "continue-watching") {
                HomeSection("Continue Watching", "Pick up instantly across your library") {
                    ProgressHomeRow(continueWatching, "CONTINUE", ::openItem)
                }
            }
        }

        if (intelligenceLoading && recent.isNotEmpty() && intelligentRows.isEmpty()) {
            item(key = "intelligence-loading") {
                HomeSection("For You", "Personalizing from your history") { HomeSkeletonRow() }
            }
        } else {
            items(
                items = intelligentRows,
                key = { row -> "intel:${row.title}:${row.badge.orEmpty()}" },
            ) { row ->
                HomeSection(row.title, row.subtitle) {
                    MetadataHomeRow(row.items, row.badge, ::openMetadata)
                }
            }
        }

        if (watchlist.isNotEmpty()) {
            item(key = "watchlist") {
                HomeSection("My Watchlist", "Saved for later") {
                    LibraryHomeRow(watchlist.map { it.item }, "WATCHLIST", ::openItem)
                }
            }
        }

        when (val current = discovery) {
            HomeDiscoveryState.Loading -> item(key = "discovery-loading") {
                HomeSection("Discover", "Loading fresh picks") { HomeSkeletonRow() }
            }
            is HomeDiscoveryState.Error -> item(key = "discovery-error") {
                AstraWavePartialDataState(message = current.message, actionLabel = null)
            }
            is HomeDiscoveryState.Ready -> {
                item(key = "trending-movies") {
                    HomeSection("Trending Movies", "What people are watching now") {
                        MetadataHomeRow(current.movies, "TRENDING", ::openMetadata)
                    }
                }
                item(key = "trending-series") {
                    HomeSection("Trending TV", "Series moving right now") {
                        MetadataHomeRow(current.series, "TRENDING", ::openMetadata)
                    }
                }
                val fresh = (current.movies + current.series)
                    .sortedByDescending { it.releaseInfo?.take(10).orEmpty() }
                    .distinctBy { "${it.type}:${it.id}" }
                    .take(20)
                if (fresh.isNotEmpty()) item(key = "fresh") {
                    HomeSection("Fresh This Week", "Newer releases across movies and television") {
                        MetadataHomeRow(fresh, "NEW", ::openMetadata)
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item(key = "recent") {
                HomeSection("Recently Watched", "Jump back into something familiar") {
                    LibraryHomeRow(recent.take(24).map { it.item }, "RECENT", ::openItem)
                }
            }
        }

        item(key = "refresh") {
            Text(
                "Refresh personalized Home",
                color = AstraWaveColors.Accent,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable {
                    intelligence.invalidate(profileId)
                    libraryRefresh += 1
                }.padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun HomeSportsHero(item: SportsGuideItem, onWatch: (SportsGuideItem) -> Unit) {
    AstraWaveFocusableCard(Modifier.fillMaxWidth().clickable { onWatch(item) }) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(AstraWaveColors.SurfaceFocus, RoundedCornerShape(24.dp))
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("● LIVE NOW", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelLarge)
                Text(item.event.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.displayLarge, maxLines = 2)
                Text(
                    listOfNotNull(item.event.league, item.watchCandidate?.channelName, item.watchCandidate?.source).joinToString(" • "),
                    color = AstraWaveColors.SecondaryText,
                    style = MaterialTheme.typography.bodyLarge,
                )
                item.event.homeScore?.let { home ->
                    item.event.awayScore?.let { away ->
                        Text(
                            "${item.event.awayTeam ?: "Away"} $away  •  ${item.event.homeTeam ?: "Home"} $home",
                            color = AstraWaveColors.PrimaryText,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
                Text("Watch now →", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.width(250.dp)) {
                Text("GAME DAY", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Text("Best matched source selected automatically", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${(item.resolution?.candidates?.size ?: 1).coerceAtLeast(1)} source option${if ((item.resolution?.candidates?.size ?: 1) == 1) "" else "s"}",
                    color = AstraWaveColors.Success,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun HomeDiscoveryHero(item: AstraWaveMetadataGateway.Item, onOpen: (AstraWaveMetadataGateway.Item) -> Unit) {
    AstraWaveFocusableCard(Modifier.fillMaxWidth().clickable { onOpen(item) }) {
        Row(
            Modifier.fillMaxWidth().background(AstraWaveColors.BackgroundRaised, RoundedCornerShape(24.dp)).padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeBadge("FEATURED")
                Text(item.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.displayLarge, maxLines = 2)
                Text(item.description ?: "Open details, ratings, trailers and watch options.", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
                Text("Explore →", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.titleMedium)
            }
            Box(Modifier.width(360.dp)) {
                AstraWaveArtwork(item.name, Modifier.fillMaxWidth(), AstraWaveArtworkKind.Backdrop)
            }
        }
    }
}

@Composable
private fun HomeSection(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(11.dp))
        content()
    }
}

@Composable
private fun HomeHero(progress: LocalLibraryStore.PlaybackProgress, onOpen: (LibraryItemRef) -> Unit) {
    val ratio = if (progress.durationMs > 0L) {
        (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    AstraWaveFocusableCard(Modifier.fillMaxWidth().clickable { onOpen(progress.item) }) {
        Row(
            Modifier.fillMaxWidth().background(AstraWaveColors.BackgroundRaised, RoundedCornerShape(24.dp)).padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                HomeBadge(if (progress.item.type == LibraryMediaType.EPISODE) "CONTINUE SERIES" else "CONTINUE")
                Text(progress.item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.displayLarge, maxLines = 2)
                LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                Text("${(ratio * 100).toInt()}% watched • Resume →", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.titleMedium)
            }
            Box(Modifier.width(360.dp)) {
                AstraWaveArtwork(progress.item.title, Modifier.fillMaxWidth(), AstraWaveArtworkKind.Backdrop)
            }
        }
    }
}

@Composable
private fun HomeSportsRow(events: List<SportsGuideItem>, onWatch: (SportsGuideItem) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 20.dp)) {
        items(events, key = { it.event.id }) { item ->
            AstraWaveFocusableCard(
                Modifier
                    .width(310.dp)
                    .clickable(enabled = item.watchCandidate != null) { onWatch(item) },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        if (item.event.isLive) "● LIVE" else if (item.watchCandidate != null) "WATCH READY" else "UPCOMING",
                        color = if (item.event.isLive) AstraWaveColors.Live else if (item.watchCandidate != null) AstraWaveColors.Success else AstraWaveColors.Accent,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(item.event.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                    Text(
                        listOfNotNull(item.event.league, item.event.time).joinToString(" • "),
                        color = AstraWaveColors.SecondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        item.watchCandidate?.let { "${it.channelName} • ${it.source}" } ?: "Broadcast match pending",
                        color = if (item.watchCandidate != null) AstraWaveColors.Success else AstraWaveColors.TertiaryText,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressHomeRow(
    progressItems: List<LocalLibraryStore.PlaybackProgress>,
    badge: String,
    onOpen: (LibraryItemRef) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 20.dp)) {
        items(progressItems, key = { it.item.id }) { progress ->
            val ratio = if (progress.durationMs > 0L) {
                (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f
            AstraWaveFocusableCard(Modifier.width(286.dp).clickable { onOpen(progress.item) }) {
                Column {
                    HomeBadge(badge)
                    Spacer(Modifier.height(7.dp))
                    AstraWaveArtwork(progress.item.title, Modifier.fillMaxWidth(), AstraWaveArtworkKind.Backdrop)
                    Spacer(Modifier.height(9.dp))
                    Text(progress.item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Spacer(Modifier.height(7.dp))
                    LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun HomeBadge(label: String) {
    Text(
        label,
        color = AstraWaveColors.PrimaryText,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.background(AstraWaveColors.Accent, RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun HomeSkeletonRow() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 20.dp)) {
        items(5) { index ->
            Column(Modifier.width(280.dp)) {
                Box(Modifier.fillMaxWidth().height(158.dp).background(AstraWaveColors.BackgroundRaised, RoundedCornerShape(18.dp)))
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width((150 + index * 8).dp).height(18.dp).background(AstraWaveColors.SurfaceFocus, RoundedCornerShape(6.dp)))
            }
        }
    }
}

@Composable
private fun LibraryHomeRow(items: List<LibraryItemRef>, badge: String? = null, onOpen: (LibraryItemRef) -> Unit) {
    val unique = items.distinctBy { it.id }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 20.dp)) {
        items(unique, key = { it.id }) { item ->
            AstraWaveFocusableCard(Modifier.width(196.dp).clickable { onOpen(item) }) {
                Column {
                    badge?.let { HomeBadge(it); Spacer(Modifier.height(7.dp)) }
                    AstraWaveArtwork(item.title, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(9.dp))
                    Text(item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun MetadataHomeRow(
    metadataItems: List<AstraWaveMetadataGateway.Item>,
    badge: String? = null,
    onOpen: (AstraWaveMetadataGateway.Item) -> Unit,
) {
    val unique = metadataItems.distinctBy { "${it.type}:${it.id}" }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 20.dp)) {
        items(unique, key = { "${it.type}:${it.id}" }) { item ->
            AstraWaveFocusableCard(Modifier.width(286.dp).clickable { onOpen(item) }) {
                Column(Modifier.animateContentSize()) {
                    badge?.let { HomeBadge(it); Spacer(Modifier.height(7.dp)) }
                    AstraWaveArtwork(item.name, Modifier.fillMaxWidth(), AstraWaveArtworkKind.Backdrop)
                    Spacer(Modifier.height(9.dp))
                    Text(item.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    item.releaseInfo?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}
