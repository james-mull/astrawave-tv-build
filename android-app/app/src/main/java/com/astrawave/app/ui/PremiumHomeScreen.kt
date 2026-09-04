package com.astrawave.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.astrawave.app.TitleDetailsActivity
import com.astrawave.app.core.LibraryItemRef
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.core.RecommendationCandidate
import com.astrawave.app.core.RecommendationEngine
import com.astrawave.app.core.RecommendationProfile
import com.astrawave.app.data.ArtworkRegistry
import com.astrawave.app.data.AstraWaveMetadataGateway
import com.astrawave.app.data.HomeIntelligenceRepository
import com.astrawave.app.data.LibraryCloudSync
import com.astrawave.app.data.LocalLibraryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

private sealed interface HomeDiscoveryState {
    data object Loading : HomeDiscoveryState
    data class Ready(
        val movies: List<AstraWaveMetadataGateway.Item>,
        val series: List<AstraWaveMetadataGateway.Item>,
    ) : HomeDiscoveryState
    data class Error(val message: String) : HomeDiscoveryState
}

@Composable
fun PremiumHomeScreen(profileId: String = "default") {
    val context = LocalContext.current
    val library = remember { LocalLibraryStore(context) }
    val cloudSync = remember { LibraryCloudSync(context) }
    val metadata = remember { AstraWaveMetadataGateway() }
    val recommender = remember { RecommendationEngine() }
    val intelligence = remember { HomeIntelligenceRepository(context) }
    var libraryRefresh by remember { mutableStateOf(0) }
    var discovery by remember { mutableStateOf<HomeDiscoveryState>(HomeDiscoveryState.Loading) }
    var intelligentRows by remember(profileId) { mutableStateOf<List<HomeIntelligenceRepository.Row>>(emptyList()) }
    var intelligenceLoading by remember(profileId) { mutableStateOf(true) }
    var cloudRestoreMessage by remember { mutableStateOf<String?>(null) }

    val allContinue = remember(profileId, libraryRefresh) { library.continueWatching(profileId).take(30) }
    val continueSeries = remember(allContinue) { allContinue.filter { it.item.type == LibraryMediaType.EPISODE }.take(20) }
    val continueWatching = remember(allContinue) { allContinue.filter { it.item.type != LibraryMediaType.EPISODE }.take(20) }
    val heroProgress = remember(continueSeries, continueWatching) { continueSeries.firstOrNull() ?: continueWatching.firstOrNull() }
    val watchlist = remember(profileId, libraryRefresh) { library.watchlist(profileId).take(24) }
    val favorites = remember(profileId, libraryRefresh) { library.favorites(profileId).take(50) }
    val recent = remember(profileId, libraryRefresh) { library.history(profileId).take(50) }
    val completed = remember(profileId, libraryRefresh) {
        library.progress(profileId).filter { it.completed }.map { it.item.id }.toSet()
    }

    LaunchedEffect(profileId) {
        cloudSync.restore(profileId) { result ->
            result.onSuccess { report ->
                val restored = report.watchlistImported + report.favoritesImported + report.listsImported + report.progressImported
                if (restored > 0) {
                    cloudRestoreMessage = "Synced $restored library updates from your account"
                    intelligence.invalidate(profileId)
                    libraryRefresh += 1
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        discovery = HomeDiscoveryState.Loading
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

    fun metadataLibraryId(item: AstraWaveMetadataGateway.Item): String {
        val type = metadataType(item)
        return when {
            item.id.startsWith("tt", true) -> "stremio:cinemeta:${if (type == LibraryMediaType.SERIES) "series" else "movie"}:${item.id}"
            item.id.toLongOrNull() != null -> "tmdb:${if (type == LibraryMediaType.SERIES) "tv" else "movie"}:${item.id}"
            else -> "metadata:${item.type}:${item.id}"
        }
    }

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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background)
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Text("ASTRAWAVE", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Text("Everything you watch and listen to.", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(7.dp))
        Text(
            "Pick up where you left off, jump into your list, or discover something new without setup friction.",
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.bodyLarge,
        )
        cloudRestoreMessage?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(message, color = AstraWaveColors.Success, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(22.dp))

        heroProgress?.let { progress ->
            HomeHero(progress = progress, onOpen = ::openItem)
            Spacer(Modifier.height(28.dp))
        }

        if (continueSeries.isNotEmpty()) {
            HomeSectionTitle("Continue Series", "Resume the exact episode you left unfinished")
            ProgressHomeRow(continueSeries, badge = "CONTINUE SERIES", onOpen = ::openItem)
            Spacer(Modifier.height(24.dp))
        }

        if (continueWatching.isNotEmpty()) {
            HomeSectionTitle("Continue Watching", "Resume movies and other playback across devices")
            ProgressHomeRow(continueWatching, badge = "CONTINUE", onOpen = ::openItem)
            Spacer(Modifier.height(24.dp))
        }

        if (watchlist.isNotEmpty()) {
            HomeSectionTitle("My Watchlist", "Saved for later")
            LibraryHomeRow(watchlist.map { it.item }, badge = "WATCHLIST", onOpen = ::openItem)
            Spacer(Modifier.height(24.dp))
        }

        if (intelligenceLoading && recent.isNotEmpty() && intelligentRows.isEmpty()) {
            AstraWaveStatePanel(
                "Personalizing your Home…",
                "Connecting your recent viewing to franchises, actors, directors, creators and new episodes.",
                loading = true,
            )
            Spacer(Modifier.height(24.dp))
        } else {
            intelligentRows.forEach { row ->
                HomeSectionTitle(row.title, row.subtitle)
                MetadataHomeRow(row.items, badge = row.badge, onOpen = ::openMetadata)
                Spacer(Modifier.height(24.dp))
            }
        }

        when (val current = discovery) {
            HomeDiscoveryState.Loading -> AstraWaveStatePanel(
                "Building your home…",
                "Loading zero-config discovery and personalizing it from your library.",
                loading = true,
            )
            is HomeDiscoveryState.Error -> AstraWaveStatePanel(
                "Discovery is temporarily unavailable",
                current.message,
            )
            is HomeDiscoveryState.Ready -> {
                val candidateItems = current.movies + current.series
                val byId = candidateItems.associateBy(::metadataLibraryId)
                val currentYear = LocalDate.now().year
                val recentMediaTypes = recent.take(12).map { history ->
                    when (history.item.type) {
                        LibraryMediaType.EPISODE -> LibraryMediaType.SERIES
                        else -> history.item.type
                    }
                }.filter { it == LibraryMediaType.MOVIE || it == LibraryMediaType.SERIES }
                val preferredMediaTypes = recentMediaTypes.groupingBy { it }.eachCount()
                    .filterValues { it >= 2 }
                    .keys
                val profile = RecommendationProfile(
                    profileId = profileId,
                    favoriteItemIds = favorites.map { it.item.id }.toSet(),
                    watchlistItemIds = watchlist.map { it.item.id }.toSet(),
                    completedItemIds = completed,
                    recentItemIds = recent.map { it.item.id },
                    preferredMediaTypes = preferredMediaTypes,
                )
                val candidates = candidateItems.mapIndexed { index, item ->
                    val releaseYear = item.releaseInfo?.take(4)?.toIntOrNull()
                    val freshness = when (releaseYear) {
                        currentYear -> 10.0
                        currentYear - 1 -> 7.0
                        currentYear - 2 -> 4.0
                        else -> 1.0
                    }
                    RecommendationCandidate(
                        id = metadataLibraryId(item),
                        title = item.name,
                        mediaType = metadataType(item),
                        popularityScore = (candidateItems.size - index).coerceAtLeast(1) * 3.0,
                        freshnessScore = freshness,
                        posterUrl = item.posterUrl,
                        metadataSource = "AstraWave",
                    )
                }
                val ranked = recommender.rank(profile, candidates, limit = 18)
                    .mapNotNull { rankedItem -> byId[rankedItem.candidate.id] }

                if (ranked.isNotEmpty() && intelligentRows.none { it.title == "Because You Watched" }) {
                    HomeSectionTitle(
                        if (recent.isNotEmpty()) "Because You Watched" else "For You",
                        if (recent.isNotEmpty()) "Personalized from your recent viewing, saves and favorites" else "AstraWave-ranked picks from what's fresh and trending",
                    )
                    MetadataHomeRow(ranked, badge = "FOR YOU", onOpen = ::openMetadata)
                    Spacer(Modifier.height(24.dp))
                }

                val recentlyAdded = candidateItems
                    .sortedByDescending { it.releaseInfo?.take(10).orEmpty() }
                    .distinctBy { "${it.type}:${it.id}" }
                    .take(20)
                if (recentlyAdded.isNotEmpty()) {
                    HomeSectionTitle("Recently Added", "Fresh movies and series from AstraWave discovery")
                    MetadataHomeRow(recentlyAdded, badge = "NEW", onOpen = ::openMetadata)
                    Spacer(Modifier.height(24.dp))
                }

                HomeSectionTitle("Trending Movies", "Fresh discovery from AstraWave metadata")
                MetadataHomeRow(current.movies, badge = "TRENDING", onOpen = ::openMetadata)
                Spacer(Modifier.height(24.dp))
                HomeSectionTitle("Trending TV", "Series people are watching now")
                MetadataHomeRow(current.series, badge = "TRENDING", onOpen = ::openMetadata)
            }
        }

        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            HomeSectionTitle("Recently Watched", "Your latest playback history")
            LibraryHomeRow(recent.take(24).map { it.item }, badge = "RECENT", onOpen = ::openItem)
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Home updates automatically as you watch, save, and revisit titles. Tap to refresh personalized shelves.",
            color = AstraWaveColors.TertiaryText,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.clickable {
                intelligence.invalidate(profileId)
                libraryRefresh += 1
            },
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun HomeHero(
    progress: LocalLibraryStore.PlaybackProgress,
    onOpen: (LibraryItemRef) -> Unit,
) {
    val ratio = if (progress.durationMs > 0L) {
        (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val isEpisode = progress.item.type == LibraryMediaType.EPISODE
    AstraWaveFocusableCard(
        Modifier.fillMaxWidth().clickable { onOpen(progress.item) },
    ) {
        Row(
            Modifier.fillMaxWidth().background(AstraWaveColors.BackgroundRaised, RoundedCornerShape(22.dp)).padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeBadge(if (isEpisode) "CONTINUE SERIES" else "CONTINUE")
                Text(
                    progress.item.title,
                    color = AstraWaveColors.PrimaryText,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 3,
                )
                Text(
                    if (isEpisode) "Pick up the exact episode you left unfinished." else "Jump back in from where you stopped.",
                    color = AstraWaveColors.SecondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                Text(
                    "${(ratio * 100).toInt()}% watched • Resume now →",
                    color = AstraWaveColors.Accent,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Box(Modifier.width(190.dp)) {
                AstraWaveArtwork(title = progress.item.title, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ProgressHomeRow(
    items: List<LocalLibraryStore.PlaybackProgress>,
    badge: String,
    onOpen: (LibraryItemRef) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { progress ->
            val ratio = if (progress.durationMs > 0L) {
                (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f
            AstraWaveFocusableCard(Modifier.width(220.dp).clickable { onOpen(progress.item) }) {
                Column {
                    HomeBadge(badge)
                    Spacer(Modifier.height(7.dp))
                    AstraWaveArtwork(title = progress.item.title, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(9.dp))
                    Text(progress.item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(5.dp))
                    Text("${(ratio * 100).toInt()}% watched", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun HomeSectionTitle(title: String, subtitle: String) {
    Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(3.dp))
    Text(subtitle, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(10.dp))
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
private fun LibraryHomeRow(
    items: List<LibraryItemRef>,
    badge: String? = null,
    onOpen: (LibraryItemRef) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.distinctBy { it.id }.forEach { item ->
            AstraWaveFocusableCard(Modifier.width(190.dp).clickable { onOpen(item) }) {
                Column {
                    badge?.let { HomeBadge(it); Spacer(Modifier.height(7.dp)) }
                    AstraWaveArtwork(title = item.title, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(9.dp))
                    Text(item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (item.type == LibraryMediaType.EPISODE) "Continue episode" else "Open details",
                        color = AstraWaveColors.Accent,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataHomeRow(
    items: List<AstraWaveMetadataGateway.Item>,
    badge: String? = null,
    onOpen: (AstraWaveMetadataGateway.Item) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.distinctBy { "${it.type}:${it.id}" }.forEach { item ->
            AstraWaveFocusableCard(Modifier.width(190.dp).clickable { onOpen(item) }) {
                Column {
                    badge?.let { HomeBadge(it); Spacer(Modifier.height(7.dp)) }
                    AstraWaveArtwork(title = item.name, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(9.dp))
                    Text(item.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    item.releaseInfo?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        item.description ?: "Open for details and watch options.",
                        color = AstraWaveColors.SecondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                    )
                }
            }
        }
    }
}
