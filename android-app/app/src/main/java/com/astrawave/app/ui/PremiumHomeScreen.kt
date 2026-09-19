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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.PlayerActivity
import com.astrawave.app.PremiumVodDetailActivity
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
    val device = LocalAstraWaveDeviceClass.current
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
    val continueItems = remember(continueSeries, continueWatching) { (continueSeries + continueWatching).distinctBy { it.item.id }.take(18) }
    val heroProgress = remember(continueItems) { continueItems.firstOrNull() }
    val watchlist = remember(profileId, libraryRefresh) { library.watchlist(profileId).take(24) }
    val recent = remember(profileId, libraryRefresh) { library.history(profileId).take(40) }

    LaunchedEffect(profileId) {
        cloudSync.restore(profileId) { result ->
            result.onSuccess { report ->
                val restored = report.watchlistImported + report.favoritesImported + report.listsImported + report.progressImported
                if (restored > 0) {
                    cloudRestoreMessage = "Synced"
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
            (pair.first + pair.second).forEach { item -> ArtworkRegistry.register(item.name, item.posterUrl ?: item.backdropUrl) }
            HomeDiscoveryState.Ready(pair.first, pair.second)
        } catch (error: Exception) {
            HomeDiscoveryState.Error(error.message ?: "Discovery is temporarily unavailable")
        }
    }

    LaunchedEffect(profileId, libraryRefresh) {
        intelligenceLoading = true
        intelligentRows = withContext(Dispatchers.IO) { runCatching { intelligence.rows(profileId) }.getOrDefault(emptyList()) }
        intelligentRows.flatMap { it.items }.forEach { item -> ArtworkRegistry.register(item.name, item.posterUrl ?: item.backdropUrl) }
        intelligenceLoading = false
    }

    LaunchedEffect(profileId) {
        sports = withContext(Dispatchers.IO) {
            val sources = IptvSourceStore(context).load(profileId)
            runCatching {
                val snapshot = sportsRepository.load(LocalDate.now(ZoneOffset.UTC), sources)
                val favorites = sportsPreferences.teams(profileId).map { it.name.lowercase() }.toSet()
                val ranked = snapshot.events.sortedWith(
                    compareByDescending<SportsGuideItem> { it.event.isLive && it.watchCandidate != null }
                        .thenByDescending { item -> listOfNotNull(item.event.homeTeam, item.event.awayTeam).any { it.lowercase() in favorites } }
                        .thenByDescending { it.watchCandidate != null }
                        .thenByDescending { it.event.isLive },
                ).take(12)
                if (ranked.isEmpty()) HomeSportsState.Empty else HomeSportsState.Ready(ranked)
            }.getOrDefault(HomeSportsState.Empty)
        }
    }

    fun openItem(item: LibraryItemRef) {
        if (item.type == LibraryMediaType.EPISODE) {
            context.startActivity(
                Intent(context, TitleDetailsActivity::class.java)
                    .putExtra(TitleDetailsActivity.EXTRA_TITLE, item.title)
                    .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, item.type.name)
                    .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, item.sourceId)
                    .putExtra(TitleDetailsActivity.EXTRA_PROFILE_ID, profileId),
            )
            return
        }
        context.startActivity(
            Intent(context, PremiumVodDetailActivity::class.java)
                .putExtra(PremiumVodDetailActivity.EXTRA_TITLE, item.title)
                .putExtra(PremiumVodDetailActivity.EXTRA_MEDIA_TYPE, item.type.name)
                .putExtra(PremiumVodDetailActivity.EXTRA_SOURCE_ID, item.sourceId)
                .putExtra(PremiumVodDetailActivity.EXTRA_PROFILE_ID, profileId),
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
            Intent(context, PremiumVodDetailActivity::class.java)
                .putExtra(PremiumVodDetailActivity.EXTRA_TITLE, item.name)
                .putExtra(PremiumVodDetailActivity.EXTRA_MEDIA_TYPE, mediaType.name)
                .putExtra(PremiumVodDetailActivity.EXTRA_SOURCE_ID, sourceId)
                .putExtra(PremiumVodDetailActivity.EXTRA_PROFILE_ID, profileId),
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

    val sportsReady = sports as? HomeSportsState.Ready
    val liveFeatured = sportsReady?.events?.firstOrNull { it.event.isLive && it.watchCandidate != null }
    val discoveryFeatured = (discovery as? HomeDiscoveryState.Ready)?.let { ready ->
        (ready.movies + ready.series).firstOrNull()
    }
    val horizontalPadding = if (device == AstraWaveDeviceClass.PHONE) 0.dp else 34.dp

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AstraWaveColors.Background),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(if (device == AstraWaveDeviceClass.PHONE) 16.dp else 30.dp),
    ) {
        when {
            liveFeatured != null -> item(key = "live-sports-hero-${liveFeatured.event.id}") { HomeSportsHero(liveFeatured, ::playSports) }
            heroProgress != null -> item(key = "resume-hero-${heroProgress.item.id}") { HomeHero(heroProgress, ::openItem) }
            discoveryFeatured != null -> item(key = "discovery-hero-${discoveryFeatured.id}") { HomeDiscoveryHero(discoveryFeatured, ::openMetadata) }
            else -> item(key = "cinematic-home-hero") { HomeFallbackHero() }
        }

        if (continueItems.isNotEmpty()) {
            item(key = "continue") {
                HomeSection("Continue Watching") { ProgressHomeRow(continueItems, ::openItem) }
            }
        }

        if (sportsReady != null && sportsReady.events.isNotEmpty()) {
            item(key = "sports-pulse") {
                HomeSection(if (liveFeatured != null) "Live Sports" else "Sports Today") {
                    HomeSportsRow(sportsReady.events, ::playSports)
                }
            }
        }

        if (intelligenceLoading && recent.isNotEmpty() && intelligentRows.isEmpty()) {
            item(key = "intelligence-loading") { HomeSection("For You") { HomeSkeletonRow() } }
        } else {
            items(items = intelligentRows.filter { it.items.isNotEmpty() }, key = { row -> "intel:${row.title}:${row.badge.orEmpty()}" }) { row ->
                HomeSection(row.title) { MetadataHomeRow(row.items, row.badge, ::openMetadata) }
            }
        }

        if (watchlist.isNotEmpty()) {
            item(key = "watchlist") { HomeSection("My List") { LibraryHomeRow(watchlist.map { it.item }, null, ::openItem) } }
        }

        when (val current = discovery) {
            HomeDiscoveryState.Loading -> item(key = "discovery-loading") { HomeSection("Popular Now") { HomeSkeletonRow() } }
            is HomeDiscoveryState.Error -> item(key = "discovery-error") { AstraWavePartialDataState(message = current.message, actionLabel = null) }
            is HomeDiscoveryState.Ready -> {
                if (current.movies.isNotEmpty()) item(key = "trending-movies") {
                    HomeSection("Trending Movies") { MetadataHomeRow(current.movies, null, ::openMetadata) }
                }
                if (current.series.isNotEmpty()) item(key = "trending-series") {
                    HomeSection("Trending TV") { MetadataHomeRow(current.series, null, ::openMetadata) }
                }
                val fresh = (current.movies + current.series)
                    .sortedByDescending { it.releaseInfo?.take(10).orEmpty() }
                    .distinctBy { "${it.type}:${it.id}" }
                    .take(20)
                if (fresh.isNotEmpty()) item(key = "fresh") {
                    HomeSection("New This Week") { MetadataHomeRow(fresh, "NEW", ::openMetadata) }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item(key = "recent") { HomeSection("Watch Again") { LibraryHomeRow(recent.take(24).map { it.item }, null, ::openItem) } }
        }

        item(key = "refresh") {
            Text(
                "Refresh",
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clickable {
                    intelligence.invalidate(profileId)
                    libraryRefresh += 1
                }.padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun HomeFallbackHero() {
    val phone = LocalAstraWaveDeviceClass.current == AstraWaveDeviceClass.PHONE
    Box(
        Modifier.fillMaxWidth()
            .height(if (phone) 450.dp else 500.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        AstraWaveColors.BackgroundRaised,
                        AstraWaveColors.SurfaceFocus.copy(alpha = 0.78f),
                        AstraWaveColors.Background,
                    ),
                ),
            ),
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        AstraWaveColors.Background.copy(alpha = 0.18f),
                        AstraWaveColors.Background,
                    ),
                ),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomStart)
                .padding(horizontal = if (phone) 18.dp else 34.dp, vertical = if (phone) 24.dp else 36.dp)
                .width(if (phone) 350.dp else 760.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text("ASTRAWAVE", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.labelLarge)
            Text(
                "Your entertainment, without the dashboard feel.",
                color = AstraWaveColors.PrimaryText,
                style = if (phone) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayLarge,
                maxLines = 2,
            )
            Text(
                "Movies • TV • Live • Sports",
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Pick up where you left off or drop straight into what is happening now.",
                color = AstraWaveColors.TertiaryText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun HomeSportsHero(item: SportsGuideItem, onWatch: (SportsGuideItem) -> Unit) {
    val phone = LocalAstraWaveDeviceClass.current == AstraWaveDeviceClass.PHONE
    Box(
        Modifier.fillMaxWidth()
            .height(if (phone) 420.dp else 500.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        AstraWaveColors.SurfaceFocus.copy(alpha = 0.68f),
                        AstraWaveColors.BackgroundRaised.copy(alpha = 0.92f),
                        AstraWaveColors.Background,
                    ),
                ),
            )
            .clickable { onWatch(item) },
    ) {
        Column(
            Modifier.align(Alignment.BottomStart)
                .padding(horizontal = if (phone) 18.dp else 34.dp, vertical = if (phone) 24.dp else 38.dp)
                .width(if (phone) 350.dp else 760.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text("● LIVE NOW", color = AstraWaveColors.Live, style = MaterialTheme.typography.labelLarge)
            Text(
                item.event.name,
                color = AstraWaveColors.PrimaryText,
                style = if (phone) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayLarge,
                maxLines = 2,
            )
            Text(
                listOfNotNull(item.event.league, item.watchCandidate?.channelName).joinToString(" • "),
                color = AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text("WATCH  ▶", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun HomeDiscoveryHero(item: AstraWaveMetadataGateway.Item, onOpen: (AstraWaveMetadataGateway.Item) -> Unit) {
    val phone = LocalAstraWaveDeviceClass.current == AstraWaveDeviceClass.PHONE
    val hero: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(if (phone) 440.dp else 560.dp).clickable { onOpen(item) }) {
            AstraWaveArtwork(item.name, Modifier.fillMaxSize(), AstraWaveArtworkKind.Backdrop)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(AstraWaveColors.Background.copy(alpha = 0.00f), AstraWaveColors.Background.copy(alpha = 0.28f), AstraWaveColors.Background)),
                ),
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(horizontal = if (phone) 18.dp else 30.dp, vertical = if (phone) 20.dp else 30.dp)
                    .width(if (phone) 340.dp else 720.dp),
                verticalArrangement = Arrangement.spacedBy(if (phone) 6.dp else 9.dp),
            ) {
                Text(item.name, color = AstraWaveColors.PrimaryText, style = if (phone) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayLarge, maxLines = 2)
                item.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = if (phone) 2 else 3)
                }
                Text("Details", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
    hero()
}

@Composable
private fun HomeSection(title: String, content: @Composable () -> Unit) {
    val phone = LocalAstraWaveDeviceClass.current == AstraWaveDeviceClass.PHONE
    Column(
        Modifier.fillMaxWidth().animateContentSize()
            .padding(horizontal = if (phone) 16.dp else 0.dp)
    ) {
        Text(title, color = AstraWaveColors.PrimaryText, style = if (phone) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(if (phone) 7.dp else 10.dp))
        content()
    }
}

@Composable
private fun HomeHero(progress: LocalLibraryStore.PlaybackProgress, onOpen: (LibraryItemRef) -> Unit) {
    val ratio = if (progress.durationMs > 0L) (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val phone = LocalAstraWaveDeviceClass.current == AstraWaveDeviceClass.PHONE
    val hero: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(if (phone) 410.dp else 420.dp).clickable { onOpen(progress.item) }) {
            AstraWaveArtwork(progress.item.title, Modifier.fillMaxSize(), AstraWaveArtworkKind.Backdrop)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(AstraWaveColors.Background.copy(alpha = 0.00f), AstraWaveColors.Background.copy(alpha = 0.32f), AstraWaveColors.Background)),
                ),
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(horizontal = if (phone) 18.dp else 30.dp, vertical = if (phone) 20.dp else 30.dp)
                    .width(if (phone) 340.dp else 700.dp),
                verticalArrangement = Arrangement.spacedBy(if (phone) 6.dp else 9.dp),
            ) {
                HomeBadge(if (progress.item.type == LibraryMediaType.EPISODE) "CONTINUE SERIES" else "CONTINUE WATCHING")
                Text(progress.item.title, color = AstraWaveColors.PrimaryText, style = if (phone) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayLarge, maxLines = 2)
                LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                Text("Resume", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
    hero()
}

@Composable
private fun HomeSportsRow(events: List<SportsGuideItem>, onWatch: (SportsGuideItem) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 20.dp)) {
        items(events, key = { it.event.id }) { item ->
            AstraWaveFocusableCard(Modifier.width(300.dp).clickable(enabled = item.watchCandidate != null) { onWatch(item) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (item.event.isLive) "● LIVE" else if (item.watchCandidate != null) "WATCH READY" else eventTimeLabel(item),
                        color = if (item.event.isLive) AstraWaveColors.Live else if (item.watchCandidate != null) AstraWaveColors.Success else AstraWaveColors.TertiaryText,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(item.event.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                    Text(listOfNotNull(item.event.league, item.event.time).joinToString(" • "), color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                    if (item.watchCandidate != null) {
                        Text("▶ Watch", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private fun eventTimeLabel(item: SportsGuideItem): String = item.event.time?.takeIf { it.isNotBlank() } ?: "UPCOMING"

@Composable
private fun ProgressHomeRow(progressItems: List<LocalLibraryStore.PlaybackProgress>, onOpen: (LibraryItemRef) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 20.dp)) {
        items(progressItems, key = { it.item.id }) { progress ->
            val ratio = if (progress.durationMs > 0L) (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
            AstraWaveFocusableCard(Modifier.width(310.dp).clickable { onOpen(progress.item) }) {
                Column {
                    AstraWaveArtwork(progress.item.title, Modifier.fillMaxWidth(), AstraWaveArtworkKind.Backdrop)
                    Spacer(Modifier.height(8.dp))
                    Text(progress.item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(5.dp))
                    Text("Resume", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.labelSmall)
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
        modifier = Modifier.background(AstraWaveColors.AccentSoft.copy(alpha = 0.88f), RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun HomeSkeletonRow() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 20.dp)) {
        items(6) { index ->
            Column(Modifier.width(if (LocalAstraWaveDeviceClass.current == AstraWaveDeviceClass.PHONE) 148.dp else 196.dp)) {
                Box(Modifier.fillMaxWidth().height(if (LocalAstraWaveDeviceClass.current == AstraWaveDeviceClass.PHONE) 222.dp else 294.dp).background(AstraWaveColors.BackgroundRaised, RoundedCornerShape(18.dp)))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width((130 + index * 7).dp).height(16.dp).background(AstraWaveColors.SurfaceFocus, RoundedCornerShape(6.dp)))
            }
        }
    }
}

@Composable
private fun LibraryHomeRow(items: List<LibraryItemRef>, badge: String? = null, onOpen: (LibraryItemRef) -> Unit) {
    val unique = items.distinctBy { it.id }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 20.dp)) {
        items(unique, key = { it.id }) { item ->
            val phone = LocalAstraWaveDeviceClass.current == AstraWaveDeviceClass.PHONE
            val body: @Composable () -> Unit = {
                Column(Modifier.width(if (phone) 148.dp else 196.dp).clickable { onOpen(item) }) {
                    badge?.let { HomeBadge(it); Spacer(Modifier.height(6.dp)) }
                    AstraWaveArtwork(item.title, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(if (phone) 6.dp else 8.dp))
                    Text(item.title, color = AstraWaveColors.PrimaryText, style = if (phone) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium, maxLines = 2)
                }
            }
            if (phone) body() else AstraWaveFocusableCard(Modifier.width(196.dp)) { body() }
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
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 20.dp)) {
        items(unique, key = { "${it.type}:${it.id}" }) { item ->
            val phone = LocalAstraWaveDeviceClass.current == AstraWaveDeviceClass.PHONE
            val body: @Composable () -> Unit = {
                Column(Modifier.width(if (phone) 148.dp else 196.dp).clickable { onOpen(item) }.animateContentSize()) {
                    badge?.let { HomeBadge(it); Spacer(Modifier.height(6.dp)) }
                    AstraWaveArtwork(item.name, Modifier.fillMaxWidth(), AstraWaveArtworkKind.Poster)
                    Spacer(Modifier.height(if (phone) 6.dp else 8.dp))
                    Text(item.name, color = AstraWaveColors.PrimaryText, style = if (phone) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium, maxLines = 2)
                    item.releaseInfo?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
            if (phone) body() else AstraWaveFocusableCard(Modifier.width(196.dp)) { body() }
        }
    }
}
